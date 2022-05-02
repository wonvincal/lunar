package com.lunar.marketdata.hkex;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.archive.MarketDataManager;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.service.ServiceLifecycleAware;

public class OMDSecurityMarketDataService implements ServiceLifecycleAware {
	static final Logger LOG = LogManager.getLogger(OMDSecurityMarketDataService.class);
    private LunarService messageService;
    private final String name;
    private final MarketDataManager mdMgr;
    private final Messenger messenger;

    public OMDSecurityMarketDataService(ServiceConfig config, LunarService messageService, MarketDataManager mdMgr) {
		this.name = config.name();
		this.messageService = messageService;
		this.mdMgr = mdMgr;
		this.messenger = this.messageService.messenger();
	}

	@Override
	public StateTransitionEvent idleStart() {
		// TODO load data from external sources if applicable
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::activateOrWait);
		return StateTransitionEvent.NULL;
	}

	private void activateOrWait(boolean status){
		if (status){
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		}
		else { // DOWN or INITIALIZING
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	@Override
	public StateTransitionEvent idleRecover() {
		// TODO Auto-generated method stub
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
		// TODO Auto-generated method stub
		
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
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForServicesExit() {
	}

	@Override
	public StateTransitionEvent activeEnter() {
		// TODO spawn another thread to get data from the exchange
		messenger.receiver().requestHandlerList().add(this::handleRequest);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		messenger.receiver().requestHandlerList().remove(this::handleRequest);
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
		LOG.info("{} stopped processing event", this.name);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
	}
	
	private void handleRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
		byte senderSinkId = header.senderSinkId();
		if (codec.requestType() == RequestType.SUBSCRIBE){
			long instSid = -1;
			long undSecSid = -1;
			SecurityType secType = SecurityType.NULL_VAL;
			for (ParametersDecoder parameter : codec.parameters()){
				if (parameter.parameterType() == ParameterType.SECURITY_SID){
					instSid = parameter.parameterValueLong();
					// look up instrument
					// add subscribers
				}
				else if (parameter.parameterType() == ParameterType.UNDERLYING_SECURITY_SID){
					undSecSid = parameter.parameterValueLong();					
				}
				else if (parameter.parameterType() == ParameterType.SECURITY_TYPE){
					secType = SecurityType.get((byte)parameter.parameterValueLong());
				}
			}
			if (instSid != -1){
				mdMgr.subscribe(instSid, messageService.messenger().sinkRef(header.senderSinkId()));
			}
			else if (undSecSid != -1 && secType != SecurityType.NULL_VAL){
				// TODO find all instrument that satisfies these criteria
				// we can route all these rule-based subscriptions to a separate module
				// and resolve them into individual instrument subscription
			}
		}
		else if (codec.requestType() == RequestType.UNSUBSCRIBE){
			// TODO extract subscribe / un-subscribe into a common method
			long instSid = -1;
			long undSecSid = -1;
			SecurityType secType = SecurityType.NULL_VAL;
			for (ParametersDecoder parameter : codec.parameters()){
				if (parameter.parameterType() == ParameterType.SECURITY_SID){
					instSid = parameter.parameterValueLong();
					// look up instrument
					// add subscribers
				}
				else if (parameter.parameterType() == ParameterType.UNDERLYING_SECURITY_SID){
					undSecSid = parameter.parameterValueLong();					
				}
				else if (parameter.parameterType() == ParameterType.SECURITY_TYPE){
					secType = SecurityType.get((byte)parameter.parameterValueLong());
				}
			}
			if (instSid != -1){
				mdMgr.unsubscribe(instSid, messageService.messenger().sinkRef(senderSinkId));
			}
			else if (undSecSid != -1 && secType != SecurityType.NULL_VAL){
				// TODO find all instrument that satisfies these criteria
				// we can route all these rule-based subscriptions to a separate module
				// and resolve them into individual instrument subscription
			}
		}
		else {
			LOG.warn("unexpected request of type {} from {}", codec.requestType().name(), senderSinkId);
			return;
		}
	}

	@Override
	public StateTransitionEvent readyEnter() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void readyExit() {
		// TODO Auto-generated method stub
		
	}
}
