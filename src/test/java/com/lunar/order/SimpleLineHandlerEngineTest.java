package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.RingBuffer;
import com.lunar.config.LineHandlerConfig;
import com.lunar.core.LifecycleState;
import com.lunar.core.LifecycleUtil;
import com.lunar.core.TimerService;
import com.lunar.core.UserControlledTimerService;
import com.lunar.core.WaitStrategy;
import com.lunar.exception.ConfigurationException;
import com.lunar.exception.StateTransitionException;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;

@RunWith(MockitoJUnitRunner.class)
public class SimpleLineHandlerEngineTest {
	static final Logger LOG = LogManager.getLogger(MockitoJUnitRunner.class);
	
	@Mock
	private OrderUpdateEventProducer producer;
	
	@Mock
	private Consumer<OrderRequest> noopOrderRequestConsumer;
	
	private SimpleLineHandlerEngine engine;
	private TimerEventSender sender;
	private TimerService timerService;
	
	@Before
	public void setup() throws ConfigurationException{
		sender = TimerEventSender.of(MessageSender.of(1024, MessageSinkRef.createNaSinkRef()));
		timerService = new UserControlledTimerService(
				System.nanoTime(),
				LocalDateTime.now(),
				sender);
		LineHandlerConfig lineHandlerConfig = LineHandlerConfig.of(1, 
				"line handler", "line handler", com.lunar.order.NullLineHandlerEngine.class,
				4, 
				Optional.empty(),
				Duration.ofSeconds(1), 
				128,
				true,
				WaitStrategy.BLOCKING_WAIT,
				1024,
				128,
				true,
				WaitStrategy.BLOCKING_WAIT,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
		engine = SimpleLineHandlerEngine.of(lineHandlerConfig, timerService, null);
	}
	
	@Test
	public void testCreate() throws ConfigurationException{
		TimerService timerService = new UserControlledTimerService(
				System.nanoTime(),
				LocalDateTime.now(),
				sender);		

		LineHandlerConfig lineHandlerConfig = LineHandlerConfig.of(1, 
				"test-engine", "test-engine", com.lunar.order.NullLineHandlerEngine.class,
				4, 
				Optional.empty(),
				Duration.ofSeconds(1), 
				128,
				true,
				WaitStrategy.BLOCKING_WAIT,
				1024,
				128,
				true,
				WaitStrategy.BLOCKING_WAIT,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		SimpleLineHandlerEngine e = SimpleLineHandlerEngine.of(lineHandlerConfig, timerService, null);
		RingBuffer<OrderUpdateEvent> ringBuffer = RingBuffer.createMultiProducer(OrderUpdateEvent::new, 128);
		OrderUpdateEventProducer producer = OrderUpdateEventProducer.of(ringBuffer);
		e.init(producer);
		assertEquals(0, e.name().compareTo(lineHandlerConfig.name()));
	}
	
	@Test
	public void givenCreatedWhenActiveThenNotSuccessful(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		
		AtomicInteger calls = new AtomicInteger(0);
		// When
		engine.active().whenComplete((state, cause) -> {
			if (cause instanceof StateTransitionException){
				calls.incrementAndGet();
			}
		});
		
		assertEquals(1, calls.get());
	}
	
	@Test
	public void givenCreatedAndInitWhenActiveThenSuccessful(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		
		// When
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.ACTIVE);
	}

	@Test
	public void givenCreatedAndInitWhenWarmupThenSuccessful(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		
		// When
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.WARMUP);
	}

	@Test
	public void givenCreatedAndInitWhenRecoveryThenSuccessful(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		
		// When
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.RECOVERY);
	}
		
	@Test
	public void givenActiveWhenIdleThenSuccessful(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.ACTIVE);
		assertEquals(LifecycleState.ACTIVE, engine.state());
		
		// When
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.RESET);
	}
	
	@Test
	public void givenActiveWhenSendNewOrderRequestThenSend(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.ACTIVE);
		
		// When
		engine.sendOrderRequest(newOrderRequest());
		
		// Then
		assertEquals(1, engine.orders().size());
		verify(producer, times(1)).onOrderAccepted(any(DirectBuffer.class), anyInt());		
	}

	@Test
	public void givenNonActiveWhenSendNewOrderRequestThenSend(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.RECOVERY);
        engine.noopOrderRequestConsumer(noopOrderRequestConsumer);
		
		// When
		engine.sendOrderRequest(newOrderRequest());
		
		// Then
		assertEquals(0, engine.orders().size());
		verify(producer, times(0)).onOrderAccepted(any(DirectBuffer.class), anyInt());
		verify(noopOrderRequestConsumer, times(1)).accept(any(OrderRequest.class));
	}

	private static NewOrderRequest newOrderRequest(){
		int clientKey = 10312;
		int ordSid = 676182;
		MessageSinkRef owner = DummyMessageSink.refOf(1, 1, "test", ServiceType.DashboardService);
		long secSid = 12345;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int quantity = 100;
		Side side = Side.BUY;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100;
		int stopPrice = 100;
		int portSid = 1;
		boolean retry = false;
		long timeoutAtNanoDay = LocalTime.now().toNanoOfDay();
		NewOrderRequest request = NewOrderRequest.ofWithDefaults(clientKey, 
				owner, 
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutAtNanoDay,
				retry,
				portSid);
		request.orderSid(ordSid);
		return request;
	}
	
	private static CancelOrderRequest cancelOrderRequest(NewOrderRequest orderRequest){
		int clientKey = orderRequest.clientKey() + 1;
		CancelOrderRequest request = CancelOrderRequest.of(clientKey, 
				orderRequest.owner(), 
				orderRequest.orderSid(), 
				orderRequest.secSid(), 
				orderRequest.side());
		request.orderSid(orderRequest.orderSid()+1);
		return request;
	}
	
	@Test
	public void givenActiveAndRecvNewOrderWhenSendCancelOrderRequestThenSend(){
		// Given
		assertEquals(LifecycleState.INIT, engine.state());
		engine.init(producer);
		LifecycleUtil.advanceTo(engine, LifecycleState.ACTIVE);
		NewOrderRequest newOrderRequest = newOrderRequest();
		engine.sendOrderRequest(newOrderRequest);
		
		// When
		engine.sendOrderRequest(cancelOrderRequest(newOrderRequest));
		
		// Then
		assertEquals(0, engine.orders().size());
		verify(producer, times(1)).onOrderAccepted(any(DirectBuffer.class), anyInt());
		verify(producer, times(1)).onOrderCancelled(any(DirectBuffer.class), anyInt());
	}

}
