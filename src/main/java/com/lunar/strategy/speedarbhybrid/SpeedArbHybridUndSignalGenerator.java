package com.lunar.strategy.speedarbhybrid;

import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.LogManager;

import com.lunar.core.TriggerInfo;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.order.Tick;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.TriggerGenerator;
import com.lunar.strategy.UnderlyingPrice;
import com.lunar.strategy.UnderlyingSpotObserver;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.statemachine.ConcreteState;
import com.lunar.strategy.statemachine.EventTranslator;
import com.lunar.strategy.statemachine.StateMachine;
import com.lunar.strategy.statemachine.StateMachineBuilder;
import com.lunar.strategy.statemachine.StateMachineEventBus;

import static org.apache.logging.log4j.util.Unbox.box;

@SuppressWarnings("unused")
public class SpeedArbHybridUndSignalGenerator implements MarketDataUpdateHandler, UnderlyingPrice {
    static final Logger LOG = LogManager.getLogger(SpeedArbHybridUndSignalGenerator.class);

    private final StrategyType m_strategyType;
    private final StrategySecurity m_security;
    private final SpreadTable m_spreadTable;
    
    private final StrategyInfoSender m_strategyInfoSender;
    private final Iterable<UnderlyingSpotObserver> m_callSpotObservers;
    private final Iterable<UnderlyingSpotObserver> m_putSpotObservers;
    
    // configurations
    private final GenericUndParams m_speedArbUndParams;
    private final GenericStrategyTypeParams m_speedArbParams;
    
    // states
    private long m_lastTickNanoOfDay;
    private int m_bestBidPrice;
    private int m_bestAskPrice;
    private int m_bestBidLevel;
    private int m_bestAskLevel;
    private long m_bestBidSize;
    private long m_bestAskSize;
    private int m_prevBestBidPrice;
    private int m_prevBestAskPrice;
    private int m_prevSpread = Integer.MAX_VALUE;
    private int m_spread = Integer.MAX_VALUE;
    private boolean m_isTightSpread;
    private boolean m_isPrevTightSpread;
    private int m_askTickSize;
    private int m_bidTickSize;
    private long m_weightedAverage;
    private long m_midPrice;
    private long m_prevWeightedAverage;
    private long m_prevMidPrice;
    private int m_stockMomentum;

    SpeedArbHybridUndSignalGenerator(final StrategyType strategyType, final StrategySecurity security, final StrategyInfoSender strategyInfoSender, final GenericUndParams speedArbUndParams, final GenericStrategyTypeParams speedArbParams, final Iterable<UnderlyingSpotObserver> callSpotObservers, final Iterable<UnderlyingSpotObserver> putSpotObservers) {
        m_strategyType = strategyType;
        m_security = security;
        m_spreadTable = security.spreadTable();
        m_speedArbUndParams = speedArbUndParams;
        m_speedArbParams = speedArbParams;
        m_strategyInfoSender = strategyInfoSender;
        m_callSpotObservers = callSpotObservers;
        m_putSpotObservers = putSpotObservers;
    }
    
    public void start() throws Exception {
        m_security.registerMdUpdateHandler(this);
    }
    
    public void reset() throws Exception {
        m_bestBidPrice = 0;
        m_bestAskPrice = 0;
        m_bestBidLevel = 0;
        m_bestAskLevel = 0;
        m_bestBidSize = 0;
        m_bestAskSize = 0;
        m_prevBestBidPrice = 0;
        m_prevBestAskPrice = 0;
        m_prevSpread = Integer.MAX_VALUE;
        m_spread = Integer.MAX_VALUE;
        m_isTightSpread = false;
        m_isPrevTightSpread = false;
        m_lastTickNanoOfDay = 0;
    }

