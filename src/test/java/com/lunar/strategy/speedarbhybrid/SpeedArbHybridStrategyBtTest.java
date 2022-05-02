package com.lunar.strategy.speedarbhybrid;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.base.Strings;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketDataReplayer;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.ReplayMarketDataSource;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.io.sbe.TradeType;
import com.lunar.order.MatchingEngine;
import com.lunar.order.MatchingEngineFactory;
import com.lunar.order.Order;
import com.lunar.order.Tick;
import com.lunar.order.Trade;
import com.lunar.order.TradeUtil;
import com.lunar.position.AggregatedSecurityPosition;
import com.lunar.position.FeeAndCommissionSchedule;
import com.lunar.position.SecurityPosition;
import com.lunar.position.SecurityPositionChangeTracker;
import com.lunar.pricing.BucketPricer;
import com.lunar.pricing.Greeks;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.DeltaLimitAlertGenerator;
import com.lunar.strategy.IssuerResponseTimeGenerator;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.StrategyExplain;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

@RunWith(MockitoJUnitRunner.class)
public class SpeedArbHybridStrategyBtTest {
    //private static final LocalDate TEST_DATE = LocalDate.of(2017, 6, 12);
    private static final LocalDate TEST_DATE = LocalDate.of(2016, 6, 27);
    private static final boolean TEST_INDEX_WARRANTS = false;
    
    private static final boolean DEBUG_MODE = false; 
    // DEBUG_AT_TIME and WARRANTS_FILTER only used when DEBUG_MODE = true
    // for GS warrants, use underlying filter + issuer filter instead because of delta limit logic
    private static final long DEBUG_AT_TIME = LocalTime.of(10,42,12,91874000).toNanoOfDay(); // need to set your own breakpoint
    private static final long[] WARRANTS_FILTER = new long[] {21501};
    private static final long[] UNDERLYING_FILTER = new long[] {};
    private static final String[] ISSUER_FILTER = new String[] {};
    
    private static final String DEF_PATH = "/data/omdc/";
    private static final String DEF_FILE = TEST_INDEX_WARRANTS ? "warrants.index." + TEST_DATE.format(DateTimeFormatter.ofPattern("uuuuMMdd")) + ".csv" : "warrants." + TEST_DATE.format(DateTimeFormatter.ofPattern("uuuuMMdd")) + ".csv";
    private static final String TICKS_FILE = "ALL_ticks_%s_fast.csv%s";
    private static final String OUT_PATH = "/data/";    

    //private static final String DEF_PATH = "D:/cygwin/data/omdc";
    //private static final String OUT_PATH = "D:/cygwin/data/lunar/";
    //private static final String DEF_PATH = "C:/Users/wongca/home/dev/data/omdc";
    //private static final String OUT_PATH = "C:/Users/wongca/home/dev/data/";

    private static final long ORDER_DELAY = TEST_DATE.equals(LocalDate.of(2016, 6, 27)) ? 1000000 : 2000000;

    private static final String HSI_FUTURES_CODE = "HSIH7";
    private static final long HSI_ADJUSTMENT = 0;
    private static final String HSCEI_FUTURES_CODE = "HHIH7";
    private static final long HSCEI_ADJUSTMENT = 0;

    static {
        SpeedArbHybridStrategySignalHandler.BT_COMPARISON_MODE = true;
    	//BucketPricer.BT_COMPARISON_MODE = true;
        
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
        loggerConfig.setLevel(Level.ERROR);
        //loggerConfig = config.getLoggerConfig(IssuerResponseTimeGenerator.class.getCanonicalName());
        //loggerConfig.setLevel(Level.INFO);
        //loggerConfig = config.getLoggerConfig(DeltaLimitAlertGenerator.class.getCanonicalName());
        //loggerConfig.setLevel(Level.DEBUG);

        ctx.updateLoggers();
    }
    
