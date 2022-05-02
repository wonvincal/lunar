package com.lunar.strategy;

import com.lunar.core.SubscriberList;
import com.lunar.core.SystemClock;
import com.lunar.core.TriggerInfo;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.OrderType;

public class LunarStrategyOrderService implements StrategyOrderService {
    final private StrategyOrderService normalOrderService;
    final private StrategyOrderService perfCheckOrderService;
    private StrategyOrderService orderService;
    private boolean isInMarketHour;
    private boolean isRecovery;
    
    public LunarStrategyOrderService(final Messenger messenger, final SystemClock systemClock, final TriggerInfo triggerInfo, final StrategyErrorHandler errorHandler, final SubscriberList performanceSubscribers) {
        this.normalOrderService = new NormalStrategyOrderService(messenger, systemClock, triggerInfo, errorHandler, performanceSubscribers);
        this.perfCheckOrderService = new PerfCheckStrategyOrderService(messenger, systemClock, triggerInfo, errorHandler, performanceSubscribers);
        this.orderService = normalOrderService;
    }

    @Override
    public void buy(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
        orderService.buy(security, price, quantity, explain);
    }

    @Override
    public void sell(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
        orderService.sell(security, price, quantity, explain);
    }

    @Override
    public void sellLimit(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain) {
        orderService.sellLimit(security, price, quantity, explain);
    }

    @Override
    public void sellToExit(StrategySecurity security, int price, long quantity, StrategyExplain explain) {
        orderService.sellToExit(security, price, quantity, explain);
    }

    @Override
    public void cancelAndSellOutstandingSell(StrategySecurity security, int price, StrategyExplain explain) {
        orderService.cancelAndSellOutstandingSell(security, price, explain);
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

    @Override
    public void setBuyOrderType(final OrderType orderType) {
        if (orderType == OrderType.ENHANCED_LIMIT_ORDER) {
            orderService = normalOrderService;
        }
        else if (orderType == OrderType.LIMIT_THEN_CANCEL_ORDER) {
            orderService = perfCheckOrderService;
        }
    }

}
