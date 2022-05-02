package com.lunar.strategy.speedarbhybrid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.StrategyExplainType;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.OrderStatusReceivedHandler;
import com.lunar.strategy.StrategyExplain;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.parameters.GenericUndParams;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

//@Ignore
@RunWith(MockitoJUnitRunner.class)
public class SpeedArbHybridStrategyTest {
    static final Logger LOG = LogManager.getLogger(SpeedArbHybridStrategyTest.class);    

    private SpeedArbHybridContext m_context;
    private SpreadTable m_spreadTable;

    private MarketOrderBook m_wrtOrderBook;
    private MarketOrderBook m_undOrderBook;

    private StrategySecurity m_warrant;
    private StrategySecurity m_underlying;

    private AtomicInteger m_orderPrice;
    private AtomicLong m_orderSize;
    private AtomicBoolean m_isBuyOrder;

    private SpeedArbHybridStrategy m_strategy;
    private Long2ObjectOpenHashMap<MarketDataUpdateHandler> m_obUpdateHandlers;
    private OrderStatusReceivedHandler m_osReceivedHandler;

    private SpeedArbHybridWrtSignalGenerator m_wrtSigGen;
    private SpeedArbHybridUndSignalGenerator m_undSigGen;
    private SpeedArbHybridStrategySignalHandler m_sigHandler;

    private StrategyExplainType m_explainType;

    @Before
    public void setUp() {

    }

