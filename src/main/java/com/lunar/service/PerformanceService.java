package com.lunar.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.config.PerformanceServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.MessageSinkRefList;
import com.lunar.core.TriggerInfo;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.GenericTrackerSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TrackerStepType;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import static org.apache.logging.log4j.util.Unbox.box;

public class PerformanceService implements ServiceLifecycleAware {
    private static final Logger LOG = LogManager.getLogger(PerformanceService.class);
    
    private final LunarService messageService;
    private final Messenger messenger;
    private final String name;
    private final ByteArrayOutputStream outputByteStream;
    private final PrintStream outputStream;
    private Timeout logTimerTask;
    private final Messenger logMessenger;
    private final long logIntervalNs;
    
    public static PerformanceService of(final ServiceConfig config, final LunarService messageService) {
        return new PerformanceService(config, messageService);
    }

    public PerformanceService(final ServiceConfig config, final LunarService messageService){
        this.name = config.name();
        this.messageService = messageService;
        this.messenger = messageService.messenger();
        this.logMessenger = this.messenger.createChildMessenger();
        if (config instanceof PerformanceServiceConfig) {
            final PerformanceServiceConfig specificConfig = (PerformanceServiceConfig)config;
            logIntervalNs = specificConfig.statGatheringFreqNs();
        }
        else {
            throw new IllegalArgumentException("Service " + this.name + " expects a PerformanceServiceConfig config");
        }
        this.outputByteStream = new ByteArrayOutputStream(); 
        this.outputStream = new PrintStream(outputByteStream);
    }
    @Override
    public StateTransitionEvent idleStart() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataSnapshotService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.StrategyService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderManagementAndExecutionService);
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
        messenger.receiver().genericTrackerHandlerList().add(genericTrackerHandler);
        messenger.receiver().commandHandlerList().add(commandHandler);

        CompletableFuture<Request> mdsRequestFuture = messenger.sendRequest(messenger.referenceManager().mds(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.GENERIC_TRACKER.value())).build(),
                ResponseHandler.NULL_HANDLER);
        mdsRequestFuture.thenAccept((r) -> { 
            CompletableFuture<Request> mdsssRequestFuture = messenger.sendRequest(messenger.referenceManager().mdsss(),
                    RequestType.SUBSCRIBE,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.GENERIC_TRACKER.value())).build(),
                    ResponseHandler.NULL_HANDLER);
            mdsssRequestFuture.thenAccept((r2) -> { 
                CompletableFuture<Request> omesRequestFuture = messenger.sendRequest(messenger.referenceManager().omes(),
                        RequestType.SUBSCRIBE,
                        new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.GENERIC_TRACKER.value())).build(),
                        ResponseHandler.NULL_HANDLER);
                omesRequestFuture.thenAccept((r3) -> {
                	MessageSinkRefList strats = messenger.referenceManager().strats();
                	if (strats.size() > 0){
                		AtomicInteger expectedAcks = new AtomicInteger(strats.size());
                		for (int i = 0; i < strats.size(); i++){
                            CompletableFuture<Request> stratRequestFuture = messenger.sendRequest(strats.elements()[i],
                                    RequestType.SUBSCRIBE,
                                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.GENERIC_TRACKER.value())).build(),
                                    ResponseHandler.NULL_HANDLER);
                            stratRequestFuture.thenAccept((r4) -> {
                            	if (expectedAcks.decrementAndGet() == 0){
                                    messageService.stateEvent(StateTransitionEvent.ACTIVATE);
                                    LOG.info("Received subscription ack from all strategies");
                            	}
                            	else {
                            		LOG.info("Received subscription ack from strategy");
                            	}
                            }).exceptionally(new Function<Throwable, Void>() {
                                @Override
                                public Void apply(Throwable t) {
                                    LOG.error("Failed to subscribe to strat!");
                                    return null;
                                }
                            });
                		}                		
                	}
                	else {
                		messageService.stateEvent(StateTransitionEvent.ACTIVATE);
                	}
                }).exceptionally(new Function<Throwable, Void>() {
                    @Override
                    public Void apply(Throwable t) {
                        LOG.error("Failed to subscribe to omes!");
                        return null;
                    }
                });
            }).exceptionally(new Function<Throwable, Void>() {
                @Override
                public Void apply(Throwable t) {
                    LOG.error("Failed to subscribe to mdsss!");
                    return null;
                }
            });         
        }).exceptionally(new Function<Throwable, Void>() {
            @Override
            public Void apply(Throwable t) {
                LOG.error("Failed to subscribe to mds!");
                return null;
            }
        });

        return StateTransitionEvent.NULL;
    }

    @Override
    public StateTransitionEvent activeEnter() {
        final Command command = Command.of(logMessenger.self().sinkId(), 0, CommandType.GATHER_PERF_STAT, new ArrayList<Parameter>(), BooleanType.TRUE);
        logTimerTask = messageService.messenger().timerService().newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
                logMessenger.sendCommand(logMessenger.self(), command);
                logTimerTask = messageService.messenger().timerService().newTimeout(this, logIntervalNs, TimeUnit.NANOSECONDS);                
            }
        }, logIntervalNs, TimeUnit.NANOSECONDS);
        return StateTransitionEvent.NULL;
    }

    @Override
    public void activeExit() {
        if (logTimerTask != null) {
            logTimerTask.cancel();
        }        
        messenger.receiver().genericTrackerHandlerList().remove(genericTrackerHandler);
        messenger.receiver().commandHandlerList().remove(commandHandler);
        messenger.unregisterEventsForOrderTracker();
    }

    private class GenericTracker {        
        final private byte sendBy;
        final private TrackerStepType stepId;
        final private long timestamp;
        final private TriggerInfo triggerInfo;
        
        public GenericTracker(final byte sendBy, final TrackerStepType stepId, final long timestamp, final TriggerInfo triggerInfo) {
        	this.sendBy = sendBy;
        	this.stepId = stepId;
        	this.timestamp = timestamp;
        	this.triggerInfo = triggerInfo;
        }
        
        @SuppressWarnings("unused")
		public byte sendBy() {
        	return sendBy;
        }
        
        public TrackerStepType stepId() {
        	return stepId;
        }
        
        public TriggerInfo triggerInfo() {
        	return triggerInfo;
        }
        
        public long timestamp() {
        	return timestamp;
        }
    }
    
    private class StartTimeHolder {
        private TrackerStepType trackerType;
        private long[] startTimes;
        
        public StartTimeHolder(final TrackerStepType trackerType) {
            this.trackerType = trackerType;
            this.startTimes = new long[EXPECTED_ENTRIES];
        }
        
        public TrackerStepType trackerType() {
            return this.trackerType;
        }
        
        public long[] startTimes() {
            return this.startTimes;
        }
    }

    private Int2ObjectOpenHashMap<StartTimeHolder> startTimesByTriggeredBy = new Int2ObjectOpenHashMap<StartTimeHolder>(10);
    
    private Object2ObjectOpenHashMap<TrackerStepType, Histogram> histogramsByStep = new Object2ObjectOpenHashMap<TrackerStepType, Histogram>(10);
    // ring buffer to keep track of the start timestamps 
    private static final int EXPECTED_ENTRIES = 65536; // 2^16
    private static final int MASK = EXPECTED_ENTRIES - 1;
    private static final long MAX_DELAY = 1000000000;
    private static final long WARN_DELAY = 1000000;
    
    private final Handler<GenericTrackerSbeDecoder> genericTrackerHandler = new Handler<GenericTrackerSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, GenericTrackerSbeDecoder codec) {
	        final TriggerInfo triggerInfo = TriggerInfo.of(codec.triggerInfo());
	        if (triggerInfo.triggerNanoOfDay() != 0) {
    	        final GenericTracker tracker = new GenericTracker(codec.sendBy(), codec.stepId(), codec.timestamp(), triggerInfo);
    	        if (!startTimesByTriggeredBy.containsKey((int)tracker.triggerInfo.triggeredBy())) {
    	        	final StartTimeHolder startTimeHolder = new StartTimeHolder(tracker.stepId);
    	            startTimesByTriggeredBy.put((int)tracker.triggerInfo.triggeredBy(), startTimeHolder);
    	        }
    	        final StartTimeHolder startTimeHolder = startTimesByTriggeredBy.get((int)tracker.triggerInfo.triggeredBy());
    	        final int index = tracker.triggerInfo().triggerSeqNum() & MASK;
    	        
    	        if (tracker.stepId != startTimeHolder.trackerType()) {
    	        	final long startTimestamp = startTimeHolder.startTimes()[index];
    	        	if (!histogramsByStep.containsKey(tracker.stepId())) {
    	        		histogramsByStep.put(tracker.stepId(), new Histogram(MAX_DELAY, 1));
    	        	}
    	        	final long delay = tracker.timestamp() - startTimestamp;
    	        	if (tracker.stepId.equals(TrackerStepType.SENDING_NEWORDERREQUEST) || tracker.stepId.equals(TrackerStepType.SENDING_ORDER)) {
    	        	    LOG.debug("Delay: Step: {}, TriggeredBy: {}, SeqNum: {}, Delay: {}", tracker.stepId(), box(tracker.triggerInfo().triggeredBy()), box(tracker.triggerInfo().triggerSeqNum()), box(delay));
    	        	}
    	        	else if (LOG.isTraceEnabled()) {
    	        	    LOG.trace("Delay: Step: {}, TriggeredBy: {}, SeqNum: {}, Delay: {}", tracker.stepId(), box(tracker.triggerInfo().triggeredBy()), box(tracker.triggerInfo().triggerSeqNum()), box(delay));
    	        	}
    	        	try {
    	        	    histogramsByStep.get(tracker.stepId()).recordValue(delay);
    	        	}
    	        	catch (final Exception e) {
    	        	    LOG.error("Error when adding entry to performance histogram for step Id: {}, recordValue: {}...", tracker.stepId(), box(delay));
    	        	}
    	        	if (delay > WARN_DELAY) {
    	        	    LOG.trace("Bad Delay: Step: {}, TriggeredBy: {}, SeqNum: {}, Delay: {}", tracker.stepId(), box(tracker.triggerInfo().triggeredBy()), box(tracker.triggerInfo().triggerSeqNum()), box(delay));
    	        	}
    	        }
    	        else {
    	            LOG.trace("Delay: Step: {}, TriggeredBy: {}, SeqNum: {}, Delay: {}", tracker.stepId(), box(tracker.triggerInfo().triggeredBy()), box(tracker.triggerInfo().triggerSeqNum()), box(0));
    	            startTimeHolder.startTimes()[index] = tracker.timestamp();
    	        }
	        }
		}
	};
    
    private Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
            byte senderSinkId = header.senderSinkId();
            try {
                switch (codec.commandType()){
                case GATHER_PERF_STAT: { 
                    for (final TrackerStepType step : histogramsByStep.keySet()) {
                        outputStream.println("Histogram for: " + step);
                        outputStream.println("=============================");
                        final Histogram histogram = histogramsByStep.get(step);
                        if (histogram != null) {
                            histogram.outputPercentileDistribution(outputStream, 1, 1.0);
                        }
                        outputStream.println();
                    }
                    LOG.info("Performance Summary\n{}", outputByteStream.toString("UTF8"));
                    outputByteStream.reset();
                }
                break;
                default:
                    break;
                }
            }
            catch (final Exception e) {
                LOG.error("Failed to handle command", e);
                messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            }            
        }
    };	

}
