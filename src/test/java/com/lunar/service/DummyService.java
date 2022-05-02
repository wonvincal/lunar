package com.lunar.service;

import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.binary.Messenger;

public class DummyService implements ServiceLifecycleAware {
	private boolean throwExceptionOnActiveEnter = false;
	private final LunarService messageService;
	private final Messenger messenger;
	private final String name;
	
	public static DummyService of(ServiceConfig serviceConfig, LunarService messageService){
		return new DummyService(serviceConfig, messageService);
	}
	
	DummyService(ServiceConfig serviceConfig, LunarService messageService){
		this.messageService = messageService;
		this.messenger = this.messageService.messenger(); 
		this.name = serviceConfig.name();
	}
	
	public DummyService throwExceptionOnActiveEnter(boolean value){
		this.throwExceptionOnActiveEnter = value;
		return this;
	}
	
	public String name(){
		return name;
	}
	
	public Messenger messenger(){
		return messenger;
	}

	@Override
	public StateTransitionEvent idleStart() {
		return StateTransitionEvent.NULL;
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
		return StateTransitionEvent.NULL;
	};
	
	@Override
	public void warmupExit() {
	}
	
	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForServicesExit() {
	}

	@Override
	public StateTransitionEvent activeEnter() {
		if (this.throwExceptionOnActiveEnter){
			throw new RuntimeException("intentional exception in activeEnter");
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
	}

	@Override
	public StateTransitionEvent readyEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void readyExit() {
	}

	@Override
	public StateTransitionEvent resetEnter() {
		return StateTransitionEvent.NULL;
	}
	
	public static ServiceBuilder BUILDER = new ServiceBuilder() {
		@Override
		public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
			return DummyService.of(serviceConfig, messageService);
		}
	};
}
