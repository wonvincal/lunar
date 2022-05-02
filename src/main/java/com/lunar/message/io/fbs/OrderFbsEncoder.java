package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.service.ServiceConstant;

public class OrderFbsEncoder {
	static final Logger LOG = LogManager.getLogger(OrderFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, OrderSbeDecoder order){
		int limit = order.limit();
        stringBuffer.clear();
        stringBuffer.limit(order.getExtId(stringBuffer.array(), 0));
        int extIdOffset = builder.createString(stringBuffer);
        
        stringBuffer.clear();
        stringBuffer.limit(order.getReason(stringBuffer.array(), 0));
        int reasonOffset = builder.createString(stringBuffer); 
	    
	    OrderFbs.startOrderFbs(builder);
	    if (order.channelId() != OrderSbeDecoder.channelIdNullValue()){
	    	OrderFbs.addChannelId(builder, order.channelId());
	    }
	    if (order.channelSnapshotSeq() != OrderSbeDecoder.channelSnapshotSeqNullValue()){
	    	OrderFbs.addChannelSnapshotSeq(builder, order.channelSnapshotSeq());
	    }
	    OrderFbs.addOrderSid(builder, order.orderSid());
	    OrderFbs.addSecSid(builder, order.secSid());
	    OrderFbs.addExtId(builder, extIdOffset);
	    OrderFbs.addReason(builder, reasonOffset);
	    OrderFbs.addOrderId(builder, order.orderId());
	    OrderFbs.addOrderType(builder, order.orderType().value());
	    OrderFbs.addQuantity(builder, order.quantity());
	    OrderFbs.addSide(builder, order.side().value());
	    OrderFbs.addTif(builder, order.tif().value());
	    if (order.createTime() != ServiceConstant.NULL_TIME_NS){
	    	OrderFbs.addCreateTime(builder, order.createTime());
	    }
	    if (order.updateTime() != ServiceConstant.NULL_TIME_NS){
	    	OrderFbs.addUpdateTime(builder, order.updateTime());
	    }
	    OrderFbs.addIsAlgoOrder(builder, order.isAlgoOrder() == BooleanType.TRUE);
	    OrderFbs.addLimitPrice(builder, order.limitPrice());
	    OrderFbs.addStopPrice(builder, order.stopPrice());
	    OrderFbs.addStatus(builder, order.status().value());
	    OrderFbs.addCumulativeQty(builder, order.cumulativeQty());
	    OrderFbs.addLeavesQty(builder, order.leavesQty());
	    if (order.parentOrderSid() != OrderSbeDecoder.parentOrderSidNullValue()){
	    	OrderFbs.addParentOrderSid(builder, order.parentOrderSid());
	    }
	    OrderFbs.addOrderRejectType(builder, order.orderRejectType().value());
	    order.limit(limit);
	    return OrderFbs.endOrderFbs(builder);
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, NewOrderRequest request){
		NewOrderRequestFbs.startNewOrderRequestFbs(builder);
		NewOrderRequestFbs.addClientKey(builder, request.clientKey());
		NewOrderRequestFbs.addIsAlgoOrder(builder, request.isAlgoOrder() == BooleanType.TRUE ? true : false);
		NewOrderRequestFbs.addLimitPrice(builder, request.limitPrice());
		NewOrderRequestFbs.addOrderType(builder, request.orderType().value());
		NewOrderRequestFbs.addPortSid(builder, request.portSid());
		NewOrderRequestFbs.addQuantity(builder, request.quantity());
		NewOrderRequestFbs.addSecSid(builder, request.secSid());
		NewOrderRequestFbs.addSide(builder, request.side().value());
		NewOrderRequestFbs.addStopPrice(builder, request.stopPrice());
		NewOrderRequestFbs.addTif(builder, request.tif().value());
		NewOrderRequestFbs.addTimeoutAt(builder, request.timeoutAtNanoOfDay());
		NewOrderRequestFbs.addRetry(builder, request.retry());
		return NewOrderRequestFbs.endNewOrderRequestFbs(builder);		
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, NewOrderRequestSbeDecoder request){
		int limit = request.limit();
		NewOrderRequestFbs.startNewOrderRequestFbs(builder);
		NewOrderRequestFbs.addClientKey(builder, request.clientKey());
		NewOrderRequestFbs.addIsAlgoOrder(builder, request.isAlgoOrder() == BooleanType.TRUE ? true : false);
		NewOrderRequestFbs.addLimitPrice(builder, request.limitPrice());
		NewOrderRequestFbs.addOrderType(builder, request.orderType().value());
		NewOrderRequestFbs.addPortSid(builder, request.portSid());
		NewOrderRequestFbs.addQuantity(builder, request.quantity());
		NewOrderRequestFbs.addSecSid(builder, request.secSid());
		NewOrderRequestFbs.addSide(builder, request.side().value());
		NewOrderRequestFbs.addStopPrice(builder, request.stopPrice());
		NewOrderRequestFbs.addTif(builder, request.tif().value());
		NewOrderRequestFbs.addTimeoutAt(builder, request.timeoutAt());
		NewOrderRequestFbs.addRetry(builder, request.retry() == BooleanType.TRUE ? true : false);
		request.limit(limit);
		return NewOrderRequestFbs.endNewOrderRequestFbs(builder);
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, CancelOrderRequest request){
		CancelOrderRequestFbs.startCancelOrderRequestFbs(builder);
		CancelOrderRequestFbs.addClientKey(builder, request.clientKey());
		CancelOrderRequestFbs.addOrderSidToBeCancelled(builder, request.ordSidToBeCancelled());
		CancelOrderRequestFbs.addSecSid(builder, request.secSid());
		CancelOrderRequestFbs.addSide(builder, request.side().value());
		CancelOrderRequestFbs.addTimeoutAt(builder, request.timeoutAtNanoOfDay());
		CancelOrderRequestFbs.addRetry(builder, request.retry());
		return CancelOrderRequestFbs.endCancelOrderRequestFbs(builder);
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, CancelOrderRequestSbeDecoder request){
		int limit = request.limit();
		CancelOrderRequestFbs.startCancelOrderRequestFbs(builder);
		CancelOrderRequestFbs.addClientKey(builder, request.clientKey());
		CancelOrderRequestFbs.addOrderSidToBeCancelled(builder, request.orderSidToBeCancelled());
		CancelOrderRequestFbs.addSecSid(builder, request.secSid());
		CancelOrderRequestFbs.addSide(builder, request.side().value());
		CancelOrderRequestFbs.addTimeoutAt(builder, request.timeoutAt());
		CancelOrderRequestFbs.addRetry(builder, request.retry() == BooleanType.TRUE ? true : false);
		request.limit(limit);
		return CancelOrderRequestFbs.endCancelOrderRequestFbs(builder);
	}

}
