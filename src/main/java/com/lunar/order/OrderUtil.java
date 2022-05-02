package com.lunar.order;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.sender.OrderSender;

import org.agrona.MutableDirectBuffer;

public class OrderUtil {
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, Order o, MutableDirectBuffer stringBuffer){
		order.wrap(buffer, offset)
		.channelId(o.channelId())
		.channelSnapshotSeq(o.channelSeq())
		.createTime(o.createTime())
		.updateTime(o.updateTime())
		.isAlgoOrder(o.isAlgo())
		.orderSid(o.sid())
		.orderId(o.orderId())
		.orderType(o.orderType())
		.quantity(o.quantity())
		.secSid(o.secSid())
		.side(o.side())
		.status(o.status())
		.limitPrice(o.limitPrice())
		.stopPrice(o.stopPrice())
		.tif(o.timeInForce())
		.leavesQty(o.leavesQty())
		.cumulativeQty(o.cumulativeExecQty())
		.orderRejectType(o.orderRejectType())
		.putReason(OrderSender.prepareRejectedReason(o.reason(), stringBuffer), 0);
	}
	
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderAcceptedWithOrderInfoSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(OrderSbeEncoder.updateTimeNullValue())
		.isAlgoOrder(source.isAlgoOrder())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.orderType(source.orderType())
		.quantity(source.quantity())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.limitPrice(source.limitPrice())
		.stopPrice(source.stopPrice())
		.tif(source.tif())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.orderRejectType(OrderRejectType.NULL_VAL);
	}

	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderAcceptedWithOrderInfoSbeDecoder source){
		order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
    	.leavesQty(source.leavesQty())
    	.cumulativeQty(source.cumulativeQty())
    	.status(source.status())
    	.updateTime(source.updateTime());
	}
	
	/**
	 * Populate an order with as many fields ad possible from orderAccepted
	 * @param order
	 * @param buffer
	 * @param offset
	 * @param source
	 */
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderAcceptedSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.isAlgoOrder(BooleanType.NULL_VAL)
		.orderType(OrderType.NULL_VAL)
		.quantity(OrderSbeEncoder.quantityNullValue())
		.limitPrice(OrderSbeEncoder.limitPriceNullValue())
		.stopPrice(OrderSbeEncoder.stopPriceNullValue())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.createTime(OrderSbeEncoder.createTimeNullValue())
		.updateTime(source.updateTime())
		.tif(TimeInForce.NULL_VAL)
		.orderRejectType(OrderRejectType.NULL_VAL);
	}
	
	/**
	 * Merge orderAccepted into an order, use only fields that are significant in an orderAccepted message
	 * @param order
	 * @param buffer
	 * @param offset
	 * @param source
	 */
	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderAcceptedSbeDecoder source){
    	order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status())
		.updateTime(source.updateTime());
	}

	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderCancelledSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.orderSid(source.origOrderSid())
		.orderId(source.orderId())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.createTime(OrderSbeEncoder.createTimeNullValue())
		.updateTime(source.updateTime())
		.isAlgoOrder(BooleanType.NULL_VAL)
		.orderType(OrderType.NULL_VAL)
		.quantity(OrderSbeEncoder.quantityNullValue())
		.limitPrice(OrderSbeEncoder.limitPriceNullValue())
		.stopPrice(OrderSbeEncoder.stopPriceNullValue())
		.tif(TimeInForce.NULL_VAL)
		.orderRejectType(OrderRejectType.NULL_VAL);
	}

	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderCancelledSbeDecoder source){
		order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status())
		.updateTime(source.updateTime());
	}

	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderCancelledWithOrderInfoSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(source.updateTime())
		.isAlgoOrder(source.isAlgoOrder())
		.orderSid(source.origOrderSid())
		.orderId(source.orderId())
		.orderType(source.orderType())
		.quantity(source.quantity())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.limitPrice(source.limitPrice())
		.stopPrice(source.stopPrice())
		.tif(source.tif())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.orderRejectType(OrderRejectType.NULL_VAL);
	}
	
	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderCancelledWithOrderInfoSbeDecoder source){
		order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status())
		.updateTime(source.updateTime());
	}
	
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderExpiredSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.createTime(OrderSbeEncoder.createTimeNullValue())
		.updateTime(source.updateTime())
		.isAlgoOrder(BooleanType.NULL_VAL)
		.orderType(OrderType.NULL_VAL)
		.quantity(OrderSbeEncoder.quantityNullValue())
		.limitPrice(OrderSbeEncoder.limitPriceNullValue())
		.stopPrice(OrderSbeEncoder.stopPriceNullValue())
		.tif(TimeInForce.NULL_VAL)
		.orderRejectType(OrderRejectType.NULL_VAL);
	}

	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderExpiredSbeDecoder source){
		order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status())
		.updateTime(source.updateTime());
	}
	
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderExpiredWithOrderInfoSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(source.updateTime())
		.isAlgoOrder(source.isAlgoOrder())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.orderType(source.orderType())
		.quantity(source.quantity())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.limitPrice(source.limitPrice())
		.stopPrice(source.stopPrice())
		.tif(source.tif())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.orderRejectType(OrderRejectType.NULL_VAL);
	}

	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderExpiredWithOrderInfoSbeDecoder source){
		order.wrap(buffer, 0)
    	.channelSnapshotSeq(source.channelSeq())
    	.leavesQty(source.leavesQty())
    	.cumulativeQty(source.cumulativeQty())
    	.status(source.status())
    	.updateTime(source.updateTime());
	}
	
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderRejectedSbeDecoder source, MutableDirectBuffer stringBuffer){
		source.getReason(stringBuffer.byteArray(), 0);

		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.createTime(OrderSbeEncoder.createTimeNullValue())
		.updateTime(source.updateTime())
		.isAlgoOrder(BooleanType.NULL_VAL)
		.orderType(OrderType.NULL_VAL)
		.quantity(OrderSbeEncoder.quantityNullValue())
		.limitPrice(OrderSbeEncoder.limitPriceNullValue())
		.stopPrice(OrderSbeEncoder.stopPriceNullValue())
		.tif(TimeInForce.NULL_VAL)
		.orderRejectType(source.rejectType())
		.putReason(stringBuffer.byteArray(), 0);
	}

	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderRejectedSbeDecoder source, MutableDirectBuffer stringBuffer){
		source.getReason(stringBuffer.byteArray(), 0);
		order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status())
		.updateTime(source.updateTime())
		.orderRejectType(source.rejectType())
		.putReason(stringBuffer.byteArray(), 0);
	}

	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderRejectedWithOrderInfoSbeDecoder source, MutableDirectBuffer stringBuffer){
		source.getReason(stringBuffer.byteArray(), 0);
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(source.updateTime())
		.isAlgoOrder(source.isAlgoOrder())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.orderType(source.orderType())
		.quantity(source.quantity())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.limitPrice(source.limitPrice())
		.stopPrice(source.stopPrice())
		.tif(source.tif())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.orderRejectType(source.rejectType())
		.putReason(stringBuffer.byteArray(), 0);
	}
	
	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, OrderRejectedWithOrderInfoSbeDecoder source, MutableDirectBuffer stringBuffer){
		source.getReason(stringBuffer.byteArray(), 0);
    	order.wrap(buffer, offset)
    	.channelSnapshotSeq(source.channelSeq())
    	.updateTime(source.updateTime())
    	.leavesQty(source.leavesQty())
    	.cumulativeQty(source.cumulativeQty())
    	.orderRejectType(source.rejectType())
    	.status(source.status())
    	.putReason(stringBuffer.byteArray(), 0);
	}
	
	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, TradeCreatedSbeDecoder source){
    	order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.createTime(OrderSbeEncoder.createTimeNullValue())
		.updateTime(source.updateTime())
		.isAlgoOrder(BooleanType.NULL_VAL)
		.orderType(OrderType.NULL_VAL)
		.quantity(OrderSbeEncoder.quantityNullValue())
		.limitPrice(OrderSbeEncoder.limitPriceNullValue())
		.stopPrice(OrderSbeEncoder.stopPriceNullValue())
		.tif(TimeInForce.NULL_VAL)
		.orderRejectType(OrderRejectType.NULL_VAL);
	}
	
	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, TradeCreatedSbeDecoder source){
		order.wrap(buffer, offset)
		.channelSnapshotSeq(source.channelSeq())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status())
		.updateTime(source.updateTime());
	}

	public static void populateFrom(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, TradeCreatedWithOrderInfoSbeDecoder source){
		order.wrap(buffer, offset)
		.channelId(source.channelId())
		.channelSnapshotSeq(source.channelSeq())
		.createTime(source.updateTime())
		.updateTime(source.updateTime())
		.isAlgoOrder(source.isAlgoOrder())
		.orderSid(source.orderSid())
		.orderId(source.orderId())
		.orderType(source.orderType())
		.quantity(source.quantity())
		.secSid(source.secSid())
		.side(source.side())
		.status(source.status())
		.limitPrice(source.limitPrice())
		.stopPrice(source.stopPrice())
		.tif(source.tif())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.orderRejectType(OrderRejectType.NULL_VAL);
	}

	public static void mergeWith(OrderSbeEncoder order, MutableDirectBuffer buffer, int offset, TradeCreatedWithOrderInfoSbeDecoder source){
		order.wrap(buffer, 0)
		.channelSnapshotSeq(source.channelSeq())
		.updateTime(source.updateTime())
		.leavesQty(source.leavesQty())
		.cumulativeQty(source.cumulativeQty())
		.status(source.status());
	}
}
