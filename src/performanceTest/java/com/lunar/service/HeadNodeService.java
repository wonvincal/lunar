package com.lunar.service;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.ServiceConfig;
import com.lunar.core.TimerService;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.journal.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder.EntryEncoder;
import com.lunar.perf.latency.LatencyTestContext;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class HeadNodeService extends NodeService {
	private static final Logger LOG = LogManager.getLogger(HeadNodeService.class);
	private MutableDirectBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
	private long remaining;
	private final TimerService timerService;
	private boolean isPrepared = false;
	
	public HeadNodeService(ServiceConfig serviceConfig, LunarService messageService, LatencyTestContext context){
		super(serviceConfig, messageService, context);
		this.timerService = messageService.messenger().timerService();
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.receiver().aggregateOrderBookUpdateHandlerList().add(this::handleLoopBackMarketData);
		return StateTransitionEvent.NULL;
	}

	
	public int prepareMessage(int desireSize){
		final int entryCount = 2;
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
		
		isPrepared = true;
		
		return MessageHeaderEncoder.ENCODED_LENGTH + sbe.encodedLength();
	}
	
	private void handleLoopBackMarketData(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder marketData){
		// LOG.info("{} got message back - {} remaining", messageService.name(), this.remaining);
		long t1 = timerService.nanoTime();
		context.recordValue(t1 - t0);
		this.remaining--;
		if (this.remaining == 0){
			LOG.info("{} completed one pass", messageService.name());
			context.completeOnePass();
			return;
		}
		
		// yield until we have paused enough time
		while (context.pauseTimeNs() > (timerService.nanoTime() - t1)){
			Thread.yield();
		}
		sendMessage();
	}
	
	private long t0;
	public void start(){
		reset();
		if (!isPrepared){
			throw new IllegalStateException(messageService.name() + " is not yet prepared");
		}
		LOG.info("{} starts sending message", messageService.name());
		sendMessage();
	}
	
	private void sendMessage(){
		t0 = timerService.nanoTime();
		AggregateOrderBookUpdateSbeDecoder decoder = new AggregateOrderBookUpdateSbeDecoder();
		messenger.marketDataSender().sendAggregateOrderBookUpdate(
				sink, 
				decoder.wrap(unsafeBuffer, 0, 
						AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH, 
						AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION));		
	}
	
	private void reset(){
		this.remaining = context.iterations();
	}
}
