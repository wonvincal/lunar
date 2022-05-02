package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;

@RunWith(MockitoJUnitRunner.class)
public class OrderManagementContextTest {
	@Mock
	private MessageSink selfSink;

	private OrderManagementContext context;
	private final int numOutstandingRequests = 1024;
	private final int numSecurities = 1024;
	private final int numOutstandingOrdersPerSecurity = 32;
	private final int numChannels = 2;

	private MessageSinkRef self;
	private Security security;
	
	@Before
	public void setup(){
		context = OrderManagementContext.of(numOutstandingRequests, numSecurities, numOutstandingOrdersPerSecurity, numChannels);
		security = Security.of(12345678L, SecurityType.WARRANT, "61727", 101, false, SpreadTableBuilder.get(SecurityType.WARRANT));
	}
	
	@Test
	public void testAddOrderUpdateSubscriber(){
		assertEquals(0, context.orderUpdateSubscribers().length);

		
		MessageSinkRef first = MessageSinkRef.of(DummyMessageSink.of(1, "test1", ServiceType.AdminService));
		context.addOrderUpdateSubscriber(first);
		assertEquals(1, context.orderUpdateSubscribers().length);

		MessageSinkRef second = MessageSinkRef.of(DummyMessageSink.of(2, "test2", ServiceType.AdminService));
		context.addOrderUpdateSubscriber(second);
		assertEquals(2, context.orderUpdateSubscribers().length);

		MessageSinkRef third = MessageSinkRef.of(DummyMessageSink.of(3, "test3", ServiceType.AdminService));
		context.addOrderUpdateSubscriber(third);
		assertEquals(3, context.orderUpdateSubscribers().length);
	}

	@Test
	public void testOrderRequest(){
		int ordSid = 1001;
		OrderRequest request = context.getOrderRequest(ordSid);
		assertNull(request);
		
		int clientKey = 100;
		NewOrderRequest newRequest = NewOrderRequest.of(
				clientKey,
				self, 
				security, 
				OrderType.LIMIT_ORDER, 
				100, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				65000, 
				65000, 
				2);
		newRequest.orderSid(ordSid);
		context.putOrderRequest(ordSid, newRequest);
		
		request = context.getOrderRequest(ordSid);
		assertNotNull(request);
		
		OrderRequest removed = context.removeOrderRequest(ordSid);
		assertNotNull(removed);
		assertEquals(newRequest, removed);
		
		request = context.getOrderRequest(ordSid);
		assertNull(request);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testPutSameRequestTwice(){
		int ordSid = 1001;
		int clientKey = 100;
		NewOrderRequest newRequest = NewOrderRequest.of(
				clientKey,
				self, 
				security, 
				OrderType.LIMIT_ORDER, 
				100, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				65000, 
				65000, 
				2);
		newRequest.orderSid(ordSid);
		context.putOrderRequest(ordSid, newRequest);
		context.putOrderRequest(ordSid, newRequest);
	}
	
	@Test
	public void testOrderBook(){
		long secSid = 1000;
		ValidationOrderBook orderBook = context.securiytLevelInfo(secSid).validationOrderBook();
		assertNotNull(orderBook);
	}
}
