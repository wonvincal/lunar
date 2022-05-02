package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
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
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.DummyExchangeLineHandlerOrderRequestSender;
import com.lunar.order.ExchangeService;
import com.lunar.order.LineHandlerEngineOrderUpdateListener;
import com.lunar.order.LineHandlerOrderRequestSender;
import com.lunar.order.MatchingEngine;
import com.lunar.order.MatchingEngineFactory;
import com.lunar.order.Order;
import com.lunar.order.Trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * All order request to be sent to thru its disruptor queue
 * 
 * To subscribe to order and trade updates: 1) send subscription request to its
 * disruptor queue 2) directly register a LineHandlerUpdateListener
 * 
 * @author wongca
 *
 */
public class ReplayExchangeService implements ServiceLifecycleAware, ExchangeService {
	public enum NewMode{
		ACCEPT_ON_NEW,
		REJECT_ON_NEW,
		MATCH_ON_NEW
	}

	public enum CancelMode {
		CANCEL_ON_CANCEL, CANCEL_REJECT_ON_CANCEL
	}

	static final Logger LOG = LogManager.getLogger(ReplayExchangeService.class);
	private final int IMAGINARY_BOARD_LOT = 100;
	private LunarService messageService;
	private Messenger messenger;
	private MessageReceiver receiver;
	private final String name;
	private NewMode currentNewMode;
	private CancelMode currentCancelMode;
	private final Int2ObjectOpenHashMap<Order> orders;
	private final Object2ObjectOpenHashMap<String, Trade> trades;
	private MessageSinkRef[] subscribers;
	private final AtomicInteger tradeSidSeq;
	private final AtomicInteger executionIdSeq;
	private final AtomicInteger orderIdSeq;
	private LineHandlerEngineOrderUpdateListener updateListener = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;
	private final DummyExchangeLineHandlerOrderRequestSender orderRequestSender;
	private volatile boolean isStopped;
	private long[] sinkSendResults;
	private final int channelId = 1;
	private final AtomicLong channelSeq = new AtomicLong();
	private Long2ObjectOpenHashMap<String> secIdToCode = new Long2ObjectOpenHashMap<String>(5000);
	private MatchingEngine matchingEngine;
	private Messenger childMessenger;
	private final TaskConsumer taskConsumer;
	private final Executor updatePublishingExecutor;
	private final BlockingQueue<Task> updateTaskQueue = new LinkedBlockingQueue<>();
	private final byte[] unknownOrderReason;

	public static ReplayExchangeService of(ServiceConfig config, LunarService messageService) {
		return new ReplayExchangeService(config, messageService);
	}

