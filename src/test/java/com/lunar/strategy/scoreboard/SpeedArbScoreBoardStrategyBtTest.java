package com.lunar.strategy.scoreboard;

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
import com.lunar.entity.StrategyType;
import com.lunar.marketdata.MarketDataReplayer;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.ReplayMarketDataSource;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.io.sbe.TradeType;
import com.lunar.message.sink.MessageSinkRef;
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
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.StrategyExplain;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategySwitch;
import com.lunar.strategy.parameters.ParamsSbeEncodable;
import com.lunar.strategy.scoreboard.StrategySwitchController.SwitchControlHandler;
import com.lunar.strategy.speedarbhybrid.SpeedArbBtParams;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridTestHelper;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

@RunWith(MockitoJUnitRunner.class)
public class SpeedArbScoreBoardStrategyBtTest {
    private static final LocalDate TEST_DATE = LocalDate.of(2017, 4, 24);
    //private static final LocalDate TEST_DATE = LocalDate.of(2017, 1, 18);
    
    private static final boolean DEBUG_MODE = false;
    // DEBUG_AT_TIME and WARRANTS_FILTER only used when DEBUG_MODE = true
    private static final long DEBUG_AT_TIME = LocalTime.of(10,16,10,669622000).toNanoOfDay(); // need to set your own breakpoint    
    private static final long[] WARRANTS_FILTER = new long[] {28780};
    private static final long[] UNDERLYING_FILTER = new long[] {388};
    private static final String[] ISSUER_FILTER = new String[] {"GS"};

    private static final String DEF_PATH = "/data/omdc/";
    private static final String DEF_FILE = "warrants.%s.csv";
    private static final String TICKS_FILE = "ALL_ticks_%s_fast.csv%s";
    private static final String OUT_PATH = "/data/";
    //private static final String OUT_PATH = "D:/cygwin/data/lunar/";

    private static final long ORDER_DELAY = 1000000;

    static {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
        loggerConfig.setLevel(Level.ERROR);
        //LoggerConfig loggerConfig = config.getLoggerConfig(BucketPricer.class.getCanonicalName()); 
        //loggerConfig.setLevel(Level.ERROR);
        //loggerConfig = config.getLoggerConfig(CallPricerValidator.class.getCanonicalName()); 
        //loggerConfig.setLevel(Level.ERROR);
        //loggerConfig = config.getLoggerConfig(PutPricerValidator.class.getCanonicalName()); 
        //loggerConfig.setLevel(Level.ERROR);
        //loggerConfig = config.getLoggerConfig(SpeedArbHybridWrtSignalGenerator.class.getCanonicalName());
        //loggerConfig.setLevel(Level.ERROR);
        //loggerConfig = config.getLoggerConfig(StrategySwitchController.class.getCanonicalName());
        //loggerConfig.setLevel(Level.TRACE);
        
        ctx.updateLoggers();
    }
    
    //TODO for wongca, comment out Ignore
    @Ignore
    @Test   
    public void run() throws Exception {
        run(TEST_DATE);
    }
    
