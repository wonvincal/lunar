package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.LongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.config.PortfolioAndRiskServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.SubscriberList;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.entity.RiskControlSetting;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.RiskControlSbeDecoder;
import com.lunar.message.io.sbe.RiskControlType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.position.AggregatedSecurityPosition;
import com.lunar.position.FeeAndCommissionSchedule;
import com.lunar.position.PositionChangeHandler;
import com.lunar.position.PositionDetails;
import com.lunar.position.RiskControlStateChangeHandler;
import com.lunar.position.RiskState;
import com.lunar.position.SecurityPosition;
import com.lunar.position.SecurityPositionChangeTracker;
import com.lunar.position.SecurityPositionDetails;

import io.netty.util.Timeout;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * 
 * To keep track of different figures on a portfolio and higher level (e.g. business entity level)
 * 
 * 1. This service subscribes:
 *    i. all omes services
 *    ii. it may need to subscribe to analytics service to get different risk inputs
 * 2. It keeps track of different figures at portfolio level or other predefined subscribable aggregation level 
 * 3. It accepts subscription at portfolio and higher level 
 * 
 * @see <a href="https://codacapital.asuscomm.com:453/centipede/lunar/wikis/port-risk-service">
 * 		https://codacapital.asuscomm.com:453/centipede/lunar/wikis/port-risk-service
 * 		</a>
 *
 * 
 * In the beginning:
 * 1) Get all trades
 * 2) Get all outstanding orders
 * 3) if security is unknown, get it from rds.  Process the message when security is retrieved.
 *    1) attach the message to security position
 *    2) send a request to get security info
 *    3) once completed, update underlying, issuer and firm
 * 
 * @author Calvin
 *
 */
