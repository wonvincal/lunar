package com.lunar.strategy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.strategy.parameters.GenericWrtParams;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TurnoverMakingSignalGenerator implements MarketDataUpdateHandler, StrategySignalGenerator {
    static final Logger LOG = LogManager.getLogger(TurnoverMakingSignalGenerator.class);

    final StrategySecurity m_security;
    final GenericWrtParams m_params;
    final ObjectArrayList<TurnoverMakingSignalHandler> m_turnoverMakingSignalHandlers;
    final IntOpenHashSet m_knownTurnOverMakingQuantities;
    long lastBigTradeTime = 0;
    
    public TurnoverMakingSignalGenerator(final StrategySecurity security, final GenericWrtParams params) {
        m_turnoverMakingSignalHandlers = ObjectArrayList.wrap(new TurnoverMakingSignalHandler[4]);
        m_turnoverMakingSignalHandlers.size(0);
        m_knownTurnOverMakingQuantities = new IntOpenHashSet(16);
        m_security = security;
        m_params = params;
    }
    
    @Override
    public void registerHandler(final StrategySignalHandler handler) {
        if (handler instanceof TurnoverMakingSignalHandler) {
            final TurnoverMakingSignalHandler turnoverMakingSignalHandler = (TurnoverMakingSignalHandler)handler;
            if (!m_turnoverMakingSignalHandlers.contains(turnoverMakingSignalHandler)) {
                m_turnoverMakingSignalHandlers.add(turnoverMakingSignalHandler);
                if (m_turnoverMakingSignalHandlers.size() == 1) {
                    m_security.registerMdUpdateHandler(this);
                    LOG.debug("Turnover making signal is enabled: secCode {}", m_security.code());
                }
            }
        }
    }

    @Override
    public void unregisterHandler(final StrategySignalHandler handler) {
        if (handler instanceof TurnoverMakingSignalHandler) {
            if (m_turnoverMakingSignalHandlers.remove((TurnoverMakingSignalHandler)handler)) {
                if (m_turnoverMakingSignalHandlers.isEmpty()) {
                    m_security.unregisterMdUpdateHandler(this);
                    LOG.debug("Turnover making signal is disabled: secCode {}", m_security.code());
                }
            }            
        }
    }
    
    @Override
    public void reset() {
        m_knownTurnOverMakingQuantities.clear();
    }

    @Override
    public void onOrderBookUpdated(final Security srcSecurity, final long transactTime, final MarketOrderBook orderBook) throws Exception {
    }

    @Override
    public void onTradeReceived(final Security srcSecurity, final long nanoOfDay, final MarketTrade trade) throws Exception {
        if (trade.quantity() >= m_params.turnoverMakingSize() && trade.numActualTrades() == 1) {
            if (m_knownTurnOverMakingQuantities.contains(trade.quantity())) {
                publishSignal(nanoOfDay, trade.price());
            }
            else if ((lastBigTradeTime > 0) && (nanoOfDay - lastBigTradeTime <= m_params.turnoverMakingPeriod())) {
                m_knownTurnOverMakingQuantities.add(trade.quantity());
                publishSignal(nanoOfDay, trade.price());
            }
            lastBigTradeTime = nanoOfDay;
        }
    }

    private void publishSignal(final long nanoOfDay, final int price) throws Exception {
        for (final TurnoverMakingSignalHandler processor : m_turnoverMakingSignalHandlers) {
            processor.onTurnoverMakingDetected(nanoOfDay, price);
        }
    }

}
