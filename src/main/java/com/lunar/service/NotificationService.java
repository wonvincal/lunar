package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lmax.disruptor.EventHandler;
import com.lunar.config.NotificationServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.SubscriberList;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Parameter;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.EventSbeDecoder.EventValuesDecoder;
import com.lunar.message.io.sbe.EventSbeEncoder;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.util.MutableDirectBufferEvent;
import com.lunar.util.ObjectCircularBuffer;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * Keep track of all events
 * Support
 * GET
 * SUBSCRIBE
 * 
 * Each message arrives should be broadcasted to subscribing services
 * 
 * Should maintain a fixed size of buffer, overwrite when exceed caps
 * 
 * Persist to file
 * 
 * @author wongca
 *
 */
public class NotificationService implements ServiceLifecycleAware {
	static final Logger LOG = LogManager.getLogger(NotificationService.class);

    private final LunarService messageService;
    private final MessageSinkRefMgr refMgr;
    private final Messenger messenger;
    private final String name;

    private final ObjectCircularBuffer<MutableDirectBufferEvent> allEventBuffer;
    private final ObjectCircularBuffer<MutableDirectBufferEvent> snapshotEventBuffer;
    private final SubscriberList subscribers;
    private Duration publishFrequency;
    private final AtomicReference<TimeoutHandlerTimerTask> publishSnapshotTask;
    private final int CLIENT_KEY_FOR_PUBLISHING = 99;
    private final int allEventBufferPurgeCountIfFull;
    private final int snapshotBufferPurgeCountIfFull;
    
	private static long THROTTLED_EVENT_SEND_THROTTLE_DURATION_NS = 150_000_000l; // 150 ms
    private final EventSbeDecoder eventDecoder = new EventSbeDecoder();
    private final Long2LongOpenHashMap lastSentThrottledEventTimeBySecId;

    public static NotificationService of(final ServiceConfig config, final LunarService messageService) {
        return new NotificationService(config, messageService);
    }

    public NotificationService(final ServiceConfig config, final LunarService messageService){
        this.name = config.name();
        this.messageService = messageService;
        this.messenger = messageService.messenger();
        this.refMgr = this.messenger.referenceManager();
        if (config instanceof NotificationServiceConfig) {
			final NotificationServiceConfig specificConfig = (NotificationServiceConfig)config;
			this.publishFrequency = specificConfig.publishFrequency();
			this.allEventBuffer = ObjectCircularBuffer.of(specificConfig.allEventBufferSize(), MutableDirectBufferEvent.FACTORY_INSTANCE);
			this.snapshotEventBuffer = ObjectCircularBuffer.of(specificConfig.snapshotEventBufferSize(), MutableDirectBufferEvent.FACTORY_INSTANCE);
			this.publishFrequency = specificConfig.publishFrequency();
			this.allEventBufferPurgeCountIfFull = specificConfig.allEventBufferPurgeCountIfFull();
			this.snapshotBufferPurgeCountIfFull = specificConfig.snapshotEventBufferPurgeCountIfFull();
        }
        else {
            throw new IllegalArgumentException("Service " + this.name + " expects a NotificationServiceConfig");
        }
        this.subscribers = SubscriberList.of();
        this.publishSnapshotTask = new AtomicReference<>();
        this.lastSentThrottledEventTimeBySecId = new Long2LongOpenHashMap(4000);
    }
    
