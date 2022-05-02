package com.lunar.service;

import static org.apache.logging.log4j.util.Unbox.box;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.LongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.config.ServiceConfig;
import com.lunar.config.StrategyServiceConfig;
import com.lunar.core.ServiceStatusTracker.AggregatedServiceStatusChangeHandler;
import com.lunar.core.SubscriberList;
import com.lunar.core.TriggerInfo;
import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.MarketDataClient;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.CommandDecoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.GreeksUpdateHandler;
import com.lunar.strategy.LunarStrategyInfoSender;
import com.lunar.strategy.LunarStrategyOrderService;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.OrderStatusReceivedHandler;
import com.lunar.strategy.Strategy;
import com.lunar.strategy.StrategyCircuitSwitch.SwitchHandler;
import com.lunar.strategy.StrategyErrorHandler;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyManager;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategyStaticScheduleSetupTask;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridFactory;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridStrategy;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
@SuppressWarnings("unused")
public class StrategyService implements ServiceLifecycleAware {
    private static final Logger LOG = LogManager.getLogger(StrategyService.class);

    private static final int DEFAULT_NUMBER_OF_STRATEGIES = 10;
    private static final int DEFAULT_UNDERLYING_SIZE = 200;
    private static final int DEFAULT_ISSUER_SIZE = 100;
    private static final long ORDER_RETRY_LONG_TIMEOUT = 10_000_000_000L;
    private static final long ORDER_RETRY_SMALL_TIMEOUT = 150_000_000L;
    
    private final String name;
    private final LunarService messageService;
    private final Messenger messenger;
    private final StrategyManager strategyManager;    
    private final StrategyServiceConfig specificConfig;
    private final LongEntityManager<StrategySecurity> securities;
    private final LongEntityManager<StrategyIssuer> issuers;

    private final MarketDataClient marketDataClient;
    private final MarketTicksStrategyScheduler scheduler;
    
    private final TriggerInfo triggerInfo;
    private final LunarStrategyOrderService orderService;
    private final Messenger timerMessenger;
    private MarketStatusType marketStatus;    
    
    private final SubscriberList globalSubscribers;
    private final SubscriberList performanceSubscribers;
    
    private final StrategyInfoSender strategyInfoSender;
    
    private final boolean canAutoSwitchOn;
    
    private Timeout m_paramsTimerTask;
    private final long sendParamsInterval;
    private final Object2IntOpenHashMap<String> assignedThrottleTracketIndexByIssuer;
    
    private final ObjectOpenHashSet<String> underlyingFilter;
    private final ObjectOpenHashSet<String> issuerFilter;

    public static StrategyService of(final ServiceConfig config, final LunarService messageService) {
        return new StrategyService(config, messageService);
    }