    private void run(final LocalDate testDate) throws Exception {
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd");
        final File ordersFile = new File(OUT_PATH, "orders." + testDate.format(dateTimeFormatter) + ".csv");
        final File tradesFile = new File(OUT_PATH, "trades." + testDate.format(dateTimeFormatter) + ".csv");
        final File issuerPnlFile = new File(OUT_PATH, "issuerPnl." + testDate.format(dateTimeFormatter) + ".csv");
        final File underlyingPnlFile = new File(OUT_PATH, "underlyingPnl." + testDate.format(dateTimeFormatter) + ".csv");
        final File warrantPnlFile = new File(OUT_PATH, "warrantPnl." + testDate.format(dateTimeFormatter) + ".csv");
        final File totalPnlFile = new File(OUT_PATH, "totalPnl." + testDate.format(dateTimeFormatter) + ".csv");        
        
        try (final BufferedWriter ordersWriter = new BufferedWriter(new FileWriter(ordersFile))) {
            try (final BufferedWriter tradesWriter = new BufferedWriter(new FileWriter(tradesFile))) {
        
                final UserControlledSystemClock systemClock = new UserControlledSystemClock(testDate);
                final SpeedArbHybridTestHelper strategyTestHelper = new SpeedArbHybridTestHelper(10000, 200, 100); 
                final ScoreBoardTestHelper scoreBoardTestHelper = new ScoreBoardTestHelper(10000, 200, 100);
               
                final Collection<Security> securityList = strategyTestHelper.securityList();
                final LongEntityManager<StrategySecurity> strategySecurities = strategyTestHelper.securities();
                final LongEntityManager<StrategySecurity> scoreBoardSecurities = scoreBoardTestHelper.securities();
                final MarketTicksStrategyScheduler strategyScheduler = strategyTestHelper.scheduler();
                final MarketTicksStrategyScheduler scoreBoardScheduler = scoreBoardTestHelper.scheduler();
                final SpeedArbBtParams btParams = strategyTestHelper.btParams();
                
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
                            final StrategySecurity security = strategySecurities.get(order.secSid());
                            System.out.println("[" + time + "] Trade: " + order.side() + " " + order.secSid() + " price: " + price + ", quantity: " + quantity);
                            final Trade trade = Trade.of(tradeSid.getAndIncrement(), order.sid(), order.orderId(), security.sid(), order.side(), 0, quantity, Strings.padEnd(tradeSid.toString(), TradeSbeEncoder.executionIdLength(), ' '), price, quantity, order.status(), TradeStatus.NEW, systemClock.nanoOfDay(), systemClock.nanoOfDay());
                            trade.channelSeq(tradeSid.get());
                            TradeUtil.populateFrom(tradeEncoder, buffer, 0, trade, tradeDecoder, stringBuffer);
                            secPositions.get(security.sid()).handleTrade(tradeDecoder, changeTrackers.get(security.sid()));
                            final ScoreBoardSecurityInfo scoreBoardSecurity = (ScoreBoardSecurityInfo)scoreBoardSecurities.get(order.secSid());
                            if (order.side() == Side.BUY) {
                                security.updatePosition(quantity);
                                scoreBoardSecurity.updateRealPosition(quantity);
                            }
                            else {
                                security.updatePosition(-quantity);
                                scoreBoardSecurity.updateRealPosition(-quantity);
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
                            final StrategySecurity security = strategySecurities.get(order.secSid());
                            security.orderStatusReceivedHandler().onOrderStatusReceived(timestamp, 0, 0, OrderRequestRejectType.NULL_VAL);
                        }
                        catch (final Exception e) {
                        }
                    }
                }, systemClock, ORDER_DELAY, false);
                
