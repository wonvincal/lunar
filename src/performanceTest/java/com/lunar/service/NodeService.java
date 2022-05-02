package com.lunar.service;

import java.util.concurrent.BrokenBarrierException;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.perf.latency.LatencyTestContext;

public class NodeService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(NodeService.class);
	protected final LunarService messageService;
	protected final Messenger messenger;
	protected MessageSinkRef sink;
	protected LatencyTestContext context;
	
	public NodeService(ServiceConfig serviceConfig, LunarService messageService, LatencyTestContext context){
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.context = context;
	}
	
	public void forwardTo(MessageSinkRef sink){
		this.sink = sink;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.receiver().aggregateOrderBookUpdateHandlerList().add(this::handleMarketData);
		return StateTransitionEvent.NULL;
	}

	private void handleMarketData(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder marketData){
//		LOG.info("received market data");
		messenger.marketDataSender().sendAggregateOrderBookUpdate(sink, marketData);
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
			this.context.readyToStart();
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
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
		this.messenger.receiver().aggregateOrderBookUpdateHandlerList().remove(this::handleMarketData);
	}
}
