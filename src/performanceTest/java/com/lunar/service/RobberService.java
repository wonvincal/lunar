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
import com.lunar.perf.throughput.ThroughputTestContext;

public class RobberService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(RobberService.class);
	protected final LunarService messageService;
	protected final Messenger messenger;
	protected MessageSinkRef sink;
	protected ThroughputTestContext context;
	private long received = 0;
	private long resultSid;

	public RobberService(ServiceConfig serviceConfig, LunarService messageService, ThroughputTestContext context){
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.context = context;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.receiver().aggregateOrderBookUpdateHandlerList().add(this::handleMarketData);
		return StateTransitionEvent.NULL;
	}

	private void handleMarketData(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder marketData){
//		LOG.info("ouch");
		resultSid += marketData.secSid();
		received++;
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
		LOG.info("{} received {} messages", this.messageService.name(), received);
		LOG.info("{} resultSid", resultSid);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
		this.messenger.receiver().aggregateOrderBookUpdateHandlerList().remove(this::handleMarketData);
	}
	
	public void run(){}
}
