package com.lunar.strategy.scoreboard;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.UserControlledSystemClock;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketDataReplayer;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.ReplayMarketDataSource;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.TradeType;
import com.lunar.order.Tick;
import com.lunar.pricing.BucketPricer;
import com.lunar.pricing.CallPricerValidator;
import com.lunar.pricing.PutPricerValidator;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketTicksStrategyScheduler;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.speedarbhybrid.SpeedArbBtParams;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridWrtSignalGenerator;

@RunWith(MockitoJUnitRunner.class)
public class ScoreBoardFullTest {
    private static final LocalDate TEST_DATE = LocalDate.of(2017, 5, 23);

    // DEBUG_AT_TIME and WARRANTS_FILTER only used when DEBUG_MODE = true
    private static final boolean DEBUG_MODE = true; 
    private static final long DEBUG_AT_TIME = LocalTime.of(13,14,45,104291000).toNanoOfDay(); // need to set your own breakpoint   

    private static final long[] WARRANTS_FILTER = new long[] {27960};
    private static final long[] UNDERLYING_FILTER = new long[] {388};
    private static final String[] ISSUER_FILTER = new String[] {"GS"};

    private static final String DEF_PATH = "/data/omdc/";
    //private static final String DEF_PATH = "D:/cygwin/data/omdc/";
    private static final String DEF_FILE = "warrants." + TEST_DATE.format(DateTimeFormatter.ofPattern("uuuuMMdd")) + ".csv";
    private static final String TICKS_FILE = "ALL_ticks_%s_fast.csv%s";
    private static final String OUT_PATH = "/data/";

    static {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(BucketPricer.class.getCanonicalName()); 
        loggerConfig.setLevel(Level.ERROR);
        loggerConfig = config.getLoggerConfig(CallPricerValidator.class.getCanonicalName()); 
        loggerConfig.setLevel(Level.ERROR);
        loggerConfig = config.getLoggerConfig(PutPricerValidator.class.getCanonicalName()); 
        loggerConfig.setLevel(Level.ERROR);
        loggerConfig = config.getLoggerConfig(SpeedArbHybridWrtSignalGenerator.class.getCanonicalName());
        loggerConfig.setLevel(Level.ERROR);
        loggerConfig = config.getLoggerConfig(WarrantBehaviourAnalyser.class.getCanonicalName());
        loggerConfig.setLevel(Level.TRACE);
        ctx.updateLoggers();
    }

    //TODO for wongca, comment out Ignore
    @Ignore
    @Test   
    public void run() throws Exception {
        final File scoreBoardOutFile = new File(OUT_PATH, "scoreboard.csv");

        final UserControlledSystemClock systemClock = new UserControlledSystemClock(TEST_DATE);
        final ScoreBoardTestHelper testHelper = new ScoreBoardTestHelper(10000, 200, 100); 

        final Collection<Security> securityList = testHelper.securityList();
        final LongEntityManager<StrategySecurity> securities = testHelper.securities();
        final MarketTicksStrategyScheduler scheduler = testHelper.scheduler();
        final SpeedArbBtParams btParams = testHelper.btParams();

        final ScoreBoardOrderService orderService = new ScoreBoardOrderService(systemClock);

        testHelper.setupStrategyManager(orderService);
        Set<Long> warrantsFilter = (DEBUG_MODE == false || WARRANTS_FILTER == null || WARRANTS_FILTER.length == 0) ? null : new HashSet<Long>(Arrays.stream(WARRANTS_FILTER).boxed().collect(Collectors.toList()));
        Set<Long> underlyingFilter = (DEBUG_MODE == false || UNDERLYING_FILTER == null || UNDERLYING_FILTER.length == 0) ? null : new HashSet<Long>(Arrays.stream(UNDERLYING_FILTER).boxed().collect(Collectors.toList())); 
        Set<String> issuerFilter = (DEBUG_MODE == false || ISSUER_FILTER == null || ISSUER_FILTER.length == 0) ? null : new HashSet<String>(Arrays.stream(ISSUER_FILTER).collect(Collectors.toList())); 

        testHelper.readInstruments(DEF_PATH, DEF_FILE, warrantsFilter, underlyingFilter, issuerFilter);
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

                final MarketOrderBook orderBook = security.orderBook();
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

                if (DEBUG_MODE) {
                    //TODO set break point here
                    if (nanoOfDay == DEBUG_AT_TIME) {
                        int a = 0;
                        a = 5;
                    }
                    System.out.println("Processing " + LocalTime.ofNanoOfDay(nanoOfDay));
                }
                mdHandler.onOrderBookUpdated(security, ob.transactNanoOfDay(), ob);
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
                security.marketDataUpdateHandler().onTradeReceived(security, security.lastMarketTrade().tradeNanoOfDay(), security.lastMarketTrade());
                return 0;
            }

