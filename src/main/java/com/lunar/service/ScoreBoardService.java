package com.lunar.service;

import static org.apache.logging.log4j.util.Unbox.box;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.config.ScoreBoardServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.MessageSinkRefList;
//import com.lunar.core.MessageSinkRefList;
import com.lunar.core.SubscriberList;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.MarketDataClient;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketStats;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.binary.ScoreBoardDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
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
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.GreeksUpdateHandler;
import com.lunar.strategy.LunarStrategyOrderService;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.StrategyErrorHandler;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyManager;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.cbbctest.CbbcTest;
import com.lunar.strategy.scoreboard.LunarScoreBoardStrategyInfoSender;
import com.lunar.strategy.scoreboard.ScoreBoard;
import com.lunar.strategy.scoreboard.ScoreBoardManager;
import com.lunar.strategy.scoreboard.ScoreBoardOrderService;
import com.lunar.strategy.scoreboard.ScoreBoardSchema;
import com.lunar.strategy.scoreboard.ScoreBoardSecurityInfo;
import com.lunar.strategy.scoreboard.ScoreBoardStrategyStaticScheduleSetupTask;
import com.lunar.strategy.scoreboard.ScoreBoardUpdateHandler;
import com.lunar.strategy.scoreboard.StrategySwitchController.SwitchControlHandler;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridFactory;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * A general market data snapshot service that serves all snapshot needs (i.e. different exchanges)
 * It receives real-time updates from {@link MarketDataRealtimeService} and get order book 
 * snapshots from {@link MarketDataRefreshService} 
 * @author wongca
 *
 */
