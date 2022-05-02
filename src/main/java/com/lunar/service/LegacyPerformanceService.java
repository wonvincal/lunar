package com.lunar.service;

import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.PerformanceServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.PerfDataManager;
import com.lunar.core.PerfDataManager.PerfData;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.PingDecoder;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.EchoSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PingSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Performance service to gather statistics from different sources and to send
 * message to compute latency. It gathers info about communication between
 * different ring buffers and aeron pub/sub and reports back to its parent.
 * 
 * Should support the following: 1. measure latency by sending ping messages to
 * different services 2. periodically sending messages to different services to
 * request for performance data performance data that relies on individual
 * service are: a. throughput - for ring buffer, (current seq - previous seq) =
 * message processed b. number messages in buffer c. number of threads running
 * d. cpu of machine (using kamon.io) e. memory of process f. hash table size
 * 
 * 
 * * Record performance related data. It can: 1. provide snapshot 2. provide
 * notifications
 * 
 * Support
 * 
 * @author Calvin
 *
 */
public class LegacyPerformanceService implements ServiceLifecycleAware {
	private static final long DEFAULT_STAT_GATHERING_FREQ_NS = 10_000_000_000l;
	static final Logger LOG = LogManager.getLogger(LegacyPerformanceService.class);
	private final Int2ObjectMap<MessageSinkRef> subscribers;
	private final PerfDataManager perfDataManager;
	private long statGatheringFreqNs;
	private Timeout statGatheringTimeout;
	@SuppressWarnings("unused")
	private Frame statGatheringCommand;
	private final Histogram histogram = new Histogram(10000000000L, 4);

	private LunarService messageService;
	private Messenger messenger;
	private final String name;

	public LegacyPerformanceService(ServiceConfig config, LunarService messageService) {
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger(); 
		this.subscribers = new Int2ObjectArrayMap<MessageSinkRef>();
		this.perfDataManager = new PerfDataManager();
		if (config instanceof PerformanceServiceConfig) {
			statGatheringFreqNs = ((PerformanceServiceConfig) config).statGatheringFreqNs();
		} else {
			statGatheringFreqNs = DEFAULT_STAT_GATHERING_FREQ_NS;
		}
		// pre-encode a statistic gathering command
		// actually, we can pre-create a command object
		// in the command object, we can increment the sequence id and send it without ack need
//		this.statGatheringCommand = this.messageService.messagingContext().createFrameBuffer();
//		this.messageService.messageCodec().encoder().encodeCommand(statGatheringCommand, 
//				this.messageService.ownSinkId(),
//				this.messageService.ownSinkId(),
//				this.messageService.messagingContext().getAndIncMsgSeq(), 
//				-1,
//				CommandType.GATHER_PERF_STAT,
//				BooleanType.FALSE);
	}

