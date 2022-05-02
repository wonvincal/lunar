package com.lunar.order;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.RingBuffer;
import com.lunar.core.Lifecycle.LifecycleExceptionHandler;
import com.lunar.core.LifecycleState;
import com.lunar.core.LifecycleUtil;
import com.lunar.core.OrderRequestEventHandlerTestHelper;
import com.lunar.core.SlidingWindowThrottleTracker;
import com.lunar.core.ThrottleTracker;
import com.lunar.core.UserControlledTimerService;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.service.ReplayExchangeService;
import com.lunar.service.ServiceConstant;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class OrderExecutorTest {
	private static AtomicInteger clientKeySeq =  new AtomicInteger(7000000);
	private static int FAKE_PORT_SID = 1;
	@Mock
	private MessageSinkRefMgr refMgr;
	
	@Mock
	private ReplayExchangeService exchangeService;
	
	@Mock
	private MessageSink selfSink;
	
	@Mock
	private MessageSink omesSink;
	
	@Mock
	private Messenger messenger;
	
	private LineHandlerEngine lineHandlerEngine;	
	private LifecycleExceptionHandler lifecycleExceptionHandler;	
	private OrderRequestCompletionHandler orderReqCompletionHandler;
	private Consumer<OrderRequest> noopOrderRequestEventHandler;
	
	private ThrottleTracker throttleTracker;
	private OrderExecutor orderExecutor;
	private TestHelper testHelper;
	private UserControlledTimerService timerService;
	private MessageSinkRef self;
	private Security security;
	private int numThrottles;
	private Duration duration;
	private ThrottleTracker[] multThrottleTrackers;

	private LineHandlerEngine lineHandlerEngineForMulti;	
	private LifecycleExceptionHandler lifecycleExceptionHandlerForMulti;	
	private OrderRequestCompletionHandler orderReqCompletionHandlerForMulti;
	private Consumer<OrderRequest> noopOrderRequestEventHandlerForMulti;
	private OrderExecutor orderExecutorWithMultiThrottleTrackers;
	
	private final static int MAX_NUM_BATCHED_ORDERS = 6;
	private int numThrottlesWithMaxBatchSize = 6;
	private OrderExecutor orderExecutorWithMaxBatchSize;
	private LineHandlerEngine lineHandlerEngineForMaxBatchSize;
	private LifecycleExceptionHandler lifecycleExceptionHandlerForMaxBatchSize;
	private OrderRequestCompletionHandler orderReqCompletionHandlerForMaxBatchSize;

	@SuppressWarnings("unchecked")
	@Before
	public void setup(){
		testHelper = TestHelper.of();
		timerService = testHelper.timerService();
		numThrottles = 4;
		duration = Duration.ofSeconds(1);
		throttleTracker = new SlidingWindowThrottleTracker(numThrottles, 
				duration, 
				timerService);
		
		lineHandlerEngine = mock(LineHandlerEngine.class); 
		lifecycleExceptionHandler = mock(LifecycleExceptionHandler.class);
		orderReqCompletionHandler = mock(OrderRequestCompletionHandler.class);
		noopOrderRequestEventHandler = mock(Consumer.class);

		orderExecutor = OrderExecutor.of("test-order-executor", 
				new ThrottleTracker[]{throttleTracker},
				timerService,
				lineHandlerEngine,
				orderReqCompletionHandler,
				Optional.empty(),
				Optional.empty(),
				128);
		orderExecutor.lifecycleExceptionHandler(lifecycleExceptionHandler);
		self = MessageSinkRef.of(selfSink);
		security = Security.of(12345678L, SecurityType.WARRANT, "61727", 101, false, SpreadTableBuilder.get(SecurityType.WARRANT));
		
		multThrottleTrackers = new ThrottleTracker[2];
		int numThrottleForFirstTracker = 2;
		multThrottleTrackers[0] = new SlidingWindowThrottleTracker(numThrottleForFirstTracker , 
				duration, 
				timerService);
		multThrottleTrackers[1] = new SlidingWindowThrottleTracker(numThrottles - numThrottleForFirstTracker, 
				duration, 
				timerService);

		lineHandlerEngineForMulti = mock(LineHandlerEngine.class); 
		lifecycleExceptionHandlerForMulti = mock(LifecycleExceptionHandler.class);
		orderReqCompletionHandlerForMulti = mock(OrderRequestCompletionHandler.class);
		noopOrderRequestEventHandlerForMulti = mock(Consumer.class);

		orderExecutorWithMultiThrottleTrackers = OrderExecutor.of("test-order-executor-multi-trackers", 
				multThrottleTrackers,
				timerService,
				lineHandlerEngineForMulti,
				orderReqCompletionHandlerForMulti,
				Optional.empty(),
				Optional.empty(), 128);
		orderExecutorWithMultiThrottleTrackers.lifecycleExceptionHandler(lifecycleExceptionHandlerForMulti);
		
		lineHandlerEngineForMaxBatchSize = mock(LineHandlerEngine.class);
		lifecycleExceptionHandlerForMaxBatchSize = mock(LifecycleExceptionHandler.class);
		orderReqCompletionHandlerForMaxBatchSize = mock(OrderRequestCompletionHandler.class);
		orderExecutorWithMaxBatchSize = OrderExecutor.of("test-order-executor-batch-size", 
				new ThrottleTracker[]{new SlidingWindowThrottleTracker(numThrottlesWithMaxBatchSize, 
						duration, 
						timerService)},
				timerService,
				lineHandlerEngineForMaxBatchSize,
				orderReqCompletionHandlerForMaxBatchSize,
				Optional.empty(),
				Optional.of(MAX_NUM_BATCHED_ORDERS), 128);
		orderExecutorWithMaxBatchSize.lifecycleExceptionHandler(lifecycleExceptionHandlerForMaxBatchSize);
	}
	
	@Test
	public void givenCreatedThenStateIsInit(){
		assertEquals(LifecycleState.INIT, orderExecutor.state()); 
	}
	
	@Test
	public void givenCreatedWhenStartThenDisruptorIsSetAsRunning(){
		assertEquals(LifecycleState.INIT, orderExecutor.state());
//		assertTrue(orderExecutor.isDisruptorRunning());
	}
	
	@Test
	public void givenRunningWhenSendOrderWithThrottleCheckNotRequiredThenOrderGetSentOut() {
		// When
		assertEquals(LifecycleState.INIT, orderExecutorWithMaxBatchSize.state());
		
		LifecycleUtil.advanceTo(orderExecutorWithMaxBatchSize, LifecycleState.ACTIVE);

		NewOrderRequest request = createNewOrderRequest(self, security);
		request.throttleCheckRequired(false);
		ArrayList<OrderRequest> requests = new ArrayList<>();
		requests.add(request);
		
		orderExecutorWithMaxBatchSize.queue().addAll(requests);
		orderExecutorWithMaxBatchSize.consumeAll();

		//numThrottlesWithMaxBatchSize
		verify(lineHandlerEngineForMaxBatchSize, times(1)).sendOrderRequest(request);
		assertTrue(orderExecutorWithMaxBatchSize.throttleTrackers()[0].getThrottle(numThrottlesWithMaxBatchSize));
	}
	
	
	@Test
	public void givenRunningWhenSendSeveralOrderWithEndOfBatchThenOrdersGetSentOut() throws Exception {
		// When
		assertEquals(LifecycleState.INIT, orderExecutorWithMaxBatchSize.state());
		
		LifecycleUtil.advanceTo(orderExecutorWithMaxBatchSize, LifecycleState.ACTIVE);

		NewOrderRequest request = createNewOrderRequest(self, security);
		ArrayList<OrderRequest> requests = new ArrayList<>();
		requests.add(request);
		requests.add(request);
		requests.add(request);
		
		assertEquals(MAX_NUM_BATCHED_ORDERS, orderExecutorWithMaxBatchSize.maxNumBatchOrders());
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchActionSize());
		orderExecutorWithMaxBatchSize.queue().addAll(requests);
		orderExecutorWithMaxBatchSize.consumeAll();
		
		// Test end of batch
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchActionSize());
		verify(lineHandlerEngineForMaxBatchSize, times(3)).sendOrderRequest(request);
		verify(orderReqCompletionHandlerForMaxBatchSize, times(3)).sendToExchange(anyObject(), anyLong());
	}

	/*
	@Test
	public void givenRunningWhenSendSeveralOrdersExceedMaxNumBatchThenOrdersGetSentOut() throws Exception {
		// When
		assertEquals(LifecycleState.INIT, orderExecutorWithMaxBatchSize.state());
		
		LifecycleUtil.advanceTo(orderExecutorWithMaxBatchSize, LifecycleState.ACTIVE);

		NewOrderRequest request = createNewOrderRequest(self, security);

		ArrayList<OrderRequest> requests = new ArrayList<>();
		for (int i = 0; i < MAX_NUM_BATCHED_ORDERS; i++){
			requests.add(request);
		}		
		orderExecutorWithMaxBatchSize.queue().addAll(requests);
		orderExecutorWithMaxBatchSize.consumeAll();
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchActionSize());
		verify(lineHandlerEngineForMaxBatchSize, times(MAX_NUM_BATCHED_ORDERS)).sendOrderRequest(request);
		verify(orderReqCompletionHandlerForMaxBatchSize, times(MAX_NUM_BATCHED_ORDERS)).sendToExchange(anyObject(), anyLong());
	}
	 */
	/*
	@Test
	public void givenRunningWhenSendSeveralOrdersTriggerThrottlingThenOrdersGetSentOut() throws Exception {
		int throttles = numThrottlesWithMaxBatchSize - 2;
		orderExecutorWithMaxBatchSize = OrderExecutor.of("test-order-executor-batch-size", 
				new ThrottleTracker[]{new SlidingWindowThrottleTracker(throttles,
						duration, 
						timerService)},
				timerService,
				lineHandlerEngineForMaxBatchSize,
				orderReqCompletionHandlerForMaxBatchSize,
				Optional.empty(),
				Optional.of(MAX_NUM_BATCHED_ORDERS), 128);
		
		assertEquals(LifecycleState.INIT, orderExecutorWithMaxBatchSize.state());
		
		LifecycleUtil.advanceTo(orderExecutorWithMaxBatchSize, LifecycleState.ACTIVE);

		NewOrderRequest request = createNewOrderRequest(self, security);

		for (int i = 0; i < throttles; i++){
			orderExecutorWithMaxBatchSize.onEvent(request, 0, false);
			assertEquals(i + 1, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
			assertEquals(i + 1, orderExecutorWithMaxBatchSize.currentBatchActionSize());
		}
		
		// Flush due to throttle, throttled request leads to one 'action'
		orderExecutorWithMaxBatchSize.onEvent(request, 0, false);
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
		assertEquals(1, orderExecutorWithMaxBatchSize.currentBatchActionSize());
		
		// Advance timer to make throttle available again
		timerService.advance(duration);
		orderExecutorWithMaxBatchSize.onEvent(request, 0, false);
		assertEquals(1, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
		assertEquals(2, orderExecutorWithMaxBatchSize.currentBatchActionSize());

		// End of batch, triggers flush
		orderExecutorWithMaxBatchSize.onEvent(request, 0, true);
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchOrderSize());
		assertEquals(0, orderExecutorWithMaxBatchSize.currentBatchActionSize());
		
		verify(lineHandlerEngineForMaxBatchSize, times(throttles + 2)).sendOrderRequest(request);
		verify(orderReqCompletionHandlerForMaxBatchSize, times(throttles + 2)).sendToExchange(anyObject(), anyLong());
		verify(orderReqCompletionHandlerForMaxBatchSize, times(1)).timeoutAfterThrottled(anyObject());
	}
	*/
	/*
	@Test
	public void givenRunningButNotWarmupOrActiveOrRecoveryWhenSendOrderRequestThenNoop() throws Exception{
		// When
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		orderExecutor.noopOrderRequestEventHandler(noopOrderRequestEventHandler);
		
		NewOrderRequest request = createNewOrderRequest(self, security);
		OrderRequestEvent event = new OrderRequestEvent();
		event.request(request);
		orderExecutor.onEvent(event, 0, true);
		
		// When
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.RECOVERY);
		orderExecutor.onEvent(event, 0, true);

		// When
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.RESET);
		orderExecutor.onEvent(event, 0, true);

		// When
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.STOPPED);
		orderExecutor.onEvent(event, 0, true);

		// Then
		verify(noopOrderRequestEventHandler, times(3)).onEvent(any(OrderRequestEvent.class), anyLong(), anyBoolean());
	}
	
	@Test
	public void givenRunningWhenStopThenNotAllowBecauseDisruptorIsStillRunning(){
		// Given
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);

		// When
		LifecycleUtil.failToAdvance(orderExecutor, LifecycleState.STOPPED);
	}
	
	@Test
	public void givenNotRunningWhenStopThenOK(){
		// Given
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		
		// When
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.RESET);
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.STOPPED);
	}

	@Test
	public void givenActiveWhenReceiveEventThenSendRequestToExchange(){
		// Given
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, orderExecutor.state());
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(OrderRequestEvent::new, 128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);

		// when

		NewOrderRequest request = createNewOrderRequest(self, security);
		helper.onData(request);
		helper.pushNextMessage();
		
		// then
		verify(lineHandlerEngine, times(1)).sendOrderRequest(any(OrderRequest.class));
		verifyNoMoreInteractions(lineHandlerEngine);
		verify(orderReqCompletionHandler, times(1)).sendToExchange(any(OrderRequest.class), anyLong());
		verifyNoMoreInteractions(orderReqCompletionHandler);
	}
	
	@Test
	public void givenActiveWhenReceiveEventsForMultipleTrackers(){
		// Given
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		LifecycleUtil.advanceTo(orderExecutorWithMultiThrottleTrackers, LifecycleState.ACTIVE);

		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(OrderRequestEvent::new, 128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutorWithMultiThrottleTrackers, ringBuffer);
		
		// When
		NewOrderRequest request = createNewOrderRequest(self, security);
		helper.onData(request);
		helper.pushNextMessage();
		
		request = createNewOrderRequest(self, security);
		helper.onData(request);
		helper.pushNextMessage();

		request = createNewOrderRequest(self, security);
		request.timeoutAtNanoOfDay(timerService.toNanoOfDay() + TimeUnit.MILLISECONDS.toNanos(500));
		helper.onData(request);
		helper.pushNextMessage();
		
		request = createNewOrderRequest(self, security, 1);
		helper.onData(request);
		helper.pushNextMessage();

		timerService.advance(duration);

		for (int i = 0; i < 2; i++){
			request = createNewOrderRequest(self, security);
			helper.onData(request);
			helper.pushNextMessage();
			
			request = createNewOrderRequest(self, security, 1);
			helper.onData(request);
			helper.pushNextMessage();
		}
		request = createNewOrderRequest(self, security);
		request.timeoutAtNanoOfDay(timerService.toNanoOfDay() + TimeUnit.MILLISECONDS.toNanos(500));
		helper.onData(request);
		helper.pushNextMessage();
		
		request = createNewOrderRequest(self, security, 1);
		request.timeoutAtNanoOfDay(timerService.toNanoOfDay() + TimeUnit.MILLISECONDS.toNanos(500));
		helper.onData(request);
		helper.pushNextMessage();

		
		// then
		verify(lineHandlerEngine, times(3 + 2*2)).sendOrderRequest(any(OrderRequest.class));
		verifyNoMoreInteractions(lineHandlerEngine);
		verify(orderReqCompletionHandler, times(3 + 2*2)).sendToExchange(any(OrderRequest.class), anyLong());
		verify(orderReqCompletionHandler, times(1 + 1*2)).timeoutAfterThrottled(any(OrderRequest.class));
		verifyNoMoreInteractions(orderReqCompletionHandler);
	}

	@Test
	public void givenActiveWhenReceiveCompositeEventThenSendRequestToExchange(){
		// Given
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, orderExecutor.state());
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(OrderRequestEvent::new, 128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);

		// when
		NewOrderRequest request = createNewCompositeOrderRequest(self, security, 4);
		helper.onData(request);
		helper.pushNextMessage();
		
		request = createNewCompositeOrderRequest(self, security, 2);
		helper.onData(request);
		helper.pushNextMessage();
		
		request = createNewCompositeOrderRequest(self, security, 4);
		helper.onData(request);
		helper.pushNextMessage();

		request = createNewCompositeOrderRequest(self, security, 3);
		helper.onData(request);
		helper.pushNextMessage();

		request = createNewCompositeOrderRequest(self, security, 2);
		helper.onData(request);
		helper.pushNextMessage();

		// then
		verify(lineHandlerEngine, times(3)).sendOrderRequest(any(OrderRequest.class));
		verifyNoMoreInteractions(lineHandlerEngine);
		verify(orderReqCompletionHandler, times(3)).sendToExchange(any(OrderRequest.class), anyLong());
		verify(orderReqCompletionHandler, times(0)).timeoutAfterThrottled(any(OrderRequest.class));
//		verifyNoMoreInteractions(orderReqCompletionHandler);
	}

	@Test
	public void givenActiveWhenReceiveEventWithExpiredTimeoutValueThenSendTimeoutBack(){
		// given
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(
				OrderRequestEvent::new, 
				128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);
		
		// when

		NewOrderRequest request = createNewOrderRequest(self, security);
		request.timeoutAtNanoOfDay(timerService.toNanoOfDay() - 1);
		helper.onData(request);
		helper.pushNextMessage();
		
		// then
		verify(orderReqCompletionHandler, times(1)).timeout(request);
		verifyZeroInteractions(lineHandlerEngine);
	}
	
	@Test
	public void givenActiveAndNoMoreThrottleWhenReceiveEventAndTimePassesThenSendTimeoutBack(){
		// given - a real-time throttle tracker
		final Duration duration = Duration.ofSeconds(1);
		throttleTracker = new SlidingWindowThrottleTracker(numThrottles, 
				duration, 
				testHelper.realTimerService());
		
		ThrottleTracker[] throttleTrackers = new ThrottleTracker[1];
		throttleTrackers[0] = throttleTracker;

		orderExecutor = OrderExecutor.of("test-order-executor", 
				throttleTrackers,
				testHelper.realTimerService(),
				lineHandlerEngine,
				orderReqCompletionHandler,
				Optional.empty(),
				Optional.empty());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, orderExecutor.state());
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(
				OrderRequestEvent::new, 
				128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);

		// when
		long nanoOfDay = testHelper.realTimerService().toNanoOfDay();
		for (int i = 0; i < numThrottles; i++){
			NewOrderRequest request = createNewOrderRequest(self, security, nanoOfDay + TimeUnit.MILLISECONDS.toNanos(900));
			helper.onData(request);
			helper.pushNextMessage();
		}
		verify(lineHandlerEngine, times(numThrottles)).sendOrderRequest(any(OrderRequest.class));
		NewOrderRequest request = createNewOrderRequest(self, security, nanoOfDay + TimeUnit.MILLISECONDS.toNanos(900));
		helper.onData(request);
		helper.pushNextMessage();
		verify(lineHandlerEngine, times(numThrottles)).sendOrderRequest(any(OrderRequest.class));
		verify(orderReqCompletionHandler, times(1)).timeoutAfterThrottled(any());
	}
*/
	private static NewOrderRequest createNewOrderRequest(MessageSinkRef self, Security security){
		final int refClientKey = clientKeySeq.getAndIncrement();
		NewOrderRequest request = NewOrderRequest.of(refClientKey, 
				self, 
				security, 
				OrderType.LIMIT_ORDER,
				100, 
				Side.BUY, 
				TimeInForce.FILL_AND_KILL, 
				BooleanType.TRUE, 
				10000, 
				10000, 
				Long.MAX_VALUE, 
				true,
				ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX,
				FAKE_PORT_SID);
		request.orderSid(refClientKey);
		return request;
	}
	
	private static NewOrderRequest createNewOrderRequest(MessageSinkRef self, Security security, int throttleTrackerIndex){
		final int refClientKey = clientKeySeq.getAndIncrement();
		NewOrderRequest request = NewOrderRequest.of(refClientKey, 
				self, 
				security, 
				OrderType.LIMIT_ORDER,
				100, 
				Side.BUY, 
				TimeInForce.FILL_AND_KILL, 
				BooleanType.TRUE, 
				10000, 
				10000, 
				Long.MAX_VALUE, 
				true,
				throttleTrackerIndex,
				FAKE_PORT_SID);
		request.orderSid(refClientKey);
		return request;
	}

	private static NewOrderRequest createNewCompositeOrderRequest(MessageSinkRef self, Security security, int numThrottlesInquire){
		final int refClientKey = clientKeySeq.getAndIncrement();
		NewOrderRequest request = NewOrderRequest.of(refClientKey, 
				self, 
				security, 
				OrderType.LIMIT_ORDER,
				100, 
				Side.BUY, 
				TimeInForce.FILL_AND_KILL, 
				BooleanType.TRUE, 
				10000, 
				10000, 
				Long.MAX_VALUE, 
				true,
				ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX,
				FAKE_PORT_SID);
		request.isPartOfCompositOrder(true).numThrottleRequiredToProceed(numThrottlesInquire);
		request.orderSid(refClientKey);
		return request;
	}

	
	private static NewOrderRequest createNewOrderRequest(MessageSinkRef self, Security security, long timeoutAtNs){
		NewOrderRequest request = NewOrderRequest.of(clientKeySeq.getAndIncrement(), 
				self, 
				security, 
				OrderType.LIMIT_ORDER,
				100, 
				Side.BUY, 
				TimeInForce.FILL_AND_KILL, 
				BooleanType.TRUE, 
				10000, 
				10000, 
				timeoutAtNs, 
				true,
				ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX,
				FAKE_PORT_SID);
		request.orderSid(request.clientKey());
		return request;
	}

	/*
	@Test
	public void givenActiveAndNoMoreThrottleWhenReceiveEventWithRetryFalseThenSendThrottledBack(){
		// given - a real-time throttle tracker
		final Duration duration = Duration.ofSeconds(1);
		throttleTracker = new SlidingWindowThrottleTracker(numThrottles, 
				duration, 
				testHelper.realTimerService());

		ThrottleTracker[] throttleTrackers = new ThrottleTracker[1];
		throttleTrackers[0] = throttleTracker;
		
		orderExecutor = OrderExecutor.of("test-order-executor", 
				throttleTrackers,
				testHelper.realTimerService(),
				lineHandlerEngine,
				orderReqCompletionHandler,
				Optional.empty(),
				Optional.empty());
		
		when(exchangeService.isStopped()).thenReturn(false);
		assertEquals(LifecycleState.INIT, orderExecutor.state());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, orderExecutor.state());
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(
				OrderRequestEvent::new, 
				128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);

		// when
		final int refClientKey = 123456;
		NewOrderRequest request = NewOrderRequest.of(refClientKey, 
				self, 
				security, 
				OrderType.LIMIT_ORDER,
				100, 
				Side.BUY, 
				TimeInForce.FILL_AND_KILL, 
				BooleanType.TRUE, 
				10000, 
				10000, 
				Long.MAX_VALUE, 
				false,
				ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX,
				FAKE_PORT_SID);
		for (int i = 0; i < numThrottles; i++){
			helper.onData(request);
			helper.pushNextMessage();
		}
		verify(lineHandlerEngine, times(numThrottles)).sendOrderRequest(any(OrderRequest.class));
		
		helper.onData(request);
		helper.pushNextMessage();
		verify(lineHandlerEngine, times(numThrottles)).sendOrderRequest(any(OrderRequest.class));
		verify(orderReqCompletionHandler, times(1)).throttled(request);
	}
	
	@Test
	public void givenActiveWhenReceiveMultipleEventsThenSendTimeoutBack(){
		// given - a real-time throttle tracker
		final Duration duration = Duration.ofSeconds(1);
		throttleTracker = new SlidingWindowThrottleTracker(numThrottles, 
				duration, 
				testHelper.realTimerService());

		ThrottleTracker[] throttleTrackers = new ThrottleTracker[1];
		throttleTrackers[0] = throttleTracker;
		
		orderExecutor = OrderExecutor.of("test-order-executor", 
				throttleTrackers,
				testHelper.realTimerService(),
				lineHandlerEngine,
				orderReqCompletionHandler,
				Optional.empty(),
				Optional.empty());
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, orderExecutor.state());
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(
				OrderRequestEvent::new, 
				128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);

		// when
		NewOrderRequest request = createNewOrderRequest(self, security);
		final long halfSecondInNs = 500_000_000l;
		request.timeoutAtNanoOfDay(testHelper.realTimerService().toNanoOfDay() + halfSecondInNs);
		for (int i = 0; i < numThrottles; i++){
			helper.onData(request);
			helper.pushNextMessage();
		}
		
		// By this time, the next throttle will be available after one second, which passes the
		// timeout value of the request
		helper.onData(request);
		helper.onData(request);
		helper.onData(request);
		helper.onData(request);
		helper.onData(request);
		helper.pushNextMessage();
		verify(lineHandlerEngine, times(numThrottles)).sendOrderRequest(any(OrderRequest.class));
		verify(orderReqCompletionHandler, times(5)).timeoutAfterThrottled(request);
	}

	@Test
	public void givenActiveWhenSendRequestAndGetExceptionThenSendFailureBack(){
		LifecycleUtil.advanceTo(orderExecutor, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, orderExecutor.state());
		RingBuffer<OrderRequestEvent> ringBuffer = RingBuffer.createMultiProducer(
				OrderRequestEvent::new, 
				128);
		OrderRequestEventHandlerTestHelper helper = new OrderRequestEventHandlerTestHelper(orderExecutor, ringBuffer);
		
		final int refClientKey = 1;
		Mockito.doThrow(new RuntimeException("Invalid order request type")).when(lineHandlerEngine).sendOrderRequest(any(InvalidOrderRequest.class));
		helper.onData(InvalidOrderRequest.of(refClientKey));
		helper.pushNextMessage();
		verify(orderReqCompletionHandler, times(1)).fail(any(), any());
		verify(lineHandlerEngine, times(1)).sendOrderRequest(any(OrderRequest.class));
	}
	
	@Test
	public void givenActiveWhenSendAndExchangeIsDownThenSendFailureBack(){
		givenActiveWhenSendRequestAndGetExceptionThenSendFailureBack();
	}
	*/
}
