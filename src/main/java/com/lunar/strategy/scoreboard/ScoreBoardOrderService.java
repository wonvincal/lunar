package com.lunar.strategy.scoreboard;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SystemClock;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.order.Tick;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.StrategyExplain;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.scoreboard.OurTriggerHandler.OurTriggerExplain;

public class ScoreBoardOrderService implements StrategyOrderService {
    private static final Logger LOG = LogManager.getLogger(ScoreBoardOrderService.class);

    final private SystemClock systemClock;
    private boolean isInMarketHour;
    private boolean isRecovery;
    
    private int velocityIndex = -1;
    private int underlyingBidIndex = -1;
    private int underlyingAskIndex = -1;
    private int underlyingSpotIndex = -1;
    private int deltaIndex = -1;
    private int pricingModeIndex = -1;
    
    final private OurTriggerExplain ourTriggerExplain = new OurTriggerExplain();
    
    public ScoreBoardOrderService(final SystemClock systemClock) {
        this.systemClock = systemClock;
    }
    
    @Override
    public void buy(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain) {
        try {
            LOG.info("Sent order request: buy, secCode {}, price {}, quantity {}, ob time {}, trigger seqNum {}", security.code(), box(price), box(quantity), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
            final Tick tick = security.orderBook().bestAskOrNullIfEmpty();
            if (tick != null) {
                // we do not support additional buy but we just want its trigger time
                if (security.position() == 0) {
                    security.updatePosition(quantity);
                    security.orderStatusReceivedHandler().onOrderStatusReceived(systemClock.nanoOfDay(), price, quantity, OrderRequestRejectType.NULL_VAL);
                    findStrategyExplainIndices(explain);
                    updateOurTriggerExplain(explain);
                    ((ScoreBoardSecurityInfo)security).ourTriggerHandler().onOurTriggerBuyReceived(systemClock.nanoOfDay(), tick.price(), ourTriggerExplain, false, explain.triggerSeqNum());
                }
                else {
                    security.orderStatusReceivedHandler().onOrderStatusReceived(systemClock.nanoOfDay(), price, quantity, OrderRequestRejectType.NULL_VAL);
                    updateOurTriggerExplain(explain);
                    ((ScoreBoardSecurityInfo)security).ourTriggerHandler().onOurTriggerBuyReceived(systemClock.nanoOfDay(), tick.price(), ourTriggerExplain, true, explain.triggerSeqNum());
                }
            }
            else {
                security.orderStatusReceivedHandler().onOrderStatusReceived(systemClock.nanoOfDay(), 0, 0, OrderRequestRejectType.INVALID_PRICE);
            }
        }
        catch (final Exception e) {
            
        }
    }

    @Override
    public void sell(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain) {
        try {
            LOG.info("Sent order request: sell, secCode {}, price {}, quantity {}, ob time {}, trigger seqNum {}", security.code(), box(price), box(quantity), box(security.orderBook().transactNanoOfDay()), box(explain.triggerSeqNum()));
            long outstanding = quantity;
            int minPrice = 0;
            final Iterator<Tick> iterator = security.orderBook().bidSide().localPriceLevelsIterator();
            while (iterator.hasNext()) {
                final Tick tick = iterator.next();
                if (tick.qty() <= 0)
                    break;
                if (tick.price() >= price) {
                    minPrice = tick.price();
                    final long quantityBought = Math.min(outstanding, tick.qty());
                    outstanding -= quantityBought;
                    if (outstanding == 0)
                        break;
                }
            }
            if (outstanding != quantity) {
                security.updatePosition(outstanding - quantity);
                final long position = security.position();
                security.orderStatusReceivedHandler().onOrderStatusReceived(systemClock.nanoOfDay(), price, quantity, OrderRequestRejectType.NULL_VAL);
                if (position == 0) {
                    updateOurTriggerExplain(explain);
                    ((ScoreBoardSecurityInfo)security).ourTriggerHandler().onOurTriggerSellReceived(systemClock.nanoOfDay(), minPrice, ourTriggerExplain, explain.triggerSeqNum());
                }
            }
            else {
                security.orderStatusReceivedHandler().onOrderStatusReceived(systemClock.nanoOfDay(), 0, 0, OrderRequestRejectType.INVALID_PRICE);
            }
        }
        catch (final Exception e) {
            
        }
    }

    @Override
    public void sellLimit(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain) {
        
    }

    @Override
    public void sellToExit(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain) {
        sell(security, price, quantity, explain);
    }

    @Override
    public void cancelAndSellOutstandingSell(final StrategySecurity security, final int price, final StrategyExplain explain) {
        
    }

    @Override
    public boolean canTrade() {
        return isInMarketHour && !isRecovery;
    }

    public StrategyOrderService isInMarketHour(final boolean isInMarketHour) {
        this.isInMarketHour = isInMarketHour;
        return this;
    }

    public boolean isInMarketHour() {
        return isInMarketHour;
    }

    public StrategyOrderService isRecovery(final boolean isRecovery) {
        this.isRecovery = isRecovery;
        return this;
    }

    public boolean isRecovery() {
        return isRecovery;
    }
    
    private void updateOurTriggerExplain(final StrategyExplain explain) {
        ourTriggerExplain.triggerValue = explain.eventValues()[velocityIndex];
        ourTriggerExplain.undBid = explain.eventValues()[underlyingBidIndex] * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        ourTriggerExplain.undAsk = explain.eventValues()[underlyingAskIndex] * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        ourTriggerExplain.undSpot = explain.eventValues()[underlyingSpotIndex];
        ourTriggerExplain.delta = (int)explain.eventValues()[deltaIndex];
        ourTriggerExplain.pricingMode = PricingMode.get((byte)explain.eventValues()[pricingModeIndex]);
    }
    
    private void findStrategyExplainIndices(final StrategyExplain explain) {
        velocityIndex = getStrategyExplainIndex(velocityIndex, explain, EventValueType.VELOCITY);
        underlyingBidIndex = getStrategyExplainIndex(underlyingBidIndex, explain, EventValueType.UNDERLYING_BID);
        underlyingAskIndex = getStrategyExplainIndex(underlyingAskIndex, explain, EventValueType.UNDERLYING_ASK);
        underlyingSpotIndex = getStrategyExplainIndex(underlyingSpotIndex, explain, EventValueType.WAVG_SPOT);
        deltaIndex = getStrategyExplainIndex(deltaIndex, explain, EventValueType.DELTA);
        pricingModeIndex = getStrategyExplainIndex(pricingModeIndex, explain, EventValueType.PRICING_MODE);
    }
    
    private int getStrategyExplainIndex(int index, final StrategyExplain explain, final EventValueType eventValue) {
        if (index == -1) {
            for (index = 0; index < explain.eventValueTypes().length; index++) {
                if (explain.eventValueTypes()[index].value() == eventValue.value())
                    break;
            }
        }
        return index;
    }
}