    StrategyService(final ServiceConfig config, final LunarService messageService){
        this.name = config.name();
        this.messageService = messageService;
        this.messenger = messageService.messenger();
        this.timerMessenger = messenger.createChildMessenger();
        this.marketStatus = MarketStatusType.NULL_VAL;     
        this.performanceSubscribers = SubscriberList.of(ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
        this.globalSubscribers = SubscriberList.of(ServiceConstant.MAX_SUBSCRIBERS);
        
        this.triggerInfo = TriggerInfo.of();        
        if (config instanceof StrategyServiceConfig){
            specificConfig = (StrategyServiceConfig)config;
            canAutoSwitchOn = specificConfig.canAutoSwitchOn();
            issuers = new LongEntityManager<StrategyIssuer>(DEFAULT_ISSUER_SIZE);
            securities = new LongEntityManager<StrategySecurity>(specificConfig.numUnderlyings() + specificConfig.numWarrants());
            orderService = new LunarStrategyOrderService(messenger, messageService.systemClock(), triggerInfo, errorHandler, performanceSubscribers);
            strategyInfoSender = new LunarStrategyInfoSender(messenger, messageService.systemClock(), globalSubscribers);
            scheduler = new MarketTicksStrategyScheduler();
            strategyManager = new StrategyManager(specificConfig, securities, issuers, orderService, strategyInfoSender, securitySwitchHandler, scheduler);
            new StrategyStaticScheduleSetupTask(scheduler, strategyManager, specificConfig.canAutoSwitchOn());
            marketDataClient = new MarketDataClient(messenger, securities, specificConfig.numUnderlyings() + specificConfig.numWarrants());
            sendParamsInterval = specificConfig.sendParamsInterval();
            
            assignedThrottleTracketIndexByIssuer = new Object2IntOpenHashMap<>();
            if (specificConfig.throttleTrackerIndexByIssuer().isPresent()){
            	StringTokenizer pairs = new StringTokenizer(specificConfig.throttleTrackerIndexByIssuer().get(), ";");
            	while (pairs.hasMoreElements()){
            		String pair = pairs.nextElement().toString();
            		String[] items = pair.split(":");
            		int trackerIndex = Integer.parseInt(items[0]);
            		String[] issuers = items[1].split(",");
            		for (String issuer : issuers){
            			assignedThrottleTracketIndexByIssuer.put(issuer, trackerIndex);
            			LOG.info("Loaded throttle tracker index by issuer [index:{}, issuer:{}]", trackerIndex, issuer);
            		}
            	}
            }
            assignedThrottleTracketIndexByIssuer.trim();            
            if (specificConfig.underlyingFilter().isPresent()) {
                underlyingFilter = new ObjectOpenHashSet<String>();
                StringTokenizer pairs = new StringTokenizer(specificConfig.underlyingFilter().get(), ";");
                while (pairs.hasMoreElements()) {
                    underlyingFilter.add(pairs.nextElement().toString());
                }
            }
            else {
                underlyingFilter = null;
            }
            if (specificConfig.issuerFilter().isPresent()) {
                issuerFilter = new ObjectOpenHashSet<String>();
                StringTokenizer pairs = new StringTokenizer(specificConfig.issuerFilter().get(), ";");
                while (pairs.hasMoreElements()) {
                    issuerFilter.add(pairs.nextElement().toString());
                }
            }
            else {
                issuerFilter = null;
            }

            registerStrategies();
        }
        else{
            throw new IllegalArgumentException("Service " + this.name + " expects a StrategyServiceConfig config");
        }
    }
    
    private void registerStrategies() {
        strategyManager.registerStrategyFactory(new SpeedArbHybridFactory());
    }
    
    private StrategyErrorHandler errorHandler = new StrategyErrorHandler() {
        @Override
        public void onError(final long secSid, final String errorMessage) {
            try {
                strategyManager.flipSwitchForSecurity(secSid, BooleanType.FALSE, StrategyExitMode.ERROR, true);
                messenger.eventSender().sendEventWithOneValue(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.CRITICAL, messenger.self().sinkId(), messageService.systemClock().nanoOfDay(), errorMessage, EventValueType.SECURITY_SID, secSid);
            }
            catch (final Exception e) {
                LOG.error("Error when trying to switch off security {}...", secSid, e);
            }
        }
        
    };

    @Override
	public StateTransitionEvent warmupEnter() {
        final LongEntityManager<StrategyType> strategyTypes = new LongEntityManager<StrategyType>(2);
        final LongEntityManager<StrategyIssuer> issuers = new LongEntityManager<StrategyIssuer>(1);
        final Long2ObjectOpenHashMap<StrategySecurity> underlyings = new Long2ObjectOpenHashMap<StrategySecurity>(1);
        final Long2ObjectOpenHashMap<StrategySecurity> warrants = new Long2ObjectOpenHashMap<StrategySecurity>(2);
        final Long2ObjectOpenHashMap<OrderStatusReceivedHandler> osReceivedHandlers = new Long2ObjectOpenHashMap<OrderStatusReceivedHandler>(1);
        
        final SubscriberList warmupSubscribers = SubscriberList.of(1);
        final LunarStrategyOrderService orderService = new LunarStrategyOrderService(messenger, messageService.systemClock(), TriggerInfo.of(),
                new StrategyErrorHandler() {
                    @Override
                    public void onError(long secSid, final String errorMessage) {
                    }
            
                },
                warmupSubscribers);
        orderService.isInMarketHour(true);
        orderService.isRecovery(false);
        StrategyInfoSender stratInfoSender = new LunarStrategyInfoSender(messenger, messageService.systemClock(), warmupSubscribers);
        
        strategyManager.initializeWarmups(messageService.systemClock(), orderService, stratInfoSender, strategyTypes, issuers, underlyings, warrants, osReceivedHandlers);
        strategyManager.doWarmups();
        strategyManager.releaseWarmups();
		return StateTransitionEvent.WARMUP_COMPLETE;
	}
	
    @Override
    public StateTransitionEvent resetEnter() {
        return StateTransitionEvent.RESET_COMPLETE;
    }
    
    @Override
    public StateTransitionEvent waitingForServicesEnter() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataSnapshotService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderManagementAndExecutionService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderAndTradeSnapshotService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PricingService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PortfolioAndRiskService);
        messenger.serviceStatusTracker().trackAggregatedServiceStatus(waitForServiceHandler);
        return StateTransitionEvent.NULL;
    }
    
    @Override
    public void waitingForServicesExit() {
        // noop
    }
    
    private final AggregatedServiceStatusChangeHandler waitForServiceHandler = new AggregatedServiceStatusChangeHandler() {
        
        @Override
        public void handle(boolean status) {
            if (status){
                messageService.stateEvent(StateTransitionEvent.READY);
            }
            else { // DOWN or INITIALIZING
                messageService.stateEvent(StateTransitionEvent.WAIT);
            }   
        }
    };
    
    @Override
    public StateTransitionEvent waitingForWarmupServicesEnter() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.WarmupService);
        messenger.serviceStatusTracker().trackAggregatedServiceStatus(waitForWarmupServiceHandler);
        return StateTransitionEvent.NULL;
    }
    
    private final AggregatedServiceStatusChangeHandler waitForWarmupServiceHandler = new AggregatedServiceStatusChangeHandler() {
        
        @Override
        public void handle(boolean status) {
            if (status){
                messageService.stateEvent(StateTransitionEvent.WARMUP);
            }
            else { // DOWN or INITIALIZING
                messageService.stateEvent(StateTransitionEvent.WAIT);
            }               
        }
    };
    
    @Override
    public void waitingForWarmupServicesExit() {
        messenger.serviceStatusTracker().untrackServiceType(ServiceType.WarmupService);
        messenger.serviceStatusTracker().untrackAggregatedServiceStatus(waitForWarmupServiceHandler);        
    };
	
    @Override
    public StateTransitionEvent idleStart() {
        return StateTransitionEvent.NULL;
    }
    
    @Override
    public StateTransitionEvent readyEnter() {
        messenger.receiver().issuerHandlerList().add(issuerHandler);
        messenger.receiver().securityHandlerList().add(securityHandler);
        messenger.receiver().strategyTypeHandlerList().add(strategyTypeHandler);
        messenger.receiver().strategyParamsHandlerList().add(strategyTypeParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().add(strategyUnderlyingParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().add(strategyWarrantParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().add(strategyIssuerParamsHandler);
        messenger.receiver().strategyIssuerUndParamsHandlerList().add(strategyIssuerUndParamsHandler);
        
        messenger.receiver().strategySwitchHandlerList().add(strategySwitchHandler);
        messenger.registerEventsForOrderTracker();
        messenger.receiver().marketStatusHandlerList().add(marketStatusHandler);
        messenger.receiver().orderBookSnapshotHandlerList().add(orderBookSnapshotHandler);        
        messenger.receiver().marketDataTradeHandlerList().add(marketDataTradeHandler);
        messenger.receiver().marketStatsHandlerList().add(marketStatsHandler);
        messenger.receiver().greeksHandlerList().add(greeksHandler);
        messenger.receiver().orderAcceptedHandlerList().add(orderAcceptedHandler);
        messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandler);
        messenger.receiver().orderExpiredHandlerList().add(orderExpiredHandler);
        messenger.receiver().orderExpiredWithOrderInfoHandlerList().add(orderExpiredWithOrderInfoHandler);
        messenger.receiver().orderCancelledHandlerList().add(orderCancelledHandler);
        messenger.receiver().orderCancelledWithOrderInfoHandlerList().add(orderCancelledWithOrderInfoHandler);        
        messenger.receiver().orderRejectedHandlerList().add(orderRejectedHandler);
        messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(orderRejectedWithOrderInfoHandler);
        messenger.receiver().tradeCreatedHandlerList().add(tradeCreatedHandler);
        messenger.receiver().tradeCreatedWithOrderInfoHandlerList().add(tradeCreatedWithOrderInfoHandler);
        messenger.receiver().orderHandlerList().add(orderHandler);
        messenger.receiver().tradeHandlerList().add(tradeHandler);
        messenger.receiver().positionHandlerList().add(positionHandler);
        getIssuers();
        
        return StateTransitionEvent.NULL;
    }
    
    private void getIssuers() {
        LOG.info("Fetching issuers...");
        CompletableFuture<Request> retrieveIssuerFuture = messenger.sendRequest(messenger.referenceManager().rds(),
                RequestType.GET,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ISSUER.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveIssuerFuture.thenAccept((r) -> {
            LOG.info("Issuers fully retrieved!");
            subscribeSecurities();
        });
    }
    
    private void subscribeSecurities() {
        LOG.info("Subscribing securities...");
        CompletableFuture<Request> retrieveSecurityFuture = messenger.sendRequest(messenger.referenceManager().rds(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveSecurityFuture.thenAccept((r) -> {
            LOG.info("Securities fully subscribed!");
            getOpenPosition();
        });        
    }
    
    private void getOpenPosition() {
        LOG.info("Fetching open positions...");
        CompletableFuture<Request> retrievePosition = messenger.sendRequest(messenger.referenceManager().risk(),
                RequestType.GET,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(DataType.ALL_POSITION_UPDATE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrievePosition.thenAccept((r) -> {
            LOG.info("Positions fully retrieved!");
            subscribeMarketStatus();
        });        
    }    
    
    private void subscribeMarketStatus() {
        LOG.info("Subscribing to market status...");
        CompletableFuture<Request> retrieveExchangeFuture = messenger.sendRequest(messenger.referenceManager().mds(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.MARKETSTATUS.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveExchangeFuture.thenAccept((r) -> {
            LOG.info("Strategy status fully subscribed!");            
            getStrategyTypes();
            //messageService.stateEvent(StateTransitionEvent.ACTIVATE);
        });        
    }
    
    private void getStrategyTypes() {
        LOG.info("Fetching strategy types...");
        strategyManager.onEntitiesLoaded();
        try {
            CompletableFuture<Request> retrieveStrategyTypeFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                    RequestType.GET,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATEGYTYPE.value())).build(),
                    ResponseHandler.NULL_HANDLER);
            retrieveStrategyTypeFuture.thenAccept((r) -> {
                LOG.info("Strategy types fully retrieved!");
                strategyManager.setupStrategies();
                subscribeMarketData();
            });
        } catch (final Exception e) {
            LOG.error("Caught exception", e);
            messageService.stateEvent(StateTransitionEvent.FAIL);
        }
    }
    
    private void subscribeMarketData() {
        for (final Security warrant : strategyManager.warrants().values()) {            
            marketDataClient.subscribe(warrant.underlyingSid(), orderBookFactory, mdSubResponseHandler);
            marketDataClient.subscribe(warrant.sid(), orderBookFactory, mdSubResponseHandler);
            
            LOG.info("Subscribing greeks for {}...", warrant.code());
            messenger.sendRequest(messenger.referenceManager().prc(),
                    RequestType.SUBSCRIBE,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.GREEKS.value()), Parameter.of(ParameterType.SECURITY_SID, warrant.sid())).build(),
                    greeksSubResponseHandler);                
        }
        messageService.stateEvent(StateTransitionEvent.ACTIVATE);
    }
    
    @Override
    public void readyExit() {
        // not removing handleSecurity, because we may get updates
    }

    @Override
    public StateTransitionEvent activeEnter() {
        // register commands
        messenger.receiver().requestHandlerList().add(requestHandler);
        messenger.receiver().commandHandlerList().add(commandHandler);
        final Command sendParamsCommand = Command.of(messenger.self().sinkId(), messenger.getNextClientKeyAndIncrement(), CommandType.SEND_STRAT_PARAMS);
        final ImmutableList<Parameter> emptyParametersList = new ImmutableList.Builder<Parameter>().build(); 
        m_paramsTimerTask = messageService.messenger().timerService().newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
                timerMessenger.sendCommand(timerMessenger.self(), sendParamsCommand);                
                m_paramsTimerTask = messageService.messenger().timerService().newTimeout(this, sendParamsInterval, TimeUnit.NANOSECONDS);
            }
        }, sendParamsInterval, TimeUnit.NANOSECONDS);
        return StateTransitionEvent.NULL;
    }
    
    @Override
    public void activeExit() {
        if (m_paramsTimerTask != null) {
            m_paramsTimerTask.cancel();
        }
        // unregister command
        messenger.unregisterEventsForOrderTracker();
        messenger.receiver().marketStatusHandlerList().remove(marketStatusHandler);
        messenger.receiver().orderBookSnapshotHandlerList().remove(orderBookSnapshotHandler);        
        messenger.receiver().marketDataTradeHandlerList().remove(marketDataTradeHandler);
        messenger.receiver().marketStatsHandlerList().remove(marketStatsHandler);
        messenger.receiver().greeksHandlerList().remove(greeksHandler);
        messenger.receiver().orderAcceptedHandlerList().remove(orderAcceptedHandler);
        messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandler);
        messenger.receiver().orderExpiredHandlerList().remove(orderExpiredHandler);
        messenger.receiver().orderExpiredWithOrderInfoHandlerList().remove(orderExpiredWithOrderInfoHandler);
        messenger.receiver().orderCancelledHandlerList().remove(orderCancelledHandler);
        messenger.receiver().orderCancelledWithOrderInfoHandlerList().remove(orderCancelledWithOrderInfoHandler);
        messenger.receiver().orderRejectedHandlerList().remove(orderRejectedHandler);
        messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(orderRejectedWithOrderInfoHandler);
        messenger.receiver().tradeCreatedHandlerList().add(tradeCreatedHandler);
        messenger.receiver().tradeCreatedWithOrderInfoHandlerList().add(tradeCreatedWithOrderInfoHandler);        
        messenger.receiver().requestHandlerList().remove(requestHandler);
        messenger.receiver().commandHandlerList().remove(commandHandler);
        messenger.receiver().securityHandlerList().remove(securityHandler);
        messenger.receiver().issuerHandlerList().remove(issuerHandler);
        messenger.receiver().strategyTypeHandlerList().remove(strategyTypeHandler);
        messenger.receiver().strategyParamsHandlerList().remove(strategyTypeParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().remove(strategyUnderlyingParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().remove(strategyWarrantParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().remove(strategyIssuerParamsHandler);
        messenger.receiver().strategyIssuerUndParamsHandlerList().remove(strategyIssuerUndParamsHandler);
        messenger.receiver().strategySwitchHandlerList().remove(strategySwitchHandler);
        messenger.receiver().orderHandlerList().remove(orderHandler);
        messenger.receiver().tradeHandlerList().remove(tradeHandler);
        messenger.receiver().positionHandlerList().remove(positionHandler);
    }

    private Handler<StrategyTypeSbeDecoder> strategyTypeHandler = new Handler<StrategyTypeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyTypeSbeDecoder strategyType) {
            try {
                strategyManager.handle(buffer, offset, header, strategyType);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }
        }
    };

    private Handler<StrategyParamsSbeDecoder> strategyTypeParamsHandler = new Handler<StrategyParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder strategyParams) {
            try {
                strategyManager.handle(buffer, offset, header, strategyParams);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }
        }
    };

    private Handler<StrategyUndParamsSbeDecoder> strategyUnderlyingParamsHandler = new Handler<StrategyUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder strategyUndParams) {      
            try {
                strategyManager.handle(buffer, offset, header, strategyUndParams);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }      
        }
    };
    
    private Handler<StrategyWrtParamsSbeDecoder> strategyWarrantParamsHandler = new Handler<StrategyWrtParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder strategyWrtParams) {     
            try {
                strategyManager.handle(buffer, offset, header, strategyWrtParams);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            } 
        }
    };
    
    private Handler<StrategyIssuerParamsSbeDecoder> strategyIssuerParamsHandler = new Handler<StrategyIssuerParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder strategyIssuerParams) {    
            try {
                strategyManager.handle(buffer, offset, header, strategyIssuerParams);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            } 
        }
    };
    
    private Handler<StrategyIssuerUndParamsSbeDecoder> strategyIssuerUndParamsHandler = new Handler<StrategyIssuerUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder strategyIssuerUndParams) {    
            try {
//            	LOG.info("Received strategy issuer und param in strategy service, load into strategy manager [issuerSid:{}, undSid:{}]", strategyIssuerUndParams.issuerSid(), strategyIssuerUndParams.undSid());
                strategyManager.handle(buffer, offset, header, strategyIssuerUndParams);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            } 
        }
    };

    private Handler<StrategySwitchSbeDecoder> strategySwitchHandler = new Handler<StrategySwitchSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder strategySwitch) {
            try {
                strategyManager.handle(buffer, offset, header, strategySwitch);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            } 
        }        
    };
    
    private boolean isSecuritySupported(final Security security) {
        if (!security.isAlgo())
            return false;
        if (security.securityType().equals(SecurityType.STOCK) || security.securityType().equals(SecurityType.INDEX)) {
            return underlyingFilter == null || underlyingFilter.contains(security.code());
        }
        else if (security.securityType().equals(SecurityType.FUTURES)) {
            final Security underlying = securities.get(security.underlyingSid());
            if (underlying == null) {
                return false;
            }
            return true;
        }
        else if (security.securityType().equals(SecurityType.WARRANT)) {
            final Security underlying = securities.get(security.underlyingSid());
            if (underlying == null) {
                return false;
            }
            final Issuer issuer = issuers.get(security.issuerSid());
            if (issuer == null) {
                return false;
            }
            return true;
        }
        return false;
    }
    
    final byte[] securityCodeBytes = new byte[SecuritySbeDecoder.codeLength()];
    int securitySortOrder = 1;
    private final Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {    
            LOG.info("Received target security {}", box(security.sid()));
            security.getCode(securityCodeBytes, 0);
            try {
                StrategyIssuer issuer = issuers.get(security.issuerSid());
                int assignedThrottleTrackerIndex = ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX;
                if (issuer != null){
                	assignedThrottleTrackerIndex = issuer.assignedThrottleTrackerIndex();
                }
                SpreadTable spreadTable = SpreadTableBuilder.getById(security.spreadTableCode());
                final int sortSuffix = securitySortOrder++;
                final int sortPrefix;
                if (issuer == null) {
                    sortPrefix = 0;
                }
                else if (issuer.code().equals("GS")) {
                    sortPrefix = 1;
                }
                else if (issuer.code().equals("SG")) {
                    sortPrefix = 2;
                }
                else if (issuer.code().equals("BI")) {
                    sortPrefix = 3;
                }
                else if (issuer.code().equals("JP")) {
                    sortPrefix = 4;
                }
                else if (issuer.code().equals("MB")) {
                    sortPrefix = 5;
                }
                else {
                    sortPrefix = 6;
                }
                final int sortOrder = sortPrefix << Short.SIZE | sortSuffix;                
                final StrategySecurity newSecurity = (StrategySecurity)StrategySecurity.of(security.sid(), 
                        security.securityType(), 
                        new String(securityCodeBytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
                        security.exchangeSid(), 
                        security.undSid(),
                        security.putOrCall(),
                        security.style(),
                        security.strikePrice(),
                        security.conversionRatio(),
                        security.issuerSid(),
                        security.lotSize(),
                        security.isAlgo() == BooleanType.TRUE, spreadTable,
                        MarketOrderBook.of(security.sid(), 10, spreadTable, Integer.MIN_VALUE, Integer.MIN_VALUE),
                        assignedThrottleTrackerIndex)
                        .sortOrder(sortOrder)
                        .omesSink(messenger.sinkRef(security.omesSinkId()))
                        .mdsSink(messenger.sinkRef(security.mdsSinkId()))
                        .mdsssSink(messenger.sinkRef(security.mdsssSinkId()));
                if (isSecuritySupported(newSecurity)) {
                    securities.add(newSecurity);
                }
            } 
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }
        }
    };

    final byte[] issuerCodeBytes = new byte[IssuerSbeDecoder.codeLength()];
    final byte[] issuerNameBytes = new byte[IssuerSbeDecoder.nameLength()];
    private final Handler<IssuerSbeDecoder> issuerHandler = new Handler<IssuerSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, IssuerSbeDecoder issuer) {
            LOG.info("Received target issuer {}", box(issuer.sid()));
            issuer.getCode(issuerCodeBytes, 0);        
            issuer.getName(issuerNameBytes, 0);
            try {
            	final String issuerCode = new String(issuerCodeBytes, IssuerSbeDecoder.codeCharacterEncoding()).trim();
            	int assignedIndex = assignedThrottleTracketIndexByIssuer.getInt(issuerCode);
            	if (assignedIndex == assignedThrottleTracketIndexByIssuer.defaultReturnValue()){
            		assignedIndex = ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX;
            	}
            	if (issuerFilter == null || issuerFilter.contains(issuerCode)) {
                    final StrategyIssuer newIssuer = StrategyIssuer.of(issuer.sid(),
                    		issuerCode,
                            new String(issuerNameBytes, IssuerSbeDecoder.nameCharacterEncoding()).trim(),
                            assignedIndex);
                    issuers.add(newIssuer);
            	}
            } 
            catch (final UnsupportedEncodingException e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }
        }
    };

    private final MarketDataClient.OrderBookUpdateHandler obUpdateHandler = new MarketDataClient.OrderBookUpdateHandler() {
        @Override
        public void onUpdate(final long transactTime, final Security security, final MarketOrderBook orderBook) {
        	scheduler.handleTimestamp(transactTime);
            final MarketDataUpdateHandler updateHandler = ((StrategySecurity)security).marketDataUpdateHandler();
            if (updateHandler != null) {
                orderService.isRecovery(orderBook.isRecovery());
                try {
                    updateHandler.onOrderBookUpdated(security, transactTime, orderBook);
                }
                catch (final Exception e) {
                    LOG.error("Error encountered when handling orderbook update for {}...",  security.code(), e);
                    errorHandler.onError(security.sid(), "Error encountered when handling orderbook update");
                }
            }
        }
    };        

    private Handler<MarketStatusSbeDecoder> marketStatusHandler = new Handler<MarketStatusSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatusSbeDecoder codec) {
            marketStatus = codec.status();
            LOG.info("Update market status to {}...",  marketStatus);
            orderService.isInMarketHour(marketStatus == MarketStatusType.CT);
        }        
    };
    
    private Handler<OrderBookSnapshotSbeDecoder> orderBookSnapshotHandler = new Handler<OrderBookSnapshotSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder codec) {
            try {
                triggerInfo.decode(codec.triggerInfo());
//                final long startTimestamp = messageService.systemClock().timestamp();                
                if (marketDataClient.handleMarketData(codec, buffer, offset, obUpdateHandler)) {
                    strategyInfoSender.broadcastAllBatched();
//                    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_MARKETDATA, triggerInfo.triggeredBy(), triggerInfo.triggerSeqNum(), triggerInfo.triggerNanoOfDay(), startTimestamp);
//                    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.PROCESSED_MARKETDATA, triggerInfo.triggeredBy(), triggerInfo.triggerSeqNum(), triggerInfo.triggerNanoOfDay(), messageService.systemClock().timestamp());
                }
            } catch (final Exception e) {
                final long secSid = codec.secSid();
                final Security security = securities.get(secSid);
                if (security != null) {
                    LOG.error("Error encountered when handling market data for {}", security.code(), e);    
                    errorHandler.onError(secSid, "Error encountered when handling market data");
                }
            }
        }        
    };
    
    private Handler<MarketStatsSbeDecoder> marketStatsHandler = new Handler<MarketStatsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder payload) {
            
        }
    };
    
    private Handler<MarketDataTradeSbeDecoder> marketDataTradeHandler = new Handler<MarketDataTradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder codec) {
            triggerInfo.decode(codec.triggerInfo());
//            final long startTimestamp = messageService.systemClock().timestamp();
            final long secSid = codec.secSid();
            final StrategySecurity security = securities.get(secSid);
            final MarketDataUpdateHandler updateHandler = security.marketDataUpdateHandler();
            if (updateHandler != null) {
                security.lastMarketTrade().decodeFrom(codec);
                security.lastMarketTrade().detectSide(security.orderBook());
                orderService.isRecovery(codec.isRecovery() == BooleanType.TRUE);
                try {
                    updateHandler.onTradeReceived(security, security.lastMarketTrade().tradeNanoOfDay() ,security.lastMarketTrade());
                } catch (final Exception e) {
                    LOG.error("Error encountered when handling trade for {}", security.code(), e);
                    errorHandler.onError(secSid, "Error encountered when handling trade");
                }
//                messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_MARKETDATA, triggerInfo.triggeredBy(), triggerInfo.triggerSeqNum(), triggerInfo.triggerNanoOfDay(), startTimestamp);
//                messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.PROCESSED_MARKETDATA, triggerInfo.triggeredBy(), triggerInfo.triggerSeqNum(), triggerInfo.triggerNanoOfDay(), messageService.systemClock().timestamp());
            }
        }
    };
    
    private Handler<PositionSbeDecoder> positionHandler = new Handler<PositionSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder position) {
            if (position.entityType() == EntityType.SECURITY) {
                final long secSid = position.entitySid();
                if (strategyManager.warrants().containsKey(secSid)) {
                    StrategySecurity security = strategyManager.warrants().get(secSid);
                    if (position.channelSnapshotSeq() > security.channelSeq()) {
                        security.channelSeq(position.channelSnapshotSeq());
                        LOG.info("Received position of {} for {}, sequence {}", position.openPosition(), security.code(), box(security.channelSeq()));
                        security.position(position.openPosition());
                    }
                }
            }
        }
    };
    
    private Handler<GreeksSbeDecoder> greeksHandler = new Handler<GreeksSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, GreeksSbeDecoder sbe) {
            final StrategySecurity security = securities.get(sbe.secSid());
            if (security != null) {
                security.greeks().decodeFrom(sbe);            
                final GreeksUpdateHandler handler = security.greeksUpdateHandler();
                if (handler != null) {
                    try {
                        handler.onGreeksUpdated(security.greeks());
                    }
                    catch (final Exception e) {
                        LOG.error("Error encountered when handling greeks update for {}", security.code(), e);
                        errorHandler.onError(security.greeks().secSid(), "Error encountered when handling greeks update");
                    }
                }
            }
        }
    };
    
    private Handler<OrderAcceptedSbeDecoder> orderAcceptedHandler = new Handler<OrderAcceptedSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder sbe) {
            final StrategySecurity security = strategyManager.warrants().get(sbe.secSid());
            if (sbe.channelSeq() > security.channelSeq()) {
                security.channelSeq(sbe.channelSeq());
                LOG.info("Received {}-side order accepted for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
            }
            else {
                LOG.info("Received and ignored stale {}-side order accepted for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
            }
        }        
    };

    private Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandler = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder sbe) {
//            final TriggerInfoDecoder triggerInfo = sbe.triggerInfo();
//            messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_ORDERACCEPT, triggerInfo.triggeredBy(), triggerInfo.triggerSeqNum(), triggerInfo.nanoOfDay(), messageService.systemClock().timestamp());
            final StrategySecurity security = strategyManager.warrants().get(sbe.secSid());
            if (sbe.channelSeq() > security.channelSeq()) {
                security.channelSeq(sbe.channelSeq());
                LOG.info("Received {}-side order accepted for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
            }
            else {
                LOG.info("Received and ignored stale {}-side order accepted for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
            }
        }
    };

    private Handler<OrderExpiredSbeDecoder> orderExpiredHandler = new Handler<OrderExpiredSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder sbe) {
            final long secSid = sbe.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (sbe.channelSeq() > security.channelSeq()) {
                        security.channelSeq(sbe.channelSeq());
                        LOG.info("Received {}-side order expired for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
                        try {
                            if (sbe.side().equals(Side.SELL)) {
                                security.updatePendingSell(-sbe.leavesQty());
                                if (sbe.orderSid() == security.limitOrderSid()) {
                                    security.limitOrderSid(0);
                                    security.limitOrderPrice(0);
                                    security.limitOrderQuantity(0);
                                }
                            }
                            handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                            LOG.error("Error encountered when handling order expired for {}", security.code(), e);    
                            errorHandler.onError(secSid, "Error encountered when handling order expired");
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side order expired for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
                    }
                }
            }            
        }        
    };
    
    private Handler<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandler = new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder sbe) {
            final long secSid = sbe.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (sbe.channelSeq() > security.channelSeq()) {
                        security.channelSeq(sbe.channelSeq());
                        LOG.info("Received {}-side order expired for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
                        try {
                            if (sbe.side().equals(Side.SELL)) {
                                security.updatePendingSell(-sbe.leavesQty());
                                if (sbe.orderSid() == security.limitOrderSid()) {
                                    security.limitOrderSid(0);
                                    security.limitOrderPrice(0);
                                    security.limitOrderQuantity(0);
                                }
                            }                        
                            handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                            LOG.error("Error encountered when handling order expired for {}", security.code(), e);    
                            errorHandler.onError(secSid, "Error encountered when handling order expired");
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side order expired for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
                    }
                }
            }
        }
    };

    private Handler<OrderCancelledSbeDecoder> orderCancelledHandler = new Handler<OrderCancelledSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder sbe) {
            final long secSid = sbe.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (sbe.channelSeq() > security.channelSeq()) {
                        security.channelSeq(sbe.channelSeq());
                        LOG.info("Received {}-side order cancelled for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
                        try {
                            if (sbe.side().equals(Side.SELL)) {
                                //positionTracker.updatePendingSell(security, -sbe.leavesQty());
                                security.updatePendingSell(sbe.cumulativeQty() - sbe.quantity());
                                if (sbe.orderSid() == security.limitOrderSid()) {
                                    security.limitOrderSid(0);
                                    security.limitOrderPrice(0);
                                    security.limitOrderQuantity(0);
                                }
                            }
                            handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                            LOG.error("Error encountered when handling order cancelled for {}", security.code(), e);    
                            errorHandler.onError(secSid, "Error encountered when handling order cancelled");
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side order cancelled for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
                    }
                }
            }            
        }        
    };
    
    private Handler<OrderCancelledWithOrderInfoSbeDecoder> orderCancelledWithOrderInfoHandler = new Handler<OrderCancelledWithOrderInfoSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledWithOrderInfoSbeDecoder sbe) {
            final long secSid = sbe.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (sbe.channelSeq() > security.channelSeq()) {
                        security.channelSeq(sbe.channelSeq());
                        LOG.info("Received {}-side order cancelled for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
                        try {
                            if (sbe.side().equals(Side.SELL)) {
                                //positionTracker.updatePendingSell(security, -sbe.leavesQty());
                                security.updatePendingSell(sbe.cumulativeQty() - sbe.quantity());
                                if (sbe.orderSid() == security.limitOrderSid()) {
                                    security.limitOrderSid(0);
                                    security.limitOrderPrice(0);
                                    security.limitOrderQuantity(0);
                                }
                            }
                            handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                            LOG.error("Error encountered when handling order cancelled for {}", security.code(), e);    
                            errorHandler.onError(secSid, "Error encountered when handling order cancelled");
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side order cancelled for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
                    }
                }
            }
        }
    };

    private Handler<OrderRejectedSbeDecoder> orderRejectedHandler = new Handler<OrderRejectedSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder sbe) {
            final long secSid = sbe.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (sbe.channelSeq() > security.channelSeq()) {
                        security.channelSeq(sbe.channelSeq());
                        LOG.info("Received {}-side order rejected for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
                        try {
                            //handled in the sendOrder future
                            //if (sbe.side().equals(Side.SELL)) {
                            //    positionTracker.updatePendingSell(security, -sbe.leavesQty());
                            //}
                            handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                            LOG.error("Error encountered when handling order rejected for {}", security.code(), e);    
                            errorHandler.onError(secSid, "Error encountered when handling order rejected");
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side order rejected for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
                    }
                }
            }
        }        
    };
    
    private Handler<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandler = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder sbe) {
            final long secSid = sbe.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {            
                    if (sbe.channelSeq() > security.channelSeq()) {
                        security.channelSeq(sbe.channelSeq());
                        LOG.info("Received {}-side order rejected for {}, sequence {}", sbe.side(), security.code(), box(security.channelSeq()));
                        try {
                            //handled in the sendOrder future
                            //if (sbe.side().equals(Side.SELL)) {
                            //    positionTracker.updatePendingSell(security, -sbe.quantity());
                            //}
                            handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                            LOG.error("Error encountered when handling order rejected for {}", security.code(), e);    
                            errorHandler.onError(secSid, "Error encountered when handling order rejected");
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side order rejected for {}, sequence {}", sbe.side(), security.code(), box(sbe.channelSeq()));
                    }
                }
            }            
        }        
    };
    
    private Handler<TradeCreatedSbeDecoder> tradeCreatedHandler = new Handler<TradeCreatedSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder trade) {
            final long secSid = trade.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (trade.channelSeq() > security.channelSeq()) {
                        security.channelSeq(trade.channelSeq());
                        LOG.info("Received {}-side trade for {}, sequence {}", trade.side(), security.code(), box(security.channelSeq()));
                        if (trade.side().equals(Side.BUY)) {
                            security.updatePosition(trade.executionQty());
                        }
                        else {
                            security.updatePendingSell(-trade.executionQty());
                            security.updatePosition(-trade.executionQty());
                            if (trade.orderSid() == security.limitOrderSid()) {
                                security.limitOrderQuantity(security.limitOrderQuantity() - trade.executionQty());
                            }
                        }
                        if (trade.status() == OrderStatus.FILLED) {
                            try {
                                handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), trade.executionPrice(), trade.executionQty(), OrderRequestRejectType.NULL_VAL);
                            }
                            catch (final Exception e) {
                                LOG.error("Error encountered when handling trade created for {}", security.code(), e);    
                                errorHandler.onError(secSid, "Error encountered when handling trade created");
                            }   
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side trade for {}, sequence {}", trade.side(), security.code(), box(trade.channelSeq()));
                    }
                }
            }            
        }        
    };
    
    private Handler<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoHandler = new Handler<TradeCreatedWithOrderInfoSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder trade) {
            final long secSid = trade.secSid();
            final StrategySecurity security = securities.get(secSid);
            if (security != null) {
                final OrderStatusReceivedHandler handler = security.orderStatusReceivedHandler();
                if (handler != null) {
                    if (trade.channelSeq() > security.channelSeq()) {
                        security.channelSeq(trade.channelSeq());
                        LOG.info("Received {}-side trade for {}, sequence {}", trade.side(), security.code(), box(security.channelSeq()));
                        if (trade.side().equals(Side.BUY)) {
                            security.updatePosition(trade.executionQty());
                        }
                        else {
                            security.updatePendingSell(-trade.executionQty());
                            security.updatePosition(-trade.executionQty());
                            if (trade.orderSid() == security.limitOrderSid()) {
                                security.limitOrderQuantity(security.limitOrderQuantity() - trade.executionQty());
                            }
                        }
                        if (trade.status() == OrderStatus.FILLED) {
                            try {
                                handler.onOrderStatusReceived(messageService.systemClock().nanoOfDay(), trade.executionPrice(), trade.executionQty(), OrderRequestRejectType.NULL_VAL);
                            }
                            catch (final Exception e) {
                                LOG.error("Error encountered when handling trade created for {}", security.code(), e);    
                                errorHandler.onError(secSid, "Error encountered when handling trade created");
                            }   
                        }
                    }
                    else {
                        LOG.info("Received and ignored stale {}-side trade for {}, sequence {}", trade.side(), security.code(), box(trade.channelSeq()));
                    }
                }
            }
        }        
    };
    
    private Handler<TradeSbeDecoder> tradeHandler = new Handler<TradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder trade) {
            final long secSid = trade.secSid();
            final StrategySecurity security = strategyManager.warrants().get(secSid);
            if (trade.channelSnapshotSeq() > security.channelSeq()) {
                security.channelSeq(trade.channelSnapshotSeq());
                LOG.info("Received {}-side trade for {}, sequence {}", trade.side(), security.code(), box(security.channelSeq()));
                if (trade.side().equals(Side.BUY)) {
                    security.updatePosition(trade.executionQty());
                }
                else {
                    security.updatePosition(-trade.executionQty());
                }
            }
            else {
                LOG.info("Received and ignored stale {}-side trade for {}, sequence {}", trade.side(), security.code(), box(trade.channelSnapshotSeq()));
            }
        }
    };
    
    private Handler<OrderSbeDecoder> orderHandler = new Handler<OrderSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder order) {
            if (order.leavesQty() > 0) {
                
            }
        }
    };
    
    private Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            byte senderSinkId = header.senderSinkId();
            LOG.info("Received request [senderSinkId:{}, clientKey:{}, requestType:{}]", box(senderSinkId), box(request.clientKey()), request.requestType().name());
            final MessageSinkRef sender = messenger.sinkRef(senderSinkId);
            try {
                handleRequest(sender, request);
            }
            catch (final Exception e) {
                LOG.error("Failed to handle request", e);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
            }            
        }        
    };
    
    private void handleRequest(final MessageSinkRef sender, final RequestSbeDecoder request) throws Exception {
        final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters = request.parameters();
        if (parameters.count() < 1) {
            throw new IllegalArgumentException("Received request with insufficient parameters");
        }
        
        boolean found = false;
        while (parameters.hasNext()){
        	parameters.next();
        	if (parameters.parameterType() != ParameterType.SERVICE_TYPE && parameters.parameterType() != ParameterType.SINK_ID){
        		break;
        	}
        }
                
        if (parameters.parameterType() != ParameterType.TEMPLATE_TYPE) {
        	throw new IllegalArgumentException("Received request with first parameter not a TEMPLATE_TYPE");
        }
        final long templateType = parameters.parameterValueLong();
        if (templateType == TemplateType.STRATEGYTYPE.value()) {
            handleStrategyTypeRequest(sender, request, parameters);
        }
        else if (templateType == TemplateType.STRATPARAMUPDATE.value()) {
            handleStratTypeParamUpdateRequest(sender, request, parameters);            
        }
        else if (templateType == TemplateType.STRATUNDPARAMUPDATE.value()) {
            handleStratUndParamUpdateRequest(sender, request, parameters);
        }
        else if (templateType == TemplateType.STRATWRTPARAMUPDATE.value()) {
            handleStratWrtParamUpdateRequest(sender, request, parameters);
        }
        else if (templateType == TemplateType.STRATISSUERPARAMUPDATE.value()) {
            handleStratIssuerParamUpdateRequest(sender, request, parameters);
        }        
        else if (templateType == TemplateType.STRAT_ISSUER_UND_PARAM_UPDATE.value()){
        	handleStratIssuerUndParamUpdateRequest(sender, request, parameters);
        }
        else if (templateType == TemplateType.StrategySwitch.value()) {
            handleStrategySwitchRequest(sender, request, parameters);
        }
        else if (templateType == TemplateType.GENERIC_TRACKER.value()) {
            handleGenericTrackerRequest(sender, request, parameters);
        }            
        else {
            throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type");
        }
    }
    
    private void handleGenericTrackerRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        if (request.requestType() == RequestType.SUBSCRIBE) {
            LOG.info("Subscribing generic tracker to sink[{}]", box(sender.sinkId()));
            this.performanceSubscribers.add(sender);
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
        else if (request.requestType() == RequestType.UNSUBSCRIBE) {
            LOG.info("Unsubscribing main switch from sink[{}]", box(sender.sinkId()));                
            performanceSubscribers.remove(sender);
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
    }

    private void handleStrategySwitchRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        if (request.requestType() == RequestType.UPDATE) {
            if (parameters.hasNext()) {
                parameters.next();
                if (parameters.parameterType() == ParameterType.STRATEGY_ID) {
                    final long strategyId = parameters.parameterValueLong();
                    parameters.next();
                    if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                        final long switchValue = parameters.parameterValueLong();
                        strategyManager.handleStrategySwitchUpdateRequest(strategyId, switchValue);
                    }
                    else {
                        throw new IllegalArgumentException("Received " + request.requestType() + " request for StrategySwitch with unsupported parameters");
                    }
                }
                else if (parameters.parameterType() == ParameterType.ISSUER_SID) {
                    final long issuerSid = parameters.parameterValueLong();
                    parameters.next();
                    if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                        final long switchValue = parameters.parameterValueLong();
                        strategyManager.handleIssuerSwitchUpdateRequest(issuerSid, switchValue);
                    }
                    else {
                        throw new IllegalArgumentException("Received " + request.requestType() + " request for StrategySwitch with unsupported parameters");
                    }
                }                
                else if (parameters.parameterType() == ParameterType.UNDERLYING_SECURITY_SID) {
                    final long underlyingSid = parameters.parameterValueLong();
                    parameters.next();
                    if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                        final long switchValue = parameters.parameterValueLong();
                        strategyManager.handleUnderlyingSwitchUpdateRequest(underlyingSid, switchValue);
                    }
                    else {
                        throw new IllegalArgumentException("Received " + request.requestType() + " request for StrategySwitch with unsupported parameters");
                    }
                }
                else if (parameters.parameterType() == ParameterType.SECURITY_SID) {
                    final long securitySid = parameters.parameterValueLong();
                    parameters.next();
                    final long switchValue;
                    if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                        switchValue = parameters.parameterValueLong();                        
                    }
                    else {
                        throw new IllegalArgumentException("Received " + request.requestType() + " request for StrategySwitch with unsupported parameters");
                    }
                    StrategySwitchType switchType = StrategySwitchType.STRATEGY_PERSIST;
                    if (parameters.hasNext()) {
                        parameters.next();
                        if (parameters.parameterType() == ParameterType.STRATEGY_SWITCH_TYPE) {
                            switchType = StrategySwitchType.get((byte)parameters.parameterValueLong());
                        }
                    }
                    if (switchType == StrategySwitchType.STRATEGY_DAY_ONLY) {
                        strategyManager.handleTempWarrantSwitchUpdateRequest(securitySid, switchValue);
                    }
                    else {
                        strategyManager.handleWarrantSwitchUpdateRequest(securitySid, switchValue);
                    }
                } 
                else if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                    final long switchValue = parameters.parameterValueLong();
                    strategyManager.handleMainSwitchUpdateRequest(switchValue);
                }
            }
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
        else if (request.requestType() == RequestType.SUBSCRIBE) {
            LOG.info("Subscribing main switch to sink[{}]", box(sender.sinkId()));
            globalSubscribers.add(sender);
            int counter = 0;
            counter = strategyManager.sendMainSwitchSnapshot(sender, request, counter++);
            this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);            
        }
        else if (request.requestType() == RequestType.UNSUBSCRIBE) {
            LOG.info("Unsubscribing main switch from sink[{}]", box(sender.sinkId()));                
            globalSubscribers.remove(sender);
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
     }

    private void handleStrategyTypeRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        if (request.requestType() == RequestType.GET) {
            int counter = 0;
            counter = strategyManager.sendStrategyTypesSnapshot(sender, request, counter);
            this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
        }
        else if (request.requestType() == RequestType.SUBSCRIBE) {
        	globalSubscribers.add(sender);
            int counter = 0;
            counter = strategyManager.sendStrategyTypesSnapshot(sender, request, counter);
            // This method should return other parameters as well
            counter = strategyManager.sendAllIssuerUndParamsSnapshot(sender, request, counter);                            
            this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
        }
        else if (request.requestType() == RequestType.UNSUBSCRIBE) {
            LOG.info("Unsubscribing strategy type from sink[{}]", box(sender.sinkId()));
            globalSubscribers.remove(sender);
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
        else {
            throw new IllegalArgumentException("No support for " + request.requestType() + " request for STRATEGYTYPE");
        }        
    }

    private void handleStratTypeParamUpdateRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        parameters.next();
        if (parameters.parameterType() == ParameterType.STRATEGY_ID) {
            final long strategyId = parameters.parameterValueLong();
            if (request.requestType() == RequestType.SUBSCRIBE) {
                LOG.info("Subscribing strategy parameters to sink[{}]", box(sender.sinkId()));
                globalSubscribers.add(sender);
                int counter = 0;
                counter = strategyManager.sendStrategyTypeParamsSnapshot(sender, request, counter);
                this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                LOG.info("Unsubscribing strategy types from sink[{}]", box(sender.sinkId()));
                globalSubscribers.remove(sender);
                this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UPDATE) {
                triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                strategyManager.handleStrategyTypeParamUpdateRequest(strategyId, parameters);
                this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else {
                throw new IllegalArgumentException("No support for " + request.requestType() + " request for STRATPARAMUPDATE");
            }            
        }
        else {
            throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATPARAMUPDATE with unsupported parameters");
        }
    }

    private void handleStratUndParamUpdateRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        parameters.next();
        if (parameters.parameterType() == ParameterType.UNDERLYING_SECURITY_SID) {
            final long secSid = parameters.parameterValueLong();
            if (request.requestType() == RequestType.SUBSCRIBE) {
                LOG.info("Subscribing strategy underlying parameters for {} to sink[{}]", box(secSid), box(sender.sinkId()));
                globalSubscribers.add(sender);
                int counter = 0;
                counter = strategyManager.sendUndParamsSnapshot(secSid, sender, request, counter);
                this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UPDATE) {
                parameters.next();
                if (parameters.parameterType() == ParameterType.STRATEGY_ID) {
                    final long strategyId = parameters.parameterValueLong();
                    triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                    strategyManager.handleUnderlyingParamUpdateRequest(strategyId, secSid, parameters);
                    this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                }
                else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATUNDPARAMUPDATE with unsupported parameters");
                }
            }
            else {
                throw new IllegalArgumentException("No support for " + request.requestType() + " request for STRATUNDPARAMUPDATE");
            }            
        }
        else {
            throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATUNDPARAMUPDATE with unsupported parameters");
        }
    }
    
    private void handleStratIssuerUndParamUpdateRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
