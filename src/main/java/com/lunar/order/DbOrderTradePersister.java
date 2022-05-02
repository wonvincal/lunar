package com.lunar.order;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.lunar.core.SystemClock;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.entity.SidManager;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.service.ServiceConstant;

public class DbOrderTradePersister implements OrderTradePersister {
    private final SystemClock systemClock;
    private final DbConnection connection;
    private final SidManager<String> securitySidManager;
    final byte[] exchangeOrderIdBytes = new byte[OrderSbeDecoder.extIdLength()];
    final byte[] reasonBytes = new byte[OrderSbeDecoder.reasonLength()];
    final byte[] executionIdBytes = new byte[TradeSbeDecoder.executionIdLength()];
    
    public DbOrderTradePersister(final SystemClock systemClock, final DbConnection connection, final SidManager<String> securitySidManager) {
        this.systemClock = systemClock;
        this.connection = connection;
        this.securitySidManager = securitySidManager;
    }
    
    @Override
    public void persistOrder(final OrderSbeDecoder order) throws Exception {
        order.getExtId(exchangeOrderIdBytes, 0);
        final String exchangeId = new String(exchangeOrderIdBytes, OrderSbeDecoder.extIdCharacterEncoding()).trim();
        order.getReason(reasonBytes, 0);
        final String reason = new String(reasonBytes, OrderSbeDecoder.reasonCharacterEncoding()).trim();
        final long createTime = order.createTime();
        long updateTime = order.updateTime();
        if (updateTime == ServiceConstant.NULL_TIME_NS) {
            updateTime = order.createTime();
        }        
        connection.executeNonQuery(LunarQueries.insertOrUpdateOrder(systemClock.dateString(),                
                order.orderSid(),
                exchangeId,
                securitySidManager.getKeyForSid(order.secSid()),
                order.orderType().value(),
                order.quantity(),
                order.side().value(),
                order.tif().value(),
                order.isAlgoOrder().value(),
                order.limitPrice(),
                order.stopPrice(),
                order.status().value(),
                order.cumulativeQty(),
                order.leavesQty(),
                order.parentOrderSid(),
                order.orderRejectType().value(),
                reason,
                LocalDateTime.of(systemClock.date(), LocalTime.ofNanoOfDay(createTime)),
                LocalDateTime.of(systemClock.date(), LocalTime.ofNanoOfDay(updateTime))));
    }

    @Override
    public void persistTrade(final TradeSbeDecoder trade) throws Exception {
        trade.getExecutionId(executionIdBytes, 0);
        final String executionId = new String(executionIdBytes, TradeSbeDecoder.executionIdCharacterEncoding());
        final long createTime = trade.createTime();
        long updateTime = trade.updateTime();
        if (updateTime == ServiceConstant.NULL_TIME_NS) {
            updateTime = createTime;
        }
        connection.executeNonQuery(LunarQueries.insertOrUpdateTrade(systemClock.dateString(),
                trade.tradeSid(),
                trade.orderSid(),
                securitySidManager.getKeyForSid(trade.secSid()),
                trade.side().value(),
                trade.tradeStatus().value(),
                executionId,
                trade.executionPrice(),
                trade.executionQty(),
                trade.cumulativeQty(),
                trade.leavesQty(),
                LocalDateTime.of(systemClock.date(), LocalTime.ofNanoOfDay(createTime)),
                LocalDateTime.of(systemClock.date(), LocalTime.ofNanoOfDay(updateTime))));
    }

}