    @Override
    public StateTransitionEvent idleStart() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackAggregatedServiceStatus((final boolean status) -> {
            if (status){
                messageService.stateEvent(StateTransitionEvent.READY);
            }
            else { // DOWN or INITIALIZING
                messageService.stateEvent(StateTransitionEvent.WAIT);
            }
        });
        return StateTransitionEvent.WAIT;        
    }

	@Override
	public StateTransitionEvent readyEnter() {
		return StateTransitionEvent.ACTIVATE;
	}

    @Override
    public StateTransitionEvent activeEnter() {
    	messenger.receiver().requestHandlerList().add(requestHandler);
    	messenger.receiver().eventHandlerList().add(eventHandler);
    	messenger.receiver().timerEventHandlerList().add(timerEventHandler);
    	
		this.publishSnapshotTask.set(messenger.timerService().createTimerTask(publishSnapshots, "ns-publish-snapshots"));
		messenger.timerService().newTimeout(publishSnapshotTask.get(), publishFrequency.toMillis(), TimeUnit.MILLISECONDS);
		return StateTransitionEvent.NULL;
    }
    
    @Override
    public void activeExit() {
		messenger.receiver().requestHandlerList().remove(requestHandler);
		messenger.receiver().eventHandlerList().remove(eventHandler);
		messenger.receiver().timerEventHandlerList().remove(timerEventHandler);
	}

    @Override
    public StateTransitionEvent stopEnter() {
        return StateTransitionEvent.NULL;
    }

    @Override
    public StateTransitionEvent stoppedEnter() {
        return StateTransitionEvent.NULL;
    }

    private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
			byte senderSinkId = header.senderSinkId();
	    	try {
				ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
				switch (request.requestType()){
				case GET:
					handleGetRequest(senderSinkId, request, parameters);
					break;
				case SUBSCRIBE:
					handleSubscriptionRequest(senderSinkId, request, parameters);
					break;
				case UNSUBSCRIBE:
					handleUnsubscriptionRequest(senderSinkId, request, parameters);
					break;
 				default:
					LOG.error("Unsupported operation [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
					messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
					break;
				}			
			} 
	    	catch (UnsupportedEncodingException e) {
	    		LOG.error("Caught exception while processing request [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
	    		messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			}
		}    
	};

	private void handleGetRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
		ImmutableList<Parameter> dataTypes = parameters.get(ParameterType.DATA_TYPE);
		if (dataTypes.size() != 1){
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			LOG.warn("Invalid get request.  Exact one data type is required [{}]", messenger.decodeToString(request));
			return;
		}
		
		DataType dataType = DataType.get(dataTypes.get(0).valueLong().byteValue());
		MessageSinkRef sink = this.refMgr.get(senderSinkId);
		int clientKey = request.clientKey();
		// Send back responses per channel
		try {
			switch (dataType){
			case ALL_EVENT:
			{
				int numMessages =
				allEventBuffer.peekTillEmpty(new EventHandler<MutableDirectBufferEvent>() {
					@Override
					public void onEvent(MutableDirectBufferEvent event, long sequence, boolean endOfBatch) throws Exception {
						messenger.responseSender().sendEvent(sink, clientKey, (int)sequence, event.buffer(), event.length());
					}
				});
				messenger.responseSender().sendResponse(sink, clientKey, BooleanType.TRUE, numMessages + 1, ResultType.OK);
				break;
			}
			default:
				LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
				return;
			}
		}
		catch (Exception ex){
			LOG.error("Caught exception [clientKey:" + request.clientKey() + ", dataType:" + dataType.name() + "]", ex);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}
	
    private void handleSubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
		ImmutableList<Parameter> dataTypes = parameters.get(ParameterType.DATA_TYPE);
		if (dataTypes.size() != 1){
			StringBuilder builder = new StringBuilder();
			dataTypes.forEach(builder::append);
			LOG.error("Number of subscription types must be 1 [{}]", builder);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
		
		MessageSinkRef sink = refMgr.get(senderSinkId);
		DataType dataType = DataType.get(dataTypes.get(0).valueLong().byteValue());
		switch (dataType){
		case ALL_EVENT:
			LOG.info("Start subscribing to event snapshots [sinkId:{}, clientKey:{}]", senderSinkId, request.clientKey());
			subscribers.add(sink);
			break;
		default:
			LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		return;
	}
	
    private void handleUnsubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
		ImmutableList<Parameter> dataTypes = parameters.get(ParameterType.DATA_TYPE);
		MessageSinkRef sink = refMgr.get(senderSinkId);
		
		if (dataTypes.size() != 1){
			LOG.error("Invalid unsubscription request.  Exact one data type is required. [{}]clientKey:" + request.clientKey() + ", numDataTypes:"+ dataTypes.size() + "]");
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		
		DataType dataType = DataType.get(dataTypes.get(0).valueLong().byteValue());
		switch (dataType){
		case ALL_EVENT:
			if (subscribers.remove(sink)){
				LOG.info("Unsubscribed from event subscription [sinkId:{}]", senderSinkId);
			}
			else{
				LOG.info("Tried to unsubscribe a non-exist event subscription [sinkId:{}]", senderSinkId);
			}
			break;
		default:
			LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		return;
	}
    
    private final Handler<EventSbeDecoder> eventHandler = new Handler<EventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder event) {
			// Store messages in both all event buffer and snapshot buffer
			storeEvent(allEventBuffer, buffer, offset + header.encodedLength(), header.payloadLength(), allEventBufferPurgeCountIfFull);
			storeEvent(snapshotEventBuffer, buffer, offset + header.encodedLength(), header.payloadLength(), snapshotBufferPurgeCountIfFull);
		}
    };
    
    private static void storeEvent(ObjectCircularBuffer<MutableDirectBufferEvent> buffer, DirectBuffer event, int offset, int eventLength, int purgeCountIfFull){
		try {
			MutableDirectBufferEvent claimed = buffer.claim();
			claimed.size(eventLength);
			claimed.buffer().putBytes(0, event, offset, eventLength);
		}
		catch (BufferOverflowException be){
			purgeEvent(buffer, purgeCountIfFull);
			MutableDirectBufferEvent claimed = buffer.claim();
			claimed.size(eventLength);
			claimed.buffer().putBytes(0, event, offset, eventLength);
		}    	
    }
    
    private static EventHandler<MutableDirectBufferEvent> NOOP = (MutableDirectBufferEvent event, long sequence, boolean endOfBatch) -> {};
    
    private static void purgeEvent(ObjectCircularBuffer<MutableDirectBufferEvent> buffer, int count){
		try {
			buffer.flush(count, NOOP);
			LOG.warn("Purge event because buffer is full [numEvents:{}]", count);
		} 
		catch (Exception e) {
			LOG.error("Caught exception when purging event from all event buffer", e);
		}    	
    }

    private long[] sinkSendResults = new long[ServiceConstant.MAX_SUBSCRIBERS];

    private void publishSnapshots(){
    	try {
			snapshotEventBuffer.flushTillEmpty(snapshotPublisher);
		} 
    	catch (Exception e) {
    		LOG.error("Caught exception when sending events out to subscribers", e);
		}
    }
    
    private final EventHandler<MutableDirectBufferEvent> snapshotPublisher = new EventHandler<MutableDirectBufferEvent>() {
		@Override
		public void onEvent(MutableDirectBufferEvent event, long sequence, boolean endOfBatch) throws Exception {
			boolean send = true;
			eventDecoder.wrap(event.buffer(), 0, EventSbeEncoder.BLOCK_LENGTH, EventSbeEncoder.SCHEMA_VERSION);
			if (eventDecoder.eventType() == EventType.THROTTLED){
				EventValuesDecoder eventValues = eventDecoder.eventValues();
				for (EventValuesDecoder eventValue : eventValues){
					if (eventValue.type() == EventValueType.SECURITY_SID){
						// for each security_sid
						long secSid = eventValue.value();
						long lastThrottledEventTime = lastSentThrottledEventTimeBySecId.get(secSid);
						if (eventDecoder.time() - lastThrottledEventTime > THROTTLED_EVENT_SEND_THROTTLE_DURATION_NS){
							lastSentThrottledEventTimeBySecId.put(secSid, eventDecoder.time());
						}
						else{
							send = false;
						}
						break;
					}
				}
			}
			
			if (send){
				messenger.trySendWithHeaderInfo(subscribers, 
						EventSbeEncoder.BLOCK_LENGTH, 
						EventSbeEncoder.TEMPLATE_ID, 
						EventSbeEncoder.SCHEMA_ID, 
						EventSbeEncoder.SCHEMA_VERSION, 
						event.buffer(), 
						0, 
						event.length(), 
						sinkSendResults);
			}
		}
	};
    
    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
	    	if (codec.timerEventType() == TimerEventType.TIMER && codec.clientKey() == CLIENT_KEY_FOR_PUBLISHING){
	    		publishSnapshots();
	    		messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
	    	}
		}
	};
	
    private final TimeoutHandler publishSnapshots = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			// Retry in a moment
			// TODO Stop after N attempt
			messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_PUBLISHING, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
			}
		}
	};
	
	ObjectCircularBuffer<MutableDirectBufferEvent> allEventBuffer(){
		return allEventBuffer;
	}
	
	ObjectCircularBuffer<MutableDirectBufferEvent> snapshotEventBuffer(){
		return snapshotEventBuffer;
	}
}

