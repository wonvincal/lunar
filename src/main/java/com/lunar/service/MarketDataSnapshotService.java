package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.agrona.DirectBuffer;
import org.agrona.collections.LongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.analysis.ChartDataHandler;
import com.lunar.analysis.OrderFlowAnalyzer;
import com.lunar.config.MarketDataSnapshotServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.SubscriberList;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.core.TimerService;
import com.lunar.entity.ChartData;
import com.lunar.entity.ChartData.VolumeAtPrice;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketStats;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.Boobs;
import com.lunar.order.Tick;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A general market data snapshot service that serves all snapshot needs (i.e. different exchanges)
 * It receives real-time updates from {@link MarketDataRealtimeService} and get order book 
 * snapshots from {@link MarketDataRefreshService} 
 * 
 * Use this to produce charting data
 * 
 * @author wongca
 *
 */
public class MarketDataSnapshotService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(MarketDataSnapshotService.class);
	
	public static final int BOOK_DEPTH = 10;
	
	private final LunarService messageService;
	private final Messenger messenger;
	private final TimerService timerService;
	private final String name;
    private final LongEntityManager<SecurityWrapper> securities;
	private final Long2ObjectLinkedOpenHashMap<SecurityWrapper> updatedMarketDataSnapshots;
	private final Long2ObjectLinkedOpenHashMap<SecurityWrapper> updatedBoobsSnapshots;
    private final Long2ObjectLinkedOpenHashMap<SecurityWrapper> updatedMarketStats;
	private long lastMarketDataUpdatedTime;
	private final long orderFlowWindowSizeNs;
	private final double[] orderFlowSupportedWindowSizeMultipliers;
	private final LongHashSet orderFlowEnabledSecSids;
	private final ObjectArrayList<SecurityWrapper> orderFlowSecurities;
	
	private final Duration sendObInterval;
	private final Duration sendStatsInterval;
	private final Messenger intervalMessenger;
	private Timeout sendTimerTask;
	private Timeout sendObTimerTask;
	private Timeout sendStatsTimerTask;
    private int m_triggerSeqNum;
    private SubscriberList performanceSubscribers;
    
    private Duration commonPeriodicTaskFreq;
    private final AtomicReference<TimeoutHandlerTimerTask> commonPeriodicTask;
    private final int CLIENT_KEY_FOR_COMMON_PERIODIC_TASK = 1;
    
    private Duration orderFlowClockTickFreq;
    private final AtomicReference<TimeoutHandlerTimerTask> orderFlowClockTickTask;
    private final int CLIENT_KEY_FOR_ORDER_FLOW_CLOCK_TICK = 2;
    private static final ImmutableList<Parameter> ORDERBOOK_SNAPSHOT_PARAM_LIST = new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ORDERBOOK_SNAPSHOT.value())).build();
    private static final ImmutableList<Parameter> MARKET_STATS_PARAM_LIST = new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.MARKET_STATS.value())).build();
    
	public class SecurityWrapper extends Security {
	    final private MarketOrderBook marketOrderBook;
	    final private Boobs boobs;
	    final private MarketStats marketStats;
	    final boolean orderFlowEnabled;
	    final private OrderFlowAnalyzer orderFlowAnalyzer;
	    private SubscriberList marketDataSnapshotSubscribers;
	    private SubscriberList boobsSnapshotSubscribers;
	    private SubscriberList marketStatsSubscribers;
	    private final SubscriberList chartDataSubscribers = SubscriberList.of(ServiceConstant.MAX_SUBSCRIBERS);
	    
        public SecurityWrapper(long sid, SecurityType secType, String code, int exchangeSid, boolean isAlgo, SpreadTable spreadTable, boolean orderFlowEnabled, long orderFlowWindowSizeNs, double[] orderFlowSupportedWindowSizeMultipliers) {
            super(sid, secType, code, exchangeSid, 1, isAlgo, spreadTable);
            this.marketOrderBook = MarketOrderBook.of(sid, BOOK_DEPTH, spreadTable, Integer.MIN_VALUE, Integer.MIN_VALUE);
            this.marketOrderBook.triggerInfo().triggeredBy((byte)messenger.self().sinkId());
            this.boobs = Boobs.of(sid);
            this.marketStats = MarketStats.of();
            this.orderFlowEnabled = orderFlowEnabled;
            this.orderFlowAnalyzer = OrderFlowAnalyzer.of(sid, orderFlowWindowSizeNs, orderFlowSupportedWindowSizeMultipliers);
            
            // Send chart data out whenever a tick is ready
            this.orderFlowAnalyzer.chartDataHandler(new ChartDataHandler() {
				@Override
				public void handleNewChartData(ChartData data) {
//					LOG.info("Send chart data back [secSid:{}, dataTime:{}]", data.secSid(), data.dataTime());
					for (int i = 0; i < chartDataSubscribers.size(); i++){
						messenger.chartDataSender().sendChartData(chartDataSubscribers.elements()[i], data);
					}
				}

				@Override
				public void handleNewChartData(long secSid, long dataTime, int windowSizeInSec, int open, int close,
						int high, int low, int vwap, int valueAreaHigh, int valueAreaLow, 
						VolumeClusterDirectionType dirType,
						Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices) {
					for (int i = 0; i < chartDataSubscribers.size(); i++){
						messenger.chartDataSender().sendChartData(chartDataSubscribers.elements()[i], 
								secSid,
								dataTime,  
								windowSizeInSec,  
								open,  
								close,
								high,  
								low,  
								vwap,  
								valueAreaHigh,  
								valueAreaLow,
								dirType, 
								volumeAtPrices);
					}
				}
			});
        }
        
        public ObjectArrayList<ChartData> chartDataList(){
        	return orderFlowAnalyzer.chartDataList();
        }
        
        public ObjectArrayList<ChartData> chartDataList(int windowSizeInSec){
        	return orderFlowAnalyzer.chartDataList(windowSizeInSec);
        }

        public void subscribeMarketDataSnapshot(final MessageSinkRef sink) {
            if (marketDataSnapshotSubscribers == null) {
                marketDataSnapshotSubscribers = SubscriberList.of(ServiceConstant.MAX_SUBSCRIBERS);
            }
            marketDataSnapshotSubscribers.add(sink);
        }

        public void unsubscribeMarketDataSnapshot(final MessageSinkRef sink) {
            if (marketDataSnapshotSubscribers != null) {
                marketDataSnapshotSubscribers.remove(sink);
            }
        }
        
        public SubscriberList marketDataSnapshotSubscribers() {
            return marketDataSnapshotSubscribers;
        }

        public void subscribeBoobsSnapshot(final MessageSinkRef sink) {
            if (boobsSnapshotSubscribers == null) {
                boobsSnapshotSubscribers = SubscriberList.of(ServiceConstant.MAX_SUBSCRIBERS);
            }
            boobsSnapshotSubscribers.add(sink);
        }

        public void unsubscribeBoobsSnapshot(final MessageSinkRef sink) {
            if (boobsSnapshotSubscribers != null) {
                boobsSnapshotSubscribers.remove(sink);
            }
        }
        
        public SubscriberList boobsSnapshotSubscribers() {
            return boobsSnapshotSubscribers;
        }
        
        public void subscribeMarketStats(final MessageSinkRef sink) {
            if (marketStatsSubscribers == null) {
                marketStatsSubscribers = SubscriberList.of(ServiceConstant.MAX_SUBSCRIBERS);
            }
            marketStatsSubscribers.add(sink);
        }

        public void unsubscribeMarketStats(final MessageSinkRef sink) {
            if (marketStatsSubscribers != null) {
                marketStatsSubscribers.remove(sink);
            }
        }
        
        public void subscribeChartData(final MessageSinkRef sink) {
            chartDataSubscribers.add(sink);
        }

        public void unsubscribeChartData(final MessageSinkRef sink) {
            if (chartDataSubscribers != null) {
            	chartDataSubscribers.remove(sink);
            }
        }
        
        public SubscriberList chartDataSubscribers() {
            return chartDataSubscribers;
        }

        public SubscriberList marketStatsSubscribers() {
            return marketStatsSubscribers;
        }
        
        public MarketOrderBook getMarketOrderBook() {
            return this.marketOrderBook;
        }
        
        public Boobs getBoobs() {
            return this.boobs;
        }
        
        public MarketStats getMarketStats() {
            return this.marketStats;
        }
        
        public void handleBestChange(long wallClockNanoOfDay, long dataTimeNanoOfDay, Boobs boobs){
        	orderFlowAnalyzer.handleBestChange(wallClockNanoOfDay, dataTimeNanoOfDay, boobs);
        }
        
        public void handleMarketDataTrade(long wallClockNanoOfDay, MarketDataTradeSbeDecoder codec){
        	orderFlowAnalyzer.handleMarketDataTrade(wallClockNanoOfDay, codec);
        }
        
        public void handleClockTick(long wallClockNanoOfDay){
        	orderFlowAnalyzer.handleClockTick(wallClockNanoOfDay);
        }
        
        public boolean orderFlowEnabled(){
        	return orderFlowEnabled;
        }
        
        public void initOrderFlowAnalyser(long startWallClockNanoOfDay){
        	orderFlowAnalyzer.init(startWallClockNanoOfDay);
        }
	}

    public static MarketDataSnapshotService of(final ServiceConfig config, final LunarService messageService){
        return new MarketDataSnapshotService(config, messageService);
    }
    
	public MarketDataSnapshotService(final ServiceConfig config, final LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = messageService.messenger();
		this.timerService = this.messenger.timerService();
		this.intervalMessenger = this.messenger.createChildMessenger();
		this.m_triggerSeqNum = 0;
		this.performanceSubscribers = SubscriberList.of(ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
		if (config instanceof MarketDataSnapshotServiceConfig) {
		    final MarketDataSnapshotServiceConfig specificConfig = (MarketDataSnapshotServiceConfig)config;
		    sendObInterval = Duration.ofMillis(specificConfig.obInterval());
		    sendStatsInterval = Duration.ofMillis(specificConfig.statsInterval());
	        securities = new LongEntityManager<SecurityWrapper>(specificConfig.numSecurities());
		    updatedMarketDataSnapshots = new Long2ObjectLinkedOpenHashMap<SecurityWrapper>(specificConfig.numSecurities());
		    updatedBoobsSnapshots = new Long2ObjectLinkedOpenHashMap<SecurityWrapper>(specificConfig.numSecurities());
		    updatedMarketStats = new Long2ObjectLinkedOpenHashMap<SecurityWrapper>(specificConfig.numSecurities());
		    orderFlowWindowSizeNs = specificConfig.orderFlowWindowSizeNs();
		    List<Double> multipliers = specificConfig.orderFlowSupportedWindowSizeMultipliers();
		    orderFlowSupportedWindowSizeMultipliers = new double[multipliers.size()];
		    for (int i = 0; i < multipliers.size(); i++){
		    	orderFlowSupportedWindowSizeMultipliers[i] = multipliers.get(i);
		    }
		    
		    Optional<List<Integer>> secSids = specificConfig.orderFlowEnabledSecSids();
		    if (secSids.isPresent()){
		    	List<Integer> items = secSids.get();
		    	orderFlowEnabledSecSids = new LongHashSet(items.size(), -1);
		    	for (Integer undSecSid : items){
		    		orderFlowEnabledSecSids.add(undSecSid);
		    	}
		    	SecurityWrapper[] securities =(SecurityWrapper[])Array.newInstance(SecurityWrapper.class, items.size());
			    orderFlowSecurities = ObjectArrayList.wrap(securities);
			    orderFlowSecurities.size(0);
		    }
		    else{
			    orderFlowEnabledSecSids = new LongHashSet(-1);
			    orderFlowSecurities = new ObjectArrayList<>();
		    }
		}
		else {
		    throw new IllegalArgumentException("Service " + this.name + " expects a MarketDataSnapshotServiceConfig config");
		}
		this.commonPeriodicTask = new AtomicReference<>();
		this.commonPeriodicTaskFreq = Duration.ofSeconds(10l);
		
		this.orderFlowClockTickTask = new AtomicReference<>();
		this.orderFlowClockTickFreq = ServiceConstant.DEFAULT_ORDER_FLOW_CLOCK_TICK_FREQ;
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
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
        messenger.receiver().securityHandlerList().add(securityHandler);

        final CompletableFuture<Request> retrieveSecurityFuture = messenger.sendRequest(messenger.referenceManager().rds(),
                RequestType.SUBSCRIBE,
				new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveSecurityFuture.thenAccept((r) -> { 
            messageService.stateEvent(StateTransitionEvent.ACTIVATE);
        });
        return StateTransitionEvent.NULL;	    
	}

	@Override
	public StateTransitionEvent activeEnter() {
		messenger.receiver().orderBookSnapshotHandlerList().add(orderBookSnapshotHandler);
		messenger.receiver().marketDataTradeHandlerList().add(marketDataTradeHandler);
		messenger.receiver().marketStatsHandlerList().add(marketStatsHandler);
		messenger.receiver().requestHandlerList().add(requestHandler);
		if (sendObInterval.compareTo(sendStatsInterval) == 0) {
	        sendTimerTask = messageService.messenger().newTimeout(new TimerTask() {
	            @Override
	            public void run(final Timeout timeout) throws Exception {
	                intervalMessenger.sendRequest(intervalMessenger.self(),
	                RequestType.ECHO,
	                Parameter.NULL_LIST,
	                ResponseHandler.NULL_HANDLER);
	                sendTimerTask = messenger.newTimeout(this, sendObInterval);
	            }
	        }, sendObInterval);
		    
		}
		else {
    		sendObTimerTask = messenger.newTimeout(new TimerTask() {
                @Override
                public void run(final Timeout timeout) throws Exception {
                    intervalMessenger.sendRequest(intervalMessenger.self(),
                    RequestType.ECHO,
                    ORDERBOOK_SNAPSHOT_PARAM_LIST,
                    ResponseHandler.NULL_HANDLER);
                    sendObTimerTask = messenger.newTimeout(this, sendObInterval);
                }
    		}, sendObInterval);
    		sendStatsTimerTask = messenger.newTimeout(new TimerTask() {
    		    @Override
    		    public void run(final Timeout timeout) throws Exception {
    		        intervalMessenger.sendRequest(intervalMessenger.self(),
    		                RequestType.ECHO,
    		                MARKET_STATS_PARAM_LIST,
    		                ResponseHandler.NULL_HANDLER);                
    		        sendStatsTimerTask = messenger.newTimeout(this, sendStatsInterval);
    		    }
    		}, sendStatsInterval);
		}		
		messenger.receiver().timerEventHandlerList().add(timerEventHandler);
		this.commonPeriodicTask.set(messenger.timerService().createTimerTask(commonPeriodicTaskTimer, "mds-common-periodic-task"));
		messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);

		this.orderFlowClockTickTask.set(messenger.timerService().createTimerTask(orderFlowClockTickTimer, "mds-order-flow-clock-tick"));
		messenger.newTimeout(orderFlowClockTickTask.get(), orderFlowClockTickFreq);
		
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
        if (sendTimerTask != null) {
            sendTimerTask.cancel();
        }	    
	    if (sendObTimerTask != null) {
	        sendObTimerTask.cancel();
	    }
        if (sendStatsTimerTask != null) {
            sendStatsTimerTask.cancel();
        }	    
		messenger.receiver().orderBookSnapshotHandlerList().remove(orderBookSnapshotHandler);
		messenger.receiver().marketDataTradeHandlerList().remove(marketDataTradeHandler);
		messenger.receiver().marketStatsHandlerList().remove(marketStatsHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
        messenger.receiver().securityHandlerList().remove(securityHandler);
        messenger.receiver().timerEventHandlerList().remove(timerEventHandler);
	}

	private Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
            LOG.info("Received target security {}", security.sid());
            final byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
            security.getCode(bytes, 0);
            try {
            	boolean orderFlowEnabled = orderFlowEnabledSecSids.contains(security.sid());
                final SecurityWrapper newSecurity = new SecurityWrapper(security.sid(), 
                        security.securityType(), 
                        new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim(), 
                        security.exchangeSid(),
                        security.isAlgo() == BooleanType.TRUE,
                        SpreadTableBuilder.getById(security.spreadTableCode()),
                        orderFlowEnabled,
                        orderFlowWindowSizeNs,
                        orderFlowSupportedWindowSizeMultipliers);
                securities.add(newSecurity);
                if (orderFlowEnabled){
                	orderFlowSecurities.add(newSecurity);
                	newSecurity.initOrderFlowAnalyser(timerService.toNanoOfDay());
                }
                final MarketOrderBook orderBook = MarketOrderBook.of(security.sid(), BOOK_DEPTH, SpreadTableBuilder.getById(security.spreadTableCode()), Integer.MIN_VALUE, Integer.MIN_VALUE);
                orderBook.triggerInfo().triggeredBy((byte)messenger.self().sinkId());
            } 
            catch (final UnsupportedEncodingException e) {
                LOG.error("Failed to decode SecuritySbe", e);
                messageService.stateEvent(StateTransitionEvent.FAIL);
                return;
            }   
        }
	};
	
	private Handler<MarketStatsSbeDecoder> marketStatsHandler = new Handler<MarketStatsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder codec) {
            final long secSid = codec.secSid();
            final SecurityWrapper security = securities.get(secSid);
            if (security != null) {
                final MarketStats marketStats = security.getMarketStats();
                marketStats.secSid(codec.secSid());
                marketStats.open(codec.open());
                marketStats.close(codec.close());
                marketStats.high(codec.high());
                marketStats.low(codec.low());
                marketStats.volume(codec.volume());
                //TODO turnover is volume from mdu...
                final Boobs boobs = security.getBoobs();
                if (boobs == null) {
                    marketStats.turnover(0);
                }
                else {
                    marketStats.turnover(codec.turnover() * boobs.last());
                }
                marketStats.isRecovery(codec.isRecovery() == BooleanType.TRUE);
                marketStats.transactTime(codec.transactTime());
                marketStats.triggerInfo().decode(codec.triggerInfo());
                updatedMarketStats.put(secSid, security);
            }
        }
	};
	
	private Handler<MarketDataTradeSbeDecoder> marketDataTradeHandler = new Handler<MarketDataTradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder codec) {
            final long secSid = codec.secSid();
            final SecurityWrapper security = securities.get(secSid);
            if (security != null) {
                final Boobs boobs = security.getBoobs();
                if (codec.price() != boobs.last()) {
                    boobs.last(codec.price());
                    updatedBoobsSnapshots.put(secSid, security);
                }
                // Feed non-recovery trade
                if(codec.isRecovery() == BooleanType.FALSE && security.orderFlowEnabled()){
                	security.handleMarketDataTrade(timerService.toNanoOfDay(), codec);
                }
            }
        }
	};
   
	private Handler<OrderBookSnapshotSbeDecoder> orderBookSnapshotHandler = new Handler<OrderBookSnapshotSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder codec) {
            final long secSid = codec.secSid();
            final SecurityWrapper security = securities.get(secSid);
            if (security != null) {
            	long transactTime = codec.transactTime();
                final MarketOrderBook orderBook = security.getMarketOrderBook();
                orderBook.askSide().clear();            
                for (final OrderBookSnapshotSbeDecoder.AskDepthDecoder tick : codec.askDepth()) {
                    orderBook.askSide().create(tick.price(), tick.quantity());
                }
                orderBook.bidSide().clear();
                for (final OrderBookSnapshotSbeDecoder.BidDepthDecoder tick : codec.bidDepth()) {
                    orderBook.bidSide().create(tick.price(), tick.quantity());
                }
                orderBook.channelSeqNum(codec.seqNum()).transactNanoOfDay(codec.transactTime()).isRecovery(codec.isRecovery() == BooleanType.TRUE).triggerInfo().decode(codec.triggerInfo());
                if (security.subscribers().length > 0) {
                    updatedMarketDataSnapshots.put(secSid, security);
                }
                //LOG.info("Updated market data for {}: {}", secSid, orderBook);
                final Boobs boobs = security.getBoobs();
                final Tick askTick = orderBook.bestAskOrNullIfEmpty();
                final int bestAsk = askTick == null ? 0 : askTick.price();
                final Tick bidTick = orderBook.bestBidOrNullIfEmpty();
                final int bestBid = bidTick == null ? 0 : bidTick.price();
                if (boobs.bestBid() != bestBid || boobs.bestAsk() != bestAsk) {
                    boobs.bestAsk(bestAsk);
                    boobs.bestBid(bestBid);
                    updatedBoobsSnapshots.put(secSid, security);
                    if (security.orderFlowEnabled){
                    	security.handleBestChange(timerService.toNanoOfDay(), transactTime, boobs);
                    }
                }           
                m_triggerSeqNum++;
                lastMarketDataUpdatedTime = codec.transactTime();                
            }             
        }	    
	};

	private Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
            byte senderSinkId = header.senderSinkId();
            final MessageSinkRef sender = messenger.sinkRef(senderSinkId);
            try {
                if (request.requestType() == RequestType.ECHO) {
                    final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters = request.parameters();
                    if (parameters.count() < 1) {
                        sendObSnapshots();
                        sendStats();
                    }
                    else if (parameters.next().parameterType() == ParameterType.TEMPLATE_TYPE) {
                        final long templateType = parameters.parameterValueLong();
                        if (templateType == TemplateType.ORDERBOOK_SNAPSHOT.value()) {
                            sendObSnapshots();
                        }
                        else if (templateType == TemplateType.MARKET_STATS.value()) {
                            sendStats();
                        }
                    }
                }
                else {
                    LOG.info("Received request from sink[{}]", senderSinkId);
                    handleRequest(sender, request);
                }
            }
            catch (final Exception e) {
                LOG.error("Failed to handle request", e);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
            }            
        }
	};

	private void handleRequest(final MessageSinkRef sender, final RequestSbeDecoder request) throws UnsupportedEncodingException {
		// Send response
		ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
        if (parameters.size() < 1) {
			throw new IllegalArgumentException("Received request with insufficient parameters");
		}
        Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);
        if (!templateType.isPresent()){
            throw new IllegalArgumentException("Received request with no TEMPLATE_TYPE");
        }
        Optional<DataType> dataType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
		if (templateType.get() == TemplateType.ORDERBOOK_SNAPSHOT) {
			Optional<Long> secSidParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
			if (secSidParam.isPresent()){
			    final long secSid = secSidParam.get();
			    final SecurityWrapper security = securities.get(secSid);
				if (security != null) {
					LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
					if (request.requestType() == RequestType.GET) {
						LOG.info("Pending send market data snapshot for {} to sink[{}]", security.code(), sender.sinkId());						
			    		final MarketOrderBook orderBook = security.getMarketOrderBook();
			    		if (orderBook != null) {
//			    		    messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_MARKETDATA, (byte)messenger.self().sinkId(), m_triggerSeqNum, orderBook.transactNanoOfDay(), messageService.systemClock().timestamp());
							messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 0, ResultType.OK, orderBook);
						}
						else {
							messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
						}
					}
					else if (request.requestType() == RequestType.SUBSCRIBE) {
						LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
						security.subscribeMarketDataSnapshot(sender);						
						final MarketOrderBook orderBook = security.getMarketOrderBook();
						if (orderBook != null) {
							messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 0, ResultType.OK, orderBook);							
			    			//messenger.marketDataSender().sendOrderBookSnapshot(security.marketDataSnapshotSubscribers.elements(), orderBook);
						}
						else {
							messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
						}
					}
					else if (request.requestType() == RequestType.UNSUBSCRIBE) {
						LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
						security.unsubscribeMarketDataSnapshot(sender);
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
				throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
			}
		}
		else if (templateType.get() == TemplateType.BOOBS) {
			Optional<Long> secSidParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
			if (secSidParam.isPresent()){
                final long secSid = secSidParam.get();
                final SecurityWrapper security = securities.get(secSid);
                if (security != null) {
                    LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
                    if (request.requestType() == RequestType.GET) {
                        LOG.info("Pending send market data price for {} to sink[{}]", security.code(), sender.sinkId());
                        final Boobs boobs = security.getBoobs();
                        if (boobs != null) {
							messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 0, ResultType.OK, boobs);
                        }
                        else {
                            messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
                        }
                    }
                    else if (request.requestType() == RequestType.SUBSCRIBE) {
                        LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
                        security.subscribeBoobsSnapshot(sender);
                        final Boobs boobs = security.getBoobs();
                        if (boobs != null) {
                            messenger.boobsSender().sendBoobs(sender, boobs);
                        }
                    }
                    else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                        LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
                        security.unsubscribeBoobsSnapshot(sender);
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
                throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
            }
        }
		else if (templateType.get() == TemplateType.MARKET_STATS) {
			Optional<Long> secSidParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
			if (secSidParam.isPresent()){
                final long secSid = secSidParam.get();
                final SecurityWrapper security = securities.get(secSid);
                if (security != null) {
                    LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
                    if (request.requestType() == RequestType.GET) {
                        LOG.info("Pending send market stats for {} to sink[{}]", security.code(), sender.sinkId());
                        final MarketStats stats = security.getMarketStats();
                        if (stats != null) {
                            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.TRUE, 0, ResultType.OK, stats);
                        }
                        else {
                            messenger.responseSender().sendResponseForRequest(sender, request, ResultType.FAILED);
                        }
                    }
                    else if (request.requestType() == RequestType.SUBSCRIBE) {
                        LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
                        security.subscribeMarketStats(sender);
                        final MarketStats stats = security.getMarketStats();
                        if (stats != null) {
                            messenger.marketStatsSender().sendMarketStats(sender, stats);
                        }
                    }
                    else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                        LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
                        security.unsubscribeMarketStats(sender);
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
                throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
            }
        }
		else if (templateType.get() == TemplateType.CHART_DATA) {
			if (dataType.isPresent() && dataType.get() == DataType.ALL_SUPPORTED_WINDOW_SIZE){
				int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;				
				for (int i = 0; i < orderFlowSupportedWindowSizeMultipliers.length; i++){
        			messenger.responseSender().sendSbeEncodable(sender, 
        					request.clientKey(), 
        					BooleanType.FALSE, 
        					responseSeq++, 
        					ResultType.OK, 
        					ChartData.of(ServiceConstant.NULL_SEC_SID, (int)TimeUnit.NANOSECONDS.toSeconds((long)(orderFlowWindowSizeNs * orderFlowSupportedWindowSizeMultipliers[i]))));
				}
				messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseSeq, ResultType.OK);
				return;
			}
			
			Optional<Long> secSidParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
			if (secSidParam.isPresent()){
			    final long secSid = secSidParam.get();
			    final SecurityWrapper security = securities.get(secSid);
				if (security != null) {
					LOG.info("Received {} request for security with sid {}", request.requestType(), secSid);
					if (request.requestType() == RequestType.GET) {
						LOG.info("Pending send chart data for {} to sink[{}]", security.code(), sender.sinkId());
					}
					else if (request.requestType() == RequestType.GET_AND_SUBSCRIBE) {
						
						Optional<Long> fromTimeParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.FROM_TIME);
						Optional<Long> toTimeParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TO_TIME);
						
						long toNanoOfDay = timerService.toNanoOfDay();
						long fromNanoOfDay = toNanoOfDay - TimeUnit.HOURS.toNanos(6);
						if (fromTimeParam.isPresent() && toTimeParam.isPresent()){
							long fromTime = fromTimeParam.get();
							long toTime = toTimeParam.get();
							if (toTime > fromTime){
								toNanoOfDay = toTime;
								fromNanoOfDay = fromTime;
								
								LOG.info("Get and subscribe chart data from {} to {}", LocalTime.ofNanoOfDay(fromNanoOfDay), LocalTime.ofNanoOfDay(toNanoOfDay));
							}
						}
						
						LOG.info("Subscribing {} to sink[{}]", security.code(), sender.sinkId());
						security.subscribeChartData(sender);						
						Optional<Long> windowSizeInSecParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.WINDOW_SIZE_IN_SEC);
						ObjectArrayList<ChartData> chartDataList;
						if (windowSizeInSecParam.isPresent()){
							chartDataList = security.chartDataList(windowSizeInSecParam.get().intValue());
						}
						else {
							chartDataList = security.chartDataList();
						}
						if (chartDataList.isEmpty()){
							LOG.warn("There is no chart data [secSid:{}, windowSizeInSec:{}]", secSid, windowSizeInSecParam.isPresent() ? windowSizeInSecParam.get() : -1L);
						}
						int responseSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
			        	for (ChartData chartData : chartDataList){
			        		if (chartData.dataTime() >= fromNanoOfDay && chartData.dataTime() <= toNanoOfDay){
			        			messenger.responseSender().sendSbeEncodable(sender, 
			        					request.clientKey(), 
			        					BooleanType.FALSE, 
			        					responseSeq++, 
			        					ResultType.OK, chartData);
			        		}
			        	}
				    	messenger.responseSender().sendResponse(sender, request.clientKey(), BooleanType.TRUE, responseSeq, ResultType.OK);
					}
					else if (request.requestType() == RequestType.UNSUBSCRIBE) {
						LOG.info("Unsubscribing {} from sink[{}]", security.code(), sender.sinkId());
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
				throw new IllegalArgumentException("Received " + request.requestType() + " request for SECURITY with unsupported parameters");
			}
		}
		else if (templateType.get() == TemplateType.GENERIC_TRACKER) {
            if (request.requestType() == RequestType.SUBSCRIBE) {
                this.performanceSubscribers.add(sender);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }
            else if (request.requestType() == RequestType.UNSUBSCRIBE) {
                this.performanceSubscribers.remove(sender);
                messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
            }		    
		}
		else {
			throw new IllegalArgumentException("Received " + request.requestType() + " request for unsupported template type");
		}
	}
	
    private void commonPeriodicTask(){
    	LOG.trace("Common periodic task");
    	messenger.serviceStatusSender().sendOwnServiceStatus(messenger.referenceManager().admin(), 
    			ServiceStatusType.HEARTBEAT,
    			messenger.timerService().toNanoOfDay(),
    			messenger.timerService().toNanoOfDay(), 
    			lastMarketDataUpdatedTime);
    }

    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
	    	if (codec.timerEventType() == TimerEventType.TIMER){
	    		if (codec.clientKey() == CLIENT_KEY_FOR_COMMON_PERIODIC_TASK){
	    			commonPeriodicTask();
	    			messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
	    		}
	    		else if (codec.clientKey() == CLIENT_KEY_FOR_ORDER_FLOW_CLOCK_TICK){
	    			// Tick each order flow analyzer
	    			SecurityWrapper[] elements = orderFlowSecurities.elements();
	    			long nanoOfDay = timerService.toNanoOfDay();
	    			//LOG.info("Send clock tick to order flow [tick:{}]", nanoOfDay);
	    			for (int i = 0; i < orderFlowSecurities.size(); i++){
	    				elements[i].handleClockTick(nanoOfDay);
	    			}
	    			messenger.newTimeout(orderFlowClockTickTask.get(), orderFlowClockTickFreq);
	    		}
	    	}
		}
	};
	
	private final TimeoutHandler orderFlowClockTickTimer = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			messenger.newTimeout(orderFlowClockTickTask.get(), orderFlowClockTickFreq);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_ORDER_FLOW_CLOCK_TICK, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(orderFlowClockTickTask.get(), orderFlowClockTickFreq);
			}
		}
	}; 
	
    private final TimeoutHandler commonPeriodicTaskTimer = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_COMMON_PERIODIC_TASK, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
			}
		}
	};
	
	private void sendObSnapshots() {
	    for (final SecurityWrapper security : updatedMarketDataSnapshots.values()) {
	        if (security.marketDataSnapshotSubscribers() != null) {
	            final MarketOrderBook orderBook = security.getMarketOrderBook();
	            if (orderBook != null) {
	                messenger.marketDataSender().sendOrderBookSnapshot(security.marketDataSnapshotSubscribers.elements(), orderBook);
	            }
	        }
	    }
	    updatedMarketDataSnapshots.clear();
	    for (final SecurityWrapper security : updatedBoobsSnapshots.values()) {
	        if (security.boobsSnapshotSubscribers() != null) {
	            final Boobs boobs = security.getBoobs();
	            if (boobs != null) {
	                messenger.boobsSender().sendBoobs(security.boobsSnapshotSubscribers().elements(), security.sid(), boobs);
	            }
	        }
	    }
	    updatedBoobsSnapshots.clear();
	}

	private void sendStats() {
	    for (final SecurityWrapper security : updatedMarketStats.values()) {
	        if (security.marketStatsSubscribers != null) {
	            final MarketStats stats = security.getMarketStats();
	            if (stats != null) {
	                messenger.marketStatsSender().sendMarketStats(security.marketStatsSubscribers().elements(), stats);
	            }
	        }
	    }
	    updatedMarketStats.clear();
	}

}
