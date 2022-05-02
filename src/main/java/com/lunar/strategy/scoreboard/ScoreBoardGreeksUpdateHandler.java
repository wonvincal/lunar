package com.lunar.strategy.scoreboard;

import java.util.Iterator;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.order.Tick;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.GreeksUpdateHandler;
import com.lunar.strategy.MarketDataUpdateHandler;

public class ScoreBoardGreeksUpdateHandler implements GreeksUpdateHandler, MarketDataUpdateHandler {
    final private ScoreBoardSecurityInfo security;
    private int tickSize = 0;
    private long vega = 0;

    public ScoreBoardGreeksUpdateHandler(final ScoreBoardSecurityInfo security) {
        this.security = security;
        security.registerGreeksUpdateHandler(this);
        security.registerMdUpdateHandler(this);
    }
    
    @Override
    public void onGreeksUpdated(final Greeks greeks) throws Exception {
        security.scoreBoard().marketStats().setImpliedVol(greeks.impliedVol());
        security.scoreBoard().marketStats().setBidImpliedVol(greeks.bidImpliedVol());
        security.scoreBoard().marketStats().setAskImpliedVol(greeks.askImpliedVol());
        vega = greeks.vega();
    }

    @Override
    public void onOrderBookUpdated(final Security srcSecurity, long transactTime, MarketOrderBook orderBook) throws Exception {
        processTick(transactTime);
    }

    @Override
    public void onTradeReceived(final Security srcSecurity, long timestamp, MarketTrade trade) throws Exception {
    }

    private void processTick(final long nanoOfDay) {
        int mmBidLevel = 0;
        int mmBidPrice = 0;
        if (!security.orderBook().bidSide().isEmpty()) {
            Tick tick = security.orderBook().bidSide().bestOrNullIfEmpty();
            if (tick.qty() >= security.wrtParams().mmBidSize()) {
                mmBidLevel = tick.tickLevel();
                mmBidPrice = tick.price();
            }
            else {
                final Iterator<Tick> iterator = security.orderBook().bidSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= security.wrtParams().mmBidSize()) {                        
                        mmBidLevel = tick.tickLevel();
                        mmBidPrice = tick.price();
                        break;
                    }
                }
            }
        }
        if (mmBidLevel > SpreadTable.SPREAD_TABLE_MIN_LEVEL) {
            final int prevTickSize = tickSize;
            tickSize = mmBidPrice - security.spreadTable().tickToPrice(mmBidLevel - 1);
            if (prevTickSize != tickSize) {
                updateVolPerTick();
            }
        }
    }

    private void updateVolPerTick() {
        if (vega != 0) {
            security.scoreBoard().marketStats().setVolPerTick((int)((tickSize * security.convRatio()) / (vega / 10000f)));
        }
    }

}
