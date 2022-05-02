package com.lunar.strategy.speedarbhybrid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.TriggerInfo;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.StrategyExplainType;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.OrderStatusReceivedHandler;
import com.lunar.strategy.StrategyExplain;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.parameters.GenericUndParams;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

//@Ignore
@RunWith(MockitoJUnitRunner.class)
public class SpeedArbHybridStrategyMultiWrtsTest {
    static final Logger LOG = LogManager.getLogger(SpeedArbHybridStrategyMultiWrtsTest.class);   
    
    private class WarrantInfoHolder {
        final StrategySecurity warrant;
        final float delta;
        SpeedArbHybridWrtSignalGenerator wrtSigGen;
        SpeedArbHybridStrategySignalHandler sigHandler;
        OrderStatusReceivedHandler osReceivedHandler;
        int orderPrice;
        long orderSize;
        boolean isBuyOrder;
        StrategyExplainType explainType;

        public WarrantInfoHolder(final long warrantSid, final PutOrCall putOrCall, final OptionStyle optionStyle, final int strikePrice, final int convRatio, final float delta, final long underlyingSid, final int issuerSid) {
            warrant = StrategySecurity.of(warrantSid, SecurityType.WARRANT, String.valueOf(warrantSid), 0, underlyingSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, 1, true, SpreadTableBuilder.get(SecurityType.WARRANT), m_wrtOrderBook);
            this.delta = delta;
        }
    }

    private SpeedArbHybridContext m_context;
    private SpreadTable m_spreadTable;

    private MarketOrderBook m_wrtOrderBook;
    private MarketOrderBook m_undOrderBook;

    private Long2ObjectLinkedOpenHashMap<WarrantInfoHolder> m_warrantInfoHolders;
    private StrategyIssuer m_issuer;
    private StrategySecurity m_underlying;

    private Long2ObjectOpenHashMap<MarketDataUpdateHandler> m_obUpdateHandlers;

    private SpeedArbHybridUndSignalGenerator m_undSigGen;