//        if (request.requestType() == RequestType.SUBSCRIBE) {
//            LOG.info("Subscribing strategy issuer underlying parameters to sink[{}]", box(sender.sinkId()));
//            globalSubscribers.add(sender);
//            
//            int counter = 0;
//            counter = strategyManager.sendAllIssuerUndParamsSnapshot(sender, request, counter);
//            this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
//            return;
//        }
//    	
    	parameters.next();
        if (parameters.parameterType() == ParameterType.ISSUER_SID) {
        	final long issuerSid = parameters.parameterValueLong();
            parameters.next();
            if (parameters.parameterType() == ParameterType.UNDERLYING_SECURITY_SID) {
            	final long secSid = parameters.parameterValueLong();
                if (request.requestType() == RequestType.UPDATE) {
                    parameters.next();
                    if (parameters.parameterType() == ParameterType.STRATEGY_ID) {
                        final long strategyId = parameters.parameterValueLong();
                        triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                        LOG.info("Update strategy issuer und [issuerSid:{}, undSid:{}]", issuerSid, secSid);
                        strategyManager.handleIssuerUndParamUpdateRequest(strategyId, issuerSid, secSid, parameters);
                        this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                    }
                    else {
                        throw new IllegalArgumentException("Received " + request.requestType() + " request for strat issuer und with unsupported parameters");
                    }
                }
                else {
                    throw new IllegalArgumentException("No support for " + request.requestType() + " request for strat issuer und");
                }
            }
            else {
                throw new IllegalArgumentException("Received " + request.requestType() + " request for strat issuer und with unsupported parameters");
            }            
        }
        else {
            throw new IllegalArgumentException("Received " + request.requestType() + " request for strat issuer und with unsupported parameters");
        }
    }

    private void handleStratIssuerParamUpdateRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        parameters.next();
        if (parameters.parameterType() == ParameterType.ISSUER_SID) {
            final long issuerSid = parameters.parameterValueLong();
            if (request.requestType() == RequestType.SUBSCRIBE) {
                LOG.info("Subscribing strategy issuer parameters for {} to sink[{}]", box(issuerSid), box(sender.sinkId()));
                globalSubscribers.add(sender);
                int counter = 0;
                counter = strategyManager.sendIssuerParamsSnapshot(issuerSid, sender, request, counter++);
                this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UPDATE) {                
                parameters.next();
                if (parameters.parameterType() == ParameterType.STRATEGY_ID) {
                    final long strategyId = parameters.parameterValueLong();
                    triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                    strategyManager.handleIssuerParamUpdateRequest(strategyId, issuerSid, parameters);
                    this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
                }
                else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATISSUERPARAMUPDATE with unsupported parameters");
                }
                this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else {
                throw new IllegalArgumentException("No support for " + request.requestType() + " request for STRATISSUERPARAMUPDATE");
            }            
        }
        else {
            throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATWRTPARAMUPDATE with unsupported parameters");
        }
    }
    
    private final LongHashSet receivedWrtSecSids = new LongHashSet(-1);
    private final LongHashSet receivedUndSecSids = new LongHashSet(-1);
    private final IntHashSet receivedIssuerSids = new IntHashSet(-1);
    private void handleStratWrtParamUpdateRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters) throws Exception {
        if (request.requestType() == RequestType.UPDATE) {
            receivedWrtSecSids.clear();
            receivedUndSecSids.clear();
            receivedIssuerSids.clear();
            long strategyId = -1;
            while (parameters.hasNext()){
                parameters.next();
                if (parameters.parameterType() == ParameterType.SECURITY_SID) {
                    receivedWrtSecSids.add(parameters.parameterValueLong());
                }
                else if (parameters.parameterType() == ParameterType.UNDERLYING_SECURITY_SID) {
                    receivedUndSecSids.add(parameters.parameterValueLong()); 
                }
                else if (parameters.parameterType() == ParameterType.ISSUER_SID){
                    receivedIssuerSids.add((int)parameters.parameterValueLong());
                }
                else if (parameters.parameterType() == ParameterType.STRATEGY_ID) {
                    strategyId = parameters.parameterValueLong();
                    break;
                }
                else{
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATWRTPARAMUPDATE with unsupported parameters");
                }
            }
            triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
            strategyManager.handleWrtParamUpdateRequest(strategyId, receivedUndSecSids, receivedIssuerSids, receivedWrtSecSids, parameters);
        }
        else {
            parameters.next();
            if (parameters.parameterType() == ParameterType.SECURITY_SID) {
                final long secSid = parameters.parameterValueLong();
                if (request.requestType() == RequestType.SUBSCRIBE) {
                    LOG.info("Subscribing strategy warrant parameters for {} to sink[{}]", box(secSid), box(sender.sinkId()));
                    globalSubscribers.add(sender);
                    int counter = 0;
                    counter = strategyManager.sendWrtParamsSnapshot(secSid, sender, request, counter);
                    this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
                }
            }
            else if (parameters.parameterType() == ParameterType.UNDERLYING_SECURITY_SID) {
                final long undSid = parameters.parameterValueLong();
                if (request.requestType() == RequestType.SUBSCRIBE) {
                    LOG.info("Subscribing strategy parameters to sink[{}]", box(sender.sinkId()));
                    globalSubscribers.add(sender);
                    int counter = 0;
                    final ArrayList<StrategySecurity> warrantsForUnderlying = strategyManager.warrantsForUnderlying(undSid);
                    if (warrantsForUnderlying != null) {
                        for (final StrategySecurity warrant : warrantsForUnderlying) {
                            counter = strategyManager.sendWrtParamsSnapshot(warrant.sid(), sender, request, counter);
                        }
                    }
                    this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);
                }
            }
            else {
                throw new IllegalArgumentException("Received " + request.requestType() + " request for STRATWRTPARAMUPDATE with unsupported parameters");
            }
        }
    }
    
    private Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
            byte senderSinkId = header.senderSinkId();
            try {
                handleCommand(senderSinkId, codec);
            }
            catch (final Exception e) {
                LOG.error("Failed to handle command", e);
                messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            }            
        }
    };

    private void handleCommand(final int senderSinkId, final CommandSbeDecoder codec) throws Exception {
        switch (codec.commandType()){
        case CAPTURE_PROFIT: {
            LOG.info("Received CAPTURE_PROFIT command from {}", box(senderSinkId));
        	final ImmutableListMultimap<ParameterType, Parameter> mm = CommandDecoder.generateParameterMap(messenger.stringBuffer(), codec);
            final List<Parameter> securitySidList = mm.get(ParameterType.SECURITY_SID);
        	triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
            for (final Parameter param : securitySidList) {
                final long securitySid = param.valueLong();
                final StrategySecurity security = securities.get(securitySid);
                if (security != null) {
                    final Strategy strategy = security.activeStrategy();
                    if (strategy != null) {
                    	strategy.captureProfit();
                    }
                }                
            }
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.OK, codec.commandType());
            break;
        }
        case PLACE_SELL_ORDER: {
            LOG.info("Received PLACE_SELL_ORDER command from {}", box(senderSinkId));
            final ImmutableListMultimap<ParameterType, Parameter> mm = CommandDecoder.generateParameterMap(messenger.stringBuffer(), codec);
            final List<Parameter> securitySidList = mm.get(ParameterType.SECURITY_SID);
            triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
            for (final Parameter param : securitySidList) {
                final long securitySid = param.valueLong();
                final StrategySecurity security = securities.get(securitySid);
                if (security != null) {
                    final Strategy strategy = security.activeStrategy();
                    if (strategy != null) {
                        strategy.placeSellOrder();
                    }   
                }                
            }
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.OK, codec.commandType());
            break;
        }        
        case START_TEST_MODE: {
            LOG.info("Received START_TEST_MODE command from {}", box(senderSinkId));
            orderService.setBuyOrderType(OrderType.LIMIT_THEN_CANCEL_ORDER);
            //messenger.strategySender().sendStrategySwitch(messenger.referenceManager().persi(), globalSubscribers.elements(), StrategyParamSource.NULL_VAL, 1L, BooleanType.TRUE);
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.OK, codec.commandType());
            break;
        }
        case STOP_TEST_MODE: {
            LOG.info("Received STOP_TEST_MODE command from {}", box(senderSinkId));
            orderService.setBuyOrderType(OrderType.ENHANCED_LIMIT_ORDER);
            //messenger.strategySender().sendStrategySwitch(messenger.referenceManager().persi(), globalSubscribers.elements(), StrategyParamSource.NULL_VAL, 1L, BooleanType.FALSE);
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.OK, codec.commandType());
            break;
        }
        case SEND_STRAT_PARAMS: {
            strategyInfoSender.broadcastAllThrottled();
            break;
        }
        default:
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            break;
        }
    }

    final ResponseHandler mdSubResponseHandler = new ResponseHandler() {
        @Override
        public void handleResponse(final DirectBuffer buffer, final int offset, final ResponseSbeDecoder response) {
            LOG.info("Received market data subscription respones: {}", response.resultType());
            if (response.resultType() != ResultType.OK){
                LOG.error("Cannot subscribe market data due to {}", response.resultData());
                messageService.stateEvent(StateTransitionEvent.FAIL);
            }
        }

        @Override
        public void handleResponse(final Response response) {
            LOG.info("Received market data subscription respones: {}", response.resultType());
            if (response.resultType() != ResultType.OK){
                LOG.error("Cannot subscribe market data due to some reasons");
                messageService.stateEvent(StateTransitionEvent.FAIL);
            }
        }
    };
    
    final ResponseHandler greeksSubResponseHandler = new ResponseHandler() {
        @Override
        public void handleResponse(final DirectBuffer buffer, final int offset, final ResponseSbeDecoder response) {
            LOG.info("Received greeks subscription respones: {}", response.resultType());
            if (response.resultType() != ResultType.OK){
                LOG.error("Cannot subscribe greeks due to {}", response.resultData());
                messageService.stateEvent(StateTransitionEvent.FAIL);
            }
        }

        @Override
        public void handleResponse(final Response response) {
            LOG.info("Received greeks subscription respones: {}", response.resultType());
            if (response.resultType() != ResultType.OK){
                LOG.error("Cannot subscribe greeks due to some reasons");
                messageService.stateEvent(StateTransitionEvent.FAIL);
            }
        }
    };    
    
    final SwitchHandler securitySwitchHandler = new SwitchHandler() {
        @Override
        public void onPerformTask(final long securitySid) {
            final StrategySecurity security = securities.get(securitySid);
            if (security != null) {
                final Strategy strategy = security.activeStrategy();
                if (strategy != null) {
                    if (strategy.isOn()) {
                        try {
                            LOG.info("Received all trades for {} ...",  strategy.getSecurity().code());
                            triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                            strategyManager.switchOnStrategy(strategy);
                        }
                        catch (final Exception e) {
                            LOG.error("Failed to switch on strategy for {}...", strategy.getSecurity().code(), e);
                            errorHandler.onError(securitySid, "Failed to switch on strategy");
                        }
                    }
                    else {
                        LOG.info("Requesting previous trades for {} from {}...",  strategy.getSecurity().code(),  strategy.getSecurity().channelSeq());
                        strategyManager.pendingSwitchOnStrategy(strategy);
                        CompletableFuture<Request> retrieveStrategyTypeFuture = messenger.sendRequest(messenger.referenceManager().otss(),
                                RequestType.GET,
                                new ImmutableList.Builder<Parameter>()
                                .add(Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()))
                                .add(Parameter.of(ParameterType.SECURITY_SID, securitySid))
                                .add(Parameter.of(ParameterType.CHANNEL_SEQ, strategy.getSecurity().channelSeq())).build(),
                                ResponseHandler.NULL_HANDLER);
                        retrieveStrategyTypeFuture.thenAccept((r) -> {
                            try {
                                LOG.info("Received all trades for {} ...", strategy.getSecurity().code());
                                triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                                strategyManager.proceedSwitchOnStrategy(strategy);
                            }
                            catch (final Exception e) {                                
                                LOG.error("Failed to switch on strategy for {}...", strategy.getSecurity().code(), e);
                                strategyManager.cancelSwitchOnStrategy(strategy);
                                errorHandler.onError(securitySid, "Failed to switch on strategy");
                            }
                        }).exceptionally(new Function<Throwable, Void>() {
                            @Override
                            public Void apply(Throwable t) {                                
                                LOG.error("Failed to switch on strategy for {}...", strategy.getSecurity().code(), t);
                                strategyManager.cancelSwitchOnStrategy(strategy);
                                errorHandler.onError(securitySid, "Failed to switch on strategy: " + t.getMessage());
                                return null;
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void onStopTask(final long securitySid) {
            final StrategySecurity security = securities.get(securitySid);
            if (security != null) {
                final Strategy strategy = security.activeStrategy();
                if (strategy != null) {
                    try {
                        triggerInfo.triggeredBy((byte)0).triggerSeqNum(0).triggerNanoOfDay(0);
                        strategyManager.switchOffStrategy(strategy);
                    }
                    catch (final Exception e) {
                        LOG.error("Failed to switch off strategy for {}...", strategy.getSecurity().code());
                    }
                }
            }
        }            
    };    
    




    public final StrategyManager strategyManager() { return strategyManager; }
    public final LongEntityManager<StrategySecurity> securities() { return securities; }
    public final LongEntityManager<StrategyIssuer> issuers() { return this.issuers; }
    public final MarketStatusType marketStatus() { return this.marketStatus; }


    final MarketDataClient.OrderBookFactory orderBookFactory = new MarketDataClient.OrderBookFactory() {
        @Override
        public MarketOrderBook createOrderBook(final Security security) {
            return ((StrategySecurity)security).orderBook();
        }            
    };
    
}