                final StrategyOrderService strategyOrderService = new StrategyOrderService() {            
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
                final ScoreBoardOrderService scoreBoardOrderService = new ScoreBoardOrderService(systemClock);
                
                strategyTestHelper.setupStrategyManager(strategyOrderService, new StrategyInfoSender() {
                    @Override
                    public void sendStrategyType(StrategyType strategyType, MessageSinkRef sink) {
                    }

                    @Override
                    public void sendStrategyType(StrategyType strategyType, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
                    }

                    @Override
                    public void broadcastStrategyType(StrategyType strategyType) {
                    }

                    @Override
                    public void sendSwitch(StrategySwitch strategySwitch, MessageSinkRef sink) {
                        if (strategySwitch.switchType() == StrategySwitchType.STRATEGY_DAY_ONLY && strategySwitch.source() == StrategyParamSource.SECURITY_SID) {
                            scoreBoardTestHelper.scoreBoardManager().updateStrategySwitchState(strategySwitch.sourceSid(), strategySwitch.onOff());
                        }
                    }

                    @Override
                    public void sendSwitch(StrategySwitch strategySwitch, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
                        if (strategySwitch.switchType() == StrategySwitchType.STRATEGY_DAY_ONLY && strategySwitch.source() == StrategyParamSource.SECURITY_SID) {
                            scoreBoardTestHelper.scoreBoardManager().updateStrategySwitchState(strategySwitch.sourceSid(), strategySwitch.onOff());
                        }
                    }

                    @Override
                    public void broadcastSwitch(StrategySwitch strategySwitch) {
                        if (strategySwitch.switchType() == StrategySwitchType.STRATEGY_DAY_ONLY && strategySwitch.source() == StrategyParamSource.SECURITY_SID) {
                            scoreBoardTestHelper.scoreBoardManager().updateStrategySwitchState(strategySwitch.sourceSid(), strategySwitch.onOff());
                        }
                    }

                    @Override
                    public void sendStrategyParams(ParamsSbeEncodable params, MessageSinkRef sink) {
                    }

                    @Override
                    public void sendStrategyParams(ParamsSbeEncodable params, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
                    }

                    @Override
                    public void broadcastStrategyParams(ParamsSbeEncodable params) {
                    }

                    @Override
                    public void broadcastStrategyParamsNoPersist(ParamsSbeEncodable params) {
                    }

                    @Override
                    public boolean broadcastStrategyParamsBatched(ParamsSbeEncodable params) {
                        return false;
                    }

                    @Override
                    public boolean broadcastStrategyParamsNoPersistBatched(ParamsSbeEncodable params) {
                        return false;
                    }

                    @Override
                    public boolean broadcastStrategyParamsNoPersistThrottled(ParamsSbeEncodable params) {
                        return false;
                    }

                    @Override
                    public boolean sendEventBatched(StrategyType strategyType, Security warrant, EventType eventType, long nanoOfDay, EventValueType valueType, long longValue) {
                        return false;
                    }

                    @Override
                    public int broadcastAllBatched() {
                        return 0;
                    }

                    @Override
                    public int broadcastAllThrottled() {
                        return 0;
                    }
                    
                });
                scoreBoardTestHelper.setupStrategyManager(scoreBoardOrderService, new SwitchControlHandler() {
                    @Override
                    public void switchOnStrategy(long securitySid) {
                        try {
                            strategyTestHelper.strategyManager().flipDayOnlySwitchForSecurity(securitySid, BooleanType.TRUE, StrategyExitMode.NULL_VAL, true);
                        }
                        catch (final Exception e) {
                            
                        }
                    }

                    @Override
                    public void switchOffStrategy(long securitySid) {
                        try {
                            strategyTestHelper.strategyManager().flipDayOnlySwitchForSecurity(securitySid, BooleanType.FALSE, StrategyExitMode.SCOREBOARD_EXIT, true);
                        }
                        catch (final Exception e) {
                            
                        }
                    }

                    @Override
                    public void onErrorControlling(long securitySid) {
                    }
                });
                
                @SuppressWarnings("unused")
                Set<Long> warrantsFilter = (DEBUG_MODE == false || WARRANTS_FILTER == null || WARRANTS_FILTER.length == 0) ? null : new HashSet<Long>(Arrays.stream(WARRANTS_FILTER).boxed().collect(Collectors.toList()));
                Set<Long> underlyingFilter = (DEBUG_MODE == false || UNDERLYING_FILTER == null || UNDERLYING_FILTER.length == 0) ? null : new HashSet<Long>(Arrays.stream(UNDERLYING_FILTER).boxed().collect(Collectors.toList())); 
                Set<String> issuerFilter = (DEBUG_MODE == false || ISSUER_FILTER == null || ISSUER_FILTER.length == 0) ? null : new HashSet<String>(Arrays.stream(ISSUER_FILTER).collect(Collectors.toList())); 

                final String defFile = String.format(DEF_FILE, testDate.format(DateTimeFormatter.ofPattern("uuuuMMdd")));
                strategyTestHelper.readInstruments(DEF_PATH, defFile, warrantsFilter, underlyingFilter, issuerFilter);
                strategyTestHelper.createStrategies();
                scoreBoardTestHelper.readInstruments(DEF_PATH, defFile, warrantsFilter, underlyingFilter, issuerFilter);
                scoreBoardTestHelper.createStrategies();
                for (final Issuer issuer : scoreBoardTestHelper.issuers().entities()) {
                    //if (issuer.code().equals("HT")) {
                        scoreBoardTestHelper.scoreBoardManager().flipSwitchForIssuer(issuer.sid(), BooleanType.TRUE,  false);
                    //}
                }
                
                final ReplayMarketDataSource mdSource = ReplayMarketDataSource.instanceOf();      
                mdSource.initialize(securityList);
                mdSource.setSystemClock(systemClock);
                mdSource.registerCallbackHandler(new ReplayMarketDataSource.MarketDataReplayerCallbackHandler() {
                    @Override
                    public int onMarketData(long instrumentSid, MarketOrderBook ob) {
                        try {
                            strategyScheduler.handleTimestamp(ob.transactNanoOfDay());
                            scoreBoardScheduler.handleTimestamp(ob.transactNanoOfDay());
                            onMarketDataThrowable(strategySecurities.get(instrumentSid), ob);
                            onMarketDataThrowable(scoreBoardSecurities.get(instrumentSid), ob);
                        }
                        catch (final Exception e) {
                            
                        }
                        return 0;
                    }
                    
                    private int onMarketDataThrowable(final StrategySecurity security, final MarketOrderBook ob) throws Exception {                        
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
                            System.out.println("Processing " + LocalTime.ofNanoOfDay(nanoOfDay));                            
                        }
                        //if (isWarrant && security instanceof ScoreBoardSecurityInfo) {
                        //    strategyTestHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(security.sid(), false).userMmBidSize((int)((ScoreBoardSecurityInfo)security).marketContext().mmBidSizeThreshold());
                        //    strategyTestHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID).getStrategyWrtParams(security.sid(), false).userMmAskSize((int)((ScoreBoardSecurityInfo)security).marketContext().mmAskSizeThreshold());
                        //}
                        mdHandler.onOrderBookUpdated(security, ob.transactNanoOfDay(), ob);
                        return 0;
                    }
        
                    @Override
                    public int onTrade(long instrumentSid, MarketTrade trade) {
                        try {
                            strategyScheduler.handleTimestamp(trade.tradeNanoOfDay());
                            scoreBoardScheduler.handleTimestamp(trade.tradeNanoOfDay());
                            onTradeThrowable(strategySecurities.get(instrumentSid), trade);                            
                            onTradeThrowable(scoreBoardSecurities.get(instrumentSid), trade);
                        }
                        catch (final Exception e) {
                            
                        }
                        return 0;
                    }
                    
                    private int onTradeThrowable(final StrategySecurity security, final MarketTrade trade) throws Exception {
                        if (trade.tradeType() != TradeType.AUTOMATCH_NORMAL)
                            return 0;
        
                        if (security == null)
                            return 0;        
                        
                        security.lastMarketTrade().copyFrom(trade);
                        security.lastMarketTrade().detectSide(security.orderBook());
                        security.marketDataUpdateHandler().onTradeReceived(security, security.lastMarketTrade().tradeNanoOfDay(), security.lastMarketTrade());
        
                        return 0;
                    }
        
                    @Override
                    public int onTradingPhase(MarketStatusType marketStatus) {
                        isInMarketHours.set(marketStatus == MarketStatusType.CT);
                        scoreBoardOrderService.isInMarketHour(marketStatus == MarketStatusType.CT);
                        return 0;
                    }
                    
                });
        
