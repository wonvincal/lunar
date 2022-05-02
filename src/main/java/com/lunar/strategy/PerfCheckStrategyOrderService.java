package com.lunar.strategy;

import java.util.concurrent.CompletableFuture;

import com.lunar.core.SubscriberList;
import com.lunar.core.SystemClock;
import com.lunar.core.TriggerInfo;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;

public class PerfCheckStrategyOrderService extends NormalStrategyOrderService {
    private final SpreadTable spreadTable = SpreadTableBuilder.get();

    public PerfCheckStrategyOrderService(final Messenger messenger, final SystemClock systemClock, final TriggerInfo triggerInfo, final StrategyErrorHandler errorHandler, final SubscriberList performanceSubscribers) {
        super(messenger, systemClock, triggerInfo, errorHandler, performanceSubscribers);
    }
    
    @Override
    protected CompletableFuture<OrderRequest> createBuyOrderFuture(StrategySecurity security, int price, long quantity, StrategyExplain explain, TriggerInfo triggerInfo) {
        final int priceLevel = spreadTable.priceToTick(price);
        final int newPrice = priceLevel > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? spreadTable.tickToPrice(Math.max(priceLevel - 5, SpreadTable.SPREAD_TABLE_MIN_LEVEL)) : price;
        final NewOrderRequest request = NewOrderRequest.of(messenger().getNextClientKeyAndIncrement(), 
                messenger().self(),
                security, 
                OrderType.LIMIT_THEN_CANCEL_ORDER, 
                (int)quantity, 
                Side.BUY, 
                TimeInForce.FILL_AND_KILL, 
                BooleanType.TRUE, 
                newPrice,
                newPrice,
                Long.MAX_VALUE,
                false,
                security.assignedThrottleTrackerIndex(),
                FAKE_PORT_SID,
                true,
                triggerInfo);
        return messenger().sendNewCompositeOrder(messenger().referenceManager().omes(), request);
    }

}
