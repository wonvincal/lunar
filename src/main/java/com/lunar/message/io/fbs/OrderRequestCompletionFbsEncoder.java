package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.order.OrderRequest;

public class OrderRequestCompletionFbsEncoder {
	static final Logger LOG = LogManager.getLogger(OrderRequestCompletionFbsEncoder.class);

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, OrderRequest request, int clientKey){
		stringBuffer.clear();
		stringBuffer.put(request.reason().getBytes());
		stringBuffer.flip();
		int reasonOffset = builder.createString(stringBuffer);
		
	    OrderRequestCompletionFbs.startOrderRequestCompletionFbs(builder);
        OrderRequestCompletionFbs.addClientKey(builder, clientKey);
        OrderRequestCompletionFbs.addOrderSid(builder, request.orderSid());
        OrderRequestCompletionFbs.addCompletionType(builder, request.completionType().value());
        OrderRequestCompletionFbs.addRejectType(builder, request.rejectType().value());
        OrderRequestCompletionFbs.addReason(builder, reasonOffset);
	    return OrderRequestCompletionFbs.endOrderRequestCompletionFbs(builder);
	}
}
