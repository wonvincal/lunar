package com.lunar.strategy;

import java.util.ArrayList;
import java.util.List;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;

public class MultiCompositeMarketDataUpdateHandler implements CompositeMarketDataUpdateHandler {
	private List<MarketDataUpdateHandler> m_handlers;
	
	public MultiCompositeMarketDataUpdateHandler(final Security security) {
		m_handlers = new ArrayList<MarketDataUpdateHandler>();
	}

	@Override
	public void onOrderBookUpdated(final Security security, final long timestamp, final MarketOrderBook orderBook) throws Exception {
		for (final MarketDataUpdateHandler handler : m_handlers) {
			handler.onOrderBookUpdated(security, timestamp, orderBook);
		}
	}
	
	@Override
	public void onTradeReceived(final Security security, final long timestamp, final MarketTrade marketTrade) throws Exception {
		for (final MarketDataUpdateHandler handler : m_handlers) {
			handler.onTradeReceived(security, timestamp, marketTrade);
		}		
	}

	@Override
	public void registerOrderBookUpdateHandler(final MarketDataUpdateHandler obUpdateHandler) {
		if (!m_handlers.contains(obUpdateHandler)) {
		    //TODO don't like this hack, but no way yet to set priority
		    if (obUpdateHandler instanceof StrategySignalGenerator) {
		        m_handlers.add(0, obUpdateHandler);
		    }
		    else {
		        m_handlers.add(obUpdateHandler);
		    }
		}
	}

	@Override
	public void unregisterOrderBookUpdateHandler(final MarketDataUpdateHandler obUpdateHandler) {
		m_handlers.remove(obUpdateHandler);
	}

}
