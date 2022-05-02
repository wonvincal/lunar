package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.order.NewOrderRequest;
import com.lunar.service.ServiceConstant;

public class OrderRequestCompletionFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(OrderRequestCompletionFbsEncoderTest.class);

	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);

	@Test
	public void test(){
		int systemId = 1;
		int sinkId = 4;
		String name = "test";
		long secSid = 123456;
		String reason = "reject on new";
		ServiceType serviceType = ServiceType.DashboardService;
		NewOrderRequest request = NewOrderRequest.ofWithDefaults(88888, 
				DummyMessageSink.refOf(systemId, sinkId, name, serviceType), 
				secSid,
				OrderType.LIMIT_ORDER, 
				10000, 
				Side.BUY,
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				System.nanoTime(),
				false,
				12345);
		int ordSid = 838383;
		request.orderSid(ordSid);
		request.reason(reason.getBytes());
		LOG.info("{}", request.reason());
		int clientKey = 737373;
		request.clientKey(clientKey);
		
		int offset = OrderRequestCompletionFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, request, clientKey);
		builder.finish(offset);

		OrderRequestCompletionFbs fbs = OrderRequestCompletionFbs.getRootAsOrderRequestCompletionFbs(builder.dataBuffer());
		assertEquals(request.clientKey(), fbs.clientKey());
		assertEquals(0, fbs.reason().trim().compareTo(reason));
	}
}
