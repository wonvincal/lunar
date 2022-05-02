package com.lunar.service;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.config.PongServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PingSbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRefMgr;

public class PongService implements ServiceLifecycleAware {
	static final Logger LOG = LogManager.getLogger(PingService.class);
	private LunarService messageService;
	private final Messenger messenger;
	@SuppressWarnings("unused")
    private final MessageSinkRefMgr refMgr;
	private final String name;
	
	public static PongService of(ServiceConfig config, LunarService messageService) {
		return new PongService(config, messageService);
	}
	
	PongService(ServiceConfig config, LunarService messageService) {
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.refMgr = this.messenger.referenceManager();
		if (config instanceof PongServiceConfig){
			@SuppressWarnings("unused")
			PongServiceConfig specificConfig = (PongServiceConfig)config;
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a PongServiceConfig config");
		}
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::handleAggregatedServiceStatusChange);
		return StateTransitionEvent.WAIT;
	}

	void handleAggregatedServiceStatusChange(boolean status){
		if (status){
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		}
		else { // DOWN or INITIALIZING
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public StateTransitionEvent activeEnter() {
		messenger.receiver().pingHandlerList().add(pingHandler);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		messenger.receiver().pingHandlerList().remove(pingHandler);
	}
	
	private final Handler<PingSbeDecoder> pingHandler = new Handler<PingSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder payload) {
		}
	};
}