	ReplayExchangeService(ServiceConfig config, LunarService messageService) {
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.childMessenger = this.messenger.createChildMessenger();
		this.receiver = this.messenger.receiver();
		this.currentCancelMode = CancelMode.CANCEL_ON_CANCEL;
		this.currentNewMode = NewMode.ACCEPT_ON_NEW;
		this.orders = new Int2ObjectOpenHashMap<>(128);
		this.trades = new Object2ObjectOpenHashMap<>(128);
		this.subscribers = new MessageSinkRef[0];
		this.sinkSendResults = new long[0];
		this.tradeSidSeq = new AtomicInteger(800000);
		this.orderIdSeq = new AtomicInteger(700000);
		this.executionIdSeq = new AtomicInteger(0);
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.childBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.taskConsumer = new TaskConsumer(updateTaskQueue);
		this.updatePublishingExecutor = Executors.newFixedThreadPool(1,
				new NamedThreadFactory("dummy-exchange", "dummy-exchange-update-publisher"));
		this.updateListener = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;
		this.orderRequestSender = DummyExchangeLineHandlerOrderRequestSender.of(this.messenger.self(),
				this.messenger.createChildMessenger().orderSender());
		this.isStopped = true;
		String reason = Strings.padEnd("INVALID ORDER", OrderCancelRejectedSbeEncoder.reasonLength(), ' ');
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderCancelRejectedSbeEncoder.reasonLength()));
		this.unknownOrderReason = OrderSender.prepareRejectedReason(reason, byteBuffer);
		
		this.matchingEngine = MatchingEngineFactory.createReplayMatchingEngine(new MatchingEngine.MatchedHandler() {
			@Override
			public void onTrade(final long timestamp, final Order order, final int price, final int quantity) {
				if (order.cumulativeExecQty() == order.quantity()) {
					order.status(OrderStatus.FILLED);
				} else {
					order.status(OrderStatus.PARTIALLY_FILLED);
				}
				tradeCreated(childMessenger, childBuffer, order, price, quantity);
			}

			@Override
			public void onOrderExpired(final long timestamp, final Order order) {
			    order.status(OrderStatus.EXPIRED);
			    orderExpired(childMessenger, childBuffer, order);
			}

		}, messageService.systemClock(), ServiceConstant.MATCHING_ENGINE_DELAY_NANO, true);
	}

	@Override
	public boolean isStopped() {
		return isStopped;
	};

	LunarService messageService() {
		return messageService;
	}

	public LineHandlerOrderRequestSender orderRequestSender() {
		return orderRequestSender;
	}

	@Override
	public void updateListener(LineHandlerEngineOrderUpdateListener updateListener) {
		this.updateListener = updateListener;
	}

	void addSubscriber(MessageSinkRef sink) {
		for (MessageSinkRef subscriber : subscribers) {
			if (sink.equals(subscriber)) {
				LOG.info("Sink {} is already a subscriber", sink);
				return;
			}
		}
		MessageSinkRef[] items = new MessageSinkRef[subscribers.length + 1];
		for (int i = 0; i < subscribers.length; i++) {
			items[i] = this.subscribers[i];
		}
		items[items.length - 1] = sink;
		this.subscribers = items;
		this.sinkSendResults = new long[this.subscribers.length];
		LOG.info("Sink {} is now subscribing to order updates", sink);
	}

	void removeSubscriber(MessageSinkRef sink) {
		int index = -1;
		for (int i = 0; i < subscribers.length; i++) {
			if (sink.equals(subscribers[i])) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			LOG.info("Sink {} is not a subscriber", sink);
			return;
		}
		MessageSinkRef[] items = new MessageSinkRef[subscribers.length - 1];
		for (int i = 0, j = 0; i < items.length; i++, j++) {
			if (j == index) {
				j++;
			}
			items[i] = this.subscribers[j];
		}
		this.subscribers = items;
		LOG.info("Sink {} is no longer subscribing to order updates", sink);
	}

	MessageSinkRef[] subscribers() {
		return subscribers;
	}

	Int2ObjectOpenHashMap<Order> orders() {
		return this.orders;
	}

	Object2ObjectOpenHashMap<String, Trade> trades() {
		return this.trades;
	}

	public String name() {
		return name;
	}

	NewMode currentNewMode() {
		return currentNewMode;
	}

	CancelMode currentCancelMode() {
		return currentCancelMode;
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus((final boolean status) -> {
			if (status) {
				messageService.stateEvent(StateTransitionEvent.READY);
			} else { // DOWN or INITIALIZING
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}
		});
		return StateTransitionEvent.WAIT;
	}

	@Override
	public StateTransitionEvent readyEnter() {
		updatePublishingExecutor.execute(taskConsumer);
		receiver.securityHandlerList().add(this::handleSecurity);
		CompletableFuture<Request> retrieveSecurityFuture = messenger
				.sendRequest(messenger.referenceManager().rds(), RequestType.SUBSCRIBE,
						new ImmutableList.Builder<Parameter>()
								.add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()),
										Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE))
								.build(),
						ResponseHandler.NULL_HANDLER);
		retrieveSecurityFuture.thenAccept((r) -> {
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		});
		return StateTransitionEvent.NULL;
	}

	@Override
	public StateTransitionEvent activeEnter() {
		this.isStopped = false;
		receiver.requestHandlerList().add(this::handleRequest);
		receiver.commandHandlerList().add(this::handleCommand);
		receiver.newOrderRequestHandlerList().add(this::handleNewOrderRequest);
		receiver.cancelOrderRequestHandlerList().add(this::handleCancelOrderRequest);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		receiver.requestHandlerList().remove(this::handleRequest);
		receiver.commandHandlerList().remove(this::handleCommand);
		receiver.securityHandlerList().remove(this::handleSecurity);
		receiver.newOrderRequestHandlerList().remove(this::handleNewOrderRequest);
		receiver.cancelOrderRequestHandlerList().remove(this::handleCancelOrderRequest);
		taskConsumer.stop();
	}

	private void handleSecurity(final DirectBuffer buffer, final int offset, MessageHeaderDecoder header, final SecuritySbeDecoder security) {
		final byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
		security.getCode(bytes, 0);
		try {
			secIdToCode.put(security.sid(), new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim());
		} catch (UnsupportedEncodingException e) {
			LOG.error("Caught exception", e);
			this.messageService.stateEvent(StateTransitionEvent.FAIL);
			return;
		}
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		this.isStopped = true;
		return StateTransitionEvent.NULL;
	}

	private OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
	private OrderRejectedSbeDecoder orderRejectedDecoder = new OrderRejectedSbeDecoder();

	private void sendOrderRejected(NewOrderRequestSbeDecoder request, OrderRejectType rejectType, byte[] reason) {
		int encodedLength = OrderSender.encodeOrderRejectedOnly(buffer, 0, orderRejectedEncoder, channelId, channelSeq.getAndIncrement(),
				request.clientKey(), orderIdSeq.getAndIncrement(), request.secSid(), request.side(),
				request.limitPrice(), 0, 0, OrderStatus.REJECTED, 
				rejectType, 
				reason,
				messenger.timerService().toNanoOfDay());
		orderRejectedDecoder.wrap(buffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH,
				OrderRejectedSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendOrderRejected));

		// Send update to subscriber
		long result = messenger.orderSender().sendOrderRejected(subscribers, buffer, 0, orderRejectedDecoder,
				sinkSendResults);
		if (result != MessageSink.OK) {
			LOG.error("Could not deliver order expired message to at least one subscribers");
		}
	}

	private OrderAcceptedSbeEncoder orderAcceptedEncoder = new OrderAcceptedSbeEncoder();
	private OrderAcceptedSbeDecoder orderAcceptedDecoder = new OrderAcceptedSbeDecoder();

	private void sendOrderAccepted(Order order) {
		int encodedLength = OrderSender.encodeOrderAcceptedOnly(buffer, 0, orderAcceptedEncoder, channelId, channelSeq.getAndIncrement(),
				order.sid(), order.status(), order.limitPrice(), order.cumulativeExecQty(), order.leavesQty(),
				order.side(), order.orderId(), order.secSid(), ExecutionType.NEW,
				messenger.timerService().toNanoOfDay());
		orderAcceptedDecoder.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH,
				OrderAcceptedSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendOrderAccepted));