            @Override
            public int onTradingPhase(MarketStatusType marketStatus) {
                orderService.isInMarketHour(marketStatus == MarketStatusType.CT);
                return 0;
            }

        });

        // update greeks
        for (final StrategySecurity warrant : testHelper.strategyManager().warrants().values()) {            
            warrant.greeks().delta((int)(btParams.getDelta(warrant.sid())));
            warrant.greeks().gamma(0);
            warrant.greeks().refSpot(0);
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
        
        saveScoreBoard(testHelper, scoreBoardOutFile);
    }
    
    private void saveScoreBoard(final ScoreBoardTestHelper testHelper, final File scoreBoardFile) throws IOException {
        try (final BufferedWriter scoreBoardWriter = new BufferedWriter(new FileWriter(scoreBoardFile))) {
            scoreBoardWriter.write("code" 
            		+ "," + "OurScore" 
            		+ "," + "OurScoreWithPunter" 
            		+ "," + "NumOurWins" 
            		+ "," + "NumOurWinsWithPunter" 
            		+ "," + "NumOurBreakEvens" 
            		+ "," + "NumOurLosses" 
            		+ "," + "OurPnlTicks" 
            		+ "," + "OurPnlTicksWithPunter" 
            		+ "," + "AvgBreakEvenTime" 
            		+ "," + "MinBreakEvenTime" 
            		+ "," + "LastTimeBreakEven" 
            		+ "," + "AvgProfitTime" 
            		+ "," + "LastTimeProfits" 
            		+ "," + "NumProfits" 
            		+ "," + "NumBreakEvens" 
            		+ "," + "NumLosses" 
            		+ "," + "AvgSmoothingTime" 
            		+ "," + "AvgSmoothingTimePerTick" 
            		+ "," + "ModeSpread" 
            		+ "," + "TwaSpread"
                    + "," + "NetSold" 
                    + "," + "NetRevenue" 
                    + "," + "NetPnl"
                    + "," + "GrossSold"
                    + "," + "GrossRevenue"
                    + "," + "GrossPnl"
                    + "," + "NumberTrades"
                    + "," + "NumberBigTrades"
                    + "," + "NumberUncertainTrades"
                    + "\n");
            for (final StrategySecurity security : testHelper.securities().entities()) {
                if (security.securityType().equals(SecurityType.WARRANT)) {
                    final ScoreBoard scoreBoard = ((ScoreBoardSecurityInfo)security).scoreBoard();
                    scoreBoardWriter.write(security.code() 
                        + "," + scoreBoard.speedArbHybridStats().getOurScore() 
                        + "," + scoreBoard.speedArbHybridStats().getOurScoreWithPunter()
                        + "," + scoreBoard.speedArbHybridStats().getNumOurWins()                    		
                        + "," + scoreBoard.speedArbHybridStats().getNumOurWinsWithPunter()                        
                        + "," + scoreBoard.speedArbHybridStats().getNumOurBreakEvens()                    		
                        + "," + scoreBoard.speedArbHybridStats().getNumOurLosses()
                        + "," + scoreBoard.speedArbHybridStats().getOurPnlTicks()
                        + "," + scoreBoard.speedArbHybridStats().getOurPnlTicksWithPunter()
                        + "," + scoreBoard.warrantBehaviourStats().getAvgBreakEvenTime()
                        + "," + scoreBoard.warrantBehaviourStats().getMinBreakEvenTime()
                        + "," + scoreBoard.warrantBehaviourStats().getLastTimeBreakEven()
                        + "," + scoreBoard.warrantBehaviourStats().getAvgProfitTime()
                        + "," + scoreBoard.warrantBehaviourStats().getLastTimeProfits()
                        + "," + scoreBoard.warrantBehaviourStats().getNumProfits()
                        + "," + scoreBoard.warrantBehaviourStats().getNumBreakEvens()
                        + "," + scoreBoard.warrantBehaviourStats().getNumLosses()
                        + "," + scoreBoard.warrantBehaviourStats().getAvgSmoothingTime()
                        + "," + scoreBoard.warrantBehaviourStats().getAvgSmoothingTimePerTick()
                        + "," + scoreBoard.warrantBehaviourStats().getModeSpread()
                        + "," + scoreBoard.warrantBehaviourStats().getTwaSpread()
                        + "," + scoreBoard.punterTradeSetStats().getNetSold() 
                        + "," + scoreBoard.punterTradeSetStats().getNetRevenue() 
                        + "," + scoreBoard.punterTradeSetStats().getNetPnl()
                        + "," + scoreBoard.punterTradeSetStats().getGrossSold()
                        + "," + scoreBoard.punterTradeSetStats().getGrossRevenue()
                        + "," + scoreBoard.punterTradeSetStats().getGrossPnl()
                        + "," + scoreBoard.punterTradeSetStats().getNumberTrades()
                        + "," + scoreBoard.punterTradeSetStats().getNumberBigTrades()
                        + "," + scoreBoard.punterTradeSetStats().getNumberUncertainTrades()
                        + "\n");
                }
            }
        }
    }

}