public class ScoreBoardService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(ScoreBoardService.class);
	
    private static final int DEFAULT_ISSUER_SIZE = 100;
    private static final int CLIENT_KEY_FOR_CLOCK_TICK = 1;
    private static final int EXPECTED_TRADE_CHANNELS = 2;
    
	private final LunarService messageService;
	private final Messenger messenger;
    private final ScoreBoardDecoder scoreBoardDecoder;
	private final String name;
    private final StrategyManager strategyManager;
    private final ScoreBoardManager scoreBoardManager;
    private final LongEntityManager<ScoreBoardSecurityInfo> securities;
    private final LongEntityManager<StrategyIssuer> issuers;

    private final ScoreBoardOrderService orderService;
    private final MarketDataClient marketDataClient;
    private final MarketTicksStrategyScheduler scheduler;
    private final StrategyInfoSender strategyInfoSender;

    private final ScoreBoardSchema scoreBoardSchema;
    private MarketStatusType marketStatus;
    
    private final SubscriberList globalSubscribers;
    
    private final AtomicReference<TimeoutHandlerTimerTask> timeAvgClockTickTask;
    public static Duration timeAvgClockTickFreq = Duration.ofSeconds(5L);
    
	// Processed trades
	private final Int2LongOpenHashMap processedTradeSeqByChannel;

    public static ScoreBoardService of(final ServiceConfig config, final LunarService messageService){
        return new ScoreBoardService(config, messageService);
    }
    
	public ScoreBoardService(final ServiceConfig config, final LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.scoreBoardDecoder = ScoreBoardDecoder.of();
		this.scoreBoardSchema = ScoreBoardSchema.of();
		this.marketStatus = MarketStatusType.NULL_VAL;
		this.globalSubscribers = SubscriberList.of(ServiceConstant.MAX_SUBSCRIBERS);
		this.processedTradeSeqByChannel = new Int2LongOpenHashMap(EXPECTED_TRADE_CHANNELS);
		this.timeAvgClockTickTask = new AtomicReference<>();
		if (config instanceof ScoreBoardServiceConfig) {		    
		    final ScoreBoardServiceConfig specificConfig = (ScoreBoardServiceConfig)config;
		    final int numSecurities = specificConfig.numUnderlyings() + specificConfig.numWarrants();
	        securities = new LongEntityManager<ScoreBoardSecurityInfo>(numSecurities);            
            issuers = new LongEntityManager<StrategyIssuer>(DEFAULT_ISSUER_SIZE);
            orderService = new ScoreBoardOrderService(messageService.systemClock());
            scheduler = new MarketTicksStrategyScheduler();
            strategyInfoSender = new LunarScoreBoardStrategyInfoSender(securities, messenger, globalSubscribers);
            strategyManager = new StrategyManager(specificConfig, securities, issuers, orderService, strategyInfoSender, scheduler);
            scoreBoardManager = new ScoreBoardManager(securities, issuers, strategyManager, strategyInfoSender, scoreBoardSwitchHandler); 
            new ScoreBoardStrategyStaticScheduleSetupTask(scheduler, strategyManager, this.messenger, specificConfig.canAutoSwitchOn());
            marketDataClient = new MarketDataClient(messenger, securities, numSecurities);
            registerStrategies();
		}
		else {
		    throw new IllegalArgumentException("Service " + this.name + " expects a MarketDataSnapshotServiceConfig config");
		}
	}
	
    private void registerStrategies() {
        strategyManager.registerStrategyFactory(new SpeedArbHybridFactory());
    }

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PortfolioAndRiskService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderAndTradeSnapshotService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.StrategyService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus((final boolean status) -> {
			if (status){
				messageService.stateEvent(StateTransitionEvent.READY);
			}
			else { // DOWN or INITIALIZING
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}
		});
		return StateTransitionEvent.WAIT;
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
        messenger.receiver().marketStatusHandlerList().add(marketStatusHandler);        
        messenger.receiver().orderBookSnapshotHandlerList().add(orderBookSnapshotHandler);      
        messenger.receiver().marketDataTradeHandlerList().add(marketDataTradeHandler);
        messenger.receiver().marketStatsHandlerList().add(marketStatsHandler);
        messenger.receiver().greeksHandlerList().add(greeksHandler);
        messenger.receiver().scoreBoardHandlerList().add(scoreBoardHandler);
        messenger.receiver().positionHandlerList().add(positionHandler);
        messenger.receiver().tradeHandlerList().add(tradeHandler);
        
        messenger.receiver().timerEventHandlerList().add(timerEventHandler);
		timeAvgClockTickTask.set(messenger.timerService().createTimerTask(timeAvgClockTickHandler, "time-average-clock-tick"));
		messenger.timerService().newTimeout(timeAvgClockTickTask.get(), timeAvgClockTickFreq.toMillis(), TimeUnit.MILLISECONDS);

		getIssuers();
        return StateTransitionEvent.NULL;	    
	}

    private final Handler<TradeSbeDecoder> tradeHandler = new Handler<TradeSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder trade) {
	    	LOG.debug("Received trade [tradeSid:{}, orderSid:{}, secSid:{}, channelId:{}, channelSeq:{}]", trade.tradeSid(), trade.orderSid(), trade.secSid(), trade.channelId(), trade.channelSnapshotSeq());
	    	// Filter out trade that we have already processed
	    	long processedTradeSeq = processedTradeSeqByChannel.get(trade.channelId());
	    	if (trade.channelSnapshotSeq() > processedTradeSeq){
	    		processedTradeSeqByChannel.put(trade.channelId(), trade.channelSnapshotSeq());
	    		
	    		// Process trade
	    		ScoreBoardSecurityInfo security = securities.get(trade.secSid());
	    		security.onOwnSecTrade(trade);	    		
	    	}
	    	else {
	    		LOG.warn("Ignored trade [tradeSid:{}, secSid:{}, channelId:{}, channelSeq:{}]", trade.tradeSid(), trade.secSid(), trade.channelId(), trade.channelSnapshotSeq());
	    	}
	    }
	};

	private void subscribeTradeSnapshot(){
		// Get outstanding orders and trades
		CompletableFuture<Request> request = messenger.sendRequest(messenger.referenceManager().otss(),
				RequestType.GET_AND_SUBSCRIBE, 
				Parameters.listOf(Parameter.of(DataType.TRADE_UPDATE)));
		request.whenComplete(new BiConsumer<Request, Throwable>(){

			@Override
			public void accept(Request request, Throwable throwable) {
				if (throwable != null){
					LOG.error("Caught throwable for request to get and subscribe order and trade snapshot", throwable);
					// TODO Make this a TimerTask
					subscribeTradeSnapshot();
					return;
				}
				if (!request.resultType().equals(ResultType.OK)){
					LOG.error("Received failure result for request to get and subscriber order and trade snapshot [{}]", request.toString());
					// TODO Make this a TimerTask
					subscribeTradeSnapshot();
					return;
				}
			}
		});		
	}

    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
    	@Override
    	public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
    		if (codec.timerEventType() == TimerEventType.TIMER){
    			if (codec.clientKey() == CLIENT_KEY_FOR_CLOCK_TICK){
    				// Tick all market making change trackers
    				for (final ScoreBoardSecurityInfo security : securities.entities()) {
    					if (security.marketMakingChangeTracker() == null){
    						continue;
    					}
    					security.marketMakingChangeTracker().observeNanoOfDay(messenger.timerService().toNanoOfDay());
    				}
    				messenger.newTimeout(timeAvgClockTickTask.get(), timeAvgClockTickFreq);
    			}
    		}
    	}
    };
    
    private final TimeoutHandler timeAvgClockTickHandler = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			// Retry in a moment
			messenger.newTimeout(timeAvgClockTickTask.get(), timeAvgClockTickFreq);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_CLOCK_TICK, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(timeAvgClockTickTask.get(), timeAvgClockTickFreq);
			}
		}
	};

	@Override
	public StateTransitionEvent activeEnter() {
        messenger.receiver().requestHandlerList().add(requestHandler);
        messenger.receiver().commandHandlerList().add(commandHandler);

//		publishSnapshotTask.set(messenger.timerService().createTimerTask(publishSnapshots, "risk-publish-snapshots"));
//		publishSnapshotTaskTimeout = messenger.timerService().newTimeout(publishSnapshotTask.get(), publishFrequency.toMillis(), TimeUnit.MILLISECONDS);
//
        return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
        messenger.receiver().issuerHandlerList().remove(issuerHandler);
        messenger.receiver().securityHandlerList().remove(securityHandler);
        messenger.receiver().strategyTypeHandlerList().remove(strategyTypeHandler);
        messenger.receiver().strategyParamsHandlerList().remove(strategyTypeParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().remove(strategyUnderlyingParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().remove(strategyWarrantParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().remove(strategyIssuerParamsHandler);
        messenger.receiver().strategyIssuerUndParamsHandlerList().remove(strategyIssuerUndParamsHandler);
        messenger.receiver().strategySwitchHandlerList().remove(strategySwitchHandler);
        messenger.receiver().marketStatusHandlerList().remove(marketStatusHandler);
		messenger.receiver().orderBookSnapshotHandlerList().remove(orderBookSnapshotHandler);
		messenger.receiver().marketDataTradeHandlerList().remove(marketDataTradeHandler);
		messenger.receiver().marketStatsHandlerList().remove(marketStatsHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
        messenger.receiver().commandHandlerList().remove(commandHandler);
        messenger.receiver().greeksHandlerList().remove(greeksHandler);
        messenger.receiver().scoreBoardHandlerList().remove(scoreBoardHandler);
        messenger.receiver().positionHandlerList().remove(positionHandler);
//		if (publishSnapshotTaskTimeout.cancel()){
//			LOG.info("Cancelled publishing task successfully");
//		}
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
            getScoreBoards();
        });        
    }
    
    private void getScoreBoards() {
        LOG.info("Fetching scoreboards...");
        try {
            CompletableFuture<Request> retrieveScoreBoardsFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                    RequestType.GET,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SCOREBOARD.value())).build(),
                    ResponseHandler.NULL_HANDLER);
            retrieveScoreBoardsFuture.thenAccept((r) -> {
                LOG.info("Scoreboards fully retrieved!");                
                subscribeMarketStatus();  
            });
        } catch (final Exception e) {
            LOG.error("Caught exception", e);
            messageService.stateEvent(StateTransitionEvent.FAIL);
        }
    }
    
    private void subscribeMarketStatus() {
        LOG.info("Subscribing to market status...");
        CompletableFuture<Request> retrieveExchangeFuture = messenger.sendRequest(messenger.referenceManager().mds(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.MARKETSTATUS.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveExchangeFuture.thenAccept((r) -> {
            LOG.info("Strategy status fully subscribed!");            
            subscribePosition();
            //messageService.stateEvent(StateTransitionEvent.ACTIVATE);
        });
    }
    
    private void subscribePosition() {
        LOG.info("Subscribing to position...");
        CompletableFuture<Request> retrievePosition = messenger.sendRequest(messenger.referenceManager().risk(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(DataType.ALL_POSITION_UPDATE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrievePosition.thenAccept((r) -> {
            LOG.info("Positions fully retrieved!");
            getStrategyTypes();
        });
    }
    
    private void getStrategyTypes() {
        LOG.info("Fetching strategy types...");
        strategyManager.onEntitiesLoaded();
        scoreBoardManager.onEntitiesLoaded();
        try {
            CompletableFuture<Request> retrieveStrategyTypeFuture = messenger.sendRequest(messenger.referenceManager().persi(),
                    RequestType.GET,
                    new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATEGYTYPE.value())).build(),
                    ResponseHandler.NULL_HANDLER);
            retrieveStrategyTypeFuture.thenAccept((r) -> {
                LOG.info("Strategy types fully retrieved!");
                switchOnStrategies();
                strategyManager.setupStrategies();
                scoreBoardManager.setupScoreBoards();
                setupScoreBoardSecurities();
            });
        } catch (final Exception e) {
            LOG.error("Caught exception", e);
            messageService.stateEvent(StateTransitionEvent.FAIL);
        }
    }
    
    private void setupScoreBoardSecurities() {
        LOG.info("Setting up scoreboard securities...");
        try {
            for (final ScoreBoardSecurityInfo warrant : scoreBoardManager.warrants().values()) {
                //TODO not necessary until we enable subscription for sb updates
                //warrant.registerScoreBoardUpdateHandler(scoreBoardUpdateHandler);
                
                final MessageSinkRefList strats = messenger.referenceManager().strats();
                if (strats.size() > 0){
                    AtomicInteger expectedAcks = new AtomicInteger(strats.size());
                    for (int i = 0; i < strats.size(); i++){
                        CompletableFuture<Request> stratRequestFuture = messenger.sendRequest(strats.elements()[i],
                                RequestType.SUBSCRIBE,
                                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATWRTPARAMUPDATE.value()), Parameter.of(ParameterType.SECURITY_SID, warrant.sid())).build(),
                                ResponseHandler.NULL_HANDLER);
                        stratRequestFuture.thenAccept((r4) -> {
                            if (expectedAcks.decrementAndGet() == 0){
                                LOG.info("Received subscription ack from all strategies");
                            }
                            else {
                                LOG.info("Received subscription ack from strategy");
                            }
                        }).exceptionally(new Function<Throwable, Void>() {
                            @Override
                            public Void apply(Throwable t) {
                                LOG.error("Failed to subscribe to strat!");
                                return null;
                            }
                        });
                    }
                }
            }
            subscribeTradeSnapshot();
            subscribeMarketData();
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
        initializeCbbcTest();
        messageService.stateEvent(StateTransitionEvent.ACTIVATE);
    }
    
    private void switchOnStrategies() {
        try {
            for (final StrategySecurity underlying : strategyManager.underlyings().values()) {
                strategyManager.flipSwitchForUnderlying(underlying.sid(), BooleanType.TRUE, StrategyExitMode.NULL_VAL, false);
            }
            for (final StrategyIssuer issuer : strategyManager.issuers().entities()) {
                strategyManager.flipSwitchForIssuer(issuer.sid(), BooleanType.TRUE, StrategyExitMode.NULL_VAL, false);
            }
            for (final StrategySecurity warrant : strategyManager.warrants().values()) {
                strategyManager.flipSwitchForSecurity(warrant.sid(), BooleanType.TRUE, StrategyExitMode.NULL_VAL, false);
            }
        }
        catch (final Exception e) {
            LOG.error("Caught exception", e);
            messageService.stateEvent(StateTransitionEvent.FAIL);  
        }
    }
    
    final MarketDataClient.OrderBookFactory orderBookFactory = new MarketDataClient.OrderBookFactory() {
        @Override
        public MarketOrderBook createOrderBook(final Security security) {
            return ((ScoreBoardSecurityInfo)security).orderBook();
        }
    };
    
    final ScoreBoardUpdateHandler scoreBoardUpdateHandler = new ScoreBoardUpdateHandler() {
        @Override
        public void onScoreBoardUpdated(long nanoOfDay, ScoreBoard scoreBoard) {
            if (globalSubscribers.size() > 0) {
                messenger.scoreBoardSender().sendScoreBoard(globalSubscribers.elements(), scoreBoard);
            }
        }
    };

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
    
    final byte[] secCodeBytes = new byte[SecuritySbeDecoder.codeLength()];
	private Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
            LOG.info("Received target security {}", security.sid());
            security.getCode(secCodeBytes, 0);
            try {                
                final SecurityType securityType = security.securityType();
                final boolean isAlgo = security.isAlgo() == BooleanType.TRUE;
                if (security.isAlgo() == BooleanType.FALSE && !securityType.equals(SecurityType.FUTURES) && !securityType.equals(SecurityType.INDEX) && !securityType.equals(SecurityType.CBBC))
                    return;
                final ScoreBoardSecurityInfo newSecurity = new ScoreBoardSecurityInfo(security.sid(), 
                        securityType, 
                        new String(secCodeBytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
                        security.exchangeSid(),
                        security.undSid(),
                        security.putOrCall(),
                        security.style(),
                        security.strikePrice(),
                        security.conversionRatio(),
                        security.issuerSid(),
                        security.lotSize(),                            
                        isAlgo,
                        SpreadTableBuilder.get(securityType));
                newSecurity.mdsSink(messenger.referenceManager().mds());
                newSecurity.orderBook().triggerInfo().triggeredBy((byte)messenger.self().sinkId());
                securities.add(newSecurity);
            } 
            catch (final UnsupportedEncodingException e) {
                LOG.error("Failed to decode SecuritySbe", e);
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
                String issuerCode = new String(issuerCodeBytes, IssuerSbeDecoder.codeCharacterEncoding()).trim();
                final StrategyIssuer newIssuer = StrategyIssuer.of(issuer.sid(),
                        issuerCode,
                        new String(issuerNameBytes, IssuerSbeDecoder.nameCharacterEncoding()).trim(),
                        0);
                issuers.add(newIssuer);
            } 
            catch (final UnsupportedEncodingException e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }
        }
    };	
    
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
                if (header.senderSinkId() != messenger.referenceManager().persi().sinkId())
                    return;
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
                if (header.senderSinkId() != messenger.referenceManager().persi().sinkId())
                    return;
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
                if (header.senderSinkId() != messenger.referenceManager().persi().sinkId())
                    return;
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
                if (header.senderSinkId() != messenger.referenceManager().persi().sinkId())
                    return;
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
                if (header.senderSinkId() != messenger.referenceManager().persi().sinkId())
                    return;
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
                scoreBoardManager.handle(buffer, offset, header, strategySwitch);
            }
            catch (final Exception e) {
                LOG.error("Caught exception", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            } 
        }        
    };    

    private Handler<MarketStatusSbeDecoder> marketStatusHandler = new Handler<MarketStatusSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatusSbeDecoder codec) {
            final MarketStatusType updatedStatus = codec.status();
            if (!marketStatus.equals(updatedStatus)) {
                //Persist when market status changes away from CT
                if (marketStatus.equals(MarketStatusType.CT)) {
                    LOG.info("Persist scoreboards...");
                    persistScoreBoards();
                }
                marketStatus = codec.status();
                LOG.info("Update market status to {}...",  marketStatus);
                orderService.isInMarketHour(marketStatus == MarketStatusType.CT);
                LOG.info("Update market status to {}...",  marketStatus);
            }
        }        
    };
                            
    private void persistScoreBoards() {
        for (final StrategySecurity security : strategyManager.warrants().values()) {
            messenger.scoreBoardSender().trySendScoreBoard(messenger.referenceManager().persi(), ((ScoreBoardSecurityInfo)security).scoreBoard());
        }
    }
        
	private Handler<MarketStatsSbeDecoder> marketStatsHandler = new Handler<MarketStatsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder codec) {
            final long secSid = codec.secSid();
            final ScoreBoardSecurityInfo security = securities.get(secSid);
            if (security != null) {
                final MarketStats marketStats = security.marketStats();
                marketStats.secSid(codec.secSid());
                marketStats.open(codec.open());
                marketStats.close(codec.close());
                marketStats.high(codec.high());
                marketStats.low(codec.low());
                marketStats.volume(codec.volume());
                marketStats.isRecovery(codec.isRecovery() == BooleanType.TRUE);
                marketStats.transactTime(codec.transactTime());
                marketStats.triggerInfo().decode(codec.triggerInfo());
            }
        }
	};

	private Handler<MarketDataTradeSbeDecoder> marketDataTradeHandler = new Handler<MarketDataTradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder codec) {
            final long secSid = codec.secSid();
            final ScoreBoardSecurityInfo security = securities.get(secSid);
            final MarketDataUpdateHandler updateHandler = security.marketDataUpdateHandler();
            if (updateHandler != null) {
                security.lastMarketTrade().decodeFrom(codec);
                security.lastMarketTrade().detectSide(security.orderBook());
                orderService.isRecovery(security.lastMarketTrade().isRecovery());
                try {
                    updateHandler.onTradeReceived(security, security.lastMarketTrade().tradeNanoOfDay(), security.lastMarketTrade());
                } catch (final Exception e) {
                    LOG.error("Error encountered when handling trade for {}", security.code(), e);
                }
            }
        }
	};
   
	private Handler<OrderBookSnapshotSbeDecoder> orderBookSnapshotHandler = new Handler<OrderBookSnapshotSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder codec) {
            try {
            	// Market data client processes the message
                marketDataClient.handleMarketData(codec, buffer, offset, obUpdateHandler);
            } catch (final Exception e) {
                final long secSid = codec.secSid();
                final Security security = securities.get(secSid);
                if (security != null) {
                    LOG.error("Error encountered when handling market data for {}", security.code(), e);    
                }
            }
        }
	};
	
    private final MarketDataClient.OrderBookUpdateHandler obUpdateHandler = new MarketDataClient.OrderBookUpdateHandler() {
        @Override
        public void onUpdate(final long transactTime, final Security security, final MarketOrderBook orderBook) {
            scheduler.handleTimestamp(transactTime);
            final MarketDataUpdateHandler updateHandler = ((ScoreBoardSecurityInfo)security).marketDataUpdateHandler();
            if (updateHandler != null) {
                orderService.isRecovery(orderBook.isRecovery());
                try {
                	// This is a list of handlers
                    updateHandler.onOrderBookUpdated(security, transactTime, orderBook);
                }
                catch (final Exception e) {
                    LOG.error("Error encountered when handling orderbook update for {}...",  security.code(), e);
                }
            }
        }
    };    
    
    private Handler<ScoreBoardSbeDecoder> scoreBoardHandler = new Handler<ScoreBoardSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSbeDecoder sbe) {
           LOG.trace("Received target scoreboard for warrant {}", sbe.secSid());
            try {
                final ScoreBoardSecurityInfo security = securities.get(sbe.secSid());
                if (security != null) {
                    final ScoreBoard scoreBoard = security.scoreBoard();
                    scoreBoardDecoder.decodeScoreBoard(sbe, scoreBoard);
                }
            } 
            catch (final Exception e) {
                messageService.stateEvent(StateTransitionEvent.FAIL);
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
                    }
                }
            }
        }
    };
    
    private Handler<PositionSbeDecoder> positionHandler = new Handler<PositionSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder position) {
            if (position.entityType() == EntityType.SECURITY) {
                final long secSid = position.entitySid();
                if (scoreBoardManager.warrants().containsKey(secSid)) {
                    final ScoreBoardSecurityInfo security = scoreBoardManager.warrants().get(secSid);
                    LOG.info("Received position of {} for {}, sequence {}", position.openPosition(), security.code(), box(security.channelSeq()));
                    security.realPosition(position.openPosition());
                }
            }
        }
    };

	private Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            byte senderSinkId = header.senderSinkId();
            final MessageSinkRef sender = messenger.sinkRef(senderSinkId);
            try {
                LOG.info("Received request from sink[{}]", senderSinkId);
                handleRequest(sender, request);
            }
            catch (final Exception e) {
                LOG.error("Failed to handle request", e);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
            }            
        }
	};

    private void handleRequest(final MessageSinkRef sender, final RequestSbeDecoder request) {
        try {
        	ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
            if (parameters.size() < 1) {
                throw new IllegalArgumentException("Received request with insufficient parameters");
            }
            Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);
            if (!templateType.isPresent()) {
                throw new IllegalArgumentException("Received request with first parameter not a TEMPLATE_TYPE");
            }
            if (templateType.get() == TemplateType.SCOREBOARD_SCHEMA) {
                handleScoreBoardSchemaRequest(sender, request, parameters);
            }
            else if (templateType.get() == TemplateType.SCOREBOARD) {
                handleScoreBoardRequest(sender, request, parameters);
            }
            else if (templateType.get() == TemplateType.StrategySwitch) {
                handleStrategySwitchRequest(sender, request, parameters);
            }
            else if (templateType.get() == TemplateType.COMMAND) {
                if (cbbcTest != null) {
                    cbbcTest.handleRequest(sender, request, parameters, messenger);
                }
            }
            else {
                throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type");
            }
        }
        catch (final Exception e) {
            LOG.error("Failed to handle request", e);
            messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
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
        switch (codec.commandType()) {
        case START_TEST_MODE: {
            LOG.info("Received START_TEST_MODE command from {}", box(senderSinkId));
            if (cbbcOrderService != null) {
                cbbcOrderService.setBuyOrderType(OrderType.LIMIT_THEN_CANCEL_ORDER);
            }
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.OK, codec.commandType());
            break;
        }
        case STOP_TEST_MODE: {
            LOG.info("Received STOP_TEST_MODE command from {}", box(senderSinkId));
            if (cbbcOrderService != null) {
                cbbcOrderService.setBuyOrderType(OrderType.ENHANCED_LIMIT_ORDER);
            }
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.OK, codec.commandType());
            break;
        }
        default:
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            break;
        }
    }
    
    private void handleScoreBoardSchemaRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) throws Exception {
        if (request.requestType() == RequestType.GET) {
            LOG.info("Pending send scoreboard schema to sink[{}]", sender.sinkId());                       
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK, this.scoreBoardSchema);
        }           
    }

    private void handleScoreBoardRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) throws Exception {
    	Optional<Long> secSid = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
    	if (secSid.isPresent()){
    		final long sid = secSid.get().longValue();
    		final ScoreBoardSecurityInfo security = securities.get(sid);
    		if (security != null) {
    			LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
    			if (request.requestType() == RequestType.GET) {
    				LOG.info("Pending send scoreboard for {} to sink[{}]", security.code(), sender.sinkId());                       
    				final ScoreBoard scoreBoard = security.scoreBoard();
    				if (scoreBoard != null) {
    					int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
    					responseMsgSeq = ScoreBoardSender.sendScoreBoard(this.messenger.responseSender(), sender, request.clientKey(), BooleanType.FALSE, responseMsgSeq, ResultType.OK, scoreBoard);
    					this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
    				}
    				else {
    					messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
    				}
    			}
    			else if (request.requestType() == RequestType.SUBSCRIBE) {
    				LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
    				globalSubscribers.add(sender);
    				final ScoreBoard scoreBoard = security.scoreBoard();
    				if (scoreBoard != null) {
    					int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
    					responseMsgSeq = ScoreBoardSender.sendScoreBoard(this.messenger.responseSender(), sender, request.clientKey(), BooleanType.FALSE, responseMsgSeq, ResultType.OK, scoreBoard);
    					this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
    				}
    				else {
    					messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
    				}
    			}
    			else if (request.requestType() == RequestType.UNSUBSCRIBE) {
    				LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
    				globalSubscribers.remove(sender);
    				messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
    			}
    			else {
    				throw new IllegalArgumentException("No support for " + request.requestType() + " request for security");
    			}
    		}
    		else {
    			throw new IllegalArgumentException("Received " + request.requestType() + " request for security with invalid sid " + secSid);
    		}
    	}
        else {
            if (request.requestType() == RequestType.GET) {
                LOG.info("Pending to send all scores to sink[{}]", sender.sinkId());
                int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
                for (ScoreBoardSecurityInfo entity : scoreBoardManager.warrants().values()){
                    if (entity.scoreBoard() != null){
                        responseMsgSeq = ScoreBoardSender.sendScoreBoard(this.messenger.responseSender(), sender, request.clientKey(), BooleanType.FALSE, responseMsgSeq, ResultType.OK, entity.scoreBoard());
                    }
                }
                messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
            }
            else if (request.requestType() == RequestType.SUBSCRIBE) {
                LOG.info("Subscribing all score boards {} to sink[{}]", sender.sinkId());
                int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
                globalSubscribers.add(sender);
                for (ScoreBoardSecurityInfo entity : scoreBoardManager.warrants().values()){
                    if (entity.scoreBoard() != null){
                        responseMsgSeq = ScoreBoardSender.sendScoreBoard(this.messenger.responseSender(), sender, request.clientKey(), BooleanType.FALSE, responseMsgSeq, ResultType.OK, entity.scoreBoard());
                    }                           
                }
                messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseMsgSeq, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                LOG.info("Unsubscribing all score boards {} from sink[{}]", sender.sinkId());
                globalSubscribers.remove(sender);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else {
                throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
            }
        }
    }

    private void handleStrategySwitchRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters) throws Exception {
        if (request.requestType() == RequestType.UPDATE) {
        	Optional<Long> issuerSid = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.ISSUER_SID);
        	if (issuerSid.isPresent()){
            	Optional<Long> paramValue = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.PARAM_VALUE);
            	if (paramValue.isPresent()){
                    final long switchValue = paramValue.get().longValue();
                    scoreBoardManager.handleIssuerSwitchUpdateRequest(issuerSid.get(), switchValue);            		
            	}
            	else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for StrategySwitch with unsupported parameters");            		
            	}
        	}
        	else {
        		Optional<Long> secSid = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
            	Optional<Long> paramValue = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.PARAM_VALUE);
            	if (paramValue.isPresent()){
                    final long switchValue = paramValue.get().longValue();
                    scoreBoardManager.handleWarrantSwitchUpdateRequest(secSid.get(), switchValue);            		
            	}
            	else {
                    throw new IllegalArgumentException("Received " + request.requestType() + " request for StrategySwitch with unsupported parameters");            		
            	}
        	}
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
        else if (request.requestType() == RequestType.SUBSCRIBE) {
            LOG.info("Subscribing switch to sink[{}]", box(sender.sinkId()));
            globalSubscribers.add(sender);
            int counter = 0;
            counter = scoreBoardManager.sendSwitchesSnapshots(sender, request, counter++);
            this.messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, counter, ResultType.OK);            
        }
        else if (request.requestType() == RequestType.UNSUBSCRIBE) {
            LOG.info("Unsubscribing main switch from sink[{}]", box(sender.sinkId()));                
            globalSubscribers.remove(sender);
            this.messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }
    }
    
    final SwitchControlHandler scoreBoardSwitchHandler = new SwitchControlHandler() {
        @Override
        public void switchOnStrategy(final long securitySid) {
            final StrategySecurity security = securities.get(securitySid);
        	final MessageSinkRefList strats = messenger.referenceManager().strats();
        	for (int i = 0; i < strats.size(); i++){
                CompletableFuture<Request> retrieveStrategyTypeFuture = messenger.sendRequest(strats.elements()[i],
                        RequestType.UPDATE,
                        new ImmutableList.Builder<Parameter>()
                        .add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.StrategySwitch.value()))
                        .add(Parameter.of(ParameterType.SECURITY_SID, security.sid()))
                        .add(Parameter.of(ParameterType.PARAM_VALUE, 1))
                        .add(Parameter.of(ParameterType.STRATEGY_SWITCH_TYPE, StrategySwitchType.STRATEGY_DAY_ONLY.value())).build(),
                        ResponseHandler.NULL_HANDLER);
                retrieveStrategyTypeFuture.thenAccept((r) -> {
                }).exceptionally(new Function<Throwable, Void>() {
                    @Override
                    public Void apply(Throwable t) {
                        LOG.error("Failed to turn on strategy for {}...", security.code(), t);
                        return null;
                    }
                });        		
        	}
        }

        @Override
        public void switchOffStrategy(final long securitySid) {
            final StrategySecurity security = securities.get(securitySid);
        	final MessageSinkRefList strats = messenger.referenceManager().strats();
        	for (int i = 0; i < strats.size(); i++){
                CompletableFuture<Request> retrieveStrategyTypeFuture = messenger.sendRequest(strats.elements()[i],
                        RequestType.UPDATE,
                        new ImmutableList.Builder<Parameter>()
                        .add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.StrategySwitch.value()))
                        .add(Parameter.of(ParameterType.SECURITY_SID, security.sid()))
                        .add(Parameter.of(ParameterType.PARAM_VALUE, StrategyExitMode.SCOREBOARD_EXIT.value() << Byte.SIZE))
                        .add(Parameter.of(ParameterType.STRATEGY_SWITCH_TYPE, StrategySwitchType.STRATEGY_DAY_ONLY.value())).build(),
                        ResponseHandler.NULL_HANDLER);
                retrieveStrategyTypeFuture.thenAccept((r) -> {
                }).exceptionally(new Function<Throwable, Void>() {
                    @Override
                    public Void apply(Throwable t) {
                        LOG.error("Failed off turn on strategy for {}...", security.code(), t);
                        return null;
                    }
                });        		
        	}
        }

        @Override
        public void onErrorControlling(final long securitySid) {
            final StrategySecurity security = securities.get(securitySid);
            try {
                scoreBoardManager.flipSwitchForSecurity(securitySid, BooleanType.FALSE, true);
            }
            catch (final Exception e) {
                LOG.error("Error when trying to switch off security {}...", security.code(), e);
            }
            
        }            
    };   
    
    CbbcTest cbbcTest = null;
    StrategyOrderService cbbcOrderService = null;
    void initializeCbbcTest() {
        LOG.info("Initializing cbbc test");
        StrategySecurity futures = null;
        for (final StrategySecurity security : securities.entities()) {
            if (security.securityType().equals(SecurityType.FUTURES)) {
                final StrategySecurity index = securities.get(security.underlyingSid());
                if (index.code().equals(ServiceConstant.HSI_CODE)) {
                    futures = security;
                }
            }
        }
        if (futures == null) {
            LOG.error("Cannot initial cbbc test because failed to find HSI futures");
            return;
        }
        cbbcOrderService = new LunarStrategyOrderService(messenger, messageService.systemClock(), futures.orderBook().triggerInfo(), 
                new StrategyErrorHandler() {
                    @Override
                    public void onError(long secSid, String message) {
                        LOG.error("Failed to send cbbc test order for {} - {}",  box(secSid), message);
                    }
                }, SubscriberList.of());
        cbbcTest = new CbbcTest(securities, futures, cbbcOrderService);
        marketDataClient.subscribe(futures.sid(), orderBookFactory, mdSubResponseHandler);
    }
}