                for (final Issuer issuer : strategyTestHelper.strategyManager().issuers().entities()) {
                    issuerPositions.put(issuer.sid(), AggregatedSecurityPosition.of(issuer.sid(), EntityType.ISSUER));
                }
                for (final StrategySecurity underlying : strategyTestHelper.strategyManager().underlyings().values()) {
                    undPositions.put(underlying.sid(), AggregatedSecurityPosition.of(underlying.sid(), EntityType.UNDERLYING)); 
                }
        
                // update greeks
                for (final StrategySecurity warrant : strategyTestHelper.strategyManager().warrants().values()) {
                    wrtPositions.put(warrant.sid(), AggregatedSecurityPosition.of(warrant.sid(), EntityType.SECURITY));
                    final SecurityPosition secPosition = SecurityPosition.of(warrant.sid(), SecurityType.WARRANT, warrant.putOrCall(), warrant.underlyingSid(), warrant.issuerSid(), warrantFees);
                    secPosition.addHandler(totalPosition);
                    secPosition.addHandler(issuerPositions.get(warrant.issuerSid()));
                    secPosition.addHandler(undPositions.get(warrant.underlyingSid()));
                    secPosition.addHandler(wrtPositions.get(warrant.sid()));
                    secPositions.put(warrant.sid(), secPosition);
                    changeTrackers.put(warrant.sid(), SecurityPositionChangeTracker.of());
                    
                    warrant.greeks().secSid(warrant.sid());
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
                for (final ScoreBoardSecurityInfo warrant : scoreBoardTestHelper.scoreBoardManager().warrants().values()) {
                    warrant.greeks().secSid(warrant.sid());
                    warrant.greeks().delta((int)(btParams.getDelta(warrant.sid())));
                    warrant.greeksUpdateHandler().onGreeksUpdated(warrant.greeks());            
                }
                
                final Path ticksPath;
                if (DEBUG_MODE == false || WARRANTS_FILTER == null || WARRANTS_FILTER.length != 1) {
                    ticksPath = FileSystems.getDefault().getPath(DEF_PATH, String.format(TICKS_FILE, testDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")), ""));
                }
                else {
                    ticksPath = FileSystems.getDefault().getPath(DEF_PATH, String.format(TICKS_FILE, testDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")), "." + String.valueOf(WARRANTS_FILTER[0]))); 
                }
                MarketDataReplayer.instanceOf().startFeed(true, 0, Optional.empty(), systemClock, true, ticksPath);
                ordersWriter.flush();
                tradesWriter.flush();
                
                savePnl(strategyTestHelper, issuerPnlFile, issuerPositions.values());
                savePnl(strategyTestHelper, underlyingPnlFile, undPositions.values());
                savePnl(strategyTestHelper, warrantPnlFile, wrtPositions.values());
                savePnl(strategyTestHelper, totalPnlFile, Arrays.asList(totalPosition));
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

