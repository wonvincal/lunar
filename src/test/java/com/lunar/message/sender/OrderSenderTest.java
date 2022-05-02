package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalTime;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Strings;
import com.lunar.core.TriggerInfo;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewCompositeOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.message.sink.ValidNullMessageSink;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.service.ServiceConstant;
import com.lunar.util.LogUtil;
import com.lunar.util.ServiceTestHelper;

public class OrderSenderTest {
	static final Logger LOG = LogManager.getLogger(RequestSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	
	private MessageSender sender;
	private OrderSender orderSender;

	// Ring buffer sink for receiving message
	private final int testSinkId = 1;
	private RingBufferMessageSinkPoller testSinkPoller;
	private MessageSinkRef testSinkRef;
	private Messenger testMessenger;
	
	@Before
	public void setup(){
		
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.ClientService, "test-client");
		sender = MessageSender.of(ServiceConstant.MAX_MESSAGE_SIZE, refSenderSink);
		orderSender = OrderSender.of(sender);
		buffer.setMemory(0, buffer.capacity(), (byte)0);
		
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		testSinkPoller = testHelper.createRingBufferMessageSinkPoller(testSinkId, 
				ServiceType.DashboardService, 256, "testDashboard");
		RingBufferMessageSink sink = testSinkPoller.sink();
		testSinkRef = MessageSinkRef.of(sink, "testSink");
		testMessenger = testHelper.createMessenger(sink, "testSinkMessenger");
		testMessenger.registerEvents();
		testSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				testMessenger.receive(event, 0);
				return false;
			}
		});
	}
	
	
	@Test
	public void testNewCompositeOrder(){
		final int refSeq = sender.overrideNextSeq(123);
		final long refSecSid = 987654321L;
		final byte refTrackerIndex = 2;
	    NewOrderRequest request = NewOrderRequest.of(refSeq , 
	    		testSinkRef, 
	            refSecSid, 
	            OrderType.LIMIT_THEN_CANCEL_ORDER, 
	            1000, 
	            Side.BUY, 
	            TimeInForce.DAY, 
	            BooleanType.TRUE, 
	            123456, 
	            123456, 
	            12345,
	            false,
	            refTrackerIndex,
	            1,
	            TriggerInfo.of());
		long result = orderSender.sendNewCompositeOrder(testSinkRef, request);
		testMessenger.receiver().newCompositeOrderRequestHandlerList().add(new Handler<NewCompositeOrderRequestSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewCompositeOrderRequestSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(OrderType.LIMIT_THEN_CANCEL_ORDER, codec.orderType());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refTrackerIndex, codec.assignedThrottleTrackerIndex());
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
	
	@Test
	public void testNewOrder(){
		final int refSeq = sender.overrideNextSeq(123);
		final long refSecSid = 987654321L;
		final byte refTrackerIndex = 3;
	    NewOrderRequest request = NewOrderRequest.of(refSeq , 
	    		testSinkRef, 
	            refSecSid, 
	            OrderType.LIMIT_ORDER, 
	            1000, 
	            Side.BUY, 
	            TimeInForce.DAY, 
	            BooleanType.TRUE, 
	            123456, 
	            123456, 
	            12345,
	            false,
	            refTrackerIndex,
	            1,
	            TriggerInfo.of());
		long result = orderSender.sendNewOrder(testSinkRef, request);
		testMessenger.receiver().newOrderRequestHandlerList().add(new Handler<NewOrderRequestSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(OrderType.LIMIT_ORDER, codec.orderType());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refTrackerIndex, codec.assignedThrottleTrackerIndex());
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
		
	@Test
	public void testOrderAccepted(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refPrice = 123456;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 1000;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final ExecutionType refExecType = ExecutionType.NEW;
		final int channelId = 1;
		final long channelSeq = 101;
		final long updateTime = LocalTime.now().toNanoOfDay();

		long result = orderSender.sendOrderAccepted(testSinkRef, 
				channelId,
				channelSeq,
				refOrderSid, 
				refStatus, 
				refPrice, 
				refCumulativeQty, 
				refLeavesQty, 
				refSide, 
				refOrderId, 
				refSecSid, 
				refExecType,
				updateTime);
		testMessenger.receiver().orderAcceptedHandlerList().add(new Handler<OrderAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(refExecType, codec.execType());
				assertEquals(refLeavesQty, codec.leavesQty());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refPrice, codec.price());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
				assertEquals(updateTime, codec.updateTime());
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}

	@Test
	public void testOrderAcceptedWithOrderInfo(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refPrice = 123456;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 1000;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final ExecutionType refExecType = ExecutionType.NEW;
		final int channelId = 3;
		final long channelSeq = 101;
		final long updateTime = LocalTime.now().toNanoOfDay();
		
		// Encode order accepted
		OrderAcceptedSbeEncoder orderAcceptedEncoder = new OrderAcceptedSbeEncoder();
		int size = OrderSender.encodeOrderAcceptedOnly(buffer, 
				0, 
				orderAcceptedEncoder, 
				channelId,
				channelSeq,
				refOrderSid, 
				refStatus, 
				refPrice, 
				refCumulativeQty, 
				refLeavesQty, 
				refSide, 
				refOrderId, 
				refSecSid, 
				refExecType,
				updateTime);
		assertEquals(orderAcceptedEncoder.encodedLength(), size);
		OrderAcceptedSbeDecoder orderAcceptedDecoder = new OrderAcceptedSbeDecoder();
		orderAcceptedDecoder.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		
		// Create an order
		final int refQty = refLeavesQty;
		final OrderType refOrderType = OrderType.LIMIT_ORDER;
		final int refLimitPrice = 123456;
		final int refStopPrice = 123457;
		final TimeInForce refTif = TimeInForce.FILL_AND_KILL;
		final BooleanType refIsAlgo = BooleanType.FALSE;
		byte triggeredBy = 5;
		int triggerSeqNum = 9999998;
		long timestamp = 101201201;
		TriggerInfo triggerInfo = TriggerInfo.of().triggeredBy(triggeredBy).triggerSeqNum(triggerSeqNum).triggerNanoOfDay(timestamp);
		long createTime = updateTime;
		Order order = Order.of(refSecSid, testSinkRef, refOrderSid, refQty, refIsAlgo, refOrderType, refSide, 
				refLimitPrice, refStopPrice, refTif, refStatus, 
				createTime, updateTime, triggerInfo);
		
		LOG.debug("Order accepted\n{}", LogUtil.dumpBinary(buffer.byteBuffer(), 128, 8));
		
		orderSender.sendOrderAcceptedWithOrderInfo(testSinkRef, 
				channelId,
				channelSeq,
				buffer, 
				0, 
				orderAcceptedDecoder, 
				order, 
				testMessenger.internalEncodingBuffer(), 
				0);

		LOG.debug("Order accepted with order info\n{}", LogUtil.dumpBinary(testMessenger.internalEncodingBuffer().byteBuffer(), 128, 8));

		testMessenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(refExecType, codec.execType());
				assertEquals(refLeavesQty, codec.leavesQty());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refPrice, codec.price());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
				assertEquals(refQty, codec.quantity());
				assertEquals(refOrderType, codec.orderType());
				assertEquals(refTif, codec.tif());
				assertEquals(refIsAlgo, codec.isAlgoOrder());
				assertEquals(refStopPrice, codec.stopPrice());
				assertEquals(triggeredBy, codec.triggerInfo().triggeredBy());
				assertEquals(triggerSeqNum, codec.triggerInfo().triggerSeqNum());
				assertEquals(timestamp, codec.triggerInfo().nanoOfDay());
				LOG.debug("Arrived");
			}
		});
		
		testMessenger.receiver().orderAcceptedHandlerList().add(new Handler<OrderAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
				LOG.info("Shit");
			}
		});
		testSinkPoller.pollAll();
	}

	@Test
	public void OrderCancelledAsOrderExpired(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrigOrderSid = 11110;
		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refPrice = 123456;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 1000;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final ExecutionType refExecType = ExecutionType.NEW;
		final int refChannelId = 1;
		final long refChannelSeq = 1012l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		
		// Encode order accepted
		OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
		int size = OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				orderCancelledEncoder, 
				refChannelId,
				refChannelSeq,
				refOrderSid, 
				refOrigOrderSid,
				refStatus, 
				refPrice, 
				refSide, 
				refOrderId,
				refSecSid, 
				refExecType,
				refLeavesQty,
				refCumulativeQty,
				refUpdateTime,
				refLeavesQty + refCumulativeQty);
		assertEquals(orderCancelledEncoder.encodedLength(), size);
		OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();
		orderCancelledDecoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		
		orderSender.sendOrderExpired(testSinkRef, refChannelId, refChannelSeq, buffer, 0, orderCancelledDecoder, testMessenger.internalEncodingBuffer(), 0);
		testMessenger.receiver().orderExpiredHandlerList().add(new Handler<OrderExpiredSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(refChannelId, codec.channelId());
				assertEquals(refChannelSeq, codec.channelSeq());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(refExecType, codec.execType());
				assertEquals(refLeavesQty, codec.leavesQty());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refPrice, codec.price());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
			}
		});
		
		testMessenger.receiver().orderCancelledHandlerList().add(new Handler<OrderCancelledSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
				LOG.info("Shit");
			}
		});
		testSinkPoller.pollAll();
	}
	
	@Test
	public void testOrderCancelledWithOrderInfo(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrigOrderSid = 11110;
		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refPrice = 123456;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 1000;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final ExecutionType refExecType = ExecutionType.NEW;
		final int refChannelId = 1;
		final long refChannelSeq = 1012l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		
		// Encode order accepted
		OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
		int size = OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				orderCancelledEncoder, 
				refChannelId,
				refChannelSeq,
				refOrderSid, 
				refOrigOrderSid,
				refStatus, 
				refPrice, 
				refSide, 
				refOrderId,
				refSecSid, 
				refExecType,
				refLeavesQty,
				refCumulativeQty,
				refUpdateTime,
				refLeavesQty + refCumulativeQty);
		assertEquals(orderCancelledEncoder.encodedLength(), size);
		OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();
		orderCancelledDecoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		
		// Create an order
		final int refQty = refLeavesQty;
		final OrderType refOrderType = OrderType.LIMIT_ORDER;
		final int refLimitPrice = 123456;
		final int refStopPrice = 123457;
		final TimeInForce refTif = TimeInForce.FILL_AND_KILL;
		final BooleanType refIsAlgo = BooleanType.FALSE;
		Order order = Order.of(refSecSid, testSinkRef, refOrderSid, refQty, refIsAlgo, refOrderType, refSide, refLimitPrice, refStopPrice, refTif, refStatus);
		
		orderSender.sendOrderCancelledWithOrderInfo(testSinkRef, 
				refChannelId,
				refChannelSeq,
				buffer, 
				0,
				orderCancelledDecoder, 
				order, testMessenger.internalEncodingBuffer(), 0);
		testMessenger.receiver().orderCancelledWithOrderInfoHandlerList().add(new Handler<OrderCancelledWithOrderInfoSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledWithOrderInfoSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(refChannelId, codec.channelId());
				assertEquals(refChannelSeq, codec.channelSeq());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(refExecType, codec.execType());
				assertEquals(refLeavesQty, codec.leavesQty());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refPrice, codec.price());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
				assertEquals(refQty, codec.quantity());
				assertEquals(refOrderType, codec.orderType());
				assertEquals(refTif, codec.tif());
				assertEquals(refIsAlgo, codec.isAlgoOrder());
				assertEquals(refStopPrice, codec.stopPrice());
			}
		});
		
		testMessenger.receiver().orderCancelledHandlerList().add(new Handler<OrderCancelledSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
				LOG.info("Shit");
			}
		});
		testSinkPoller.pollAll();
	}
	
	@Test
	public void testOrderRejectedWithOrderInfo(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refPrice = 123456;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 0;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final OrderRejectType refRejectType = OrderRejectType.DUPLICATE_ORDER;
		final int refChannelId = 1;
		final long refChannelSeq = 1012l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		String refReason = Strings.padEnd("INCORRECT_QTY",  OrderRejectedSbeEncoder.reasonLength(), ' ');
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderRejectedSbeEncoder.reasonLength()));

		// Encode order accepted
		OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
		int size = OrderSender.encodeOrderRejectedOnly(buffer, 
				0, 
				orderRejectedEncoder, 
				refChannelId,
				refChannelSeq,
				refOrderSid, 
				refOrderId,
				refSecSid, 
				refSide, 
				refPrice, 
				refCumulativeQty,
				refLeavesQty,
				refStatus, 
				refRejectType,
				OrderSender.prepareRejectedReason(refReason, byteBuffer),
				refUpdateTime);
		assertEquals(orderRejectedEncoder.encodedLength(), size);
		OrderRejectedSbeDecoder orderRejectedDecoder = new OrderRejectedSbeDecoder();
		orderRejectedDecoder.wrap(buffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);
		
		// Create an order
		final int refQty = refLeavesQty;
		final OrderType refOrderType = OrderType.LIMIT_ORDER;
		final int refLimitPrice = 123456;
		final int refStopPrice = 123457;
		final TimeInForce refTif = TimeInForce.FILL_AND_KILL;
		final BooleanType refIsAlgo = BooleanType.FALSE;
		Order order = Order.of(refSecSid, testSinkRef, refOrderSid, refQty, refIsAlgo, refOrderType, refSide, refLimitPrice, refStopPrice, refTif, refStatus);
		
		orderSender.sendOrderRejectedWithOrderInfo(testSinkRef,
				refChannelId,
				refChannelSeq,
				buffer, 
				0, 
				orderRejectedDecoder, order, testMessenger.internalEncodingBuffer(), 0);
		testMessenger.receiver().orderRejectedWithOrderInfoHandlerList().add(new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(ExecutionType.REJECT, codec.execType());
				assertEquals(refRejectType, codec.rejectType());
				assertEquals(refLeavesQty, codec.leavesQty());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refPrice, codec.price());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
				assertEquals(refQty, codec.quantity());
				assertEquals(refOrderType, codec.orderType());
				assertEquals(refTif, codec.tif());
				assertEquals(refIsAlgo, codec.isAlgoOrder());
				assertEquals(refStopPrice, codec.stopPrice());
				byte[] reasonBuffer = new byte[OrderRejectedWithOrderInfoSbeDecoder.reasonLength()];
				assertEquals(0, refReason.compareTo(new String(reasonBuffer, 0, codec.getReason(reasonBuffer, 0))));
			}
		});
		
		testMessenger.receiver().orderRejectedHandlerList().add(new Handler<OrderRejectedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder codec) {
				LOG.info("Shit");
			}
		});
		testSinkPoller.pollAll();
	}

	@Test
	public void testOrderExpiredWithOrderInfo(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refPrice = 123456;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 1000;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final int refChannelId = 1;
		final long refChannelSeq = 1012l;
		final int channelId = 1;
		final long channelSeq = 101;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();

		// Encode order accepted
		OrderExpiredSbeEncoder orderExpiredEncoder = new OrderExpiredSbeEncoder();
		int size = OrderSender.encodeOrderExpiredOnly(buffer, 
				0, 
				orderExpiredEncoder, 
				refChannelId, 
				refChannelSeq,
				refOrderSid, 
				refOrderId,
				refSecSid, 
				refSide, 
				refPrice, 
				refCumulativeQty,
				refLeavesQty,
				refStatus,
				refUpdateTime);
		assertEquals(orderExpiredEncoder.encodedLength(), size);
		OrderExpiredSbeDecoder orderExpiredDecoder = new OrderExpiredSbeDecoder();
		orderExpiredDecoder.wrap(buffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		
		// Create an order
		final int refQty = refLeavesQty;
		final OrderType refOrderType = OrderType.LIMIT_ORDER;
		final int refLimitPrice = 123456;
		final int refStopPrice = 123457;
		final TimeInForce refTif = TimeInForce.FILL_AND_KILL;
		final BooleanType refIsAlgo = BooleanType.FALSE;
		Order order = Order.of(refSecSid, testSinkRef, refOrderSid, refQty, refIsAlgo, refOrderType, refSide, refLimitPrice, refStopPrice, refTif, refStatus);
		
		orderSender.sendOrderExpiredWithOrderInfo(testSinkRef, 
				channelId,
				channelSeq,
				buffer, 
				0, 
				orderExpiredDecoder, order, testMessenger.internalEncodingBuffer(), 0);
		testMessenger.receiver().orderExpiredWithOrderInfoHandlerList().add(new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(ExecutionType.EXPIRE, codec.execType());
				assertEquals(refLeavesQty, codec.leavesQty());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refPrice, codec.price());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
				assertEquals(refQty, codec.quantity());
				assertEquals(refOrderType, codec.orderType());
				assertEquals(refTif, codec.tif());
				assertEquals(refIsAlgo, codec.isAlgoOrder());
				assertEquals(refStopPrice, codec.stopPrice());
			}
		});
		
		testMessenger.receiver().orderExpiredHandlerList().add(new Handler<OrderExpiredSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder codec) {
				LOG.info("Shit");
			}
		});
		testSinkPoller.pollAll();
	}
	
	@Test
	public void testTradeWithOrderInfo(){
		final int refSeq = sender.overrideNextSeq(123);

		final int refOrderSid = 11111;
		final OrderStatus refStatus = OrderStatus.NEW;
		final int refCumulativeQty = 0;
		final int refLeavesQty = 1000;
		final Side refSide = Side.BUY;
		final int refOrderId = 3;
		final long refSecSid = 987654321L;
		final String refExecutionId = Strings.padEnd("34567", TradeCreatedSbeEncoder.executionIdLength(), ' ');
		final int refExecutionPrice = 123456;
		final int refExecutionQty = 1000;
		final int refTradeSid = 98989;
		final int refChannelId = 5;
		final long refChannelSeq = 1012l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		byte[] byteBuffer = new byte[128];
		
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		// Encode order accepted
		TradeCreatedSbeEncoder tradeCreatedEncoder = new TradeCreatedSbeEncoder();
		int size = OrderSender.encodeTradeCreatedOnly(buffer, 
				0, 
				tradeCreatedEncoder, 
				refChannelId,
				refChannelSeq,
				refTradeSid,
				refOrderSid, 
				refOrderId,
				refStatus,
				refSide,
				refLeavesQty,
				refCumulativeQty,
				OrderSender.prepareExecutionId(refExecutionId, stringBuffer),
				refExecutionPrice,
				refExecutionQty,
				refSecSid,
				refUpdateTime);
		assertEquals(tradeCreatedEncoder.encodedLength(), size);
		TradeCreatedSbeDecoder tradeCreatedSbeDecoder = new TradeCreatedSbeDecoder();
		tradeCreatedSbeDecoder.wrap(buffer, 0, 
				TradeCreatedSbeDecoder.BLOCK_LENGTH, 
				TradeCreatedSbeDecoder.SCHEMA_VERSION);
		
		// Create an order
		final int refQty = refLeavesQty;
		final OrderType refOrderType = OrderType.LIMIT_ORDER;
		final int refLimitPrice = 123456;
		final int refStopPrice = 123457;
		final TimeInForce refTif = TimeInForce.FILL_AND_KILL;
		final BooleanType refIsAlgo = BooleanType.FALSE;
		Order order = Order.of(refSecSid, testSinkRef, refOrderSid, refQty, refIsAlgo, refOrderType, refSide, refLimitPrice, refStopPrice, refTif, refStatus);
		
		orderSender.sendTradeCreatedWithOrderInfo(testSinkRef,
				refChannelId,
				refChannelSeq,
				buffer, 
				0, 
				tradeCreatedSbeDecoder, 
				order, 
				testMessenger.internalEncodingBuffer(), 
				0);
		testMessenger.receiver().tradeCreatedWithOrderInfoHandlerList().add(new Handler<TradeCreatedWithOrderInfoSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder codec) {
				assertEquals(ValidNullMessageSink.VALID_NULL_INSTANCE_ID, header.senderSinkId());
				assertEquals(header.dstSinkId(), testSinkId);
				assertEquals(refSeq, header.seq());
//				LOG.debug("sid:{}, channelId:{}, channelSeq:{}", codec.sid(), codec.ch);
				assertEquals(refTradeSid, codec.tradeSid());
				assertEquals(refCumulativeQty, codec.cumulativeQty());
				assertEquals(ExecutionType.TRADE, codec.execType());
				assertEquals(refOrderId, codec.orderId());
				assertEquals(refOrderSid, codec.orderSid());
				assertEquals(refSecSid, codec.secSid());
				assertEquals(refSide, codec.side());
				assertEquals(refStatus, codec.status());
				assertEquals(refQty, codec.quantity());
				assertEquals(refOrderType, codec.orderType());
				assertEquals(refTif, codec.tif());
				assertEquals(refIsAlgo, codec.isAlgoOrder());
				assertEquals(refStopPrice, codec.stopPrice());
				assertEquals(0, refExecutionId.compareTo(new String(byteBuffer, 0, codec.getExecutionId(byteBuffer, 0))));
				assertEquals(refExecutionPrice, codec.executionPrice());
				assertEquals(refExecutionQty, codec.executionQty());
			}
		});
		
		testMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.info("Shit");
			}
		});
		testSinkPoller.pollAll();
	}
}