//		updateListener.receiveAccepted(buffer, 0, orderAcceptedDecoder);

		// Send update to subscriber
		long result = messenger.orderSender().sendOrderAccepted(subscribers, buffer, 0, orderAcceptedDecoder,
				this.sinkSendResults);
		if (result != MessageSink.OK) {
			LOG.error("Couldn't deliver message");
		}
	}

	private OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
	private OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();

	private void sendOrderCancelled(int orderSid, int orderSidBeingCancelled, OrderStatus status, int price, Side side,
			int orderId, long secSid, int leavesQty, int cumulativeQty, long updateTime) {
		int encodedLength = OrderSender.encodeOrderCancelledOnly(buffer, 0, orderCancelledEncoder, channelId,
				channelSeq.getAndIncrement(), orderSid, orderSidBeingCancelled, status, price, side, orderId, secSid,
				ExecutionType.CANCEL, leavesQty, cumulativeQty, updateTime, 0 /* exchange doesn't return quantity */);
		orderCancelledDecoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH,
				OrderCancelledSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendOrderCancelled));
		// updateListener.receiveCancelled(buffer, 0, orderCancelledDecoder);

		// Send update to subscriber
		long result = messenger.orderSender().sendOrderCancelled(subscribers, buffer, 0, orderCancelledDecoder,
				sinkSendResults);
		if (result != MessageSink.OK) {
			LOG.error("Could not send order cancelled to at least one subscriber");
		}
	}

	private static class TaskConsumer implements Runnable {
		private final BlockingQueue<Task> queue;
		private volatile boolean isRunning;
		TaskConsumer(BlockingQueue<Task> queue){
			this.queue = queue;
			this.isRunning = false;
		}
		@Override
		public void run() {
			isRunning = true;
			while (isRunning){
				try {
					Task task = queue.poll(1, TimeUnit.SECONDS);
					if (task != null){
						task.consumer.accept(task);
					}
				} 
				catch (InterruptedException e) {
					break;
				}
			}
		}
		public void stop(){
			isRunning = false;
		}
	}
	
	private static class Task {
		private final DirectBuffer buffer;
		private final int offset;
		private final Consumer<Task> consumer;

		public static Task of(MutableDirectBuffer buffer, int offset, int length, Consumer<Task> consumer) {
			MutableDirectBuffer newBuffer = new UnsafeBuffer(ByteBuffer.allocate(length));
			newBuffer.putBytes(0, buffer, offset, length);
			return new Task(newBuffer, 0, consumer);
		}

		Task(MutableDirectBuffer buffer, int offset, Consumer<Task> consumer) {
			this.buffer = buffer;
			this.offset = offset;
			this.consumer = consumer;
		}
	}

	private void handleNewOrderRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder request) {
		byte senderSinkId = header.senderSinkId();
		if (currentNewMode == NewMode.ACCEPT_ON_NEW) {
			LOG.info("handleNewOrderRequest [clientKey:{}, ordSid:{}]", request.clientKey());
			// create an order
			Order order = Order.of(request.secSid(), this.messenger.referenceManager().get(senderSinkId),
					request.clientKey(), request.quantity(), BooleanType.FALSE, request.orderType(), request.side(),
					request.limitPrice(), request.stopPrice(), request.tif(), OrderStatus.NULL_VAL, 
					messenger.timerService().toNanoOfDay(),
					OrderSbeEncoder.updateTimeNullValue());

			Order existingOrder;
			if ((existingOrder = this.orders.put(order.sid(), order)) != null) {
				LOG.error("Order with same sid {} already exists", existingOrder.sid());
				// put the existing order back into the map
				this.orders.put(existingOrder.sid(), existingOrder);
				// reject this new order
				sendOrderRejected(request, OrderRejectType.DUPLICATE_ORDER, unknownOrderReason);
				return;
			}
			// send an order accepted back
			order.status(OrderStatus.NEW);
			sendOrderAccepted(order);

		} else if (currentNewMode == NewMode.REJECT_ON_NEW) {
			// no need to create an order
			sendOrderRejected(request, OrderRejectType.OTHER, unknownOrderReason);
		} else if (currentNewMode == NewMode.MATCH_ON_NEW) {
			LOG.info("handleNewOrderRequest [clientKey:{}, ordSid:{}]", request.clientKey());
			// create an order
			final TriggerInfoDecoder triggerInfo = request.triggerInfo();
			Order order = Order.of(request.secSid(), this.messenger.referenceManager().get(senderSinkId),
					request.clientKey(), request.quantity(), BooleanType.FALSE, request.orderType(), request.side(),
					request.limitPrice(), request.stopPrice(), request.tif(), OrderStatus.NULL_VAL, 
					messenger.timerService().toNanoOfDay(),
					OrderSbeEncoder.updateTimeNullValue(),
					triggerInfo);
			try {
				order.status(OrderStatus.NEW);
				sendOrderAccepted(order);
				matchingEngine.addOrder(request.secSid(), order);
			} catch (Exception e) {
				LOG.error("Cannot add order to matching engine...");
				order.status(OrderStatus.REJECTED);
				sendOrderRejected(request, OrderRejectType.OTHER, unknownOrderReason);
			}
		} else {
			throw new IllegalStateException("Invalid NewMode " + currentNewMode.name());
		}
	}

	private OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder = new OrderCancelRejectedSbeEncoder();
	private OrderCancelRejectedSbeDecoder orderCancelRejectedDecoder = new OrderCancelRejectedSbeDecoder();

	private void sendOrderCancelRejected(int orderSid, OrderStatus status, long secSid,
			OrderCancelRejectType rejectType,
			byte[] reason,
			ExecutionType executionType, 
			long updateTime) {
		int encodedLength = OrderSender.encodeOrderCancelRejectedOnly(buffer, 
				0, 
				orderCancelRejectedEncoder, 
				channelId,
				channelSeq.getAndIncrement(), orderSid, status, secSid, 
				rejectType, 
				reason,
				executionType, 
				updateTime);
		orderCancelRejectedDecoder.wrap(buffer, 0, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH,
				OrderCancelRejectedSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendOrderCancelRejected));

		// Send update to subscriber
		long result = messenger.orderSender().sendOrderCancelRejected(subscribers, buffer, 0,
				orderCancelRejectedDecoder, sinkSendResults);
		if (result != MessageSink.OK) {
			LOG.error("Could not send order cancel rejected to at least one subscriber");
		}
	}

	private void handleCancelOrderRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder decoder,
			CancelOrderRequestSbeDecoder request) {
		long nanoOfDay = messenger.timerService().toNanoOfDay();
		if (currentCancelMode == CancelMode.CANCEL_ON_CANCEL) {
			// check if the order exist
			int orderSidToBeCancelled = request.orderSidToBeCancelled();
			Order order = this.orders.remove(orderSidToBeCancelled);
			if (order != null) {
				order.status(OrderStatus.CANCELLED);
				// If an order exists, send it to updateListener and subscribers
				sendOrderCancelled(request.clientKey(), request.orderSidToBeCancelled(), order.status(),
						order.limitPrice(), order.side(), order.orderId(), order.secSid(), order.leavesQty(),
						order.cumulativeExecQty(), 
						nanoOfDay);
			} else {
				sendOrderCancelRejected(request.clientKey(), OrderStatus.REJECTED, request.secSid(),
						OrderCancelRejectType.UNKNOWN_ORDER, 
						unknownOrderReason,
						ExecutionType.CANCEL_REJECT, 
						nanoOfDay);
			}
		} else if (currentCancelMode == CancelMode.CANCEL_REJECT_ON_CANCEL) {
			LOG.info("Received cancel order request [clientKey:{}, orderSidToBeCancelled:{}]", request.clientKey(),
					request.orderSidToBeCancelled());
			// reject the cancel request
			OrderStatus status = OrderStatus.REJECTED;
			int orderSidToBeCancelled = request.orderSidToBeCancelled();
			Order order = this.orders.remove(orderSidToBeCancelled);
			if (order != null) {
				status = order.status();
			}
			// OrderCancelRejectType has to be UNKNOWN_ORDER, otherwise a valid
			// order must exist
			sendOrderCancelRejected(request.clientKey(), status, request.secSid(), 
					OrderCancelRejectType.UNKNOWN_ORDER,
					unknownOrderReason,
					ExecutionType.CANCEL_REJECT,
					nanoOfDay);
		} else {
			throw new IllegalStateException("Invalid NewMode " + currentNewMode.name());
		}
	}

	private void handleRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
		byte senderSinkId = header.senderSinkId();
		if (request.requestType() == RequestType.SUBSCRIBE) {
			addSubscriber(this.messenger.referenceManager().get(senderSinkId));
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		} else if (request.requestType() == RequestType.UNSUBSCRIBE) {
			removeSubscriber(this.messenger.referenceManager().get(senderSinkId));
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		} else {
			LOG.info("Received unexpected type: {}", request.requestType().name());
			throw new IllegalStateException("Invalid request type " + request.requestType().name());
		}
	}

	private void handleCommand(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder command) {
		byte senderSinkId = header.senderSinkId();
		if (command.commandType() == CommandType.LINE_HANDLER_ACTION) {
			LineHandlerActionType actionType = LineHandlerActionType.NULL_VAL;
			for (ParametersDecoder parameter : command.parameters()) {
				switch (parameter.parameterType()) {
				case LINE_HANDLER_ACTION_TYPE:
					actionType = LineHandlerActionType.get((byte) parameter.parameterValueLong());
					break;
				default:
					LOG.warn("Received unexpected parameter | {}", parameter.parameterType().name());
				}
			}
			if (actionType != LineHandlerActionType.NULL_VAL) {
				messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.OK, command.commandType());
				processAction(actionType);
			} else {
				messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.NOT_SUPPORTED,
						command.commandType());
			}
		}
	}

	private void processAction(LineHandlerActionType actionType) {
		switch (actionType) {
		case CANCEL_ALL_TRADES:
			cancelAllTrades();
			break;
		case CHANGE_MODE_ACCEPT_ON_NEW:
			this.currentNewMode = NewMode.ACCEPT_ON_NEW;
			LOG.info("Changed NewMode to {}", currentNewMode.name());
			break;
		case CHANGE_MODE_MATCH_ON_NEW:
			this.currentNewMode = NewMode.MATCH_ON_NEW;
			LOG.info("Changed NewMode to {}", currentNewMode.name());
			break;
		case CHANGE_MODE_REJECT_ON_NEW:
			this.currentNewMode = NewMode.REJECT_ON_NEW;
			LOG.info("Changed NewMode to {}", currentNewMode.name());
			break;
		case CHANGE_MODE_ACCEPT_ON_CANCEL:
			this.currentCancelMode = CancelMode.CANCEL_ON_CANCEL;
			LOG.info("Changed CancelMode to {}", this.currentCancelMode.name());
			break;
		case CHANGE_MODE_REJECT_ON_CANCEL:
			this.currentCancelMode = CancelMode.CANCEL_REJECT_ON_CANCEL;
			LOG.info("Changed CancelMode to {}", this.currentCancelMode.name());
			break;
		case RESET:
			clearAllOrdersAndTrades();
			break;
		case EXPIRE_ALL_LIVE_ORDERS:
			expireAllLiveOrders();
			break;
		case FILL_ALL_LIVE_ORDERS:
			fillAllLiveOrders();
			break;
		case PARTIAL_FILL_ALL_LIVE_ORDERS:
			partialFillAllLiveOrders();
			break;
		default:
			break;
		}
	}

	private TradeCancelledSbeEncoder tradeCancelledEncoder = new TradeCancelledSbeEncoder();
	private TradeCancelledSbeDecoder tradeCancelledDecoder = new TradeCancelledSbeDecoder();

	private void cancelAllTrades() {
		LOG.info("Cancelling all trades");
		for (Trade trade: this.trades.values()) {
			Order order = orders.get(trade.orderSid());
			long seq = channelSeq.getAndIncrement();
			long result = messenger.orderSender().sendTradeCancelled(subscribers, channelId, seq, trade.sid(),
					trade.orderSid(), order.status(), ExecutionType.TRADE_CANCEL, order.side(), 
					OrderSender.prepareExecutionId(trade.executionId(), messenger.stringBuffer()),
					trade.executionPrice(), trade.executionQty(), order.secSid(), trade.leavesQty(),
					trade.cumulativeQty(), messenger.timerService().toNanoOfDay(), sinkSendResults);
			if (result != MessageSink.OK) {
				LOG.error("Could not send trade cancelled");
			}

			if (updateListener != null) {
				int encodedLength = OrderSender.encodeTradeCancelledOnly(buffer, 0, tradeCancelledEncoder, channelId, seq, trade.sid(),
						trade.orderSid(), order.status(), ExecutionType.TRADE_CANCEL, order.side(), 
						OrderSender.prepareExecutionId(trade.executionId(), messenger.stringBuffer()),
						trade.executionPrice(), trade.executionQty(), order.secSid(), trade.leavesQty(),
						trade.cumulativeQty(), messenger.timerService().toNanoOfDay());
				tradeCancelledDecoder.wrap(buffer, 0, TradeCancelledSbeDecoder.BLOCK_LENGTH,
						TradeCancelledSbeDecoder.SCHEMA_VERSION);
				updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendTradeCancelled));
//				this.updateListener.receiveTradeCancelled(buffer, 0, tradeCancelledDecoder);
			}
		}
		LOG.info("Cancelled all trades");
	}

	private void clearAllOrdersAndTrades() {
		LOG.info("Cleared all orders and trades");
		this.trades.clear();
		this.orders.clear();
	}

	private OrderExpiredSbeEncoder orderExpiredEncoder = new OrderExpiredSbeEncoder();

    private void orderExpired(Messenger messenger, MutableDirectBuffer buffer, Order order) {
        long seq = channelSeq.getAndIncrement();
        int encodedLength = OrderSender.encodeOrderExpiredOnly(buffer, 0, orderExpiredEncoder, channelId, seq,
                order.sid(), order.orderId(), order.secSid(), order.side(),
                order.limitPrice(), order.cumulativeExecQty(), order.leavesQty(), order.status(), messenger.timerService().toNanoOfDay());
        updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendOrderExpired));
    }
    
	private void expireAllLiveOrders() {
		LOG.info("Expiring all live orders");
		for (Entry<Order> entry : this.orders.int2ObjectEntrySet()) {
			Order order = entry.getValue();
			long seq = channelSeq.getAndIncrement();
			if (order.status() == OrderStatus.NEW) {
				order.status(OrderStatus.EXPIRED);
				long result = messenger.orderSender().sendOrderExpired(subscribers, channelId, seq, order.sid(),
						order.orderId(), order.secSid(), order.side(), order.limitPrice(), order.cumulativeExecQty(), order.leavesQty(),
						order.status(), messenger.timerService().toNanoOfDay(), sinkSendResults);
				if (result != MessageSink.OK) {
					LOG.error("Could not deliver order expired message to at least one subscribers");
				}

				if (updateListener != null) {
					int encodedLength = OrderSender.encodeOrderExpiredOnly(buffer, 0, orderExpiredEncoder, channelId, seq, order.sid(),
							order.orderId(), order.secSid(), order.side(), order.limitPrice(), order.cumulativeExecQty(), order.leavesQty(),
							order.status(), messenger.timerService().toNanoOfDay());
					updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendOrderExpired));
				}
			}
		}
		LOG.info("Expired all existing orders");
	}

	private void fillAllLiveOrders() {
		LOG.info("Filling all live orders");
		for (Entry<Order> entry : this.orders.int2ObjectEntrySet()) {
			Order order = entry.getValue();
			if (order.status() == OrderStatus.NEW || order.status() == OrderStatus.PARTIALLY_FILLED) {
				order.status(OrderStatus.FILLED);
				int executionQty = order.leavesQty();
				order.leavesQty(0);
				order.cumulativeExecQty(order.cumulativeExecQty() + executionQty);
				tradeCreated(order, order.limitPrice(), executionQty);
			}
		}
		LOG.info("Filled all live orders");
	}

	private void partialFillAllLiveOrders() {
		LOG.info("Partial-filling all live orders");
		for (Entry<Order> entry : this.orders.int2ObjectEntrySet()) {
			Order order = entry.getValue();
			if (order.status() == OrderStatus.NEW || order.status() == OrderStatus.PARTIALLY_FILLED) {
				// Try to fill one fake board lot
				if (order.leavesQty() > IMAGINARY_BOARD_LOT) {
					order.status(OrderStatus.PARTIALLY_FILLED);
					order.leavesQty(order.leavesQty() - IMAGINARY_BOARD_LOT);
					order.cumulativeExecQty(order.cumulativeExecQty() + IMAGINARY_BOARD_LOT);
					tradeCreated(order, order.limitPrice(), IMAGINARY_BOARD_LOT);
				}
			}
		}
		LOG.info("Partial-filled all live orders");
	}

	private final MutableDirectBuffer buffer;
	private final MutableDirectBuffer childBuffer;
	private TradeCreatedSbeEncoder tradeCreatedEncoder = new TradeCreatedSbeEncoder();

	private void tradeCreated(Order order, int executionPrice, int executionQty) {
		tradeCreated(messenger, buffer, order, executionPrice, executionQty);
	}

	private void tradeCreated(Messenger messenger, MutableDirectBuffer buffer, Order order, int executionPrice,
			int executionQty) {
		// This is the IMAGINARY entry point of each trade, we want to tag a
		// Trade SID
		// to each of these trades (These are all imaginary stuff though)
		// This should really be part of the LineHandler logic
		int tradeSid = tradeSidSeq.getAndIncrement();
		String executionId = Integer.toString(executionIdSeq.getAndIncrement());
		Trade trade = Trade.of(tradeSid, order.sid(), order.orderId(), order.secSid(), order.side(), order.leavesQty(),
				order.cumulativeExecQty(), executionId, executionPrice, executionQty, order.status(), TradeStatus.NEW,
				messenger.timerService().toNanoOfDay(), ServiceConstant.NULL_TIME_NS);
		this.trades.put(trade.executionId(), trade);

		// TODO encode trade into a buffer first before sending them out to
		// subscribers
		long seq = channelSeq.getAndIncrement();
		long result = messenger.orderSender().sendTradeCreated(subscribers, channelId, seq, trade.sid(), order.sid(),
				order.orderId(), order.status(), order.side(), order.leavesQty(), order.cumulativeExecQty(),
				OrderSender.prepareExecutionId(trade.executionId(), messenger.stringBuffer()),
				executionPrice, executionQty, order.secSid(), 
				messenger.timerService().toNanoOfDay(),
				sinkSendResults);
		if (result != MessageSink.OK) {
			LOG.error("Could not send trades");
		}

		int encodedLength = OrderSender.encodeTradeCreatedOnly(buffer, 0, tradeCreatedEncoder, channelId, seq, trade.sid(),
				trade.orderSid(), trade.orderId(), order.status(), trade.side(), trade.leavesQty(),
				trade.cumulativeQty(), 
				OrderSender.prepareExecutionId(trade.executionId(), messenger.stringBuffer()),
				trade.executionPrice(), trade.executionQty(),
				order.secSid(), 
				messenger.timerService().toNanoOfDay());
		updateTaskQueue.offer(Task.of(buffer, 0, encodedLength, this::sendTradeCreated));
	}

	private final OrderCancelledSbeDecoder orderCancelledDecoderForUpdatePublisher = new OrderCancelledSbeDecoder();
	private void sendOrderCancelled(Task task) {
		orderCancelledDecoderForUpdatePublisher.wrap(task.buffer, task.offset, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveCancelled(task.buffer, task.offset, orderCancelledDecoderForUpdatePublisher);
	}

	private final OrderRejectedSbeDecoder orderRejectedDecoderForUpdatePublisher = new OrderRejectedSbeDecoder();
	private void sendOrderRejected(Task task) {
		orderRejectedDecoderForUpdatePublisher.wrap(task.buffer, task.offset, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveRejected(task.buffer, task.offset, orderRejectedDecoderForUpdatePublisher);
	}

	private final OrderAcceptedSbeDecoder orderAcceptedDecoderForUpdatePublisher = new OrderAcceptedSbeDecoder();
	private void sendOrderAccepted(Task task) {
		orderAcceptedDecoderForUpdatePublisher.wrap(task.buffer, task.offset, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveAccepted(task.buffer, task.offset, orderAcceptedDecoderForUpdatePublisher);
	}

	private final OrderCancelRejectedSbeDecoder orderCancelRejectedDecoderForUpdatePublisher = new OrderCancelRejectedSbeDecoder();
	private void sendOrderCancelRejected(Task task) {
		orderCancelRejectedDecoderForUpdatePublisher.wrap(task.buffer, task.offset, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH, OrderCancelRejectedSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveCancelRejected(task.buffer, task.offset, orderCancelRejectedDecoderForUpdatePublisher);
	}

	private final TradeCancelledSbeDecoder tradeCancelledDecoderForUpdatePublisher = new TradeCancelledSbeDecoder();
	private void sendTradeCancelled(Task task) {
		tradeCancelledDecoderForUpdatePublisher.wrap(task.buffer, task.offset, TradeCancelledSbeDecoder.BLOCK_LENGTH, TradeCancelledSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveTradeCancelled(task.buffer, task.offset, tradeCancelledDecoderForUpdatePublisher);
	}

	private final OrderExpiredSbeDecoder orderExpiredDecoderForUpdatePublisher = new OrderExpiredSbeDecoder();
	private void sendOrderExpired(Task task) {
		orderExpiredDecoderForUpdatePublisher.wrap(task.buffer, task.offset, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveExpired(task.buffer, task.offset, orderExpiredDecoderForUpdatePublisher);
	}

	private final TradeCreatedSbeDecoder tradeCreatedDecoderForUpdatePublisher = new TradeCreatedSbeDecoder();
	private void sendTradeCreated(Task task) {
		tradeCreatedDecoderForUpdatePublisher.wrap(task.buffer, task.offset, TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION);
		updateListener.receiveTradeCreated(task.buffer, task.offset, tradeCreatedDecoderForUpdatePublisher);
	}

}
