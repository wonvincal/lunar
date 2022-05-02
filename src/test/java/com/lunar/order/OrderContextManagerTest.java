package com.lunar.order;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.time.LocalTime;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.base.Strings;
import com.lunar.core.SequencingOnlyChannel;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.service.ServiceConstant;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class OrderContextManagerTest {
	private OrderContextManager orderContextManager;
	
	@Mock
	private MessageSink orderUpdateSubscriberSink;
	
	@Mock
	private MessageSink tradeSubscriberSink;

	@Mock
	private MessageSink omesSink;
	
	@Mock
	private MessageSink selfSink;
	
	@Mock
	private OrderSender orderSender;
	
	@Mock
	private Messenger messenger;
	
	@Mock
	private Messenger childMessenger;

	@Mock
	private MessageSinkRefMgr refMgr;
	
	@Mock
	private OrderRequestCompletionSender ordReqCompletionSender;
	
	@Mock
	private ExposureUpdateHandler exposureUpdateHandler;
	
	private MessageSinkRef omes;
	private OrderManagementContext orderManagementContext;
	private MessageSinkRef self;
	private MessageSinkRef orderUpdateSubscriber = MessageSinkRef.createValidNullSinkRef(1, ServiceType.DashboardService, "test-order-update-subscriber");
	private Security security;
	private MutableDirectBuffer buffer;
	private NewOrderRequest newOrderRequest;
	private MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	
	@Before
	public void setup(){
		omes = MessageSinkRef.of(omesSink, "test-omes");
		TestHelper helper = TestHelper.of();
		buffer = helper.createDirectBuffer();
		orderManagementContext = OrderManagementContext.of(128, 128, 128, 2);
		orderManagementContext.addOrderUpdateSubscriber(orderUpdateSubscriber);
		
		when(refMgr.omes()).thenReturn(omes);
		when(messenger.createChildMessenger()).thenReturn(childMessenger);
		when(childMessenger.orderSender()).thenReturn(orderSender);
		MutableDirectBuffer directBuffer = helper.createDirectBuffer();
		when(childMessenger.internalEncodingBuffer()).thenReturn(directBuffer);
		when(childMessenger.stringByteBuffer()).thenReturn(stringBuffer.byteArray());
		when(messenger.timerService()).thenReturn(helper.timerService());
		when(childMessenger.timerService()).thenReturn(helper.timerService());
		when(childMessenger.referenceManager()).thenReturn(refMgr);
		orderContextManager = OrderContextManager.createWithChildMessenger(128, 
				orderManagementContext, 
				messenger,
				ordReqCompletionSender,
				exposureUpdateHandler);
		self = MessageSinkRef.of(selfSink);
		security = Security.of(12345678L, SecurityType.WARRANT, "61727", 101, false, SpreadTableBuilder.get(SecurityType.WARRANT));
		
		newOrderRequest = NewOrderRequest.of(88888, 
				self, 
				security,
				OrderType.LIMIT_ORDER, 
				10000, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				12345);
		newOrderRequest.orderSid(22222);		
	}
	
	@Test
	public void givenOrderRequestExistWhenReceiveAcceptedThenSendOrderRequestCompletionAndOrderAcceptedWithOrderInfo(){
		// given
		SecurityLevelInfo securityLevelInfo = SecurityLevelInfo.of(security.sid(), 
				SequencingOnlyChannel.of(3, 200), 
				ValidationOrderBook.of(12, security.sid()));
		long expectedChannelSeq = securityLevelInfo.channel().peekSeq();
		NewOrderRequest request = NewOrderRequest.of(88888, 
				self, 
				security,
				OrderType.LIMIT_ORDER, 
				10000, 
				Side.BUY,
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				12345,
				securityLevelInfo);
		request.orderSid(22222);
		orderManagementContext.putOrderRequest(request.orderSid(), request);
		
		OrderAcceptedSbeEncoder encoder = new OrderAcceptedSbeEncoder();
		final int invalidChannelId = -1;
		final long invalidChannelSeq = -1;
		OrderSender.encodeOrderAcceptedOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId,
				invalidChannelSeq,
				request.orderSid(), 
				OrderStatus.NEW, 
				request.limitPrice(), 
				0, 
				request.quantity(), 
				request.side(), 
				555555, 
				request.secSid(), 
				ExecutionType.NEW,
				LocalTime.now().toNanoOfDay());
		OrderAcceptedSbeDecoder accepted = new OrderAcceptedSbeDecoder();
		accepted.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		
		final int expectedLength = 1;
		when(orderSender.sendOrderAcceptedWithOrderInfo(any(), 
				anyInt(),
				anyLong(),
				any(), 
				anyInt(),
				any(), 
				any(), 
				any(), 
				anyInt())).thenReturn(expectedLength);
		
		// when
		orderContextManager.receiveAccepted(buffer, 0, accepted);
		
		// then
		verify(ordReqCompletionSender, times(1)).completeWithOrdSidOnly(request.orderSid());
		verify(orderSender, times(1)).sendOrderAcceptedWithOrderInfo(eq(request.owner()), 
				eq(securityLevelInfo.channel().id()),
				eq(expectedChannelSeq),
				eq(buffer), 
				eq(0), 
				refEq(accepted), 
				isA(Order.class),
				any(),
				eq(0));
		verify(childMessenger, times(1)).trySend(eq(orderManagementContext.orderUpdateSubscribers()), any(), eq(0), eq(expectedLength), eq(orderContextManager.sinkSendResults()));
	}

	@Test
	public void givenOrderRequestExistWhenReceiveRejectedThenSendOrderRequestCompletionAndOrderRejectedWithOrderInfo(){
		// given
		SecurityLevelInfo securityLevelInfo = SecurityLevelInfo.of(security.sid(), 
				SequencingOnlyChannel.of(3, 200), 
				ValidationOrderBook.of(12, security.sid()));
		long expectedChannelSeq = securityLevelInfo.channel().peekSeq();
		NewOrderRequest request = NewOrderRequest.of(88888, 
				self, 
				security,
				OrderType.LIMIT_ORDER, 
				10000, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				12345,
				securityLevelInfo);
		request.orderSid(22222);
		orderManagementContext.putOrderRequest(request.orderSid(), request);
		
		final int orderId = 55555;
		final int cumulativeQty = 100;
		final int leavesQty = 0;
		final int invalidChannelId = -1;
		final long invalidChannelSeq = -1;
		final long updateTime = LocalTime.now().toNanoOfDay();
		String reason = "INSUFFICIENT_LONG_POSITION";
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderRejectedSbeEncoder.reasonLength()));

		OrderRejectedSbeEncoder encoder = new OrderRejectedSbeEncoder();
		OrderSender.encodeOrderRejectedOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId,
				invalidChannelSeq,
				request.orderSid(),
				orderId,
				request.secSid(),
				request.side(),
				request.limitPrice(),
				cumulativeQty,
				leavesQty,
				OrderStatus.REJECTED, 
				OrderRejectType.OTHER,
				OrderSender.prepareRejectedReason(reason, byteBuffer),
				updateTime);
		OrderRejectedSbeDecoder rejected = new OrderRejectedSbeDecoder();
		rejected.wrap(buffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);

		final int expectedLength = 31;
		when(orderSender.sendOrderRejectedWithOrderInfo(any(), 
				anyInt(), 
				anyLong(), 
				any(), 
				anyInt(), 
				isA(OrderRejectedSbeDecoder.class), 
				isA(Order.class), 
				isA(MutableDirectBuffer.class), 
				anyInt())).thenReturn(expectedLength);

		// when
		orderContextManager.receiveRejected(buffer, 0, rejected);
		
		// then