    private void setup(final long warrantSid, final PutOrCall putOrCall, final OptionStyle optionStyle, final int strikePrice, final int convRatio, final float delta) throws Exception {
        final SpeedArbHybridTestHelper speedArbTestHelper = new SpeedArbHybridTestHelper(1, 1, 1);

        m_spreadTable = SpreadTableBuilder.get();

        final StrategyIssuer issuer = StrategyIssuer.of(1, "AB", "AB", 0);
        m_wrtOrderBook = MarketOrderBook.of(10, SpreadTableBuilder.get(SecurityType.WARRANT), Integer.MIN_VALUE, Integer.MIN_VALUE);
        m_undOrderBook = MarketOrderBook.of(10, SpreadTableBuilder.get(SecurityType.STOCK), Integer.MIN_VALUE, Integer.MIN_VALUE);
        m_warrant = StrategySecurity.of(warrantSid, SecurityType.WARRANT, String.valueOf(warrantSid), 0, 3988L, putOrCall, optionStyle, strikePrice, convRatio, (int)issuer.sid(), 1, true, SpreadTableBuilder.get(SecurityType.WARRANT), m_wrtOrderBook);
        m_underlying = StrategySecurity.of(3988L, SecurityType.STOCK, "3988", 0, 0L, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, (int)issuer.sid(), 1, true, SpreadTableBuilder.get(SecurityType.STOCK), m_undOrderBook);
        m_warrant.underlying(m_underlying);
        m_warrant.pricingInstrument(m_underlying);
        m_warrant.issuer(issuer);
        speedArbTestHelper.addIssuer(issuer);
        speedArbTestHelper.addSecurity(m_underlying);
        speedArbTestHelper.addSecurity(m_warrant);
        speedArbTestHelper.btParams().setWarrantParams(warrantSid, 0, 0, (int)delta);
        
        m_orderPrice = new AtomicInteger(0);
        m_orderSize = new AtomicLong(0);
        m_isBuyOrder = new AtomicBoolean(false);
        final StrategyOrderService orderService = new StrategyOrderService() {
            @Override
            public void buy(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                LOG.info("Received buy order request for: secCode {}, price {}, quantity {}, strategyExplain {}", security.code(), price,  quantity,  explain.strategyExplain());                
                m_isBuyOrder.set(true);
                m_orderPrice.set(price);
                m_orderSize.set(quantity);
                m_explainType = explain.strategyExplain();
            }

            @Override
            public void sell(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                LOG.info("Received sell order request for: secCode {}, price {}, quantity {}, strategyExplain {}", security.code(), price,  quantity,  explain.strategyExplain());
                m_isBuyOrder.set(false);
                m_orderPrice.set(price);
                m_orderSize.set(quantity);
                m_explainType = explain.strategyExplain();
            }

            @Override
            public void sellToExit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                LOG.info("Received sell order request for: secCode {}, price {}, quantity {}, strategyExplain {}", security.code(), price,  quantity,  explain.strategyExplain());
                m_isBuyOrder.set(false);
                m_orderPrice.set(price);
                m_orderSize.set(quantity);
                m_explainType = explain.strategyExplain();
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
        speedArbTestHelper.setupStrategyManager(orderService);
        speedArbTestHelper.createStrategies();
        
        m_context = (SpeedArbHybridContext)speedArbTestHelper.strategyManager().getStrategyContext(SpeedArbBtParams.STRATEGY_ID);
        m_strategy = (SpeedArbHybridStrategy)m_warrant.activeStrategy();

        m_obUpdateHandlers = new Long2ObjectOpenHashMap<MarketDataUpdateHandler>();
        m_obUpdateHandlers.put(m_underlying.sid(), m_underlying.marketDataUpdateHandler());
        m_obUpdateHandlers.put(m_warrant.sid(), m_warrant.marketDataUpdateHandler());
        m_osReceivedHandler = m_warrant.orderStatusReceivedHandler();

        m_wrtSigGen = m_context.getWrtSignalGenerator(m_warrant);
        m_undSigGen = m_context.getUndSignalGenerator(m_underlying);
        m_sigHandler = m_context.getStrategySignalHandler(m_warrant);
        
        m_wrtSigGen.getSpeedArbWrtParams().sellOnVolDown(false);
        m_warrant.greeks().delta((int)delta);
        m_warrant.greeksUpdateHandler().onGreeksUpdated(m_warrant.greeks());
        
        m_strategy.switchOn();
    }

    @Test    
    // Test SpeedArbHybridUndSignalGenerator and SpeedArbHybridWrtSignalGenerator Statemachines
    public void testMarketDataStatesCall() throws Exception {
        long nanoOfDay = 1;
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        m_wrtSigGen.getSpeedArbWrtParams().wideSpreadBuffer(0);

        // Test case: test that underlying state is tight spread
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        // Test case: test that underlying state is wide spread
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertFalse(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        // Test case: test that underlying state is tight spread again
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());        

        // Test case: test that warrant state is mm tight spread
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: test that warrant state is tight spread
        setBestBidAsk(m_warrant, m_spreadTable, 230, 30000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: test that warrant state is mm tight spread again
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        // Test case: test that warrant state is tight spread
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 30000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: test that warrant state is mm tight spread
        // Underlying spread: 100
        // Delta: .5f
        // Conv Ratio: 15
        // Warrant tight spread: 100 * .5 / 15 = 3.3333
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 232, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: test that warrant state is mm tight spread
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 233, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: test that warrant state is tight spread
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 234, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: test that warrant state is wide spread
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 235, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        // Test case: Delta increased
        // Underlying spread: 50100 - 50000 = 100
        // Delta: .80f
        // Conv Ratio: 15
        // Warrant tight spread: 5.3
        final Greeks greeks = m_warrant.greeks();
        greeks.delta(80000);
        m_wrtSigGen.onGreeksUpdated(greeks);
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test case: Delta decreased
        // Underlying spread: 50100 - 50000 = 100
        greeks.delta(50000);
        m_wrtSigGen.onGreeksUpdated(greeks);
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 234, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 232, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        // Test that "filter bid" works
        setBestBidAsk(m_warrant, m_spreadTable, 231, 30000, 232, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        m_warrant.orderBook().bidSide().create(230, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test that "filter ask" works
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 30000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        m_warrant.orderBook().askSide().create(232, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

    }    
    
    @Test
    // Test simple cases where we don't have bucket info and cannot buy
    public void testNoBucketNoBuyCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test that nothing is done if no price is moved
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Test that nothing is done if underlying movement is enough to shift warrant price, but we have no velocity
        // Weighted Average: 100090
        // Prev Weighted Average: 100050
        // 100090-100050=40; 40 * 0.5 / 15 = 1.3; 1.3  > 231 - 230
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 9000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Generate velocity
        setUnderlyingTrade(nanoOfDay, -1, 100100, 1.2);

        // Test case: test that nothing is done if underlying movement is not enough to shift warrant price and we have velocity
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 3999990, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get()); 
    }
    
    @Test
    // Test simple cases for velocity
    public void testNoBucketVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Test that nothing is done if underlying movement is enough to shift warrant price, but we have no velocity
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 1000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 9000000, 100100, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Generate not enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100100, 0.9);
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        
        // Generate enough velocity accumulatively
        nanoOfDay += 4_000_000;
        setUnderlyingTrade(nanoOfDay, -1, 50150, 0.2);
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        
        // Cancel buy order
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, 0, 0, OrderRequestRejectType.NULL_VAL);
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        // Generate not enough velocity accumulatively
        nanoOfDay += 4_000_000;
        setUnderlyingTrade(nanoOfDay, -1, 5030, 0.2);
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
    }

