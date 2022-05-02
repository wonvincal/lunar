package com.lunar.marketdata.hkex;

import com.lunar.marketdata.archive.MarketDataChannelArbitrator;
import com.lunar.marketdata.archive.MarketDataPublisher;
import com.lunar.marketdata.archive.MarketDataChannelArbitrator.GapHandler;

public final class ChannelArbitratorBuilder {
	public MarketDataChannelArbitrator build(MarketDataPublisher publisher, GapHandler gapHandler){
		return MarketDataChannelArbitrator.of(0, 0, publisher, gapHandler, 0, null, 0);
	}
}