    @Override
    public void onOrderBookUpdated(final Security security, final long timestamp, final MarketOrderBook orderBook) throws Exception {
        m_lastTickNanoOfDay = timestamp;
        m_isTightSpread = false;
        m_spread = Integer.MAX_VALUE;
        if (!m_security.orderBook().bidSide().isEmpty()) {
            final Tick tick = m_security.orderBook().bidSide().bestOrNullIfEmpty();
            m_bestBidPrice = tick.price();
            m_bestBidSize = tick.qty();
            m_bestBidLevel = tick.tickLevel();
        }
        else {
            m_bestBidPrice = 0;
            m_bestBidSize = 0;
            m_bestBidLevel = 0;
        }

        if (!m_security.orderBook().askSide().isEmpty()) {
            final Tick tick = m_security.orderBook().askSide().bestOrNullIfEmpty();
            m_bestAskPrice = tick.price();
            m_bestAskSize = tick.qty();
            m_bestAskLevel = tick.tickLevel();
            
            // additional logic to handle when we have a spread
            if (m_bestBidPrice != 0) {
                m_spread = m_bestAskLevel - m_bestBidLevel;
                if (!m_security.orderBook().isRecovery()) {
                    m_midPrice = (long)((m_bestAskPrice + m_bestBidPrice) * ServiceConstant.WEIGHTED_AVERAGE_SCALE) / 2L;
                    if (m_spread == 1) {
                        m_isTightSpread = true;
                        m_weightedAverage = (long)((double)(m_bestAskPrice * m_bestBidSize + m_bestBidPrice * m_bestAskSize) / (m_bestBidSize + m_bestAskSize) * ServiceConstant.WEIGHTED_AVERAGE_SCALE);
                    }
                    else {
                        m_weightedAverage = m_midPrice;
                    }
                    signalSpotObservers(m_isTightSpread, m_security.orderBook().triggerInfo());
                }
            }
        }
        else {
            m_bestAskPrice = 0;
            m_bestAskSize = 0;
            m_bestAskLevel = 0;
        }
        
        // handle tick size updates
        if (m_prevBestAskPrice != m_bestAskPrice && m_bestAskPrice != 0) {
            final int tickBelowAsk = m_spreadTable.tickToPrice(m_bestAskLevel - 1);
            final int tickSize = m_bestAskPrice - tickBelowAsk;
            if (tickSize != m_askTickSize) {
                m_askTickSize = tickSize;
                for (final UnderlyingSpotObserver observer : m_putSpotObservers) {
                    observer.onUnderlyingTickSizeChanged(tickSize);
                }
            }
        }
        if (m_prevBestBidPrice != m_bestBidPrice && m_bestBidPrice != 0) {
            final int tickSize = m_spreadTable.priceToTickSize(m_bestBidPrice);
            if (tickSize != m_bidTickSize) {
                m_bidTickSize = tickSize;
                for (final UnderlyingSpotObserver observer : m_callSpotObservers) {
                    observer.onUnderlyingTickSizeChanged(tickSize);
                }
            }
        }           
        
        m_prevBestBidPrice = m_bestBidPrice;
        m_prevBestAskPrice = m_bestAskPrice;
        m_prevSpread = m_spread;
        m_isPrevTightSpread = m_isTightSpread;
        m_prevWeightedAverage = m_weightedAverage;
        m_prevMidPrice = m_midPrice;
    }
    
    @Override
    public void onTradeReceived(final Security security, final long timestamp, final MarketTrade trade) {
    }

    @Override
    public int getBidPrice() {
        return m_bestBidPrice;
    }
    
    @Override
    public int getAskPrice() {
        return m_bestAskPrice;
    }

    @Override
    public int getPrevBidPrice() {
        return m_prevBestBidPrice;
    }

    @Override
    public int getPrevAskPrice() {
        return m_prevBestAskPrice;
    }
    
    @Override
    public boolean isTightSpread() {
        return m_isTightSpread;
    }
    
    @Override
    public boolean isPrevTightSpread() {
        return m_isPrevTightSpread;
    }
    
    @Override
    public int getAskTickSize() {
        return m_askTickSize;
    }

    @Override
    public int getBidTickSize() {
        return m_bidTickSize;
    }
    
    @Override
    public long getWeightedAverage() {
        return m_weightedAverage;
    }

    @Override
    public long getPrevWeightedAverage() {
        return m_prevWeightedAverage;
    }
    
    @Override
    public long getMidPrice() {
        return m_midPrice;
    }
    
    @Override
    public long getPrevMidPrice() {
        return m_prevMidPrice;
    }

    public int getStockMomentum() {
        return m_stockMomentum;
    }
    
    public GenericUndParams getSpeedArbUndParams() {
        return m_speedArbUndParams;
    }

    private void signalSpotObservers(final boolean isTightSpread, final TriggerInfo triggerInfo) throws Exception {
        if (m_weightedAverage > m_prevWeightedAverage) { 
            for (final UnderlyingSpotObserver observer : m_putSpotObservers) {
                observer.observeUndSpot(m_lastTickNanoOfDay, m_weightedAverage, m_midPrice, isTightSpread, triggerInfo);
            }
            for (final UnderlyingSpotObserver observer : m_callSpotObservers) {
                observer.observeUndSpot(m_lastTickNanoOfDay, m_weightedAverage, m_midPrice, isTightSpread, triggerInfo);
            }            
        }
        else {
            for (final UnderlyingSpotObserver observer : m_callSpotObservers) {
                observer.observeUndSpot(m_lastTickNanoOfDay, m_weightedAverage, m_midPrice, isTightSpread, triggerInfo);
            }
            for (final UnderlyingSpotObserver observer : m_putSpotObservers) {
                observer.observeUndSpot(m_lastTickNanoOfDay, m_weightedAverage, m_midPrice, isTightSpread, triggerInfo);
            }
        }
    }

}