	private void handleRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
		byte senderSinkId = header.senderSinkId();
		MessageSinkRef senderSinkRef = messenger.sinkRef(senderSinkId);
		if (codec.requestType() == RequestType.SUBSCRIBE) {
			LOG.info("received request to subscribe from sink {}", senderSinkId);
			subscribers.put(senderSinkId, senderSinkRef); // add
																								// subscriber
																								// to
																								// list
			// send response back
			messenger.responseSender().sendResponseForRequest(senderSinkRef, codec, ResultType.OK);
			return;
		}
		if (codec.requestType() == RequestType.UNSUBSCRIBE) {
			LOG.info("received request to unsubscribe from sink {}", senderSinkId);
			subscribers.remove(senderSinkId); // remove subscriber from list
			messenger.responseSender().sendResponseForRequest(senderSinkRef, codec, ResultType.OK);
			return;
		}
		if (codec.requestType() == RequestType.GET_PERF_STAT) {
			requestGetPerfState(senderSinkId, header.seq(), codec);
			return;
		}
		LOG.warn("unexpected message {} from {}",
				RequestDecoder.decodeToString(codec, new byte[25]),
				senderSinkId);
	}

	private void requestGetPerfState(int senderSinkId, int requestSeq, RequestSbeDecoder request) {
		LOG.info("received request to get performance stat from sink {} ", senderSinkId);
		MessageSinkRef sink = messageService.messenger().sinkRef(senderSinkId);

//		messageService.publishResponse(sink, request, ResultType.OK);
		LOG.info("send response back to {}", senderSinkId);

		for (PerfData perfData : perfDataManager.perfDataBySinkId().values()) {
			messageService.messenger().sendPerfData(sink, perfData.sinkId(), perfData.roundTripNs());
			LOG.info("send perf data back to {}", senderSinkId);
		}
	}

	private void handlePingCompletion(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder codec) {
		long startTimeNs = codec.startTime();
		long endTimeNs = messageService.messenger().timerService().nanoTime();
		// TODO revise this again when we know what to do with timestamp taken
		// at each hop
		long rt = endTimeNs - startTimeNs;
		perfDataManager.roundTripNs(header.senderSinkId(), rt);
		LOG.info("completed ping: start:{}, end:{}, roundtrip:{}", startTimeNs, endTimeNs, rt);
		// histogram.recordValueWithExpectedInterval(rt, echoExpectedPauseTime);
		// received++;
		// if (received < ITERATIONS){
		// while (PAUSE_NANOS > (System.nanoTime() - startTimeNs))
		// {
		// Thread.yield();
		// }
		// ping();
		// }
		// else{
		// histogram.outputPercentileDistribution(System.out, 1, 1000.0);
		// }
	}

	private final long maxEvents = 100000L;
	private final long echoExpectedPauseTime = 100000L;
	private long t0 = 0;
	private long counter = 0;
	private int echoSinkId = 3;
	private MessageSinkRef echoRef;
	private int iterations = 3;

	private void handleEcho(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EchoSbeDecoder codec) {
		long t1 = messageService.messenger().timerService().nanoTime();
		histogram.recordValueWithExpectedInterval(t1 - t0, echoExpectedPauseTime);
		if (header.seq() < maxEvents) {
			while (echoExpectedPauseTime > (System.nanoTime() - t1)) {
				Thread.yield();
			}
			sendEcho();
		} else {
			histogram.outputPercentileDistribution(System.out, 1, 1000.0);
			counter = 0;
			iterations--;
			if (iterations > 0) {
				histogram.reset();
				sendEcho();
			}
		}
	}

	private void sendEcho() {
		t0 = System.nanoTime();
		messenger.echoSender().sendEcho(echoRef, (int)counter, System.nanoTime(), BooleanType.FALSE);
		counter++;
	}

	private void handleCommand(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
		byte senderSinkId = header.senderSinkId();
		int seq = header.seq();
		if (codec.commandType() == CommandType.GATHER_PERF_STAT) {
			// suggest to gather stats (performance service should know when to
			// gather stat)
			// 1. send a message to another service
			// 2. the other service receives it, takes a timestamp, sends it
			// back
			// 3. sender receives the response, takes a timestamp, compute the
			// latency
			// 4. latency can be stored in a data structure for later retrieval
			// 5. if no response for certain time, write an error log, send a
			// suspecting service is down message to admin
			// 6. admin can decide what to do
			// TODO there should be a latency test and a throughput test
			// both can be done with ping pong, where the latency test requires
			// receiving of pong before sending
			// the next ping
			messenger.sendCommandAck(senderSinkId, seq, CommandAckType.OK, codec.commandType());
			ping();
			return;
		}
		messenger.sendCommandAck(senderSinkId, seq, CommandAckType.NOT_SUPPORTED, codec.commandType());
	}

	private void ping() {
		messenger.pingSender().sendPingToLocalSinks(BooleanType.FALSE, messageService.messenger().timerService().nanoTime());
	}

	private void ready() {
//		messageService.tellAdminReady();
		startStatGatheringTimer();
	}

	/**
	 * Start a timer to periodically send a {@link CommandType.GATHER_PERF_STAT}
	 * to itself
	 */
	private void startStatGatheringTimer() {
		statGatheringTimeout = messageService.messenger().timerService().newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
//				messageService.publishSelf(statGatheringCommand);
				startStatGatheringTimer();
			}
		}, statGatheringFreqNs, TimeUnit.NANOSECONDS);
	}

	PerfDataManager perfDataManager() {
		return perfDataManager;
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService, this::activateOrWait);

		return StateTransitionEvent.NULL;
	}

	/**
	 * Set state event to {@link StateTransitionEvent.ACTIVATE} or
	 * {@link StateTransitionEvent.WAIT}
	 * 
	 * @param status
	 */
	private void activateOrWait(ServiceStatus status) {
		if (status.serviceType() != ServiceType.AdminService) {
			return;
		}
		if (status.statusType() == ServiceStatusType.UP) {
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		} else { // DOWN or INITIALIZING
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	@Override
	public StateTransitionEvent activeEnter() {
		// register different handlers
		messenger.receiver().commandHandlerList().add(this::handleCommand);
		messenger.receiver().requestHandlerList().add(this::handleRequest);
		messenger.receiver().echoHandlerList().add(this::handleEcho);
		messageService.pingCompletionHandler(this::handlePingCompletion);
		echoRef = messageService.messenger().sinkRef(echoSinkId);
		ready();
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		messenger.receiver().commandHandlerList().remove(this::handleCommand);
		messenger.receiver().requestHandlerList().remove(this::handleRequest);
		messageService.pingCompletionHandler(PingDecoder.NULL_HANDLER);
	}

	@Override
	public StateTransitionEvent stopEnter() {
		if (statGatheringTimeout != null) {
			statGatheringTimeout.cancel();
		}
		return StateTransitionEvent.NULL;
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		LOG.info("{} stopped processing event", this.name);
		return StateTransitionEvent.NULL;
	}
}
