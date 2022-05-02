package com.lunar.service;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.ServiceType;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
public class DummyOrderManagementAndExecutionService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(DummyOrderManagementAndExecutionService.class);
	private final String name;
	private LunarService messageService;
	private final Messenger messenger;
	private int numReceived = 0;

	public static DummyOrderManagementAndExecutionService of(ServiceConfig config, LunarService messageService){
		return new DummyOrderManagementAndExecutionService(config, messageService);
	}
	
	DummyOrderManagementAndExecutionService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof OrderManagementAndExecutionServiceConfig){
			@SuppressWarnings("unused")
			OrderManagementAndExecutionServiceConfig specificConifg = (OrderManagementAndExecutionServiceConfig)config;
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a OrderManagementAndExecutionServiceConfig config");
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
		// register requests
		messenger.receiver().newOrderRequestHandlerList().add(this::handleNewOrder);
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public void activeExit() {
		// unregister command
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		LOG.info("Received {} orders", numReceived);
		return StateTransitionEvent.NULL;
	}

	private void handleNewOrder(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder request){
	}
}