public class PortfolioAndRiskService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(PortfolioAndRiskService.class);
	private static final int EXPECTED_TRADE_CHANNELS = 2;
	private static long FIRM_SID = 1212121211l;
	private LunarService messageService;
	private final Messenger messenger;
	private MessageSinkRefMgr refMgr;
	private final String name;
    private Duration publishFrequency;
    private final Duration requestMissingFrequency = Duration.ofSeconds(5);
    private final int CLIENT_KEY_FOR_PUBLISHING = 1;
    private final int CLIENT_KEY_FOR_REQUEST_MISSING = 2;
    private AtomicReference<TimeoutHandlerTimerTask> publishSnapshotTask;
    private Timeout publishSnapshotTaskTimeout;
    private AtomicReference<TimeoutHandlerTimerTask> requestMissingTask;
    private Timeout requestMissingTaskTimeout;

    private MessageSinkRef otSnapshotSink;
    private MessageSinkRef mdSnapshotSink;
    private boolean receivedInitPos;
    private boolean receivedInitOrderAndTradeSnapshot;
    
    // Position
    private final Long2ObjectOpenHashMap<SecurityPosition> secPositions;
    private final Int2ObjectOpenHashMap<AggregatedSecurityPosition> issuerPositions;
    private final Long2ObjectOpenHashMap<AggregatedSecurityPosition> undPositions;
    private final AggregatedSecurityPosition firmPosition;
	private final EnumMap<SecurityType, FeeAndCommissionSchedule> schedules;
    
	// Position update subscribers
	private final ObjectOpenHashSet<SecurityPositionDetails> updSecPositions;
	private final ObjectOpenHashSet<PositionDetails> updPositions;
	private final SubscriberList allPosUpdSubs;
	private final Int2ReferenceOpenHashMap<SubscriberList> portPosUpdSubs;
	
	// Risk control
	private final EnumMap<EntityType, RiskControlSetting> defaultRiskCtrlSettings;
	private final Long2ReferenceOpenHashMap<RiskState> secRiskStates;
	private final Int2ReferenceOpenHashMap<RiskState> issuerRiskStates;
	private final Long2ReferenceOpenHashMap<RiskState> undRiskStates;
	private final RiskState firmRiskState;

	// Risk control update subscribers
	private final SubscriberList allRiskStateChangeSubs;
	private final Int2ReferenceOpenHashMap<SubscriberList> portRiskStateChangeSubs;

	// Processed trades
	private final Int2LongOpenHashMap processedTradeSeqByChannel;

	/**
	 * A tracker that can be reused by all "leaf level" SecurityPosition because change in one
	 * {@link SecurityPosition} will impact by another {@link SecurityPosition} 
	 */
	private final SecurityPositionChangeTracker commonSecChangeTracker;
	private final long[] sendResults;
	
	private final PositionChangeHandler batchIfChange = new PositionChangeHandler() {
		@Override
		public void handleChange(SecurityPositionDetails current, SecurityPositionDetails previous) {
			LOG.debug("Added update to security position [entityType:{}, entitySid:{}, tradeCount:{}]", 
					current.entityType().name(), current.entitySid(), current.tradeCount());
			updSecPositions.add(current);
		}
		@Override
		public void handleChange(PositionDetails current, PositionDetails previous) {
			LOG.debug("Added update to non security position [entityType:{}, entitySid:{}]",
					current.entityType().name(), current.entitySid());
			updPositions.add(current);
		}
	};
	
	private final RiskControlStateChangeHandler sendIfChange = new RiskControlStateChangeHandler() {
		
		@Override
		public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
			// Currently we support only 1)receive all risk control update, 2)receive risk 
			messenger.riskStateSender().sendRiskState(allRiskStateChangeSubs.elements(), 
					allRiskStateChangeSubs.size(),
					entityType, 
					entitySid, 
					RiskControlType.MAX_OPEN_POSITION, 
					current, 
					previous, 
					exceeded, 
					sendResults);
		}
		
		@Override
		public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
			messenger.riskStateSender().sendRiskState(allRiskStateChangeSubs.elements(), 
					allRiskStateChangeSubs.size(),
					entityType, 
					entitySid, 
					RiskControlType.MAX_PROFIT, 
					current, 
					previous, 
					exceeded, 
					sendResults);
		}
		
		@Override
		public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
			messenger.riskStateSender().sendRiskState(allRiskStateChangeSubs.elements(), 
					allRiskStateChangeSubs.size(),
					entityType, 
					entitySid, 
					RiskControlType.MAX_LOSS, 
					current, 
					previous, 
					exceeded, 
					sendResults);
		}
		
		@Override
		public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
			messenger.riskStateSender().sendRiskState(allRiskStateChangeSubs.elements(), 
					allRiskStateChangeSubs.size(),
					entityType, 
					entitySid, 
					RiskControlType.MAX_CAP_LIMIT, 
					current, 
					previous, 
					exceeded, 
					sendResults);
		}
	};
	
	public static PortfolioAndRiskService of(ServiceConfig config, LunarService messageService){
		return new PortfolioAndRiskService(config, messageService);
	}
	
	PortfolioAndRiskService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.refMgr = this.messenger.referenceManager();
		this.sendResults = new long[ServiceConstant.DEFAULT_MAX_NUM_SINKS];
		this.publishSnapshotTask = new AtomicReference<>();
		this.requestMissingTask = new AtomicReference<>();
		PortfolioAndRiskServiceConfig specificConfig = null;
		if (config instanceof PortfolioAndRiskServiceConfig){
			specificConfig = (PortfolioAndRiskServiceConfig)config;
			this.publishFrequency = specificConfig.publishFrequency();
			this.defaultRiskCtrlSettings = new EnumMap<>(EntityType.class);
			this.defaultRiskCtrlSettings.put(EntityType.SECURITY, RiskControlSetting.of(EntityType.SECURITY, specificConfig.defaultSecurityRiskControlConfig()));
			this.defaultRiskCtrlSettings.put(EntityType.ISSUER, RiskControlSetting.of(EntityType.ISSUER, specificConfig.defaultIssuerRiskControlConfig())); 
			this.defaultRiskCtrlSettings.put(EntityType.UNDERLYING, RiskControlSetting.of(EntityType.UNDERLYING, specificConfig.defaultUndRiskControlConfig()));
			this.defaultRiskCtrlSettings.put(EntityType.FIRM, RiskControlSetting.of(EntityType.FIRM, specificConfig.defaultFirmRiskControlConfig()));
			
			for (Entry<EntityType, RiskControlSetting> entry : this.defaultRiskCtrlSettings.entrySet()){
				LOG.debug("[{}:{}]", entry.getKey().name(), entry.getValue());
			}
		}
		else{
			throw new IllegalArgumentException("PortfolioAndRiskServiceConfig is missing");
		}
		
		this.allPosUpdSubs = SubscriberList.of();
		this.portPosUpdSubs = new Int2ReferenceOpenHashMap<>();
		this.allRiskStateChangeSubs = SubscriberList.of();
		this.portRiskStateChangeSubs = new Int2ReferenceOpenHashMap<>();
		
		// Position
		this.secPositions = new Long2ObjectOpenHashMap<>();
		this.issuerPositions = new Int2ObjectOpenHashMap<>();
		this.undPositions = new Long2ObjectOpenHashMap<>();
		this.firmPosition = AggregatedSecurityPosition.of(FIRM_SID, EntityType.FIRM);
		this.firmPosition.addHandler(batchIfChange);
		this.updSecPositions = new ObjectOpenHashSet<>();
		this.updPositions = new ObjectOpenHashSet<>();
		
		// Risk control
		this.secRiskStates = new Long2ReferenceOpenHashMap<>();
		this.issuerRiskStates = new Int2ReferenceOpenHashMap<>();
		this.undRiskStates = new Long2ReferenceOpenHashMap<>();
		this.firmRiskState = RiskState.of(FIRM_SID, EntityType.FIRM, defaultRiskCtrlSettings.get(EntityType.FIRM));
		this.firmRiskState.addHandler(sendIfChange);
		this.firmPosition.addHandler(this.firmRiskState);
		
		// Fees and commission schedule
		// TODO: How to distinguish what should be provided by reference data service
		// 1) Risk control - firm, underlying, issuer, security
		// 2) Fees - this looks like something that is not service specific
		this.schedules = new EnumMap<>(SecurityType.class);
		this.schedules.put(SecurityType.STOCK, FeeAndCommissionSchedule.inBps(0.27, 10, 0.5, 0.5, 0.2, 2, 100, 1.5));
		FeeAndCommissionSchedule warrantFees = FeeAndCommissionSchedule.inBps(0.27, 0, 0.5, 0.5, 0.2, 2, 100, 1.5);
		this.schedules.put(SecurityType.WARRANT, warrantFees);
		this.schedules.put(SecurityType.CBBC, warrantFees);
		this.commonSecChangeTracker = SecurityPositionChangeTracker.of();
		
		this.processedTradeSeqByChannel = new Int2LongOpenHashMap(EXPECTED_TRADE_CHANNELS);
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataSnapshotService, this::handleMdSnapshotServiceChange);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderAndTradeSnapshotService, this::handleOtSnapshotServiceChange);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::waitOrReady);
		return StateTransitionEvent.NULL;
	}

	private void handleOtSnapshotServiceChange(ServiceStatus status){
		if (status.statusType() == ServiceStatusType.UP){
			this.otSnapshotSink = refMgr.get(status.sinkId());
		}
	}
	
	private void handleMdSnapshotServiceChange(ServiceStatus status){
		if (status.statusType() == ServiceStatusType.UP){
			this.mdSnapshotSink = refMgr.get(status.sinkId());
		}
	}

	private void waitOrReady(boolean status){
		if (status) {
			messageService.stateEvent(StateTransitionEvent.READY);
		}
		else { 
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	private void sendRequestToGetInitialPosition(){
		CompletableFuture<Request> request = messenger.sendRequest(refMgr.persi(),
				RequestType.GET, 
				Parameters.listOf(Parameter.of(TemplateType.POSITION)));
		request.whenComplete(new BiConsumer<Request, Throwable>(){

			@Override
			public void accept(Request request, Throwable throwable) {
				if (throwable != null){
					LOG.error("Caught throwable for request to get initial position", throwable);
					sendRequestToGetInitialPosition();
					return;
				}
				if (!request.resultType().equals(ResultType.OK)){
					LOG.error("Received failure result for request to get initial position [{}]", request.toString());
					sendRequestToGetInitialPosition();
					return;
				}
			    receivedInitPos = true;
			    if (receivedInitPos && receivedInitOrderAndTradeSnapshot){
			    	messageService.stateEvent(StateTransitionEvent.ACTIVATE);
			    }
			}
		});		
	}
	
	private void sendRequestToGetAndSubscribeOrderAndTradeSnapshot(){
		// Get outstanding orders and trades
		CompletableFuture<Request> request = messenger.sendRequest(this.otSnapshotSink,
				RequestType.GET_AND_SUBSCRIBE, 
				Parameters.listOf(Parameter.of(DataType.ALL_ORDER_AND_TRADE_UPDATE)));
		request.whenComplete(new BiConsumer<Request, Throwable>(){

			@Override
			public void accept(Request request, Throwable throwable) {
				if (throwable != null){
					LOG.error("Caught throwable for request to get and subscribe order and trade snapshot", throwable);
					// TODO Make this a TimerTask
					sendRequestToGetAndSubscribeOrderAndTradeSnapshot();
					return;
				}
				if (!request.resultType().equals(ResultType.OK)){
					LOG.error("Received failure result for request to get and subscriber order and trade snapshot [{}]", request.toString());
					// TODO Make this a TimerTask
					sendRequestToGetAndSubscribeOrderAndTradeSnapshot();
					return;
				}
			    receivedInitOrderAndTradeSnapshot = true;
			    if (receivedInitPos && receivedInitOrderAndTradeSnapshot){
			    	messageService.stateEvent(StateTransitionEvent.ACTIVATE);
			    }
			}
		});		
	}

	private final LongHashSet missingSecurities = new LongHashSet(128, -1);
	
    private void trySendRequestToGetSecurity(long secSid){
    	// No need to try fetching security if it was found to be missing already
    	if (missingSecurities.contains(secSid)){
    		return;
    	}
    	sendRequestToGetSecurity(secSid);
    }
    
    private void sendRequestToGetSecurity(long secSid){
		CompletableFuture<Request> request = messenger.sendRequest(refMgr.rds(), 
				RequestType.GET,
				Parameters.listOf(
						Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()),
						Parameter.of(ParameterType.SECURITY_SID, secSid)));
		request.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				if (throwable != null){
					LOG.error("Caught throwable for request to get security", throwable);
					missingSecurities.add(secSid);
					return;
				}
				if (!request.resultType().equals(ResultType.OK)){
					LOG.error("Received failure result for request to get security [{}]", request.toString());
					missingSecurities.add(secSid);
					return;
				}
				missingSecurities.remove(secSid);
				sendRequestToSubscribeBoobs(secSid);
			}
		});    	
    }
    
    /**
     * We may want to turn this into a timer-related task, similar for getting security info
     * @param secSid
     */
    private void sendRequestToSubscribeBoobs(long secSid){
		CompletableFuture<Request> request = messenger.sendRequest(this.mdSnapshotSink, 
				RequestType.SUBSCRIBE,
				Parameters.listOf(
						Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.BOOBS.value()),
						Parameter.of(ParameterType.SECURITY_SID, secSid)));
		request.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				if (throwable != null){
					LOG.error("Caught throwable for request to subscribe market data for security", throwable);
					// TODO Make this a TimerTask
					sendRequestToSubscribeBoobs(secSid);
					return;
				}
				if (!request.resultType().equals(ResultType.OK)){
					LOG.error("Received failure result for request to subscribe market data for security [{}]", request.toString());
					// TODO Make this a TimerTask
					sendRequestToSubscribeBoobs(secSid);
					return;
				}
			}
		});
    }
    
	@Override
	public StateTransitionEvent readyEnter() {
		messenger.receiver().orderHandlerList().add(orderHandler);
		messenger.receiver().tradeHandlerList().add(tradeHandler);
		messenger.receiver().positionHandlerList().add(positionHandler);
		messenger.receiver().securityHandlerList().add(securityHandler);
		sendRequestToGetAndSubscribeOrderAndTradeSnapshot();
		sendRequestToGetInitialPosition();
		return StateTransitionEvent.NULL;
	}

	@Override
	public void readyExit() {
		messenger.receiver().positionHandlerList().remove(positionHandler);
	}
	
	@Override
	public StateTransitionEvent activeEnter() {
		messenger.receiver().orderHandlerList().add(orderHandler);
		messenger.receiver().tradeHandlerList().add(tradeHandler);
		messenger.receiver().boobsHandlerList().add(boobsHandler);
		messenger.receiver().requestHandlerList().add(requestHandler);
		messenger.receiver().timerEventHandlerList().add(timerEventHandler);
		publishSnapshotTask.set(messenger.timerService().createTimerTask(publishSnapshots, "risk-publish-snapshots"));
		publishSnapshotTaskTimeout = messenger.timerService().newTimeout(publishSnapshotTask.get(), publishFrequency.toMillis(), TimeUnit.MILLISECONDS);
		requestMissingTask.set(messenger.timerService().createTimerTask(requestMissing, "risk-request-missing"));
		requestMissingTaskTimeout = messenger.timerService().newTimeout(requestMissingTask.get(), requestMissingFrequency.toMillis(), TimeUnit.MILLISECONDS);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		messenger.receiver().orderHandlerList().remove(orderHandler);
		messenger.receiver().tradeHandlerList().remove(tradeHandler);
		messenger.receiver().boobsHandlerList().remove(boobsHandler);
		messenger.receiver().securityHandlerList().remove(securityHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
		messenger.receiver().timerEventHandlerList().remove(timerEventHandler);
		if (publishSnapshotTaskTimeout.cancel()){
			LOG.info("Cancelled publishing task successfully");
		}
		if (requestMissingTaskTimeout.cancel()){
			LOG.info("Cancelled request missing task successfully");
		}
	}

	@Override
	public StateTransitionEvent stopEnter() {
		LOG.info("{} stopped", name);
		return StateTransitionEvent.NULL;
	}

	private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            LOG.info("Received request [senderSinkId:{}, clientKey:{}, requestType:{}]", header.senderSinkId(), request.clientKey(), request.requestType().name());
			byte senderSinkId = header.senderSinkId();
	    	try {
				ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
				switch (request.requestType()){
				case GET:
					handleGetRequest(senderSinkId, request, parameters);
					break;
				case SUBSCRIBE:
					handleSubscriptionRequest(senderSinkId, request, parameters);
					break;
				case UNSUBSCRIBE:
					handleUnsubscriptionRequest(senderSinkId, request, parameters);
					break;
				case UPDATE:
					handleUpdateRequest(buffer, offset, senderSinkId, request, parameters);
				default:
					LOG.error("Unsupported operation [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
					messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
					break;
				}			
			} 
	    	catch (UnsupportedEncodingException e) {
	    		LOG.error("Caught exception while processing request [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
	    		messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			}
		}
	};

    /**
     * As of now, we support update of risk control only.
     * 
     * I expect a client who sends request to update X is also a subscriber of data X.  Therefore, there is no need to send updated data X
     * back to the client.  There are two ways to a client to use data X:
     * 1) Latest data is required
     *    Then a client must subscribe to all changes in order to get latest data.  The only exception is when
     *    the client is the only client of the data.
     *    
     * 2) Latest data is not required
     *    I really cannot think of any use case for this.
     *    
     * @param buffer
     * @param offset
     * @param senderSinkId
     * @param request
     * @param parameters
     */
	private void handleUpdateRequest(final DirectBuffer buffer, final int offset, int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		try {
			// Extract entity from request
			RiskControlSbeDecoder sbe = this.messenger.receiver().extractRiskControlFromRequest(buffer, offset, senderSinkId, request);
			if (sbe.toAllEntity() == BooleanType.TRUE){
				defaultRiskCtrlSettings.get(sbe.entityType()).merge(sbe.controls());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
				return;
			}
			
			long sid = sbe.specificEntitySid();
			switch (sbe.entityType()){
			case FIRM:{
				LOG.error("Unexpected specific firm id for risk control type for our firm [clientKey:{}, specificEntitySid:{}]", request.clientKey(), sbe.specificEntitySid());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
				return;
			}
			case SECURITY:{
				RiskState control = getSecurityRiskControl(sid);
				control.setting(control.setting().clone(EntityType.SECURITY, sid).merge(sbe.controls()));
				break;
			}
			case ISSUER:{
				RiskState control = getIssuerRiskControl((int)sid);
				control.setting(control.setting().clone(EntityType.ISSUER, sid).merge(sbe.controls()));
				break;
			}
			case UNDERLYING:{
				RiskState control = getUndRiskControl((int)sid);
				control.setting(control.setting().clone(EntityType.UNDERLYING, sid).merge(sbe.controls()));
				break;
			}
			default:
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.UNSUPPORTED_OPERATION);
				return;
			}
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		}
		catch (IllegalArgumentException e){
			LOG.error("Caught exception in handleUpdateRequest [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
		}
		catch (Exception e){
			LOG.error("Caught unexpected exception while handling subscription request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}

	@SuppressWarnings("unused")
	private void handleGetAndSubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		
	}
	private void handleGetRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		try {
			int clientKey = request.clientKey();
			int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
			MessageSinkRef sink = refMgr.get(senderSinkId);
			
			Optional<DataType> parameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
			if (!parameter.isPresent()){
				LOG.error("Invalid parameter. Expect only one data type");
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
				return;
			}
			DataType dataType = parameter.get();
			switch (dataType){
			case ALL_POSITION_UPDATE:
				messenger.responseSender().sendSbeEncodable(sink, request.clientKey(), BooleanType.FALSE, responseSeq++, ResultType.OK, this.firmPosition.details());
		    	for (Long2ObjectMap.Entry<SecurityPosition> entry : this.secPositions.long2ObjectEntrySet()){
		    		messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue().details());
		    	}
				for (Int2ObjectMap.Entry<AggregatedSecurityPosition> entry : this.issuerPositions.int2ObjectEntrySet()){
		    		messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue().details());
		    	}
		    	for (Long2ObjectMap.Entry<AggregatedSecurityPosition> entry : this.undPositions.long2ObjectEntrySet()){
		    		messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue().details());
		    	}
		    	messenger.responseSender().sendResponse(sink, clientKey, BooleanType.TRUE, responseSeq, ResultType.OK);
				break;
			case ALL_RISK_CONTROL_STATE_UPDATE:
				handleGetRiskState(sink, clientKey, false);
				break;
			case ALL_RISK_CONTROL_STATE_UPDATE_IF_CHANGED:
				handleGetRiskState(sink, clientKey, true);
				break;
			default:
				LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.UNSUPPORTED_OPERATION);
				return;
			}
		}
		catch (IllegalArgumentException e){
			LOG.error("Caught IllegalArgumentException while handling get request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		catch (Exception e){
			LOG.error("Caught unexpected exception while handling get request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}
	
	private void handleGetRiskState(MessageSinkRef sink, int clientKey, boolean changedOnly){
		int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
		if (!changedOnly){
			messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, this.firmRiskState);
			for (Long2ReferenceMap.Entry<RiskState> entry : this.secRiskStates.long2ReferenceEntrySet()){
				messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue());
			}
			for (Int2ReferenceMap.Entry<RiskState> entry : this.issuerRiskStates.int2ReferenceEntrySet()){
				messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue());
			}
			for (Long2ReferenceMap.Entry<RiskState> entry : this.undRiskStates.long2ReferenceEntrySet()){
				messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue());
			}
		}
		else{
			if (firmRiskState.changed()){
				messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, this.firmRiskState);
			}
			for (Long2ReferenceMap.Entry<RiskState> entry : this.secRiskStates.long2ReferenceEntrySet()){
				if (entry.getValue().changed()){
					messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue());
				}
			}
			for (Int2ReferenceMap.Entry<RiskState> entry : this.issuerRiskStates.int2ReferenceEntrySet()){
				if (entry.getValue().changed()){
					messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue());
				}
			}
			for (Long2ReferenceMap.Entry<RiskState> entry : this.undRiskStates.long2ReferenceEntrySet()){
				if (entry.getValue().changed()){
					messenger.responseSender().sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseSeq++, ResultType.OK, entry.getValue());
				}
			}		
		}
    	messenger.responseSender().sendResponse(sink, clientKey, BooleanType.TRUE, responseSeq, ResultType.OK);
	}
    
    private void handleSubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		try{
			// Get the snapshots and send them back
			Optional<DataType> parameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
			if (!parameter.isPresent()){
				LOG.error("Invalid parameter. Expect only one data type");
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
				return;
			}
			
			MessageSinkRef sink = refMgr.get(senderSinkId);
			DataType dataType = parameter.get();
			switch (dataType){
			case ALL_POSITION_UPDATE:
				LOG.debug("Added subscriber for ALL_POSITION_UPDATE");
				allPosUpdSubs.add(sink);
				break;
			case PORTFOLIO_POSITION_UPDATE:
				addSubscriber(sink, parameters.get(ParameterType.PORTFOLIO_SID), portPosUpdSubs);
				break;
			case ALL_RISK_CONTROL_STATE_UPDATE:
				// Send updates of risk control back to subscribers
				allRiskStateChangeSubs.add(sink);
				break;
			case PORTFOLIO_RISK_CONTROL_STATE_UPDATE:
				addSubscriber(sink, parameters.get(ParameterType.PORTFOLIO_SID), portRiskStateChangeSubs);
				break;
			default:
				LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.UNSUPPORTED_OPERATION);
				return;
			}
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
			return;
		}
		catch (IllegalArgumentException e){
			LOG.error("Caught IllegalArgumentException while handling subscription request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		catch (Exception e){
			LOG.error("Caught unexpected exception while handling subscription request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}
    
    private static void addSubscriber(MessageSinkRef sink, ImmutableList<Parameter> portfolioSids, Int2ReferenceOpenHashMap<SubscriberList> map){
		if (portfolioSids.size() == 0){
			throw new IllegalArgumentException("Number of portfolio sid must be >= 1");
		}
		for (Parameter portfolioSid : portfolioSids){
			int portSid = portfolioSid.valueLong().intValue();
			SubscriberList subscriberList = map.get(portSid);
			if (subscriberList == null){
				subscriberList = SubscriberList.of();
				map.put(portSid, subscriberList);
			}
			subscriberList.add(sink);				
		}
    }
    
    private static void removeSubscriber(MessageSinkRef sink, ImmutableList<Parameter> portfolioSids, Int2ReferenceOpenHashMap<SubscriberList> map){
		if (portfolioSids.size() == 0){
			throw new IllegalArgumentException("Number of portfolio sid must be >= 1");
		}
		for (Parameter portfolioSid : portfolioSids){
			int portSid = portfolioSid.valueLong().intValue();
			SubscriberList subscriberList = map.get(portSid);
			if (subscriberList != null){
				subscriberList.remove(sink);
			}
		}
    }
    
    private void handleUnsubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
    	try {
			// Get the snapshots and send them back
			Optional<DataType> parameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
			if (!parameter.isPresent()){
				LOG.error("Invalid parameter. Expect only one data type");
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
				return;
			}

			// Get the snapshots and send them back
    		MessageSinkRef sink = refMgr.get(senderSinkId);
    		DataType dataType = parameter.get();
    		switch (dataType){
    		case ALL_POSITION_UPDATE:
    			allPosUpdSubs.remove(sink);
    			break;
    		case PORTFOLIO_POSITION_UPDATE:
				removeSubscriber(sink, parameters.get(ParameterType.PORTFOLIO_SID), portPosUpdSubs);
				break;
    		case ALL_RISK_CONTROL_STATE_UPDATE:
    			allRiskStateChangeSubs.remove(sink);
    			// Send updates of risk control back to subscribers
    			break;
    		case PORTFOLIO_RISK_CONTROL_STATE_UPDATE:
				removeSubscriber(sink, parameters.get(ParameterType.PORTFOLIO_SID), portRiskStateChangeSubs);
				break;
    		default:
    			LOG.error("Unsupported subscription type [clientKey:{}, subscriptionType:{}]", request.clientKey(), dataType.name());
    			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
    			return;
    		}
    		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
    		return;    		
    	}
		catch (IllegalArgumentException e){
			LOG.error("Caught IllegalArgumentException while handling unsubscription request", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		catch (Exception e){
			LOG.error("Caught unexpected exception while handling unsubscription request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}
    
    private final SecurityPositionDetails NULL_POSITION = SecurityPositionDetails.of();
    private final Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
			long secSid = security.sid();
			LOG.debug("Received security update [secSid:{}, undSecSid:{}]", secSid, security.undSid());
	    	SecurityPosition position = secPositions.get(secSid);
	    	if (position != null){
	    		if (position.issuerSid() <= 0){
	            	int issuerSid = security.issuerSid();
	        		AggregatedSecurityPosition issuerPosition = getIssuerPosition(issuerSid);
	    			position.addHandler(issuerPosition);
	    			issuerPosition.handleChange(position.details(), NULL_POSITION);
	    		}
	    		else if (position.issuerSid() != security.issuerSid()){
	    			LOG.error("Issuer of security has changed.  This is currently not supported. [secSid:{}, fromIssuerId:{}, toIssuerId:{}", secSid, position.issuerSid(), security.issuerSid());
	    		}
	    		
	    		if (position.undSecSid() <= 0 && security.undSid() > 0){
	    			long undSecSid = security.undSid();
	        		AggregatedSecurityPosition undPosition = getUndPosition(undSecSid);
	    			position.addHandler(undPosition);
	    			undPosition.handleChange(position.details(), NULL_POSITION);
	    		}
	    		else if (position.undSecSid() != security.undSid()){
	    			LOG.error("Underlying of security has changed.  This is currently not supported. [secSid:{}, fromUndSecId:{}, toUndSecId:{}", secSid, position.undSecSid(), security.undSid());
	    		}
	    		// Add only if not already exists
	    		position.addHandler(firmPosition);

	    		// Link up issuer and underlying first before calling position.handleSecurity()
	    		position.handleSecurity(security, schedules, commonSecChangeTracker);
	    	}
	    	else {
	    		// No existing position
	    		position = createSecurityPosition(secSid, security.securityType(), security.putOrCall(), security.undSid(), security.issuerSid());
	    	}			
		}
    };
    
    SecurityPosition createSecurityPosition(long secSid){
		SecurityPosition position = SecurityPosition.of(secSid);
		position.addHandler(batchIfChange);
		secPositions.put(secSid, position);

		// Create PositionRiskControl for security
		RiskState securityRiskControl = getSecurityRiskControl(secSid);
		securityRiskControl.addHandler(sendIfChange);
		position.addHandler(securityRiskControl);

		// Link up AggregatedSecurityPosition at firm level
		position.addHandler(firmPosition);
		
		return position;
    }
    
    SecurityPosition createSecurityPosition(long secSid, SecurityType securityType, PutOrCall putOrCall, long undSecSid, int issuerSid){
		FeeAndCommissionSchedule newSchedule = schedules.get(securityType);
		if (newSchedule == null){
			LOG.warn("There is no fees and commission schedule for this security, assume all zeros [secSid:{}, secType:{}]", secSid, securityType.name());
			newSchedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION; 
		}
		
		SecurityPosition position = SecurityPosition.of(secSid, 
				securityType, 
				putOrCall,
				undSecSid, 
				issuerSid, 
				newSchedule);
		position.addHandler(batchIfChange);
		secPositions.put(secSid, position);

		// Create PositionRiskControl for security
		RiskState securityRiskControl = getSecurityRiskControl(secSid);
		securityRiskControl.addHandler(sendIfChange);
		position.addHandler(securityRiskControl);

		// Create AggregatedSecurityPosition for issuer
		AggregatedSecurityPosition issuerPosition = getIssuerPosition(issuerSid); 
		position.addHandler(issuerPosition);
		
		// Create AggregatedSecurityPosition for underlying
		if (undSecSid >= 0){
			AggregatedSecurityPosition undPosition = getUndPosition(undSecSid);
			position.addHandler(undPosition);
		}
		
		position.addHandler(firmPosition);    	
		
		return position;
    }
    
    private AggregatedSecurityPosition getIssuerPosition(int issuerSid){
		AggregatedSecurityPosition issuerPosition = issuerPositions.get(issuerSid);
		if (issuerPosition == null){
			issuerPosition = AggregatedSecurityPosition.of(issuerSid, EntityType.ISSUER);
			issuerPosition.addHandler(batchIfChange);
			issuerPositions.put(issuerSid, issuerPosition);

			// Add risk control to issuerPosition if not already exist
			RiskState issuerRiskControl = getIssuerRiskControl(issuerSid);
			issuerRiskControl.addHandler(sendIfChange);
			issuerPosition.addHandler(issuerRiskControl);
		}
		return issuerPosition;
    }
    
    private AggregatedSecurityPosition getUndPosition(long undSecSid){
		AggregatedSecurityPosition undPosition = undPositions.get(undSecSid);
		if (undPosition == null){
			undPosition = AggregatedSecurityPosition.of(undSecSid, EntityType.UNDERLYING);
			undPosition.addHandler(batchIfChange);
			undPositions.put(undSecSid, undPosition);
			
			// Add risk control to undPosition if not already exist
			RiskState undRiskControl = getUndRiskControl(undSecSid);
			undRiskControl.addHandler(sendIfChange);
			undPosition.addHandler(undRiskControl);
		}
		return undPosition;
    }
    
    private final Handler<BoobsSbeDecoder> boobsHandler = new Handler<BoobsSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, BoobsSbeDecoder boobs) {
			LOG.debug("Received market data [secSid:{}, bid:{}, ask:{}]", boobs.secSid(), boobs.bid(), boobs.ask());
			getSecurityPosition(boobs.secSid()).handleBoobs(boobs, commonSecChangeTracker);
		}
    };
    
    private final Handler<PositionSbeDecoder> positionHandler = new Handler<PositionSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder position) {
	    	LOG.debug("Received position [entitySid:{}, entityType:{}, openPosition:{}]", position.entitySid(), position.entityType().name(), position.openPosition());
	    	if (position.entityType() == EntityType.SECURITY){
	    		getSecurityPosition(position.entitySid()).handleInitPosition(position);
	    	}
		}
	};

	private final Handler<OrderSbeDecoder> orderHandler = new Handler<OrderSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder order) {
	    	LOG.debug("Received order [orderSid:{}, secSid:{}]", order.orderSid(), order.secSid());
	    	getSecurityPosition(order.secSid()).handleOrder(order, commonSecChangeTracker);			
		}
	};
    
    private final Handler<TradeSbeDecoder> tradeHandler = new Handler<TradeSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder trade) {
	    	LOG.debug("Received trade [tradeSid:{}, orderSid:{}, secSid:{}, channelId:{}, channelSeq:{}]", trade.tradeSid(), trade.orderSid(), trade.secSid(), trade.channelId(), trade.channelSnapshotSeq());
	    	// Filter out trade that we have already processed
	    	long processedTradeSeq = processedTradeSeqByChannel.get(trade.channelId());
	    	if (trade.channelSnapshotSeq() > processedTradeSeq){
	    		processedTradeSeqByChannel.put(trade.channelId(), trade.channelSnapshotSeq());
	    		getSecurityPosition(trade.secSid()).handleTrade(trade, commonSecChangeTracker);
	    	}
	    	else {
	    		LOG.warn("Ignored trade [tradeSid:{}, secSid:{}, channelId:{}, channelSeq:{}]", trade.tradeSid(), trade.secSid(), trade.channelId(), trade.channelSnapshotSeq());
	    	}
	    }
	};

    private RiskState getSecurityRiskControl(long sid){
    	RiskState securityRiskState = secRiskStates.get(sid);
		if (securityRiskState == null){
			securityRiskState = RiskState.of(sid, EntityType.SECURITY, defaultRiskCtrlSettings.get(EntityType.SECURITY));
			LOG.debug("Created security risk state [secSid:{}, {}]", sid, securityRiskState);
			secRiskStates.put(sid, securityRiskState);
		}
		return securityRiskState;
    }
    
    private RiskState getIssuerRiskControl(int sid){
    	RiskState issuerRiskState = issuerRiskStates.get(sid);
    	if (issuerRiskState == null){
    		issuerRiskState = RiskState.of(sid, EntityType.ISSUER, defaultRiskCtrlSettings.get(EntityType.ISSUER));
    		LOG.debug("Created issuer risk state [issuerSid:{}, {}]", sid, issuerRiskState);
    		issuerRiskStates.put(sid, issuerRiskState);
    	}
    	return issuerRiskState;
    }
    
    private RiskState getUndRiskControl(long sid){
    	RiskState undRiskState = undRiskStates.get(sid);
    	if (undRiskState == null){
    		undRiskState = RiskState.of(sid, EntityType.UNDERLYING, defaultRiskCtrlSettings.get(EntityType.UNDERLYING));
    		LOG.debug("Created underlying risk state [undSid:{}, {}]", sid, undRiskState);
    		undRiskStates.put(sid, undRiskState);
    	}
    	return undRiskState;
    }
    
    private SecurityPosition getSecurityPosition(long secSid){
       	SecurityPosition position = secPositions.get(secSid);
    	if (position == null){
    		// We only have secSid at this point, so
    		// there is no need to link up handler for issuer and underlying
    		position = createSecurityPosition(secSid);
    		
    		// Send a request to get security
			// On complete, update 
    		trySendRequestToGetSecurity(secSid);
    	}
    	return position;
    }

    private void requestMissing(){
    	missingSecurities.forEach((id) -> {
    		sendRequestToGetSecurity(id);
    	});
    }
    
    /**
     * Publish changed position snapshots only.  Changed risk states are published immediately.
     */
    private void publishSnapshots(){
    	// Only publish position snapshots
    	for (PositionDetails updatedPosition : this.updPositions){
    		LOG.debug("publishSnapshots for PositionDetails");
    		this.messenger.positionSender().sendPosition(this.allPosUpdSubs.elements(), this.allPosUpdSubs.size(), updatedPosition, sendResults);
    	}
    	this.updPositions.clear();
    	for (SecurityPositionDetails updatedPosition : this.updSecPositions){
    		LOG.debug("publishSnapshots for SecurityPositionDetails");
    		LOG.debug("{}", updatedPosition);
    		this.messenger.positionSender().sendPosition(this.allPosUpdSubs.elements(), this.allPosUpdSubs.size(), updatedPosition, sendResults);
    	}
    	this.updSecPositions.clear();
    	
    }
    
    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
    	@Override
    	public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
    		if (codec.timerEventType() == TimerEventType.TIMER){
    			if (codec.clientKey() == CLIENT_KEY_FOR_PUBLISHING){
    				publishSnapshots();
    				messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
    			}
    			else if (codec.clientKey() == CLIENT_KEY_FOR_REQUEST_MISSING){
    				requestMissing();
    				messenger.newTimeout(requestMissingTask.get(), requestMissingFrequency);
    			}
    		}
    	}
    };
    
    private final TimeoutHandler publishSnapshots = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			// Retry in a moment
			messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_PUBLISHING, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
			}
		}
	};
	
	private final TimeoutHandler requestMissing = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			messenger.newTimeout(requestMissingTask.get(), requestMissingFrequency);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_REQUEST_MISSING, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(requestMissingTask.get(), requestMissingFrequency);
			}
		}
	};
	
	Long2ObjectOpenHashMap<SecurityPosition> secPositions(){
		return secPositions;
	}

	Long2ObjectOpenHashMap<AggregatedSecurityPosition> undPositions(){
		return undPositions;
	}

	Int2ObjectOpenHashMap<AggregatedSecurityPosition> issuerPositions(){
		return issuerPositions;
	}

	AggregatedSecurityPosition firmPosition(){
		return firmPosition;
	}

	Long2ReferenceOpenHashMap<RiskState> secRiskStates(){
		return secRiskStates;
	}

	Long2ReferenceOpenHashMap<RiskState> undRiskStates(){
		return undRiskStates;
	}
	
	Int2ReferenceOpenHashMap<RiskState> issuerRiskStates(){
		return issuerRiskStates;
	}
	
	RiskState firmRiskState(){
		return firmRiskState;
	}
	
	SubscriberList allPosUpdSubs(){
		return allPosUpdSubs;
	}
	
	SubscriberList allRiskStateChangeSubs(){
		return allRiskStateChangeSubs;
	}
}
