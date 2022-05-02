package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.lunar.config.ServiceConfig;
import com.lunar.config.StrategyServiceConfig;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.order.NewOrderRequest;
import com.lunar.util.DateUtil;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
public class DummyStrategyService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(DummyStrategyService.class);
	private final String name;
	private LunarService messageService;
	private final Messenger messenger;
	private final long secSid;
	private Security security = null;

	public static DummyStrategyService of(ServiceConfig config, LunarService messageService){
		return new DummyStrategyService(config, messageService);
	}
	
	DummyStrategyService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		if (config instanceof StrategyServiceConfig){
			StrategyServiceConfig specificConifg = (StrategyServiceConfig)config;
			secSid = Long.valueOf(specificConifg.securityCode());
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a StrategyServiceConfig config");
		}
	}

	Security security(){
		return security;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderManagementAndExecutionService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::handleAggregatedServiceStatusChange);
		return StateTransitionEvent.WAIT;
	}
	
	void handleAggregatedServiceStatusChange(boolean status){
		if (status){
			messageService.stateEvent(StateTransitionEvent.READY);
		}
		else { // DOWN or INITIALIZING
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	@Override
	public StateTransitionEvent readyEnter() {
		// we should get security and exchange from reference data service here
		if (this.security == null){
			messenger.receiver().securityHandlerList().add(securityHandler);

			messenger.sendRequest(messenger.referenceManager().rds(), 
					RequestType.GET,
					new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.SECURITY_SID, secSid)).build(),
					null /* think... */
					);
			// TODO call future
			return StateTransitionEvent.NULL;
		}
		return StateTransitionEvent.ACTIVATE;
	}

	private final Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder codec) {
			handleSecurity(buffer, offset, header, codec);
		}
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, SecuritySbeDecoder codec) {
			handleSecurity(buffer, offset, header, codec);
		}
	};
	
	private void handleSecurity(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security){
		if (security.sid() == secSid){
			LOG.info("Received target security {}, maturity: {}", secSid, security.maturity());
			byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
			security.getCode(bytes, 0);

			try {
				this.security = Security.of(secSid, 
						security.securityType(), 
						new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
						security.exchangeSid(), 
						security.undSid(),
						(security.maturity() == SecuritySbeDecoder.maturityNullValue()) ? 
								Optional.empty() :
								Optional.of(DateUtil.fromSbeToLocalDate(security.maturity())),
						DateUtil.fromSbeToLocalDate(security.listingDate()),
						security.putOrCall(),
						security.style(),
						security.strikePrice(),
						security.conversionRatio(),
						security.issuerSid(),
						security.lotSize(),
						security.isAlgo() == BooleanType.TRUE, 
						SpreadTableBuilder.get(security.securityType()))
						.omesSink(messenger.sinkRef(security.omesSinkId()))
						.mdsSink(messenger.sinkRef(security.mdsSinkId()))
						.mdsssSink(messenger.sinkRef(security.mdsssSinkId()));
			} 
			catch (UnsupportedEncodingException e) {
				LOG.error("Caught exception", e);
				this.messageService.stateEvent(StateTransitionEvent.FAIL);
				return;
			}
			this.messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		}
	}

	@Override
	public StateTransitionEvent activeEnter() {
		// register commands
		messenger.receiver().aggregateOrderBookUpdateHandlerList().add(this::handleMarketData);
		messenger.receiver().commandHandlerList().add(this::handleCommand);
		start();
		return StateTransitionEvent.NULL;
	}
	
	boolean receivedMarketData = false;
	private int numReceived = 0;
	private void handleMarketData(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder codec){
		receivedMarketData = true;
		numReceived++;
//		if (numReceived % 10000 == 0){
//			LOG.info("Received 10000 market data");
//		}

		
		NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(),
				messenger.self(),
				security, 
				OrderType.LIMIT_ORDER, 
				1000, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.TRUE, 
				100, 
				100, 
				1);
		messenger.sendNewOrder(messenger.referenceManager().omes(), request);
	}
	
	private int numReceivedOrderStatus = 0;

	private void handleCommand(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec){
		handleCommand(header.senderSinkId(), codec);
	}
	
	void handleCommand(int senderSinkId, CommandSbeDecoder codec){
		switch (codec.commandType()){
		case START:
			LOG.info("Received START command from {}", senderSinkId);
			// start(); - uncomment once Dashboard Service is ready
			break;
		case STOP:
			LOG.info("Received STOP command from {}", senderSinkId);
			messageService.stateEvent(StateTransitionEvent.STOP);
			break;
		default:
			break;
		}		
	}

	private void start(){
		// subscribe
		messenger.sendRequest(security.mdsSink(),
				RequestType.SUBSCRIBE,
				new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.SECURITY_SID, secSid)).build(),
				mdSubReponseHandler);
	}
	
	private final ResponseHandler mdSubReponseHandler = new ResponseHandler() {
		@Override
		public void handleResponse(DirectBuffer buffer, int offset, ResponseSbeDecoder response) {
			LOG.info("Received market data subscription respones: {}", response.resultType());
			if (response.resultType() != ResultType.OK){
				LOG.error("Cannot subscribe market data due to {}", response.resultData());
				messageService.stateEvent(StateTransitionEvent.FAIL);
			}
		}
		
		@Override
		public void handleResponse(Response response) {
			LOG.info("Received market data subscription respones: {}", response.resultType());
			if (response.resultType() != ResultType.OK){
				LOG.error("Cannot subscribe market data due to some reasons");
				messageService.stateEvent(StateTransitionEvent.FAIL);
			}
		}
	};
	
	@Override
	public void activeExit() {
		// unregister command
		messenger.receiver().aggregateOrderBookUpdateHandlerList().remove(this::handleMarketData);
		messenger.receiver().commandHandlerList().remove(this::handleCommand);
		messenger.receiver().securityHandlerList().remove(securityHandler);
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		LOG.info("Received {} market data messages", numReceived);
		LOG.info("Received {} order status messages", numReceivedOrderStatus);
		return StateTransitionEvent.NULL;
	}
}
