package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.order.Order;
import com.lunar.order.OrderUtil;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class OrderFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(OrderFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
	private final OrderSbeEncoder sbeEncoder = new OrderSbeEncoder();
	private final OrderSbeDecoder sbeDecoder = new OrderSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    Order order = Order.of(1, null, 123, 1234, BooleanType.FALSE, OrderType.LIMIT_ORDER, Side.BUY, 120, 121, TimeInForce.FILL_AND_KILL, OrderStatus.NEW);
	    String reason = "nothing goes wrong";
	    order.reason(reason);
	    int channelId = 5;
		order.channelId(channelId);
		long channelSeq = 123456;
		order.channelSeq(channelSeq);
	    OrderUtil.populateFrom(sbeEncoder, buffer, 0, order, stringBuffer);
		sbeDecoder.wrap(buffer, 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		
		int offset = OrderFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		OrderFbs orderFbs = OrderFbs.getRootAsOrderFbs(builder.dataBuffer());
		assertEquals(order.channelId(), orderFbs.channelId());
		assertEquals(order.stopPrice(), orderFbs.stopPrice());
		assertEquals(order.limitPrice(), orderFbs.limitPrice());
		assertEquals(order.side().value(), orderFbs.side());
		assertEquals(order.status().value(), orderFbs.status());
		assertEquals(0, reason.compareTo(orderFbs.reason().trim()));
	}
	
}

