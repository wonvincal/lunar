package com.lunar.order;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.OrderSender;

public class TradeUtil {
	public static void populateFrom(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, Trade source, TradeSbeDecoder decoder, MutableDirectBuffer stringBuffer){
		populateFrom(trade, buffer, offset, source, stringBuffer);
    	decoder.wrap(buffer, offset, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
    }

	@SuppressWarnings("deprecation")
	public static void populateFrom(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, Trade source, MutableDirectBuffer stringBuffer){
    	trade.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.createTime())
		.updateTime(source.updateTime())
		.tradeSid(source.sid())
		.orderSid(source.orderSid())
		.secSid(source.secSid())
		.side(source.side())
		.orderId(source.orderId())
		.status(source.orderStatus())
		.tradeStatus(source.tradeStatus())
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionQty(source.executionQty())
		.executionPrice(source.executionPrice())
		.putExecutionId(OrderSender.prepareExecutionId(source.executionId(), stringBuffer), 0);
    }

	public static void populateFrom(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, TradeCreatedWithOrderInfoSbeDecoder source, MutableDirectBuffer stringBuffer){
    	trade.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(TradeSbeDecoder.updateTimeNullValue())
		.tradeSid(source.tradeSid())
		.orderSid(source.orderSid())
		.secSid(source.secSid())
		.side(source.side())
		.orderId(source.orderId())
		.status(source.status())
		.tradeStatus(TradeStatus.NEW)
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionQty(source.executionQty())
		.executionPrice(source.executionPrice());

    	// Look for a better method later
    	source.getExecutionId(stringBuffer.byteArray(), 0);
    	trade.putExecutionId(stringBuffer.byteArray(), 0);
    }

	public static void mergeWith(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, TradeCreatedWithOrderInfoSbeDecoder source, MutableDirectBuffer stringBuffer){
		trade.wrap(buffer, 0)
    	.channelSnapshotSeq(source.channelSeq())
		.updateTime(source.updateTime())
		.status(source.status())
		.tradeStatus(TradeStatus.NEW)
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionQty(source.executionQty())
		.executionPrice(source.executionPrice());

    	// Look for a better method later
    	source.getExecutionId(stringBuffer.byteArray(), 0);
    	trade.putExecutionId(stringBuffer.byteArray(), 0);
	}
	
	public static void populateFrom(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, TradeCreatedSbeDecoder source, MutableDirectBuffer stringBuffer){
    	trade.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(TradeSbeEncoder.updateTimeNullValue())
		.tradeSid(source.tradeSid())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionQty(source.executionQty())
		.executionPrice(source.executionPrice());

    	// Look for a better method later
    	source.getExecutionId(stringBuffer.byteArray(), 0);
    	trade.putExecutionId(stringBuffer.byteArray(), 0);
	}

	public static void mergeWith(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, TradeCreatedSbeDecoder source, MutableDirectBuffer stringBuffer){
    	trade.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
		.updateTime(source.updateTime())
		.status(source.status())
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionQty(source.executionQty())
		.executionPrice(source.executionPrice());

    	// Look for a better method later
    	source.getExecutionId(stringBuffer.byteArray(), 0);
    	trade.putExecutionId(stringBuffer.byteArray(), 0);
	}

	public static void populateFrom(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, TradeCancelledSbeDecoder source, MutableDirectBuffer stringBuffer){
		trade.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(TradeSbeEncoder.createTimeNullValue())
		.updateTime(source.updateTime())
		.tradeSid(source.tradeSid())
		.orderSid(source.orderSid())
		.secSid(source.secSid())
		.side(source.side())
		.orderId(source.orderId())
		.status(source.status())
		.tradeStatus(TradeStatus.CANCELLED)
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionPrice(source.cancelledExecutionPrice())
		.executionQty(source.cancelledExecutionQty());

    	// Look for a better method later
    	source.getExecutionId(stringBuffer.byteArray(), 0);
    	trade.putExecutionId(stringBuffer.byteArray(), 0);
	}

	public static void mergeWith(TradeSbeEncoder trade, MutableDirectBuffer buffer, int offset, TradeCancelledSbeDecoder source, MutableDirectBuffer stringBuffer){
		trade.wrap(buffer, 0)
    	.channelSnapshotSeq(source.channelSeq())
		.updateTime(source.updateTime())
		.status(source.status())
		.tradeStatus(TradeStatus.CANCELLED)
		.cumulativeQty(source.cumulativeQty())
		.leavesQty(source.leavesQty())
		.executionPrice(source.cancelledExecutionPrice())
		.executionQty(source.cancelledExecutionQty());
		
    	// Look for a better method later
    	source.getExecutionId(stringBuffer.byteArray(), 0);
    	trade.putExecutionId(stringBuffer.byteArray(), 0);
	}
}