    @Test
    // Test cases where we buy without bucket info
    public void testNoBucketMMVeryTightStopLossCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());

        // Update order status with position
        m_warrant.position(10000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Stop loss not yet triggered
        nanoOfDay++;
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Stop loss triggered but has buying ban
        nanoOfDay++;
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000100);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Stop loss triggered
        nanoOfDay+=10000000000L;
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000100);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, m_explainType);
        assertFalse(m_isBuyOrder.get());

        // Order is updated and we have no position
        m_warrant.position(0);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestBidOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
    }

    @Test
    // Test cases where we buy without bucket info
    public void testNoBucketMMNotTightStopLossCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());

        // Update order status with position
        m_warrant.position(10000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Stop loss not yet triggered
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        
        setBestBidAsk(m_warrant, m_spreadTable, 231, 5000, 236, 100000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 50000, 100200, 1000100);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, m_explainType);
        assertFalse(m_isBuyOrder.get());
        assertEquals(10000L, m_orderSize.get());
        assertEquals(230, m_orderPrice.get());

        // Order is updated and we have no position
        m_warrant.position(0);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestBidOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
    }
    
    @Test
    // Test cases where we buy without bucket info
    public void testNoBucketMMNotTightStopLossCallSellBreakEvenOnly() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());

        // Update order status with position
        m_warrant.position(10000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Stop loss not yet triggered
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        
        setBestBidAsk(m_warrant, m_spreadTable, 231, 5000, 236, 100000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        // Do not sell turned on
        m_sigHandler.getSpeedArbWrtParams().userDoNotSell(true);
        
        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 50000, 100200, 1000100);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(100150000, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // Do not sell turned off, stop loss modified so won't sell
        m_sigHandler.getSpeedArbWrtParams().userDoNotSell(false);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(100104761, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // warrant price below breakeven and too wide to exit
        setBestBidAsk(m_warrant, m_spreadTable, 229, 5000, 235, 100000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 50000, 100200, 1000200);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        // ignore wide spread set
        m_sigHandler.getSpeedArbWrtParams().userAllowStopLossOnWideSpread(true);
        assertEquals(100100000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        
        // mm size increased
        m_sigHandler.getSpeedArbWrtParams().userMmBidSize(10000);        
        
        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 100000, 50000, 100100, 1000200);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        // ignore MM size set
        m_sigHandler.getSpeedArbWrtParams().userIgnoreMmSizeOnSell(true);
        assertEquals(100000000, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // sell only at breakeven set
        m_sigHandler.getSpeedArbWrtParams().userSellAtBreakEvenOnly(true);
        
        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 90000, 50000, 100000, 1000200);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // ignore MM size unset
        m_sigHandler.getSpeedArbWrtParams().userSellAtBreakEvenOnly(false);
        assertEquals(95000000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        
        // warrant falling below safe bid price
        setBestBidAsk(m_warrant, m_spreadTable, 223, 5000, 235, 100000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 90000, 50000, 95000, 100000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        // Safe bid price updated
        m_sigHandler.getSpeedArbWrtParams().userSafeBidLevelBuffer(7);

        // Stop loss triggered
        setBestBidAsk(m_underlying, m_spreadTable, 90000, 50000, 95000, 100000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, m_explainType);
        assertFalse(m_isBuyOrder.get());
        assertEquals(10000L, m_orderSize.get());
        assertEquals(223, m_orderPrice.get());

        // Order is updated and we have no position
        m_warrant.position(0);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestBidOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());

    }
    
    @Test
    public void testNoBucketMMVeryTightProfitRun() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100100, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().enterLevel());

        // Update order status with position
        m_warrant.position(10);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 10, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Profit run with mm-bid so revise stop loss to the weighted price
        setBestBidAsk(m_warrant, m_spreadTable, 231, 3000000, 232, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(m_spreadTable.priceToTick(231) + 1, m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(100180000, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // Set bullish outlook and increase profit run
        // Stop loss should be revised to the weighted price - 1 bucket length
        m_sigHandler.getSpeedArbWrtParams().marketOutlook(MarketOutlookType.BULLISH);
        setBestBidAsk(m_warrant, m_spreadTable, 232, 3000000, 233, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(m_spreadTable.priceToTick(232) + 1, m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(100180000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        
        // Increase spot
        // (5020 * 1000000 + 5030 * 9000000) / (1000000 + 9000000) = 5039
        setBestBidAsk(m_underlying, m_spreadTable, 100200, 9000000, 100300, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(m_spreadTable.priceToTick(232) + 1, m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(100180000, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // Increase profit run
        // Stop loss should be revised to the weighted price - 1 bucket length
        // Bucket length = tick size of 231 / delta = 1 / .3 = 3.3333
        // Stop loss = 5039 - 3.3333 = 5035.6666
        setBestBidAsk(m_warrant, m_spreadTable, 233, 3000000, 234, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(m_spreadTable.priceToTick(233) + 1, m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(100260000L, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // Increase profit run to 20 ticks from enter bid
        setBestBidAsk(m_warrant, m_spreadTable, m_spreadTable.tickToPrice(m_spreadTable.priceToTick(230)+10), 3000000, m_spreadTable.tickToPrice(m_spreadTable.priceToTick(231)+10), 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertEquals(m_spreadTable.priceToTick(230) + 11, m_sigHandler.getSpeedArbWrtParams().exitLevel());
        //assertEquals(5035666666666L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(100260000L, m_sigHandler.getSpeedArbWrtParams().stopLoss());

        // Increase profit run to 20 ticks from enter ask
        // Stop loss should be revised to the weighted price - 1 bucket length
        // Bucket length = tick size of 231 / delta = 1 / .3 = 3.3333
        // Stop loss = 5039 - 3.3333 = 5035.6666
        setBestBidAsk(m_warrant, m_spreadTable, m_spreadTable.tickToPrice(m_spreadTable.priceToTick(231)+20), 3000000, m_spreadTable.tickToPrice(m_spreadTable.priceToTick(232)+20), 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.PROFIT_RUN_SELL_SIGNAL, m_explainType);
    }

    @Test
    public void testNoBucketMMVeryTightStopProfit() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().enterLevel());

        // Update order status with position
        m_warrant.position(5000000);        
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 5000000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        setBestBidAsk(m_warrant, m_spreadTable, 232, 3000000, 233, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.STOP_PROFIT_SELL_SIGNAL, m_explainType);
    }
    
    @Test
    public void testSellOnTurnoverMakingImmediateSell() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().enterLevel());

        // Update order status with position
        m_warrant.position(10);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 10, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Quantity not enough for TO Making
        m_obUpdateHandlers.get(m_warrant.sid()).onTradeReceived(m_warrant, nanoOfDay, m_warrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(230).quantity(900_000).numActualTrades(1));
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        nanoOfDay += 1_000_000_000;        
        m_obUpdateHandlers.get(m_warrant.sid()).onTradeReceived(m_warrant, nanoOfDay, m_warrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(230).quantity(900_000).numActualTrades(1));
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Time too far for TO Making
        nanoOfDay += 30_000_000_001L;
        m_obUpdateHandlers.get(m_warrant.sid()).onTradeReceived(m_warrant, nanoOfDay, m_warrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(230).quantity(1_000_000).numActualTrades(1));
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // TO Making
        nanoOfDay += 1_000_000_000;
        m_obUpdateHandlers.get(m_warrant.sid()).onTradeReceived(m_warrant, nanoOfDay, m_warrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(230).quantity(1_000_000).numActualTrades(1));
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.TO_MAKING_SELL_SIGNAL, m_explainType);
    }

    @Test
    public void testSellOnTurnoverMakingDelayedSell() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_explainType);
        assertEquals(100150000, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        assertEquals(m_spreadTable.priceToTick(231), m_sigHandler.getSpeedArbWrtParams().enterLevel());

        // Update order status with position
        m_warrant.position(10);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 10, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        m_obUpdateHandlers.get(m_warrant.sid()).onTradeReceived(m_warrant, nanoOfDay, m_warrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(231).quantity(1_100_000).numActualTrades(1));
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // TO Making
        nanoOfDay += 1_000_000_000;
        m_obUpdateHandlers.get(m_warrant.sid()).onTradeReceived(m_warrant, nanoOfDay, m_warrant.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(231).quantity(1_100_000).numActualTrades(1));
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        setBestBidAsk(m_warrant, m_spreadTable, 231, 3000000, 232, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.TO_MAKING_SELL_SIGNAL, m_explainType);
    }
    
    @Test
    public void testOrderSizeNormalVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 2.0f);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(100_000, m_orderSize.get());
    }
    
    @Test
    public void testOrderSizeHighVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 2.1);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        //assertEquals(450_000, m_orderSize.get());
        assertEquals(100_000, m_orderSize.get());
    }
    
    @Test
    public void testOrderSizeVeryHighVelocityCall() throws Exception {
        long nanoOfDay = 1; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        // Setup initial warrant price
        setBestBidAsk(m_warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 100200, 3.1);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        // Weighted Average: 5030*9 + 5020*1 / (9) = 5029
        // Prev Weighted Average: 5025
        // 5028-5025=4; 4 * 0.3 = 1.2; 1.2  > 231 - 230        
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        //assertEquals(600_000, m_orderSize.get());
        assertEquals(100_000, m_orderSize.get());
    }
    
    @Test
    @Ignore
    public void testScenario1() throws Exception {
        long nanoOfDay = 0;
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);
        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;        
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        // Build up velocity
        nanoOfDay += 1_000_000_000L + 16000000000L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95041666, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        
        m_warrant.position(1000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());

        // Warrant up
        nanoOfDay += 1_00_000_000L - 1_000_000L;
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        // Stop loss trigger
        nanoOfDay += 1_00_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502399);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        assertEquals(99, m_orderPrice.get());
    }
    
    @Test
    @Ignore
    public void testScenario2() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
                
        // Build up velocity
        nanoOfDay += 1_000_000_000L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95041666L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        
        m_warrant.position(1000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        // Warrant up
        nanoOfDay += 1_00_000_000L - 1_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(95066666L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(102), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        
        // Stop loss trigger
        nanoOfDay += 1_00_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        assertEquals(100, m_orderPrice.get());
    }
    
    @Test
    @Ignore
    public void testScenario3() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_wrtSigGen.getSpeedArbWrtParams().marketOutlook(MarketOutlookType.BULLISH);
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Build up velocity
        nanoOfDay += 50_000_000L - 1L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95025000L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());

        m_warrant.position(1000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
        
        // Warrant up
        nanoOfDay += 7*60*1_00_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        assertEquals(95025000L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());
        
        // Stop loss trigger
        nanoOfDay += 1_00_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502399);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_sigHandler.getCurrentStateId());
        assertFalse(m_isBuyOrder.get());
        assertEquals(100, m_orderPrice.get());        
    }
    
    @Test
    @Ignore
    public void testScenario4() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        // Build up velocity
        nanoOfDay += 1_000_000_000L - 1L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95041666L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());  
    }
    
    @Test
    @Ignore
    public void testScenario5() throws Exception {
        long nanoOfDay = 0;
        setup(21181L, PutOrCall.PUT, OptionStyle.ASIAN, 1000, 15000, -.5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);
        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;        
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        // Build up velocity
        nanoOfDay += 1_000_000_000L + 16000000000L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95091667L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());

        m_warrant.position(1000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
    }
    
    @Test
    @Ignore
    public void testScenario6() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.PUT, OptionStyle.ASIAN, 1000, 15000, -.5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
                
        // Build up velocity
        nanoOfDay += 1_000_000_000L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95091667L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());

        m_warrant.position(1000);
        m_osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrant.orderBook().bestAskOrNullIfEmpty().price(), 1000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_sigHandler.getCurrentStateId());
    }
    
    @Test
    @Ignore
    public void testScenario7() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.PUT, OptionStyle.ASIAN, 1000, 15000, -.5000f * 100000);
        
        m_wrtSigGen.getSpeedArbWrtParams().marketOutlook(MarketOutlookType.BULLISH);
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Build up velocity
        nanoOfDay += 50_000_000L - 1L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95095455L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());        
    }
    
    @Test
    @Ignore
    public void testScenario8() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.PUT, OptionStyle.ASIAN, 1000, 15000, -.5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(-.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        // Build up velocity
        nanoOfDay += 1_000_000_000L - 1L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(1).price(95050).quantity(200000).numActualTrades(1));

        // Trigger entry
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_sigHandler.getCurrentStateId());
        assertTrue(m_isBuyOrder.get());
        assertEquals(101, m_orderPrice.get());
        assertEquals(95091667L, m_sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(101), m_sigHandler.getSpeedArbWrtParams().exitLevel());  
    }
    
    @Test
    @Ignore
    public void testScenario9a() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Move warrant price down, but stock stays the same (as next bucket)
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 99, 3000000, 100, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
    }

    @Test
    @Ignore
    public void testScenario10a() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Move warrant price down, but stock stays the same (as next bucket)
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant back to lower bucket
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        // Move stock up but warrant stays the same
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 2_000_000_000L - 50_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
    }
    
    @Test
    @Ignore
    public void testScenario11() throws Exception {
        long nanoOfDay = 0; 
        setup(21181L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000);
        
        m_undSigGen.getSpeedArbUndParams().velocityThreshold(100_000_000);        
        final Greeks greeks = m_warrant.greeks();

        greeks.delta((int)(.5f * 100000));
        greeks.gamma((int)(0.005f * 100000));
        greeks.refSpot(95100);
        m_wrtSigGen.onGreeksUpdated(greeks);

        // Prep bucket pricer
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9502500);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504995);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Move stock and warrant price back to lower bucket
        nanoOfDay += 950_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook()); 
        
        // Move warrant price down, but stock stays the same (as next bucket)
        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 101, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506666);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9509545);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        
        // Widen warrant spread
        nanoOfDay += 1_000_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 102, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());

        nanoOfDay = 11*60*1_000_000_000L + 50_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9504166);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());

        nanoOfDay += 50_000_000L;
        setBestBidAsk(m_warrant, m_spreadTable, 100, 3000000, 101, 3000000);
        m_obUpdateHandlers.get(m_warrant.sid()).onOrderBookUpdated(m_warrant, nanoOfDay, m_warrant.orderBook());
        
        // Build up velocity
        nanoOfDay += 25_000_000L;
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(-1).price(95050).quantity(200000).numActualTrades(1));
        
        nanoOfDay += 1_000_000L;
        setBestBidAsk(m_underlying, m_spreadTable, WA_9506724);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_sigHandler.getCurrentStateId());
    }
    
    private void setUnderlyingTrade(final long nanoOfDay, final int side, final int price, final double velocityRatio) throws Exception {
        final GenericUndParams undParams = m_context.getStrategyUndParams(m_underlying.sid(), false);
        final int quantity = (int)Math.floor(undParams.velocityThreshold() * velocityRatio / price);
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(side).price(price).quantity(quantity).numActualTrades(1));
    }
    
    static final int WA_9502269 = 9502269;
    static final int WA_9502399 = 9502399;
    static final int WA_9502500 = 9502500;
    static final int WA_9504166 = 9504166;
    static final int WA_9504995 = 9504995;
    static final int WA_9506666 = 9506666;
    static final int WA_9506724 = 9506724; 
    static final int WA_9509166 = 9509166;
    static final int WA_9509545 = 9509545;
    
    private void setBestBidAsk(final StrategySecurity security, final SpreadTable spreadTable, final int weightedSpot) throws Exception {
        security.orderBook().bidSide().clear();
        security.orderBook().askSide().clear();
        switch (weightedSpot) {
        case WA_9502269:
            security.orderBook().bidSide().create(95000, 100000);
            security.orderBook().askSide().create(95050, 120334);
            break;
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

    private void setBestBidAsk(final StrategySecurity security, final SpreadTable spreadTable, final int bidPrice, final int bidSize, final int askPrice, final int askSize) {
        security.orderBook().bidSide().clear();
        security.orderBook().askSide().clear();
        if (bidPrice > 0)
            security.orderBook().bidSide().create(bidPrice, bidSize);
        if (askPrice > 0)
            security.orderBook().askSide().create(askPrice, askSize);
    }

}
