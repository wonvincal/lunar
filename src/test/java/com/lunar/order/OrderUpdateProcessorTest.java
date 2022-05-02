package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.EventHandler;
import com.lunar.core.LifecycleState;
import com.lunar.core.LifecycleUtil;
import com.lunar.core.UserControlledTimerService;
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
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.service.ServiceConstant;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class OrderUpdateProcessorTest {
	@Mock
	private OrderContextManager contextManager;
	
	@Mock
	private EventHandler<OrderUpdateEvent> noopOrderUpdateEventHandler;
	
	private String name;
	private OrderUpdateProcessor processor;
	private TestHelper testHelper;
	private UserControlledTimerService timerService;
	private MutableDirectBuffer buffer;
	private MutableDirectBuffer stringBuffer;
	
	@Before
	public void setup(){
		name = "test-processor";
		testHelper = TestHelper.of();
		timerService = testHelper.timerService();
		processor = OrderUpdateProcessor.of(name, timerService, contextManager, Optional.empty());
		buffer = testHelper.createDirectBuffer();
		stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	}
	
	@Test
	public void givenCreatedThenStateIsInit(){
		assertEquals(LifecycleState.INIT, processor.state()); 
	}
	
	@Test
	public void givenCreatedWhenStartThenDisruptorIsSetAsRunning(){
		assertEquals(LifecycleState.INIT, processor.state());
		processor.onStart();
//		assertTrue(processor.isDisruptorRunning());
	}
	
	@Test
	public void givenRunningButNotWarmupOrActiveOrRecoveryWhenSendOrderRequestThenNoop() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		processor.noopOrderUpdateEventHandler(noopOrderUpdateEventHandler);
		
		OrderUpdateEvent event = createOrderAccepted(buffer);
		processor.onEvent(event, 0, true);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.RECOVERY);
		processor.onEvent(event, 0, true);

		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		processor.onEvent(event, 0, true);

		// When
		processor.onShutdown();
		LifecycleUtil.advanceTo(processor, LifecycleState.STOPPED);
		processor.onEvent(event, 0, true);

		// Then
		// Recovery uses active handler instead of noop handler
		verify(noopOrderUpdateEventHandler, times(3)).onEvent(any(OrderUpdateEvent.class), anyLong(), anyBoolean());
	}
	
	@Test
	public void givenRunningAtWarmupOrActiveWhenSendOrderRequestThenProcess() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		
		OrderUpdateEvent event = createOrderAccepted(buffer);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.WARMUP);
		processor.onEvent(event, 0, true);

		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		processor.onEvent(event, 0, true);

		// Then
		verify(contextManager, times(2)).receiveAccepted(any(DirectBuffer.class), anyInt(), any(OrderAcceptedSbeDecoder.class));
		verify(contextManager, times(1)).reset();
		verify(contextManager, times(1)).warmup();
		verify(contextManager, times(1)).active();
		verifyNoMoreInteractions(contextManager);
		verifyZeroInteractions(noopOrderUpdateEventHandler);
	}

	private static OrderUpdateEvent createOrderAccepted(MutableDirectBuffer buffer){
		OrderAcceptedSbeEncoder encoder = new OrderAcceptedSbeEncoder();
		int invalidChannelId = 1;
		long invalidChannelSeq = 1;
		int orderSid = 12345678;
		OrderStatus orderStatus = OrderStatus.NEW;
		int limitPrice = 150000;
		int stopPrice = 0;
		int quantity = 2000;
		Side side = Side.BUY;
		int orderId = 2323232;
		long secSid = 888888888;
		ExecutionType execType = ExecutionType.NEW;
		int length = OrderSender.encodeOrderAcceptedOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId,
				invalidChannelSeq,
				orderSid, 
				orderStatus, 
				limitPrice, 
				stopPrice, 
				quantity, 
				side, 
				orderId, 
				secSid, 
				execType,
				LocalTime.now().toNanoOfDay());
		OrderAcceptedSbeDecoder accepted = new OrderAcceptedSbeDecoder();
		accepted.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		return new OrderUpdateEvent().merge(OrderUpdateEvent.ORDER_ACCEPTED, OrderAcceptedSbeDecoder.TEMPLATE_ID, OrderAcceptedSbeDecoder.BLOCK_LENGTH, buffer, 0, length); 
	}

	private static OrderUpdateEvent createOrderCancelled(MutableDirectBuffer buffer){
		OrderCancelledSbeEncoder encoder = new OrderCancelledSbeEncoder();
		int invalidChannelId = 1;
		long invalidChannelSeq = 1;
		int orderSid = 12345678;
		OrderStatus orderStatus = OrderStatus.CANCELLED;
		int limitPrice = 150000;
		Side side = Side.BUY;
		int orderId = 2323232;
		long secSid = 888888888;
		ExecutionType execType = ExecutionType.CANCEL;
		int origOrderSid = 987654321;
		int leavesQty = 2000;
		int cumulativeQty = 0;
		long updateTime = LocalTime.now().toNanoOfDay();
		int length = OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId, 
				invalidChannelSeq, 
				orderSid, 
				origOrderSid, 
				orderStatus, 
				limitPrice, 
				side, 
				orderId, 
				secSid, 
				execType, 
				leavesQty, 
				cumulativeQty, 
				updateTime,
				0);
				
		OrderCancelledSbeDecoder cancelled = new OrderCancelledSbeDecoder();
		cancelled.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		return new OrderUpdateEvent().merge(OrderUpdateEvent.ORDER_CANCELLED, OrderCancelledSbeDecoder.TEMPLATE_ID, OrderCancelledSbeDecoder.BLOCK_LENGTH, buffer, 0, length); 
	}

	private static OrderUpdateEvent createOrderCancelRejected(MutableDirectBuffer buffer, MutableDirectBuffer stringBuffer){
		OrderCancelRejectedSbeEncoder encoder = new OrderCancelRejectedSbeEncoder();
		int invalidChannelId = 1;
		long invalidChannelSeq = 1;
		OrderStatus orderStatus = OrderStatus.NEW;
		long secSid = 888888888;
		ExecutionType execType = ExecutionType.CANCEL_REJECT;
		int origOrderSid = 987654321;
		long updateTime = LocalTime.now().toNanoOfDay();
		String reason = "Test reject reason";
		OrderCancelRejectType rejectType = OrderCancelRejectType.ORDER_ALREADY_IN_PENDING_CANCEL_OR_PENDING_REPLACE_STATUS;
		int length = OrderSender.encodeOrderCancelRejectedOnly(buffer, 
				0,
				encoder, 
				invalidChannelId, 
				invalidChannelSeq, 
				origOrderSid, 
				orderStatus, 
				secSid, 
				rejectType,
				OrderSender.prepareRejectedReason(reason, stringBuffer),
				execType, 
				updateTime);
				
		OrderCancelRejectedSbeDecoder cancelled = new OrderCancelRejectedSbeDecoder();
		cancelled.wrap(buffer, 0, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH, OrderCancelRejectedSbeDecoder.SCHEMA_VERSION);
		return new OrderUpdateEvent().merge(OrderUpdateEvent.ORDER_CANCEL_REJECTED, OrderCancelRejectedSbeDecoder.TEMPLATE_ID, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH, buffer, 0, length); 
	}

	private static OrderUpdateEvent createOrderExpired(MutableDirectBuffer buffer){
		OrderExpiredSbeEncoder encoder = new OrderExpiredSbeEncoder();
		int invalidChannelId = 1;
		long invalidChannelSeq = 1;
		OrderStatus orderStatus = OrderStatus.EXPIRED;
		long secSid = 888888888;
		int orderId = 2323232;
		long updateTime = LocalTime.now().toNanoOfDay();
		int orderSid = 123456789;
		int price = 150000;
		Side side = Side.BUY;
		int leavesQty = 20000;
		int cumulativeQty = 0;
		int length = OrderSender.encodeOrderExpiredOnly(buffer, 
				0,
				encoder, 
				invalidChannelId, 
				invalidChannelSeq, 
				orderSid, 
				orderId, 
				secSid, 
				side, 
				price, 
				cumulativeQty, 
				leavesQty, 
				orderStatus, 
				updateTime);
								
		OrderExpiredSbeDecoder cancelled = new OrderExpiredSbeDecoder();
		cancelled.wrap(buffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		return new OrderUpdateEvent().merge(OrderUpdateEvent.ORDER_EXPIRED, OrderExpiredSbeDecoder.TEMPLATE_ID, OrderExpiredSbeDecoder.BLOCK_LENGTH, buffer, 0, length); 
	}

	private static OrderUpdateEvent createTradeCreated(MutableDirectBuffer buffer, MutableDirectBuffer stringBuffer){
		TradeCreatedSbeEncoder encoder = new TradeCreatedSbeEncoder();
		int invalidChannelId = 1;
		long invalidChannelSeq = 1;
		OrderStatus orderStatus = OrderStatus.FILLED;
		long secSid = 888888888;
		int orderId = 2323232;
		long updateTime = LocalTime.now().toNanoOfDay();
		int orderSid = 123456789;
		Side side = Side.BUY;
		int leavesQty = 0;
		int cumulativeQty = 20000;
		int tradeSid = 234567;
		String executionId = "121902819";
		int executionPrice = 150000;
		int executionQty = 20000;
		int length = OrderSender.encodeTradeCreatedOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId, 
				invalidChannelSeq, 
				tradeSid, 
				orderSid, 
				orderId, 
				orderStatus, 
				side, 
				leavesQty, 
				cumulativeQty, 
				OrderSender.prepareExecutionId(executionId, stringBuffer), 
				executionPrice, 
				executionQty, 
				secSid, 
				updateTime);
				
		TradeCreatedSbeDecoder created = new TradeCreatedSbeDecoder();
		created.wrap(buffer, 0, TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION);
		return new OrderUpdateEvent().merge(OrderUpdateEvent.TRADE_CREATED, TradeCreatedSbeDecoder.TEMPLATE_ID, TradeCreatedSbeDecoder.BLOCK_LENGTH, buffer, 0, length); 
	}

	private static OrderUpdateEvent createTradeCancelled(MutableDirectBuffer buffer, MutableDirectBuffer stringBuffer){
		TradeCancelledSbeEncoder encoder = new TradeCancelledSbeEncoder();
		int invalidChannelId = 1;
		long invalidChannelSeq = 1;
		OrderStatus orderStatus = OrderStatus.FILLED;
		long secSid = 888888888;
		long updateTime = LocalTime.now().toNanoOfDay();
		int orderSid = 123456789;
		Side side = Side.BUY;
		int leavesQty = 0;
		int cumulativeQty = 20000;
		int tradeSid = 234567;
		String executionId = "121902819";
		int executionPrice = 150000;
		int executionQty = 20000;
		ExecutionType execType = ExecutionType.TRADE_CANCEL;
		int length = OrderSender.encodeTradeCancelledOnly(buffer, 
				0, 
				encoder, 
				invalidChannelId, 
				invalidChannelSeq, 
				tradeSid, 
				orderSid, 
				orderStatus, 
				execType, 
				side, 
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				executionPrice, 
				executionQty, secSid, leavesQty, cumulativeQty, updateTime);
				
		TradeCancelledSbeDecoder cancelled = new TradeCancelledSbeDecoder();
		cancelled.wrap(buffer, 0, TradeCancelledSbeDecoder.BLOCK_LENGTH, TradeCancelledSbeDecoder.SCHEMA_VERSION);
		return new OrderUpdateEvent().merge(OrderUpdateEvent.TRADE_CANCELLED, TradeCancelledSbeDecoder.TEMPLATE_ID, TradeCancelledSbeDecoder.BLOCK_LENGTH, buffer, 0, length); 
	}

	@Test
	public void givenRunningWhenStopThenNotAllowBecauseDisruptorIsStillRunning(){
		// Given
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);

		// When
		LifecycleUtil.failToAdvance(processor, LifecycleState.STOPPED);
	}
	
	@Test
	public void givenNotRunningWhenStopThenOK(){
		// Given
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		processor.onShutdown();
		LifecycleUtil.advanceTo(processor, LifecycleState.STOPPED);
	}
	
	@Test
	public void givenRunningAtWarmupOrActiveWhenReceiveCancelledThenProcess() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		
		OrderUpdateEvent event = createOrderCancelled(buffer);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.WARMUP);
		processor.onEvent(event, 0, true);

		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		processor.onEvent(event, 0, true);

		// Then
		verify(contextManager, times(2)).receiveCancelled(any(DirectBuffer.class), anyInt(), any(OrderCancelledSbeDecoder.class));
		verify(contextManager, times(1)).reset();
		verify(contextManager, times(1)).warmup();
		verify(contextManager, times(1)).active();
		verifyNoMoreInteractions(contextManager);
		verifyZeroInteractions(noopOrderUpdateEventHandler);
	}
	
	@Test
	public void givenRunningAtWarmupOrActiveWhenReceiveCancelRejectedThenProcess() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		
		OrderUpdateEvent event = createOrderCancelRejected(buffer, stringBuffer);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.WARMUP);
		processor.onEvent(event, 0, true);

		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		processor.onEvent(event, 0, true);

		// Then
		verify(contextManager, times(2)).receiveCancelRejected(any(DirectBuffer.class), anyInt(), any(OrderCancelRejectedSbeDecoder.class));
		verify(contextManager, times(1)).reset();
		verify(contextManager, times(1)).warmup();
		verify(contextManager, times(1)).active();
		verifyNoMoreInteractions(contextManager);
		verifyZeroInteractions(noopOrderUpdateEventHandler);
	}

	@Test
	public void givenRunningAtWarmupOrActiveWhenReceiveExpiredThenProcess() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		
		OrderUpdateEvent event = createOrderExpired(buffer);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.WARMUP);
		processor.onEvent(event, 0, true);

		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		processor.onEvent(event, 0, true);

		// Then
		verify(contextManager, times(2)).receiveExpired(any(DirectBuffer.class), anyInt(), any(OrderExpiredSbeDecoder.class));
		verify(contextManager, times(1)).reset();
		verify(contextManager, times(1)).warmup();
		verify(contextManager, times(1)).active();
		verifyNoMoreInteractions(contextManager);
		verifyZeroInteractions(noopOrderUpdateEventHandler);
	}
	
	@Test
	public void givenRunningAtWarmupOrActiveWhenReceiveTradeCreatedThenProcess() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		
		OrderUpdateEvent event = createTradeCreated(buffer, stringBuffer);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.WARMUP);
		processor.onEvent(event, 0, true);

		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		processor.onEvent(event, 0, true);

		// Then
		verify(contextManager, times(2)).receiveTradeCreated(any(DirectBuffer.class), anyInt(), any(TradeCreatedSbeDecoder.class));
		verify(contextManager, times(1)).reset();
		verify(contextManager, times(1)).warmup();
		verify(contextManager, times(1)).active();
		verifyNoMoreInteractions(contextManager);
		verifyZeroInteractions(noopOrderUpdateEventHandler);
	}

	@Test
	public void givenRunningAtWarmupOrActiveWhenReceiveTradeCancelledThenProcess() throws Exception{
		// When
		processor.onStart();
		assertEquals(LifecycleState.INIT, processor.state());
		
		OrderUpdateEvent event = createTradeCancelled(buffer, stringBuffer);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.WARMUP);
		processor.onEvent(event, 0, true);

		LifecycleUtil.advanceTo(processor, LifecycleState.RESET);
		
		// When
		LifecycleUtil.advanceTo(processor, LifecycleState.ACTIVE);
		processor.onEvent(event, 0, true);

		// Then
		verify(contextManager, times(2)).receiveTradeCancelled(any(DirectBuffer.class), anyInt(), any(TradeCancelledSbeDecoder.class));
		verify(contextManager, times(1)).reset();
		verify(contextManager, times(1)).warmup();
		verify(contextManager, times(1)).active();
		verifyNoMoreInteractions(contextManager);
		verifyZeroInteractions(noopOrderUpdateEventHandler);
	}
}
