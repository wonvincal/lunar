package com.lunar.service;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.OrderAndTradeSnapshotServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.ServiceType;

public class DummyOrderAndTradeSnapshotService implements ServiceLifecycleAware {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(DummyOrderAndTradeSnapshotService.class);
	private final String name;
	private LunarService messageService;
	private final Messenger messenger;

	public static DummyOrderAndTradeSnapshotService of(ServiceConfig config, LunarService messageService){
		return new DummyOrderAndTradeSnapshotService(config, messageService);
	}
	
	DummyOrderAndTradeSnapshotService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof OrderAndTradeSnapshotServiceConfig){
			@SuppressWarnings("unused")
			OrderAndTradeSnapshotServiceConfig specificConifg = (OrderAndTradeSnapshotServiceConfig)config;
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a OrderAndTradeSnapshotServiceConfig config");
		}
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::handleAggregatedServiceStatusChange);
		return StateTransitionEvent.NULL;
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
	public StateTransitionEvent activeEnter() {
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public void activeExit() {
		// unregister command
	}

}