//		verify(ordReqCompletionSender, times(1)).rejectWithOrdSidOnly(request.orderSid(), OrderRequestRejectType.OTHER, anyObject());
		verify(ordReqCompletionSender, times(1)).rejectWithOrdSidOnly(anyInt(), anyObject(), anyObject());
		verify(orderSender, times(1)).sendOrderRejectedWithOrderInfo(eq(request.owner()),
				eq(securityLevelInfo.channel().id()),
				eq(expectedChannelSeq),
				eq(buffer), 
				eq(0), 
				eq(rejected),
				isA(Order.class),
				any(),
				eq(0));
		verify(childMessenger, times(1)).trySend(eq(orderManagementContext.orderUpdateSubscribers()), any(), eq(0), eq(expectedLength), eq(orderContextManager.sinkSendResults()));
	}
	
	@Test
	public void givenOrderRequestExistWhenReceiveCancelledThenReceiveOrderCancelled(){
		// given
		// create dummy order context and order for an existing order
		final int origOrderSid = 22222;
		SequencingOnlyChannel channel = SequencingOnlyChannel.of(5, 100); 
		long expectedChannelSeq = channel.peekSeq();
		OrderContext origContext = OrderContext.of(orderContextManager.orderUpdateDistributor(), channel);
		origContext.order(Order.of(newOrderRequest, OrderStatus.NEW, 0, 0));
		orderContextManager.orderContextByOrdSid().put(origOrderSid, origContext);
		
		CancelOrderRequest request = CancelOrderRequest.of(88889, 
				self, 
				origOrderSid,
				newOrderRequest.secSid(),
				newOrderRequest.side());
		request.orderSid(22223);
		orderManagementContext.putOrderRequest(request.orderSid(), request);
		orderManagementContext.origOrdSidToCancelOrderSid().put(origOrderSid, request.orderSid());

		final int orderId = 999999;
		OrderCancelledSbeEncoder encoder = new OrderCancelledSbeEncoder();
		final int invalidChannelId = -1;
		final long invalidChannelSeq = -1;
		OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId,
				invalidChannelSeq,
				request.orderSid(), 
				origOrderSid, 
				OrderStatus.CANCELLED, 
				650, 
				Side.BUY, 
				orderId,
				security.sid(), 
				ExecutionType.CANCEL, 
				0, 
				0,
				LocalTime.now().toNanoOfDay(),
				0);
		OrderCancelledSbeDecoder cancelled = new OrderCancelledSbeDecoder();
		cancelled.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		
		final long expectedLength = 1;
		when(orderSender.sendOrderCancelledWithOrderQuantityOverride(
		        any(MessageSinkRef[].class),
                anyInt(),
				anyInt(), 
				anyLong(), 
				any(), 
				anyInt(), 
				isA(OrderCancelledSbeDecoder.class), 
				any(),
				any(long[].class))).thenReturn(expectedLength);

		
		// when
		orderContextManager.receiveCancelled(buffer, 0, cancelled);

		// then
		verify(orderSender, times(1)).sendOrderCancelledWithOrderQuantityOverride(//eq(newOrderRequest.owner()),
		        any(MessageSinkRef[].class),
		        anyInt(),
				eq(channel.id()),
				eq(expectedChannelSeq),
				eq(buffer), 
				eq(0), 
				any(OrderCancelledSbeDecoder.class),
				any(Order.class),
				any(long[].class));
		verify(childMessenger, times(1)).trySend(eq(newOrderRequest.owner().sinkId()), any(), eq(0), eq((int)expectedLength));

		
		verify(ordReqCompletionSender, times(1)).completeWithOrdSidOnly(request.orderSid());
	}
	
	@Test
	public void givenOrderRequestExistWhenReceiveExpiredThenSendOrderExpired(){
		// given
		// create dummy order context and order for an existing order
		final int origOrderSid = 22222;
		SequencingOnlyChannel channel = SequencingOnlyChannel.of(5, 100);
		long expectedChannelSeq = channel.peekSeq();
		OrderContext origContext = OrderContext.of(orderContextManager.orderUpdateDistributor(), channel);
		origContext.order(Order.of(newOrderRequest, OrderStatus.NEW, 0, 0));
		orderContextManager.orderContextByOrdSid().put(origOrderSid, origContext);

		final int orderId = 55555;
		final int invalidChannelId = -1;
		final long invalidChannelSeq = -1l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();

		OrderExpiredSbeEncoder encoder = new OrderExpiredSbeEncoder();
		OrderSender.encodeOrderExpiredOnly(buffer,
				0,
				encoder,
				invalidChannelId, 
				invalidChannelSeq,
				origOrderSid,
				orderId,
				security.sid(),
				newOrderRequest.side(),
				newOrderRequest.limitPrice(),
				0,
				0,
				OrderStatus.EXPIRED,
				refUpdateTime
				);
		OrderExpiredSbeDecoder expired = new OrderExpiredSbeDecoder();
		expired.wrap(buffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		
		final int expectedLength = 1;
		when(orderSender.sendOrderExpired(any(), 
				anyInt(),
				anyLong(),
				isA(DirectBuffer.class), 
				anyInt(),
				isA(OrderExpiredSbeDecoder.class), 
				isA(MutableDirectBuffer.class), anyInt())).thenReturn(expectedLength);

		// when
		orderContextManager.receiveExpired(buffer, 0, expired);
		// then
		verifyZeroInteractions(ordReqCompletionSender);
		verify(orderSender, times(1)).sendOrderExpired(eq(newOrderRequest.owner()), 
				eq(channel.id()),
				eq(expectedChannelSeq),
				eq(buffer), 
				eq(0), 
				eq(expired),
				any(),
				eq(0));
		verify(childMessenger, times(1)).trySend(eq(orderManagementContext.orderUpdateSubscribers()), any(), eq(0), eq(expectedLength), eq(orderContextManager.sinkSendResults()));
	}
	
	public void givenOrderRequestExistWhenReceiveAmendedThenSendOrderRequestCompletionAndOrderAmended(){
	}

	public void givenOrderRequestExistWhenReceiveAmendRejectedThenSendOrderRequestCompletion(){
		
	}
	
	@Test
	public void givenOrderRequestExistWhenReceiveCancelRejectedThenSendOrderRequestCompletion(){
		// given
		// create dummy order context and order for an existing order
		final int origOrderSid = 22222;
		SequencingOnlyChannel channel = SequencingOnlyChannel.of(5, 100); 
		OrderContext origContext = OrderContext.of(orderContextManager.orderUpdateDistributor(), channel);
		origContext.order(Order.of(newOrderRequest, OrderStatus.NEW, 0, 0));
		orderContextManager.orderContextByOrdSid().put(origOrderSid, origContext);

		CancelOrderRequest request = CancelOrderRequest.of(88889, 
				self, 
				origOrderSid,
				newOrderRequest.secSid(),
				newOrderRequest.side());
		request.orderSid(22223);
		orderManagementContext.putOrderRequest(request.orderSid(), request);

		OrderCancelRejectedSbeEncoder encoder = new OrderCancelRejectedSbeEncoder();
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		long updateTime = LocalTime.now().toNanoOfDay();
		String reason = Strings.padEnd("INVALID ORDER", OrderCancelRejectedSbeEncoder.reasonLength(), ' ');
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		OrderSender.encodeOrderCancelRejectedOnly(buffer, 
				0, 
				encoder, 
				refChannelId,
				refChannelSeq,
				request.orderSid(), 
				OrderStatus.NEW, 
				security.sid(), 
				OrderCancelRejectType.UNKNOWN_ORDER,
				OrderSender.prepareRejectedReason(reason, byteBuffer),
				ExecutionType.CANCEL_REJECT,
				updateTime);
		OrderCancelRejectedSbeDecoder cancelRejected = new OrderCancelRejectedSbeDecoder();
		cancelRejected.wrap(buffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		
		// when
		orderContextManager.receiveCancelRejected(buffer, 0, cancelRejected);
		
		// then
		verify(ordReqCompletionSender, times(1)).rejectWithOrdSidOnly(request.orderSid(), OrderRequestRejectType.UNKNOWN_ORDER, OrderSender.ORDER_REJECTED_EMPTY_REASON);
	}

	@Test
	public void givenOrderContextExistWhenReceiveTradeThenSendTrade(){
		final int origOrderSid = 22222;
		SequencingOnlyChannel channel = SequencingOnlyChannel.of(5, 100);
		long expecteChannelSeq = channel.peekSeq();
		OrderContext origContext = OrderContext.of(orderContextManager.orderUpdateDistributor(), channel);
		origContext.order(Order.of(newOrderRequest, OrderStatus.NEW, 0, 0));
		orderContextManager.orderContextByOrdSid().put(origOrderSid, origContext);

		final int orderId = 55555;
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long updateTime = LocalTime.now().toNanoOfDay();
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		String executionId = Strings.padEnd("88888", TradeCancelledSbeEncoder.executionIdLength(), ' ');

		TradeCreatedSbeEncoder encoder = new TradeCreatedSbeEncoder();
		OrderSender.encodeTradeCreatedOnly(buffer, 
				0, 
				encoder,
				refChannelId,
				refChannelSeq,
				ServiceConstant.NULL_TRADE_SID,
				origOrderSid,
				orderId,
				OrderStatus.PARTIALLY_FILLED,
				newOrderRequest.side(),
				100,
				0,
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				650, 
				50000, 
				security.sid(),
				updateTime);
		TradeCreatedSbeDecoder trade = new TradeCreatedSbeDecoder();
		trade.wrap(buffer, 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
		
		final long expectedLength = 31;
		when(orderSender.sendTradeCreated(
		        any(MessageSinkRef[].class),
                anyInt(),
				anyInt(), 
				anyLong(), 
				any(), 
				anyInt(), 
				isA(TradeCreatedSbeDecoder.class),
				anyInt(),
				any(long[].class))).thenReturn(expectedLength);

		// when
		orderContextManager.receiveTradeCreated(buffer, 0, trade);
		
		// then
		verify(orderSender, times(1)).sendTradeCreated(//eq(newOrderRequest.owner()),
		        any(MessageSinkRef[].class),
		        anyInt(),
				eq(channel.id()),
				eq(expecteChannelSeq),
				eq(buffer), 
				eq(0), 
				eq(trade),
				anyInt(),
				any(long[].class));
		verify(childMessenger, times(1)).trySend(eq(newOrderRequest.owner().sinkId()), any(), eq(0), eq((int)expectedLength));
	}
}