    @Before
    public void setUp() throws Exception {
        final SpeedArbHybridTestHelper speedArbTestHelper = new SpeedArbHybridTestHelper(1, 1, 1);

        m_spreadTable = SpreadTableBuilder.get();

        m_issuer = StrategyIssuer.of(1, "GS", "GS", 0);
        m_wrtOrderBook = MarketOrderBook.of(10, SpreadTableBuilder.get(SecurityType.WARRANT), Integer.MIN_VALUE, Integer.MIN_VALUE);
        m_undOrderBook = MarketOrderBook.of(10, SpreadTableBuilder.get(SecurityType.STOCK), Integer.MIN_VALUE, Integer.MIN_VALUE);
        m_underlying = StrategySecurity.of(3988L, SecurityType.STOCK, "3988", 0, 0L, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, (int)m_issuer.sid(), 1, true, SpreadTableBuilder.get(SecurityType.STOCK), m_undOrderBook);
        setupWarrants();
        speedArbTestHelper.addIssuer(m_issuer);
        speedArbTestHelper.addSecurity(m_underlying);
        for (final WarrantInfoHolder warrantInfo : m_warrantInfoHolders.values()) {
            speedArbTestHelper.addSecurity(warrantInfo.warrant);
            speedArbTestHelper.btParams().setWarrantParams(warrantInfo.warrant.sid(), 0, 0, (int)warrantInfo.delta);
        }
        
        final StrategyOrderService orderService = new StrategyOrderService() {
            @Override
            public void buy(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                LOG.info("Received buy order request for: secCode {}, price {}, quantity {}, strategyExplain {}", security.code(), price,  quantity,  explain.strategyExplain());                
                m_warrantInfoHolders.get(security.sid()).isBuyOrder = true;
                m_warrantInfoHolders.get(security.sid()).orderPrice = price;
                m_warrantInfoHolders.get(security.sid()).orderSize = quantity;
                m_warrantInfoHolders.get(security.sid()).explainType = explain.strategyExplain();
            }

            @Override
            public void sell(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                LOG.info("Received sell order request for: secCode {}, price {}, quantity {}, strategyExplain {}", security.code(), price,  quantity,  explain.strategyExplain());
                m_warrantInfoHolders.get(security.sid()).isBuyOrder = false;
                m_warrantInfoHolders.get(security.sid()).orderPrice = price;
                m_warrantInfoHolders.get(security.sid()).orderSize = quantity;
                m_warrantInfoHolders.get(security.sid()).explainType = explain.strategyExplain();
            }

            @Override
            public void sellToExit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
                LOG.info("Received sell order request for: secCode {}, price {}, quantity {}, strategyExplain {}", security.code(), price,  quantity,  explain.strategyExplain());
                m_warrantInfoHolders.get(security.sid()).isBuyOrder = false;
                m_warrantInfoHolders.get(security.sid()).orderPrice = price;
                m_warrantInfoHolders.get(security.sid()).orderSize = quantity;
                m_warrantInfoHolders.get(security.sid()).explainType = explain.strategyExplain();
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
        m_obUpdateHandlers = new Long2ObjectOpenHashMap<MarketDataUpdateHandler>();
        m_obUpdateHandlers.put(m_underlying.sid(), m_underlying.marketDataUpdateHandler());

        for (final WarrantInfoHolder warrantInfo : m_warrantInfoHolders.values()) {
            SpeedArbHybridStrategy strategy = (SpeedArbHybridStrategy)warrantInfo.warrant.activeStrategy();
            m_obUpdateHandlers.put(warrantInfo.warrant.sid(), warrantInfo.warrant.marketDataUpdateHandler());
            warrantInfo.osReceivedHandler = warrantInfo.warrant.orderStatusReceivedHandler();
            warrantInfo.wrtSigGen = m_context.getWrtSignalGenerator(warrantInfo.warrant);
            warrantInfo.sigHandler = m_context.getStrategySignalHandler(warrantInfo.warrant);
            
            warrantInfo.wrtSigGen.getSpeedArbWrtParams().sellOnVolDown(false);            
            warrantInfo.warrant.greeks().delta((int)warrantInfo.delta);
            warrantInfo.warrant.greeksUpdateHandler().onGreeksUpdated(warrantInfo.warrant.greeks());
            
            strategy.switchOn();
        }
        m_undSigGen = m_context.getUndSignalGenerator(m_underlying);
    }

    private void setupWarrants() {
        m_warrantInfoHolders = new Long2ObjectLinkedOpenHashMap<WarrantInfoHolder>(5);
        m_warrantInfoHolders.put(12345L, new WarrantInfoHolder(12345L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .5000f * 100000, m_underlying.sid(), (int)m_issuer.sid()));
        m_warrantInfoHolders.put(12346L, new WarrantInfoHolder(12346L, PutOrCall.CALL, OptionStyle.ASIAN, 1000, 15000, .2000f * 100000, m_underlying.sid(), (int)m_issuer.sid()));
        m_warrantInfoHolders.put(12347L, new WarrantInfoHolder(12347L, PutOrCall.PUT, OptionStyle.ASIAN, 1000, 15000, -.3000f * 100000, m_underlying.sid(), (int)m_issuer.sid()));
    }
    
   
    @Test
    public void test1() throws Exception {
        long nanoOfDay = 1; 

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        // Setup initial warrant price
        setBestBidAsk(m_warrantInfoHolders.get(12345).warrant, m_spreadTable, 230, 3000000, 231, 3000000);
        m_obUpdateHandlers.get(m_warrantInfoHolders.get(12345).warrant.sid()).onOrderBookUpdated(m_warrantInfoHolders.get(12345).warrant, nanoOfDay, m_warrantInfoHolders.get(12345).warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Test that we enter buy state if underlying movement is enough to shift warrant price and we have velocity
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertTrue(m_warrantInfoHolders.get(12345).isBuyOrder);
        assertEquals(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL, m_warrantInfoHolders.get(12345).explainType);
        assertEquals(100150000, m_warrantInfoHolders.get(12345).sigHandler.getSpeedArbWrtParams().stopLoss());
        assertEquals(m_spreadTable.priceToTick(231), m_warrantInfoHolders.get(12345).sigHandler.getSpeedArbWrtParams().exitLevel());

        // Update order status with position
        m_warrantInfoHolders.get(12345).warrant.position(10000);
        m_warrantInfoHolders.get(12345).osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrantInfoHolders.get(12345).warrant.orderBook().bestAskOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());

        // Stop loss not yet triggered
        nanoOfDay++;
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());

        // Delta limit alert but cannot sell because not breakeven
        final MarketTrade marketTrade = MarketTrade.of(TriggerInfo.of());
        marketTrade.price(230).side(MarketTrade.ASK).quantity(100000);
        nanoOfDay+=10000000000L;
        setWarrantTrade(nanoOfDay, 12345, MarketTrade.ASK, 100000);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        
        setBestBidAsk(m_warrantInfoHolders.get(12346).warrant, m_spreadTable, 130, 5000000, 131, 3000000);
        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12346, MarketTrade.ASK, 1000000);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        
        setBestBidAsk(m_warrantInfoHolders.get(12347).warrant, m_spreadTable, 130, 5000000, 131, 3000000);
        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12347, MarketTrade.ASK, 50000);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());

        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12347, MarketTrade.BID, 50000);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());

        nanoOfDay+=1000L;
        setBestBidAsk(m_warrantInfoHolders.get(12345).warrant, m_spreadTable, 231, 3000000, 232, 3000000);
        m_obUpdateHandlers.get(m_warrantInfoHolders.get(12345).warrant.sid()).onOrderBookUpdated(m_warrantInfoHolders.get(12345).warrant, nanoOfDay, m_warrantInfoHolders.get(12345).warrant.orderBook());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.SELLING_POSITION, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertEquals(StrategyExplainType.DELTA_LIMIT_SELL_SIGNAL, m_warrantInfoHolders.get(12345).explainType);
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        // Order is updated and we have no position
        m_warrantInfoHolders.get(12345).warrant.position(0);
        m_warrantInfoHolders.get(12345).osReceivedHandler.onOrderStatusReceived(nanoOfDay, m_warrantInfoHolders.get(12345).warrant.orderBook().bestBidOrNullIfEmpty().price(), 10000, OrderRequestRejectType.NULL_VAL);
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
    }
    
    @Test
    public void test2() throws Exception {
        long nanoOfDay = 1; 

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        // Setup initial warrant price
        setBestBidAsk(m_warrantInfoHolders.get(12345).warrant, m_spreadTable, 230, 6000000, 231, 6000000);
        m_obUpdateHandlers.get(m_warrantInfoHolders.get(12345).warrant.sid()).onOrderBookUpdated(m_warrantInfoHolders.get(12345).warrant, nanoOfDay, m_warrantInfoHolders.get(12345).warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Delta limit so cannot buy
        final MarketTrade marketTrade = MarketTrade.of(TriggerInfo.of());
        marketTrade.price(230).side(MarketTrade.ASK).quantity(100000);
        setWarrantTrade(nanoOfDay, 12345, MarketTrade.ASK, 100000);
        setBestBidAsk(m_warrantInfoHolders.get(12346).warrant, m_spreadTable, 130, 5000000, 131, 3000000);
        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12346, MarketTrade.ASK, 1000000);
        
        setBestBidAsk(m_warrantInfoHolders.get(12347).warrant, m_spreadTable, 130, 5000000, 131, 3000000);
        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12347, MarketTrade.ASK, 50000);

        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12347, MarketTrade.BID, 50000);

        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
    }

    @Test
    // Test cases where we buy without bucket info
    public void test3() throws Exception {
        long nanoOfDay = 1; 

        // Setup initial underlying price
        setBestBidAsk(m_underlying, m_spreadTable, 100100, 1000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertFalse(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        // Setup initial warrant price
        setBestBidAsk(m_warrantInfoHolders.get(12345).warrant, m_spreadTable, 230, 6000000, 231, 6000000);
        m_obUpdateHandlers.get(m_warrantInfoHolders.get(12345).warrant.sid()).onOrderBookUpdated(m_warrantInfoHolders.get(12345).warrant, nanoOfDay, m_warrantInfoHolders.get(12345).warrant.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertTrue(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.NO_POSITION_HELD, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
        assertFalse(m_warrantInfoHolders.get(12345).isBuyOrder);

        nanoOfDay += 1_000_000_000L;

        // Generate enough velocity
        setUnderlyingTrade(nanoOfDay, -1, 5030, 1.2);

        // Delta limit so cannot buy
        final MarketTrade marketTrade = MarketTrade.of(TriggerInfo.of());
        marketTrade.price(230).side(MarketTrade.ASK).quantity(100000);
        setWarrantTrade(nanoOfDay, 12345, MarketTrade.ASK, 100000);
        setBestBidAsk(m_warrantInfoHolders.get(12346).warrant, m_spreadTable, 130, 5000000, 131, 3000000);
        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12346, MarketTrade.ASK, 400000);
        
        setBestBidAsk(m_warrantInfoHolders.get(12347).warrant, m_spreadTable, 130, 5000000, 131, 3000000);
        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12347, MarketTrade.ASK, 50000);

        nanoOfDay+=1000L;
        setWarrantTrade(nanoOfDay, 12347, MarketTrade.BID, 50000);

        setBestBidAsk(m_underlying, m_spreadTable, 100100, 9000000, 100200, 1000000);
        m_obUpdateHandlers.get(m_underlying.sid()).onOrderBookUpdated(m_underlying, nanoOfDay, m_underlying.orderBook());
        assertTrue(m_undSigGen.isTightSpread());
        assertEquals(39450, m_warrantInfoHolders.get(12345).orderSize);
        assertTrue(m_warrantInfoHolders.get(12345).wrtSigGen.isLooselyTight());
        assertEquals(SpeedArbHybridStrategySignalHandler.StateIds.BUYING_POSITION, m_warrantInfoHolders.get(12345).sigHandler.getCurrentStateId());
    }

    private void setUnderlyingTrade(final long nanoOfDay, final int side, final int price, final double velocityRatio) throws Exception {
        final GenericUndParams undParams = m_context.getStrategyUndParams(m_underlying.sid(), false);
        final int quantity = (int)Math.floor(undParams.velocityThreshold() * velocityRatio / price);
        m_obUpdateHandlers.get(m_underlying.sid()).onTradeReceived(m_underlying, nanoOfDay, m_underlying.lastMarketTrade().tradeNanoOfDay(nanoOfDay).side(side).price(price).quantity(quantity).numActualTrades(1));
    }

    private void setWarrantTrade(final long nanoOfDay, final long warrantSid, final int side, int quantity) throws Exception {
        final WarrantInfoHolder infoHolder = m_warrantInfoHolders.get(warrantSid);
        final StrategySecurity security =  infoHolder.warrant;
        final MarketTrade marketTrade = MarketTrade.of(TriggerInfo.of());
        final int price;
        if (side == MarketTrade.BID) {
            price = security.orderBook().bestBidOrNullIfEmpty().price();
            final int maxQuantity = (int)security.orderBook().bestBidOrNullIfEmpty().qty();
            if (quantity > maxQuantity) {
                security.orderBook().bidSide().clear();
                security.orderBook().bidSide().create(m_spreadTable.tickToPrice(m_spreadTable.priceToTick(price) - 1), maxQuantity);
                quantity = maxQuantity;
            }
            else {
                security.orderBook().bidSide().clear();
                security.orderBook().bidSide().create(price, maxQuantity - quantity);
            }
        }
        else {
            price = security.orderBook().bestAskOrNullIfEmpty().price();
            final int maxQuantity = (int)security.orderBook().bestAskOrNullIfEmpty().qty();
            if (quantity > maxQuantity) {
                security.orderBook().askSide().clear();
                security.orderBook().askSide().create(m_spreadTable.tickToPrice(m_spreadTable.priceToTick(price) + 1), maxQuantity);
                quantity = maxQuantity;
            }
            else {
                security.orderBook().askSide().clear();
                security.orderBook().askSide().create(price, maxQuantity - quantity);
            }
        }
        marketTrade.secSid(warrantSid).tradeNanoOfDay(nanoOfDay).price(price).quantity(quantity).side(side);   
        m_obUpdateHandlers.get(warrantSid).onTradeReceived(security, nanoOfDay, marketTrade);
        m_obUpdateHandlers.get(warrantSid).onOrderBookUpdated(security, nanoOfDay, security.orderBook());
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
