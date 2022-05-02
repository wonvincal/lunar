package com.lunar.service;

import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.ServiceConfig;
import com.lunar.core.TimerService;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.journal.io.sbe.MessageHeaderEncoder;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder.EntryEncoder;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.perf.throughput.ThroughputTestContext;

public class PoliceService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(PoliceService.class);
	protected final LunarService messageService;
	protected final Messenger messenger;
	protected MessageSinkRef sink;
	protected ThroughputTestContext context;
	private MutableDirectBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
	private final TimerService timerService;
	private boolean isPrepared = false;
	private long remaining;

	public PoliceService(ServiceConfig serviceConfig, LunarService messageService, ThroughputTestContext context){
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.timerService = messageService.messenger().timerService();
		this.context = context;
	}

	public int prepareMessage(int desireSize){
		final int entryCount = 4;
		final long secSid = 300000;
		
		AggregateOrderBookUpdateSbeEncoder sbe = new AggregateOrderBookUpdateSbeEncoder();
		EntryEncoder entryEncoder = sbe.wrap(unsafeBuffer, 0)
			.secSid(secSid)
			.entryCount(entryCount);
		entryEncoder.next()
			.numOrders(1)
			.price(1)
			.priceLevel((byte)1)
			.quantity(1)
			.tickLevel(1);
		entryEncoder.next()
			.numOrders(2)
			.price(2)
			.priceLevel((byte)2)
			.quantity(2)
			.tickLevel(2);
		entryEncoder.next()
		.numOrders(3)
		.price(3)
		.priceLevel((byte)3)
		.quantity(3)
		.tickLevel(3);
		entryEncoder.next()
		.numOrders(4)
		.price(4)
		.priceLevel((byte)4)
		.quantity(4)
		.tickLevel(4);
		
		isPrepared = true;
		
		return MessageHeaderEncoder.ENCODED_LENGTH + sbe.encodedLength();
	}
	
	public void aim(MessageSinkRef sink){
		this.sink = sink;
	}

	private void warmup(){
		reset();
		if (!isPrepared){
			throw new IllegalStateException(messageService.name() + " is not yet prepared");
		}
		LOG.info("{} warming up", messageService.name());
		int burstSize = context.messageQueueSize() / 4;
		final long burstIntervalNs = 20_000_000; /* 10 millisecond */
		int sent = 0;
		boolean overflow = false;
		int runs = 150;
		for (int j = 0; j < runs; j++){
			long t0 = timerService.nanoTime();
			for (int i = 0; i < burstSize; i++){
				AggregateOrderBookUpdateSbeDecoder decoder = new AggregateOrderBookUpdateSbeDecoder();
				if (messenger.marketDataSender().trySendAggregateOrderBookUpdate(
						sink, 
						decoder.wrap(unsafeBuffer, 0, 
								AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH, 
								AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION)) == MessageSink.OK){
					sent++;
				}
				else {
					// done - sink overflow
					overflow = true;
					break;
				}
			}
			if (burstIntervalNs > (timerService.nanoTime() - t0)){
				// yield until we have paused enough time
				while (burstIntervalNs > (timerService.nanoTime() - t0)){
					Thread.yield();
				}
			}
		}
		if (overflow){
			throw new IllegalStateException("Warmup failed, got buffer overflow during warmup");
		}
		LOG.info("{} warmup completed, sent {} messages", this.messageService.name(), sent);
		context.completeWarmup();
	}
	
	private void shoot(){
		reset();
		if (!isPrepared){
			throw new IllegalStateException(messageService.name() + " is not yet prepared");
		}
		if (context.burstSize() <= 0){
			throw new IllegalArgumentException("burstSize must be non-zero");
		}
		LOG.info("{} starts shooting {} messages", messageService.name(), this.remaining);
		boolean overflow = false;
		boolean intervalTooShort = false;
		long begin = timerService.nanoTime();
		while (this.remaining > 0){
			long t0 = timerService.nanoTime();

			// burst
			for (int i = 0; i < context.burstSize(); i++){
//				LOG.info("shoot");
				AggregateOrderBookUpdateSbeDecoder decoder = new AggregateOrderBookUpdateSbeDecoder();
				if (messenger.marketDataSender().trySendAggregateOrderBookUpdate(
						sink, 
						decoder.wrap(unsafeBuffer, 0, 
								AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH, 
								AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION)) > 0L){
					this.remaining--;
				}
				else {
					// done - sink overflow
					overflow = true;
					break;
				}
			}
			
			if (overflow){
				break;
			}
			
			if (context.burstIntervalNs() > (timerService.nanoTime() - t0)){
				// yield until we have paused enough time
				while (context.burstIntervalNs() > (timerService.nanoTime() - t0)){
					Thread.yield();
				}
			}
//			else{
//				LOG.info("too short: burstIntervalNs {}, diff {}", context.burstIntervalNs(), timerService.nanoTime() - t0);
//				// done - it takes more than the burst interval to handle the burst of messages
//				intervalTooShort = true;
//				break;
//			}
		}
		context.recordValue(this.messenger.self().sinkId(),
				timerService.nanoTime() - begin,
				this.remaining,
				overflow,
				intervalTooShort);
		context.completeOnePass();
	}
	
	private void reset(){
		this.remaining = context.iterations();
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.receiver().commandHandlerList().add(this::handleCommand);
		return StateTransitionEvent.NULL;
	}

	private void handleCommand(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder command) {
		switch (command.commandType()){
		case START:
			shoot();
			break;
		case WARMUP:
			warmup();
			break;
		case STOP:
			break;
		default:
			break;
		}
	}

	@Override
	public StateTransitionEvent idleRecover() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent waitingForWarmupServicesEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForWarmupServicesExit() {
	}

	@Override
	public StateTransitionEvent warmupEnter() {
		return StateTransitionEvent.READY;
	};
	
	@Override
	public void warmupExit() {
	}
	
	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		return StateTransitionEvent.ACTIVATE;
	}

	@Override
	public void waitingForServicesExit() {
	}

	@Override
	public StateTransitionEvent readyEnter() {
		return StateTransitionEvent.ACTIVATE;
	}

	@Override
	public void readyExit() {
	}

	@Override
	public StateTransitionEvent activeEnter() {
		try {
			LOG.info("{} is ready", this.messageService.name());
			context.readyToStart();
		} 
		catch (InterruptedException | BrokenBarrierException e) {
			LOG.error("Caught exception in active enter: " + this.messageService.name(), e);
			return StateTransitionEvent.FAIL;
		}
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
	}

	@Override
	public StateTransitionEvent stopEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stopExit() {
		messenger.receiver().commandHandlerList().remove(this::handleCommand);
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
	}

}
