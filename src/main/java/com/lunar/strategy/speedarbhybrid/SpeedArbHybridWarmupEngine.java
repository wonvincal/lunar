package com.lunar.strategy.speedarbhybrid;

import static org.apache.logging.log4j.util.Unbox.box;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.HdrHistogram.Histogram;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import com.lunar.core.SystemClock;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.StrategyType;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.pricing.BucketPricer;
import com.lunar.pricing.BucketPricerWarmup;
import com.lunar.pricing.Greeks;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.GreeksUpdateHandler;
import com.lunar.strategy.LunarStrategyInfoSender;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.OrderStatusReceivedHandler;
import com.lunar.strategy.StrategyExplain;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategyScheduler;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategyWarmupEngine;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.parameters.ParamsSbeEncodable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class SpeedArbHybridWarmupEngine implements StrategyWarmupEngine {
    private static final Logger LOG = LogManager.getLogger(SpeedArbHybridWarmupEngine.class);
            
    private static final int PERFORMANCE_COUNTS = 500;
	private static final int COMPILE_WARMUP_COUNTS = 10000; // default compile threshold is 10000
	private static final int PREDICTIVE_WARMUP_COUNTS = 0;
	
    private static final long MAX_DELAY = 1000000000;
    private static final double INTERESTED_PERCENTILE = 99;

    private static final long VELOCITY_THRESHOLD = 2000000000L;
    private static final int PROFIT_RUN_TICKS = 20;
    private static final int MM_SIZE = 600000;
    private static final int TICK_SENSITIVITY_THRESHOLD = 1000;
    private static final int ALLOWED_MAX_SPREAD = 3;
    private static final long BAN_TO_DOWNVOL_PERIOD = 10_000_000_000L;
    private static final long BAN_TO_TOMAKE_PERIOD = 15_000_000_000L;
    private static final long SPREAD_OBSERVE_PERIOD = 600_000_000_000L;
    private static final int ORDER_SIZE = 300_000;
    private static final int TURNOVER_MAKING_QUANTITY = 1_000_000;
    private static final long TURNOVER_MAKING_PERIOD = 30_000_000_000L;
    private static final long ISSUER_MAX_LAG = 100_000_000L;
    private static final boolean SELL_ON_VOL_DOWN = true;
    private static final long STOP_PROFIT = 5000000;
    
    static final int WA_9502399 = 9502399;
    static final int WA_9502500 = 9502500;
    static final int WA_9504166 = 9504166;
    static final int WA_9504995 = 9504995;
    static final int WA_9506666 = 9506666;
    static final int WA_9506724 = 9506724; 
    static final int WA_9509166 = 9509166;
    static final int WA_9509545 = 9509545;
    
    private final SpreadTable spreadTable = SpreadTableBuilder.get();
	private Long2ObjectOpenHashMap<MarketDataUpdateHandler> obUpdateHandlers;
	private Long2ObjectOpenHashMap<GreeksUpdateHandler> greeksHandlers;
	private StrategyIssuer issuer;
	private StrategyType strategyType;
	private StrategySecurity callWarrant;
	private StrategySecurity putWarrant;
	private StrategySecurity underlying;
	private MarketOrderBook callWrtOrderBook;
	private MarketOrderBook putWrtOrderBook;
	private MarketOrderBook undOrderBook;
	private SpeedArbHybridStrategy callStrategy;
	private SpeedArbHybridStrategy putStrategy;
	private GenericUndParams defaultUndParams;	
	private GenericWrtParams defaultCallWrtParams;
	private GenericWrtParams defaultPutWrtParams;
	private GenericUndParams undParams;	
	private GenericWrtParams callWrtParams;
	private GenericWrtParams putWrtParams;
	private SpeedArbHybridTriggerController triggerController;
	private SystemClock systemClock;
	
    StrategyOrderService orderService;
    StrategyInfoSender strategyInfoSender;

    public void initialize(final SystemClock systemClock) {
        final StrategyOrderService orderService = new StrategyOrderService() {
            @Override
            public void buy(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
            }

            @Override
            public void sell(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
            }
            
            @Override
            public void sellToExit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
            }

            @Override
            public boolean canTrade() {
                return true;
            }

            @Override
            public void sellLimit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
            }
            
            @Override
            public void cancelAndSellOutstandingSell(StrategySecurity security, int price, StrategyExplain explain) {
                
            }
        };

        final StrategyInfoSender paramsSender = StrategyInfoSender.NULL_PARAMS_SENDER;     
        final LongEntityManager<StrategyType> strategyTypes = new LongEntityManager<StrategyType>(2);
        final LongEntityManager<StrategyIssuer> issuers = new LongEntityManager<StrategyIssuer>(1);
        final Long2ObjectOpenHashMap<StrategySecurity> underlyings = new Long2ObjectOpenHashMap<StrategySecurity>(1);
        final Long2ObjectOpenHashMap<StrategySecurity> warrants = new Long2ObjectOpenHashMap<StrategySecurity>(2);
        final Long2ObjectOpenHashMap<OrderStatusReceivedHandler> osReceivedHandlers = new Long2ObjectOpenHashMap<OrderStatusReceivedHandler>(1);
        initialize(systemClock, orderService, paramsSender, strategyTypes, issuers, underlyings, warrants, osReceivedHandlers);
    }
    @Override
	public void initialize(final SystemClock systemClock, final StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender, final LongEntityManager<StrategyType> strategyTypes, final LongEntityManager<StrategyIssuer> issuers, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final Long2ObjectOpenHashMap<OrderStatusReceivedHandler> osReceivedHandlers) {
        this.systemClock = systemClock;
        this.orderService = orderService;
        this.strategyInfoSender = strategyInfoSender;
		obUpdateHandlers = new Long2ObjectOpenHashMap<MarketDataUpdateHandler>(3);
		greeksHandlers = new Long2ObjectOpenHashMap<GreeksUpdateHandler>(2);
		callWrtOrderBook = MarketOrderBook.of(5L, 10, SpreadTableBuilder.get(), Integer.MIN_VALUE, Integer.MIN_VALUE);
		putWrtOrderBook = MarketOrderBook.of(5L, 10, SpreadTableBuilder.get(), Integer.MIN_VALUE, Integer.MIN_VALUE);
		undOrderBook = MarketOrderBook.of(12345L, 10, SpreadTableBuilder.get(), Integer.MIN_VALUE, Integer.MIN_VALUE);
		
		strategyType = StrategyType.of(1, "SpeedArb");
		issuer = StrategyIssuer.of(1, "PK", "PK", ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX);
		callWarrant = StrategySecurity.of(12345L, SecurityType.WARRANT, "12345", 0, 5L, PutOrCall.CALL, OptionStyle.ASIAN, 123456, 15000, (int)issuer.sid(), 1, true, SpreadTableBuilder.get(SecurityType.WARRANT), callWrtOrderBook);
		putWarrant = StrategySecurity.of(12346L, SecurityType.WARRANT, "12346", 0, 5L, PutOrCall.PUT, OptionStyle.ASIAN, 123456, 15000, (int)issuer.sid(), 1, true, SpreadTableBuilder.get(SecurityType.WARRANT), putWrtOrderBook);
		underlying = StrategySecurity.of(5L, SecurityType.STOCK, "5", 0, 0L, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, (int)issuer.sid(), 1, true, SpreadTableBuilder.get(SecurityType.STOCK), undOrderBook);
		callWarrant.underlying(underlying);
		callWarrant.pricingInstrument(underlying);
		callWarrant.issuer(issuer);
		putWarrant.underlying(underlying);
		putWarrant.pricingInstrument(underlying);		
		putWarrant.issuer(issuer);
        final Object2LongOpenHashMap<String> securitySymbols = new Object2LongOpenHashMap<String>(3);
        strategyTypes.add(strategyType);
        issuers.add(issuer);
        underlyings.put(underlying.sid(), underlying);
        warrants.put(callWarrant.sid(), callWarrant);
        warrants.put(putWarrant.sid(), putWarrant);

        securitySymbols.put(underlying.code(), underlying.sid());
        securitySymbols.put(callWarrant.code(), callWarrant.sid());
        securitySymbols.put(putWarrant.code(), putWarrant.sid());
        
        final GenericStrategyTypeParams speedArbParams = new GenericStrategyTypeParams();
        defaultUndParams = new GenericUndParams();
        defaultUndParams.sizeThreshold(MM_SIZE);
        defaultUndParams.velocityThreshold(VELOCITY_THRESHOLD);
        
        defaultCallWrtParams = new GenericWrtParams();
        defaultCallWrtParams.mmBidSize(MM_SIZE);
        defaultCallWrtParams.mmAskSize(MM_SIZE);
        defaultCallWrtParams.runTicksThreshold(PROFIT_RUN_TICKS);
        defaultCallWrtParams.tickSensitivityThreshold(TICK_SENSITIVITY_THRESHOLD);
        defaultCallWrtParams.allowedMaxSpread(ALLOWED_MAX_SPREAD);
        defaultCallWrtParams.banPeriodToDownVol(BAN_TO_DOWNVOL_PERIOD);
        defaultCallWrtParams.banPeriodToTurnoverMaking(BAN_TO_TOMAKE_PERIOD);
        defaultCallWrtParams.spreadObservationPeriod(SPREAD_OBSERVE_PERIOD);
        defaultCallWrtParams.baseOrderSize(ORDER_SIZE);
        defaultCallWrtParams.turnoverMakingPeriod(TURNOVER_MAKING_PERIOD);
        defaultCallWrtParams.turnoverMakingSize(TURNOVER_MAKING_QUANTITY);
        defaultCallWrtParams.sellOnVolDown(SELL_ON_VOL_DOWN);
        defaultCallWrtParams.issuerMaxLag(ISSUER_MAX_LAG);
        defaultCallWrtParams.stopProfit(STOP_PROFIT);
        defaultCallWrtParams.strategyTriggerType(StrategyTriggerType.VELOCITY_5MS);
        defaultCallWrtParams.defaultPricingMode(PricingMode.WEIGHTED);
        
        defaultCallWrtParams.greeks().delta(50000);
        defaultCallWrtParams.greeks().gamma(10);
        defaultCallWrtParams.baseOrderSize(10000);

        defaultPutWrtParams = defaultCallWrtParams.clone();
        defaultPutWrtParams.greeks().delta(-50000);
        
        undParams = defaultUndParams.clone();
        callWrtParams = defaultCallWrtParams.clone();
        putWrtParams = defaultPutWrtParams.clone();
        
        final GenericIssuerParams speedArbIssuerParams = new GenericIssuerParams(); 
        final StrategyScheduler scheduler = new MarketTicksStrategyScheduler();
        final SpeedArbHybridContext context = new SpeedArbHybridContext(StrategyType.of(0, "SpeedArb"), underlyings, warrants, issuers, orderService, strategyInfoSender, scheduler, new SpeedArbHybridFactory());
        speedArbParams.copyTo((ParamsSbeEncodable)context.getStrategyTypeParams());
        undParams.copyTo((ParamsSbeEncodable)context.getStrategyUndParams(underlying.sid(), true));
        speedArbIssuerParams.copyTo((ParamsSbeEncodable)context.getStrategyIssuerParams(issuer.sid(), true));
        callWrtParams.copyTo((ParamsSbeEncodable)context.getStrategyWrtParams(callWarrant.sid(), true));
        putWrtParams.copyTo((ParamsSbeEncodable)context.getStrategyWrtParams(putWarrant.sid(), true));
        
        callStrategy = SpeedArbHybridStrategy.of(context, callWarrant.sid(), 0);
        putStrategy = SpeedArbHybridStrategy.of(context, putWarrant.sid(), 0);
        try {
            callStrategy.start();
            putStrategy.start();
        } catch (final Exception e) {
            LOG.error("Cannot start strategies for warmup", e);
        }
        
        triggerController = context.getSpeedArbHybridTriggerController();
        
        greeksHandlers.put(callWarrant.sid(), callWarrant.greeksUpdateHandler());
        greeksHandlers.put(putWarrant.sid(), putWarrant.greeksUpdateHandler());
        obUpdateHandlers.put(underlying.sid(), underlying.marketDataUpdateHandler());
        obUpdateHandlers.put(callWarrant.sid(), callWarrant.marketDataUpdateHandler());
        obUpdateHandlers.put(putWarrant.sid(), putWarrant.marketDataUpdateHandler());
        osReceivedHandlers.put(callWarrant.sid(), callWarrant.orderStatusReceivedHandler());
        osReceivedHandlers.put(putWarrant.sid(), putWarrant.orderStatusReceivedHandler());		
	}

    private Level getLogLevel(final String loggerName) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        final LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
        return loggerConfig.getLevel();
    }

    private void setLogLevel(final String loggerName, final Level newLevel) {
	    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    	final Configuration config = ctx.getConfiguration();
    	final LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
    	loggerConfig.setLevel(newLevel);
	    ctx.updateLoggers();
    }


	@Override
	public void doWarmup() {
        final ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
        final PrintStream outputStream = new PrintStream(outputByteStream);

        final Level strategyLogLevel = getLogLevel(LogManager.getLogger(SpeedArbHybridStrategy.class).getName());
        final Level bucketPricerLevel = getLogLevel(LogManager.getLogger(BucketPricer.class).getName());
        final Level infoSenderLevel = getLogLevel(LogManager.getLogger(LunarStrategyInfoSender.class).getName());
	    try {
            LOG.info("Starting warmup...");
		    setLogLevel(LogManager.getLogger(SpeedArbHybridStrategy.class).getName(), Level.ERROR);
		    setLogLevel(LogManager.getLogger(BucketPricer.class).getName(), Level.ERROR);
		    setLogLevel(LogManager.getLogger(LunarStrategyInfoSender.class).getName(), Level.ERROR);
	    	
	        doMeasurePerformance(false, outputStream);
	        
	        setLogLevel(LogManager.getLogger(SpeedArbHybridStrategy.class).getName(), Level.INFO);
	        LOG.info("Performance Summary\n{}", outputByteStream.toString("UTF8"));
	        outputByteStream.reset();
	        setLogLevel(LogManager.getLogger(SpeedArbHybridStrategy.class).getName(), Level.ERROR);	        
	        
	    	BucketPricerWarmup.warmup();
    	    doCompileWarmup();
    	    doPredictiveWarmup();
    	    doMeasurePerformance(true, outputStream);
    	    
	        setLogLevel(LogManager.getLogger(SpeedArbHybridStrategy.class).getName(), Level.INFO);
	        LOG.info("Performance Summary\n{}", outputByteStream.toString("UTF8"));
	        outputByteStream.reset();
	    }
	    catch (final Exception e) {
	        LOG.error("Error when running warmup code...", e);	        
	    }
	    finally {
            setLogLevel(LogManager.getLogger(SpeedArbHybridStrategy.class).getName(), strategyLogLevel);
            setLogLevel(LogManager.getLogger(BucketPricer.class).getName(), bucketPricerLevel);
            setLogLevel(LogManager.getLogger(LunarStrategyInfoSender.class).getName(), infoSenderLevel);
            LOG.info("Finished warmup...");
	    }
	}
	
    private void doMeasurePerformance(final boolean isWarm, final PrintStream outputStream) throws Exception {
        final Histogram histogram = new Histogram(MAX_DELAY, 1);

        for (int k = 0; k < PERFORMANCE_COUNTS; k ++) {
            final long startTime = systemClock.timestamp();

            testMarketDataStatesCall();
            testNoBucketNoBuyCall();
            testNoBucketVelocityCall();
            testNoBucketMMVeryTightStopLossCall();
            testNoBucketMMVeryTightProfitRun();
            testNoBucketMMVeryTightStopProfit();
            testSellOnTurnoverMakingImmediateSell();
            testSellOnTurnoverMakingDelayedSell();
            testOrderSizeNormalVelocityCall();
            testOrderSizeVeryHighVelocityCall();
            testScenario1();
            testScenario2();
            testScenario3();
            testScenario4();
            testScenario5();
            testScenario6();
            testScenario7();
            testScenario8();

            strategyInfoSender.broadcastAllBatched();
            strategyInfoSender.broadcastAllThrottled();

            final long timeElasped = systemClock.timestamp() - startTime;
            if (timeElasped <= MAX_DELAY) {
                histogram.recordValue(systemClock.timestamp() - startTime);
            }
            else {
                LOG.warn("Warmup cycle took {}ns which is longer than the max expected {}ns...", box(timeElasped), box(MAX_DELAY));
            }
        }
        if (isWarm) {
            outputStream.println("Histogram for SpeedArb after warmup: " + histogram.getValueAtPercentile(INTERESTED_PERCENTILE));
        }
        else {
            outputStream.println("Histogram for SpeedArb before warmup: " + histogram.getValueAtPercentile(INTERESTED_PERCENTILE));
        }
        outputStream.println("=============================");
        histogram.outputPercentileDistribution(outputStream, 1, 1.0);
        outputStream.println();
    }
    
	private void doCompileWarmup() throws Exception {
		for (int k = 0; k < COMPILE_WARMUP_COUNTS; k ++) {
		    LOG.trace("Do compile warmup iteration #{}...", box(k));		    
		    testMarketDataStatesCall();
		    testNoBucketNoBuyCall();
		    testNoBucketVelocityCall();
		    testNoBucketMMVeryTightStopLossCall();
		    testNoBucketMMVeryTightProfitRun();
		    testNoBucketMMVeryTightStopProfit();
		    testSellOnTurnoverMakingImmediateSell();
		    testSellOnTurnoverMakingDelayedSell();
		    testOrderSizeNormalVelocityCall();
		    testOrderSizeVeryHighVelocityCall();
		    testScenario1();
		    testScenario2();
		    testScenario3();
		    testScenario4();
		    testScenario5();
		    testScenario6();
		    testScenario7();
		    testScenario8();
		    strategyInfoSender.broadcastAllBatched();
            strategyInfoSender.broadcastAllThrottled();
		}
	}
	
	private void doPredictiveWarmup() throws Exception {
	    // no price movements most of the time...
        callStrategy.reset();
        putStrategy.reset();
        callStrategy.switchOn();
        putStrategy.switchOn();
        
        undOrderBook.askSide().clear();
        undOrderBook.bidSide().clear();
        undOrderBook.askSide().create(spreadTable.tickToPrice(999), MM_SIZE);
        undOrderBook.bidSide().create(spreadTable.tickToPrice(998), MM_SIZE);
        
        callWrtOrderBook.askSide().clear();
        callWrtOrderBook.bidSide().clear();
        callWrtOrderBook.askSide().create(spreadTable.tickToPrice(99), MM_SIZE);
        callWrtOrderBook.bidSide().create(spreadTable.tickToPrice(98), MM_SIZE);

        putWrtOrderBook.askSide().clear();
        putWrtOrderBook.bidSide().clear();
        putWrtOrderBook.askSide().create(spreadTable.tickToPrice(99), MM_SIZE);
        putWrtOrderBook.bidSide().create(spreadTable.tickToPrice(98), MM_SIZE);        
        for (int k = 0; k < PREDICTIVE_WARMUP_COUNTS; k ++) {
            LOG.trace("Do compile predictive iteration #{}...", box(k));
        }
        strategyInfoSender.broadcastAllBatched();
        strategyInfoSender.broadcastAllThrottled();
        
	}

	@Override
	public void release() {
		// TODO Auto-generated method stub
		
	}

    private void setUnderlyingTrade(final long nanoOfDay, final int side, final int price, final double velocityRatio) throws Exception {
        final int quantity = (int)Math.floor(undParams.velocityThreshold() * velocityRatio / price);
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(side).price(price).quantity(quantity).numActualTrades(1));
    }
    
    private void setBestBidAsk(final StrategySecurity security, final SpreadTable spreadTable, final int bidPrice, final int bidSize, final int askPrice, final int askSize) {
        security.orderBook().bidSide().clear();
        security.orderBook().askSide().clear();
        if (bidPrice > 0)
            security.orderBook().bidSide().create(bidPrice, bidSize);
        if (askPrice > 0)
            security.orderBook().askSide().create(askPrice, askSize);
    }
    
    private void setBestBidAsk(final StrategySecurity security, final SpreadTable spreadTable, final int weightedSpot) throws Exception {
        security.orderBook().bidSide().clear();
        security.orderBook().askSide().clear();
        switch (weightedSpot) {
        case WA_9502399:
            security.orderBook().bidSide().create(95000, 100000);
            security.orderBook().askSide().create(95050, 108334);
            break;
        case WA_9502500:
            security.orderBook().bidSide().create(95000, 100000);
            security.orderBook().askSide().create(95050, 100000);
            break;
        case WA_9504166:
            security.orderBook().bidSide().create(95000, 500000);
            security.orderBook().askSide().create(95050, 100000);
            break; 
        case WA_9504995:
            security.orderBook().bidSide().create(95000, 499500);
            security.orderBook().askSide().create(95050, 500);
            break;
        case WA_9506666:
            security.orderBook().bidSide().create(95050, 100000);
            security.orderBook().askSide().create(95100, 200000);
            break;
        case WA_9509166:
            security.orderBook().bidSide().create(95050, 5000);
            security.orderBook().askSide().create(95100, 1000);
            break;
        case WA_9509545:
            security.orderBook().bidSide().create(95050, 10000);
            security.orderBook().askSide().create(95100, 1000);
            break;
        case WA_9506724:
            security.orderBook().bidSide().create(95050, 100000);
            security.orderBook().askSide().create(95100, 190000);
            break;
        default:
            throw new Exception("Invalid weighted average");
        }
    }
    
    private void resetStates() throws Exception {
    	defaultUndParams.copyTo((ParamsSbeEncodable)undParams);
    	defaultCallWrtParams.copyTo((ParamsSbeEncodable)callWrtParams);
    	defaultPutWrtParams.copyTo((ParamsSbeEncodable)putWrtParams);
        
    	triggerController.resetAllTriggers(callWarrant);
    	triggerController.resetAllTriggers(putWarrant);
        callStrategy.reset();
        putStrategy.reset();
        callWarrant.position(0);
        putWarrant.position(0);
        callStrategy.switchOn();
        putStrategy.switchOn();
    }
    
    public void testMarketDataStatesCall() throws Exception {
        long nanoOfDay = 1;
        resetStates();

        // Test case: test that underlying state is tight spread
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Test case: test that underlying state is wide spread
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Test case: test that underlying state is tight spread again
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Test case: test that warrant state is mm tight spread
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test case: test that warrant state is tight spread
        setBestBidAsk(callWarrant, spreadTable, 230, 30000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test case: test that warrant state is mm tight spread again
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Test case: test that warrant state is tight spread
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 30000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test case: test that warrant state is mm tight spread
        // Underlying spread: 100
        // Delta: .5f
        // Conv Ratio: 15
        // Warrant tight spread: 100 * .5 / 15 = 3.3333
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test case: test that warrant state is mm tight spread
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 233, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test case: test that warrant state is tight spread
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 234, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test case: test that warrant state is wide spread
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 235, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Test case: Delta increased
        // Underlying spread: 50100 - 50000 = 100
        // Delta: .80f
        // Conv Ratio: 15
        // Warrant tight spread: 5.3
        final Greeks greeks = callWarrant.greeks();
        greeks.delta(80000);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Test case: Delta decreased
        // Underlying spread: 50100 - 50000 = 100
        greeks.delta(50000);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);
        
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 234, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Test that "filter bid" works
        setBestBidAsk(callWarrant, spreadTable, 231, 30000, 232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        callWarrant.orderBook().bidSide().create(230, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test that "filter ask" works
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 30000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        callWarrant.orderBook().askSide().create(232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
    }    
    
    // Test simple cases where we don't have bucket info and cannot buy
    public void testNoBucketNoBuyCall() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test that nothing is done if no price is moved
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Test that nothing is done if underlying movement is enough to shift warrant price, but we have no velocity
        // Weighted Average: 100090
        // Prev Weighted Average: 100050
        // 100090-100050=40; 40 * 0.5 / 15 = 1.3; 1.3  > 231 - 230
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(underlying, spreadTable, 100000, 9000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Generate velocity
        setUnderlyingTrade(nanoOfDay, -1, 100100, 1.2);

        // Test case: test that nothing is done if underlying movement is not enough to shift warrant price and we have velocity
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(underlying, spreadTable, 100000, 3999990, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    // Test simple cases for velocity
    public void testNoBucketVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Test that nothing is done if underlying movement is enough to shift warrant price, but we have no velocity
        setBestBidAsk(underlying, spreadTable, 100000, 1000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(underlying, spreadTable, 100000, 9000000, 100100, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Generate not enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100100, 0.9);
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Generate enough velocity accumulatively
        nanoOfDay += 4_000_000;
        setUnderlyingTrade(nanoOfDay, -1, 50150, 0.2);
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Cancel buy order
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, 0, 0, OrderRequestRejectType.NULL_VAL); 
        
        // Generate not enough velocity accumulatively
        nanoOfDay += 4_000_000;
        setUnderlyingTrade(nanoOfDay, -1, 5030, 0.2);
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }

    
    // Test cases where we buy without bucket info
    public void testNoBucketMMVeryTightStopLossCall() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Update order status with position
        callWarrant.position(10000);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);

        // Stop loss not yet triggered
        nanoOfDay++;
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Stop loss triggered but has buying ban
        nanoOfDay++;
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000100);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Stop loss triggered
        nanoOfDay+=10000000000L;
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000100);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Order is updated and we have no position
        callWarrant.position(0);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestBidOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
    }

    
    public void testNoBucketMMVeryTightProfitRun() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100100, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Update order status with position
        callWarrant.position(10);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 10, OrderRequestRejectType.NULL_VAL);

        // Profit run without mm-bid so cannot revise stop loss
        setBestBidAsk(callWarrant, spreadTable, 231, 500000, 232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Profit run with mm-bid so revise stop loss to the weighted price
        setBestBidAsk(callWarrant, spreadTable, 231, 3000000, 232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Set bullish outlook and increase profit run
        // Stop loss should be revised to the weighted price - 1 bucket length, but will not because we do not revise down stop loss
        callWrtParams.marketOutlook(MarketOutlookType.BULLISH);
        setBestBidAsk(callWarrant, spreadTable, 232, 3000000, 233, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Increase spot
        // (5020 * 1000000 + 5030 * 9000000) / (1000000 + 9000000) = 5039
        setBestBidAsk(underlying, spreadTable, 100200, 9000000, 100300, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Increase profit run
        // Stop loss should be revised to the weighted price - 1 bucket length
        // Bucket length = tick size of 231 / delta = 1 / .3 = 3.3333
        // Stop loss = 5039 - 3.3333 = 5035.6666
        setBestBidAsk(callWarrant, spreadTable, 232, 3000000, 233, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Increase profit run to 20 ticks from enter bid
        setBestBidAsk(callWarrant, spreadTable, spreadTable.tickToPrice(spreadTable.priceToTick(230)+20), 3000000, spreadTable.tickToPrice(spreadTable.priceToTick(231)+20), 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Increase profit run to 20 ticks from enter ask
        // Stop loss should be revised to the weighted price - 1 bucket length
        // Bucket length = tick size of 231 / delta = 1 / .3 = 3.3333
        // Stop loss = 5039 - 3.3333 = 5035.6666
        setBestBidAsk(callWarrant, spreadTable, spreadTable.tickToPrice(spreadTable.priceToTick(231)+20), 3000000, spreadTable.tickToPrice(spreadTable.priceToTick(232)+20), 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
    }

    
    public void testNoBucketMMVeryTightStopProfit() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Update order status with position
        callWarrant.position(5000000);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 5000000, OrderRequestRejectType.NULL_VAL);

        setBestBidAsk(callWarrant, spreadTable, 232, 3000000, 233, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
    }
    
    
    public void testSellOnTurnoverMakingImmediateSell() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Update order status with position
        callWarrant.position(10);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 10, OrderRequestRejectType.NULL_VAL);
        
        // Quantity not enough for TO Making
        obUpdateHandlers.get(callWarrant.sid()).onTradeReceived(callWarrant, nanoOfDay, callWarrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(230).quantity(1_000_000).numActualTrades(1));
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000;
        obUpdateHandlers.get(callWarrant.sid()).onTradeReceived(callWarrant, nanoOfDay, callWarrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(230).quantity(1_000_000).numActualTrades(1));
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Time too far for TO Making
        nanoOfDay += 30_000_000_001L;
        obUpdateHandlers.get(callWarrant.sid()).onTradeReceived(callWarrant, nanoOfDay, callWarrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(230).quantity(1_100_000).numActualTrades(1));
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // TO Making
        nanoOfDay += 1_000_000_000;
        obUpdateHandlers.get(callWarrant.sid()).onTradeReceived(callWarrant, nanoOfDay, callWarrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(230).quantity(1_100_000).numActualTrades(1));
    }

    
    public void testSellOnTurnoverMakingDelayedSell() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Update order status with position
        callWarrant.position(10);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 10, OrderRequestRejectType.NULL_VAL);

        obUpdateHandlers.get(callWarrant.sid()).onTradeReceived(callWarrant, nanoOfDay, callWarrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(231).quantity(1_100_000).numActualTrades(1));
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // TO Making
        nanoOfDay += 1_000_000_000;
        obUpdateHandlers.get(callWarrant.sid()).onTradeReceived(callWarrant, nanoOfDay, callWarrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(231).quantity(1_100_000).numActualTrades(1));
        
        setBestBidAsk(callWarrant, spreadTable, 231, 3000000, 232, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
    }
    
    
    public void testOrderSizeNormalVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 2.0f);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testOrderSizeHighVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 2.1);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testOrderSizeVeryHighVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        resetStates();

        // Setup initial underlying price
        setBestBidAsk(underlying, spreadTable, 100100, 1000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Setup initial warrant price
        setBestBidAsk(callWarrant, spreadTable, 230, 3000000, 231, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 3.1);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(underlying, spreadTable, 100100, 9000000, 100200, 1000000);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }

    
    public void testScenario1() throws Exception {
        long nanoOfDay = 0;
        resetStates();

        undParams.velocityThreshold(100_000_000);
        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;        
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Build up velocity
        nanoOfDay += 1_000_000_000L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        callWarrant.position(1000);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);

        // Warrant up
        nanoOfDay += 1_00_000_000L - 1_000_000L;
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Stop loss trigger
        nanoOfDay += 1_00_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502399);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testScenario2() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
                
        // Build up velocity
        nanoOfDay += 1_000_000_000L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        callWarrant.position(1000);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        
        // Warrant up
        nanoOfDay += 1_00_000_000L - 1_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Stop loss trigger
        nanoOfDay += 1_00_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    //TODO
    
    //@Ignore
    public void testScenario3() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        callWrtParams.marketOutlook(MarketOutlookType.BULLISH);
        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Build up velocity
        nanoOfDay += 50_000_000L - 1L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        callWarrant.position(1000);
        callWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, callWarrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        
        // Warrant up
        nanoOfDay += 7*60*1_00_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Stop loss trigger
        nanoOfDay += 1_00_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testScenario4() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Build up velocity
        nanoOfDay += 1_000_000_000L - 1L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testScenario5() throws Exception {
        long nanoOfDay = 0;
        resetStates();

        undParams.velocityThreshold(100_000_000);
        
        final Greeks greeks = putWarrant.greeks();
        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(putWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(putWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;        
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(putWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(putWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());
        
        // Build up velocity
        nanoOfDay += 1_000_000_000L + 16000000000L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        putWarrant.position(1000);
        putWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, putWarrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
    }
    
    
    public void testScenario6() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = putWarrant.greeks();
        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(putWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(putWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(putWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());
                
        // Build up velocity
        nanoOfDay += 1_000_000_000L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        putWarrant.position(1000);
        putWarrant.orderStatusReceivedHandler().onOrderStatusReceived(nanoOfDay, putWarrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
    }
    
    
    public void testScenario7() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        putWrtParams.marketOutlook(MarketOutlookType.BULLISH);
        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = putWarrant.greeks();
        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(putWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(putWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Build up velocity
        nanoOfDay += 50_000_000L - 1L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testScenario8() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = putWarrant.greeks();
        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(putWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(putWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(putWarrant.sid()).onOrderBookUpdated(putWarrant, nanoOfDay, putWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        // Build up velocity
        nanoOfDay += 1_000_000_000L - 1L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testScenario9a() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Move warrant price down, but stock stays the same (as next bucket)
        nanoOfDay += 50_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 99, 3000000, 100, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }

    
    public void testScenario10a() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Move warrant price down, but stock stays the same (as next bucket)
        nanoOfDay += 50_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant back to lower bucket
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        // Move stock up but warrant stays the same
        nanoOfDay += 50_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 2_000_000_000L - 50_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }
    
    
    public void testScenario11() throws Exception {
        long nanoOfDay = 0; 
        resetStates();

        undParams.velocityThreshold(100_000_000);        
        final Greeks greeks = callWarrant.greeks();
        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        greeksHandlers.get(callWarrant.sid()).onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9502500);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504995);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook()); 
        
        // Move warrant price down, but stock stays the same (as next bucket)
        nanoOfDay += 50_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 101, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506666);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9509545);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
        
        // Widen warrant spread
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 102, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());

        nanoOfDay = 11*60*1_000_000_000L + 50_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9504166);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());

        nanoOfDay += 50_000_000L;
        setBestBidAsk(callWarrant, spreadTable, 100, 3000000, 101, 3000000);
        obUpdateHandlers.get(callWarrant.sid()).onOrderBookUpdated(callWarrant, nanoOfDay, callWarrant.orderBook());
        
        // Build up velocity
        nanoOfDay += 25_000_000L;
        obUpdateHandlers.get(underlying.sid()).onTradeReceived(underlying, nanoOfDay, underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));
        
        nanoOfDay += 1_000_000L;
        setBestBidAsk(underlying, spreadTable, WA_9506724);
        obUpdateHandlers.get(underlying.sid()).onOrderBookUpdated(underlying, nanoOfDay, underlying.orderBook());
    }


}