    //TODO for wongca, comment out Ignore
    @Ignore
    @Test   
    public void run() throws Exception {
        final File ordersFile = new File(OUT_PATH, "orders.csv");
        final File tradesFile = new File(OUT_PATH, "trades.csv");
        final File issuerPnlFile = new File(OUT_PATH, "issuerPnl.csv");
        final File underlyingPnlFile = new File(OUT_PATH, "underlyingPnl.csv");
        final File warrantPnlFile = new File(OUT_PATH, "warrantPnl.csv");
        final File totalPnlFile = new File(OUT_PATH, "totalPnl.csv");        
        
        try (final BufferedWriter ordersWriter = new BufferedWriter(new FileWriter(ordersFile))) {
            try (final BufferedWriter tradesWriter = new BufferedWriter(new FileWriter(tradesFile))) {
        
                final UserControlledSystemClock systemClock = new UserControlledSystemClock(TEST_DATE);
                final SpeedArbHybridTestHelper testHelper = new SpeedArbHybridTestHelper(10000, 200, 100); 
                
                final Collection<Security> securityList = testHelper.securityList();
                final LongEntityManager<StrategySecurity> securities = testHelper.securities();
                final MarketTicksStrategyScheduler scheduler = testHelper.scheduler();
                final SpeedArbBtParams btParams = testHelper.btParams();
                
                final AtomicBoolean isInMarketHours = new AtomicBoolean(false);
                final AtomicInteger tradeSid = new AtomicInteger(1);                
                
                final FeeAndCommissionSchedule warrantFees = FeeAndCommissionSchedule.inBps(0.27, 0, 0.5, 0.5, 0.2, 2, 100, 1.5);
                final AggregatedSecurityPosition totalPosition = AggregatedSecurityPosition.of(0, EntityType.FIRM); 
                final Long2ObjectOpenHashMap<AggregatedSecurityPosition> issuerPositions = new Long2ObjectOpenHashMap<AggregatedSecurityPosition>();
                final Long2ObjectOpenHashMap<AggregatedSecurityPosition> undPositions = new Long2ObjectOpenHashMap<AggregatedSecurityPosition>();
                final Long2ObjectOpenHashMap<AggregatedSecurityPosition> wrtPositions = new Long2ObjectOpenHashMap<AggregatedSecurityPosition>();
                final Long2ObjectOpenHashMap<SecurityPosition> secPositions = new Long2ObjectOpenHashMap<SecurityPosition>();
                final Long2ObjectOpenHashMap<SecurityPositionChangeTracker> changeTrackers = new Long2ObjectOpenHashMap<SecurityPositionChangeTracker>();

                final TradeSbeEncoder tradeEncoder = new TradeSbeEncoder();
                final TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
                final MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
                final BoobsSbeEncoder boobsEncoder = new BoobsSbeEncoder();
                final BoobsSbeDecoder boobsDecoder = new BoobsSbeDecoder();
        
                final MatchingEngine matchingEngine = MatchingEngineFactory.createReplayMatchingEngine(new MatchingEngine.MatchedHandler() {
                    @Override
                    public void onTrade(final long timestamp, final Order order, final int price, final int quantity) {
                        try {
                            final LocalTime time = LocalTime.ofNanoOfDay(timestamp);
                            final StrategySecurity security = securities.get(order.secSid());
                            System.out.println("[" + time + "] Trade: " + order.side() + " " + order.secSid() + " price: " + price + ", quantity: " + quantity);
                            final Trade trade = Trade.of(tradeSid.getAndIncrement(), order.sid(), order.orderId(), security.sid(), order.side(), 0, quantity, Strings.padEnd(tradeSid.toString(), TradeSbeEncoder.executionIdLength(), ' '), price, quantity, order.status(), TradeStatus.NEW, systemClock.nanoOfDay(), systemClock.nanoOfDay());
                            trade.channelSeq(tradeSid.get());
                            TradeUtil.populateFrom(tradeEncoder, buffer, 0, trade, tradeDecoder, stringBuffer);
                            secPositions.get(security.sid()).handleTrade(tradeDecoder, changeTrackers.get(security.sid()));
                            if (order.side() == Side.BUY) {
                                security.updatePosition(quantity);                                
                            }
                            else {
                                security.updatePendingSell(-quantity);
                                security.updatePosition(-quantity);
                            }
                            tradesWriter.write(order.secSid() + "," + price + ".0," + quantity + ".0," + (order.side().equals(Side.BUY) ? "Buy" : "Sell") + "," + systemClock.date() + " " + time + "\n");
                            if (order.cumulativeExecQty() == order.quantity()) {
                                security.orderStatusReceivedHandler().onOrderStatusReceived(timestamp, price, quantity, OrderRequestRejectType.NULL_VAL);
                            }
                        }
                        catch (final Exception e) {
                        }
                    }
                    
                    @Override
                    public void onOrderExpired(final long timestamp, final Order order) {
                        try {
                            final StrategySecurity security = securities.get(order.secSid());
                            if (order.side() == Side.SELL) {
                                security.updatePendingSell(-order.leavesQty());
                            }
                            security.orderStatusReceivedHandler().onOrderStatusReceived(timestamp, 0, 0, order.cumulativeExecQty() == 0 ? OrderRequestRejectType.INVALID_PRICE : OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                        }
                    }
                }, systemClock, ORDER_DELAY, false);
                
                final StrategyOrderService orderService = new StrategyOrderService() {            
                    @Override
                    public void buy(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                        try {
                            System.out.println("[" + systemClock.time() + "] Order: Buy " + security.code() + " price: " + price + ", quantity: " + quantity);
                            explain.logExplainForBuyOrder();                    
                            try {
                                ordersWriter.write(security.sid() + "," + price + ".0," + quantity + ".0," + "Buy" + "," + systemClock.date() + " " + systemClock.time() + "\n");
                            } catch (final IOException e) {
                            }
                            matchingEngine.addOrder(security.sid(), Order.of(security.sid(), null, 0, (int)quantity, BooleanType.TRUE, OrderType.LIMIT_ORDER, Side.BUY, price, price, TimeInForce.FILL_AND_KILL, OrderStatus.NEW, systemClock.nanoOfDay() + ORDER_DELAY, systemClock.nanoOfDay() + ORDER_DELAY));
                        }
                        catch (final Exception e) {                    
                        }
                    }
        
                    @Override
                    public void sell(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                        try {
                            System.out.println("[" + systemClock.time() + "] Order: Sell " + security.code() + " price: " + price + ", quantity: " + quantity);
                            security.updatePendingSell(quantity);
                            explain.logExplainForSellOrder();
                            try {
                                ordersWriter.write(security.sid() + "," + price + ".0," + quantity + ".0," + "Sell" + "," + systemClock.date() + " " + systemClock.time() + "\n");
                            } catch (final IOException e) {
                            }
                            matchingEngine.addOrder(security.sid(), Order.of(security.sid(), null, 0, (int)quantity, BooleanType.TRUE, OrderType.LIMIT_ORDER, Side.SELL, price, price, TimeInForce.FILL_AND_KILL, OrderStatus.NEW, systemClock.nanoOfDay() + ORDER_DELAY, systemClock.nanoOfDay() + ORDER_DELAY));
                        }
                        catch (final Exception e) {                    
                        }
                    }
        
                    @Override
                    public void sellToExit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                        try {
                            System.out.println("[" + systemClock.time() + "] Order: Sell " + security.code() + " price: " + price + ", quantity: " + quantity);
                            security.updatePendingSell(quantity);
                            explain.logExplainForSellOrder();
                            try {
                                ordersWriter.write(security.sid() + "," + price + ".0," + quantity + ".0," + "Sell" + "," + systemClock.date() + " " + systemClock.time() + "\n");
                            } catch (final IOException e) {
                            }
                            matchingEngine.addOrder(security.sid(), Order.of(security.sid(), null, 0, (int)quantity, BooleanType.TRUE, OrderType.LIMIT_ORDER, Side.SELL, price, price, TimeInForce.FILL_AND_KILL, OrderStatus.NEW, systemClock.nanoOfDay() + ORDER_DELAY, systemClock.nanoOfDay() + ORDER_DELAY));
                        }
                        catch (final Exception e) {                    
                        }
                    }
                    
                    @Override
                    public boolean canTrade() {
                        return isInMarketHours.get();
                    }
        
                    @Override
                    public void sellLimit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                        
                    }
        
                    @Override
                    public void cancelAndSellOutstandingSell(StrategySecurity security, int price, StrategyExplain explain) {
                        
                    }
                    
                };
                
                testHelper.setupStrategyManager(orderService);
                @SuppressWarnings("unused")
                Set<Long> warrantsFilter = (DEBUG_MODE == false || WARRANTS_FILTER == null || WARRANTS_FILTER.length == 0) ? null : new HashSet<Long>(Arrays.stream(WARRANTS_FILTER).boxed().collect(Collectors.toList()));
                Set<Long> underlyingFilter = (DEBUG_MODE == false || UNDERLYING_FILTER == null || UNDERLYING_FILTER.length == 0) ? null : new HashSet<Long>(Arrays.stream(UNDERLYING_FILTER).boxed().collect(Collectors.toList())); 
                Set<String> issuerFilter = (DEBUG_MODE == false || ISSUER_FILTER == null || ISSUER_FILTER.length == 0) ? null : new HashSet<String>(Arrays.stream(ISSUER_FILTER).collect(Collectors.toList())); 

                testHelper.readInstruments(DEF_PATH, DEF_FILE, warrantsFilter, underlyingFilter, issuerFilter);
                if (TEST_INDEX_WARRANTS) {
                    testHelper.registerFutures(HSI_FUTURES_CODE, HSCEI_FUTURES_CODE);
                }

                testHelper.createStrategies();
                
                final ReplayMarketDataSource mdSource = ReplayMarketDataSource.instanceOf();      
                mdSource.initialize(securityList);
                mdSource.setSystemClock(systemClock);
                mdSource.registerCallbackHandler(new ReplayMarketDataSource.MarketDataReplayerCallbackHandler() {
                    @Override
                    public int onMarketData(long instrumentSid, MarketOrderBook ob) {
                        try {
                            scheduler.handleTimestamp(ob.transactNanoOfDay());
                            return onMarketDataThrowable(instrumentSid, ob);
                        }
                        catch (final Exception e) {
                            
                        }
                        return 0;
                    }
                    
                    private int onMarketDataThrowable(long instrumentSid, MarketOrderBook ob) throws Exception { 
                        final StrategySecurity security = securities.get(instrumentSid);
                        if (security == null)
                            return 0;
                        
                        final MarketDataUpdateHandler mdHandler = security.marketDataUpdateHandler();
                        if (mdHandler == null)
                            return 0;
        
                        final long nanoOfDay = ob.transactNanoOfDay();
                        
                        final boolean isWarrant = security.securityType().equals(SecurityType.WARRANT);
                        final MarketOrderBook orderBook = security.orderBook();
                        final int prevBidPrice = orderBook.bestBidOrNullIfEmpty() == null ? 0 : orderBook.bestBidOrNullIfEmpty().price();
                        final int prevAskPrice = orderBook.bestAskOrNullIfEmpty() == null ? 0 : orderBook.bestAskOrNullIfEmpty().price();
                        final long prevBidSize = orderBook.bestBidOrNullIfEmpty() == null ? 0 : orderBook.bestBidOrNullIfEmpty().qty();
                        final long prevAskSize = orderBook.bestAskOrNullIfEmpty() == null ? 0 : orderBook.bestAskOrNullIfEmpty().qty();
                        
                        orderBook.triggerInfo().triggerSeqNum(ob.triggerInfo().triggerSeqNum());
                        orderBook.triggerInfo().triggerNanoOfDay(ob.triggerInfo().triggerNanoOfDay());
                        orderBook.transactNanoOfDay(ob.transactNanoOfDay());
                        orderBook.bidSide().clear();
                        orderBook.askSide().clear();
                        final Iterator<Tick> bidIterator = ob.bidSide().localPriceLevelsIterator();
                        while (bidIterator.hasNext()) {
                            final Tick tick = bidIterator.next();
                            orderBook.bidSide().create(tick.price(), tick.qty());
                        }
                        final Iterator<Tick> askIterator = ob.askSide().localPriceLevelsIterator();
                        while (askIterator.hasNext()) {
                            final Tick tick = askIterator.next();
                            orderBook.askSide().create(tick.price(), tick.qty());
                        }

                        final int bidPrice = orderBook.bestBidOrNullIfEmpty() == null ? 0 : orderBook.bestBidOrNullIfEmpty().price();
                        final int askPrice = orderBook.bestAskOrNullIfEmpty() == null ? 0 : orderBook.bestAskOrNullIfEmpty().price();
                        final long bidSize = orderBook.bestBidOrNullIfEmpty() == null ? 0 : orderBook.bestBidOrNullIfEmpty().qty();
                        final long askSize = orderBook.bestAskOrNullIfEmpty() == null ? 0 : orderBook.bestAskOrNullIfEmpty().qty();
                        if (isWarrant) {
                            boobsEncoder.wrap(buffer, 0).secSid(security.sid()).ask(askPrice).bid(askPrice);
                            boobsDecoder.wrap(buffer, 0, BoobsSbeDecoder.BLOCK_LENGTH, BoobsSbeDecoder.SCHEMA_VERSION);
                            secPositions.get(security.sid()).handleBoobs(boobsDecoder, changeTrackers.get(security.sid()));
                        }
                        else {
                            if (bidPrice == prevBidPrice && bidSize == prevBidSize && askPrice == prevAskPrice && askSize == prevAskSize) {
                                return 0;
                            }                   
                        }
                        if (DEBUG_MODE) {
                            //TODO set break point here
                            if (nanoOfDay == DEBUG_AT_TIME) {
                                int a = 0;
                                a = 5;
                            }
                            System.out.println("Processing MarketData " + LocalTime.ofNanoOfDay(nanoOfDay));                            
                        }
                        mdHandler.onOrderBookUpdated(security, ob.transactNanoOfDay(), ob);
                       
                        // Tweaks to match BT
                        //if (nanoOfDay == LocalTime.of(10, 12, 16, 721809000).toNanoOfDay()) {
                        //    testHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(28568, false).userIssuerMaxLag(24950000);
                        //}
                        //if (nanoOfDay == LocalTime.of(13, 32, 44, 712684000).toNanoOfDay()) {
                        //    testHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(27971, false).userIssuerMaxLag(10000000);
                        //}
                        
                        //Trying to test out an actual scenario with user interaction
                        //if (nanoOfDay == LocalTime.of(10, 44, 14, 845460000).toNanoOfDay()) {
                        //    testHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(14040, false).userBaseOrderSize(20000);
                        //}
                        //if (nanoOfDay == LocalTime.of(10, 50, 6, 501551000).toNanoOfDay()) {
                        //    securities.get(14040).activeStrategy().switchOff(StrategyExitMode.FORCE_NO_EXIT);
                        //}
                        //if (nanoOfDay == LocalTime.of(10, 57, 31, 845754000).toNanoOfDay()) {
                        //    testHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(14040, false).userStopLoss(10000000);
                        //}
                        //if (nanoOfDay == LocalTime.of(10, 57, 41, 171005000).toNanoOfDay()) {
                        //    testHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(14040, false).userStopLoss(10010000);
                        //}
                        //if (nanoOfDay == LocalTime.of(10, 57, 46, 823287000).toNanoOfDay()) {
                        //    securities.get(14040).activeStrategy().switchOn();
                        //}
                        //if (nanoOfDay == LocalTime.of(10, 57, 52, 629324000).toNanoOfDay()) {
                        //    securities.get(14040).activeStrategy().switchOff(StrategyExitMode.STOPLOSS_EXIT);
                        //}
                        
                        return 0;
                    }
        
                    @Override
                    public int onTrade(long instrumentSid, MarketTrade trade) {
                        try {
                            scheduler.handleTimestamp(trade.tradeNanoOfDay());
                            return onTradeThrowable(instrumentSid, trade);
                        }
                        catch (final Exception e) {
                            
                        }
                        return 0;
                    }
                    
                    private int onTradeThrowable(long instrumentSid, MarketTrade trade) throws Exception {
                        if (trade.tradeType() != TradeType.AUTOMATCH_NORMAL)
                            return 0;
        
                        final StrategySecurity security = securities.get(instrumentSid);
                        if (security == null)
                            return 0;
                        
                        security.lastMarketTrade().copyFrom(trade);
                        security.lastMarketTrade().detectSide(security.orderBook());                        
                        if (DEBUG_MODE) {
                            //TODO set break point here
                            if (trade.tradeNanoOfDay() == DEBUG_AT_TIME) {
                                int a = 0;
                                a = 5;
                            }
                            System.out.println("Processing Trade " + LocalTime.ofNanoOfDay(trade.tradeNanoOfDay()));                            
                        }                        
                        security.marketDataUpdateHandler().onTradeReceived(security, security.lastMarketTrade().tradeNanoOfDay(), security.lastMarketTrade());
                        return 0;
                    }
        
                    @Override
                    public int onTradingPhase(MarketStatusType marketStatus) {
                        isInMarketHours.set(marketStatus == MarketStatusType.CT);
                        return 0;
                    }
                    
                });
        
                for (final Issuer issuer : testHelper.strategyManager.issuers().entities()) {
                    issuerPositions.put(issuer.sid(), AggregatedSecurityPosition.of(issuer.sid(), EntityType.ISSUER));
                }
                for (final StrategySecurity underlying : testHelper.strategyManager.underlyings().values()) {
                    undPositions.put(underlying.sid(), AggregatedSecurityPosition.of(underlying.sid(), EntityType.UNDERLYING)); 
                }
        
                // update greeks
                for (final StrategySecurity warrant : testHelper.strategyManager().warrants().values()) {
                    wrtPositions.put(warrant.sid(), AggregatedSecurityPosition.of(warrant.sid(), EntityType.SECURITY));
                    final SecurityPosition secPosition = SecurityPosition.of(warrant.sid(), SecurityType.WARRANT, warrant.putOrCall(), warrant.underlyingSid(), warrant.issuerSid(), warrantFees);
                    secPosition.addHandler(totalPosition);
                    secPosition.addHandler(issuerPositions.get(warrant.issuerSid()));
                    secPosition.addHandler(undPositions.get(warrant.underlyingSid()));
                    secPosition.addHandler(wrtPositions.get(warrant.sid()));
                    secPositions.put(warrant.sid(), secPosition);
                    changeTrackers.put(warrant.sid(), SecurityPositionChangeTracker.of());
                    
                    warrant.greeks().gamma(0);
                    warrant.greeks().refSpot(0);
                    warrant.greeks().delta((int)(btParams.getDelta(warrant.sid())));
                    
                    //Testing out actual scenario for a particular warrant...
                    //if (warrant.sid() == 14040) {
                    //    greeks.delta(13834);
                    //    greeks.refSpot(9965);
                    //    greeks.gamma(6616);       
                    //    testHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(warrant.sid(), false).issuerMaxLagCap(500_000_000L);
                    //}
                    warrant.greeksUpdateHandler().onGreeksUpdated(warrant.greeks());            
                }
                final Path ticksPath;
                if (DEBUG_MODE == false || WARRANTS_FILTER == null || WARRANTS_FILTER.length != 1) {
                    ticksPath = FileSystems.getDefault().getPath(DEF_PATH, String.format(TICKS_FILE, TEST_DATE.format(DateTimeFormatter.ofPattern("yyyyMMdd")), ""));
                }
                else {
                    ticksPath = FileSystems.getDefault().getPath(DEF_PATH, String.format(TICKS_FILE, TEST_DATE.format(DateTimeFormatter.ofPattern("yyyyMMdd")), "." + String.valueOf(WARRANTS_FILTER[0]))); 
                }
                MarketDataReplayer.instanceOf().startFeed(true, 0, Optional.empty(), systemClock, true, ticksPath);
                ordersWriter.flush();
                tradesWriter.flush();
                
                savePnl(testHelper, issuerPnlFile, issuerPositions.values());
                savePnl(testHelper, underlyingPnlFile, undPositions.values());
                savePnl(testHelper, warrantPnlFile, wrtPositions.values());
                savePnl(testHelper, totalPnlFile, Arrays.asList(totalPosition));
            }
        }
    }
    
    private void savePnl(final SpeedArbHybridTestHelper testHelper, final File pnlFile, final Collection<AggregatedSecurityPosition> positions) throws IOException {
        try (final BufferedWriter pnlWriter = new BufferedWriter(new FileWriter(pnlFile))) {
            pnlWriter.write("id" + "," + "buyQty" + "," + "sellQty" + "," + "tradeCount" + "," + "netRealizedPnl" + "," + "unrealizedPnl" + "," + "totalPnl" + "\n");
            for (final AggregatedSecurityPosition position : positions) {
                final String id;
                switch (position.entityType()) {
                case FIRM:
                    id = "Coda";
                    break;
                case ISSUER:
                    id = testHelper.issuers().get(position.entitySid()).code();
                    break;
                case UNDERLYING:
                    id = testHelper.securities().get(position.entitySid()).code();
                    break;
                case SECURITY:
                    id = testHelper.securities().get(position.entitySid()).code();
                    break;
                default:
                    id = String.valueOf(position.entitySid());             
                }
                pnlWriter.write(id + "," + position.details().buyQty() + "," + position.details().sellQty() + "," + position.details().tradeCount() + "," + position.details().experimentalNetRealizedPnl() + "," + position.details().experimentalUnrealizedPnl() + "," + position.details().experimentalTotalPnl() + "\n");                
            }
        }
    }

}

