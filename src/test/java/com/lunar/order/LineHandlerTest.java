package com.lunar.order;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.config.LineHandlerConfig;
import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.core.LifecycleState;
import com.lunar.core.SystemClock;
import com.lunar.core.WaitStrategy;
import com.lunar.exception.ConfigurationException;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.LifecycleController;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class LineHandlerTest {
	private static final Logger LOG = LogManager.getLogger(LineHandlerTest.class);
	
	@Mock
	private OrderExecutor orderExecutor;
	
	@Mock
	private LineHandlerEngine engine;
	
	@Mock
	private OrderUpdateProcessor updateProcessor;
	
	@Mock
	private OrderManagementAndExecutionServiceConfig omesConfig;
	
	@Mock
	private MessageSink omesSink;

	private OrderManagementContext managementContext;
	private MessageSinkRef omes;
	private int lineHandlerId = 5;
	private String name = "test-line";

	private int numChannels = 1;
	private int numOutstandingOrdersPerSecurity = 128;
	private int numSecurities = 40;
	private int numOutstandingRequests = 128;
	private Messenger messenger;
	private SystemClock systemClock;
	private int throttle = 8;
	private Duration throttleDuration = Duration.ofSeconds(1);
	private int ordExecQueueSize = 128;
	private boolean singleProducerToOrdExec = true;
	private WaitStrategy ordExecWaitStrategy = WaitStrategy.BLOCKING_WAIT;
	private int ordUpdRecvQueueSize = 1024;
	private boolean singleProducerToOrdUpdRecv = false;
	private WaitStrategy ordUpdRecvWaitStrategy = WaitStrategy.YIELDING_WAIT;
	private int numOutstandingOrders = 1024;
	private LineHandlerConfig lineHandlerConfig;

	@Mock
	private LifecycleController trueController;
	
	@Before
	public void setup() throws ConfigurationException{
		lineHandlerConfig = LineHandlerConfig.of(lineHandlerId, 
				name, 
				name, 
				NullLineHandlerEngine.class, 
				throttle, 
				Optional.empty(),
				throttleDuration, 
				ordExecQueueSize, 
				singleProducerToOrdExec, 
				ordExecWaitStrategy, 
				numOutstandingOrders,
				ordUpdRecvQueueSize, 
				singleProducerToOrdUpdRecv, 
				ordUpdRecvWaitStrategy,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
		
		omes = MessageSinkRef.of(omesSink);
		managementContext = OrderManagementContext.of(numOutstandingRequests, 
				numSecurities, 
				numOutstandingOrdersPerSecurity, 
				numChannels);
		TestHelper helper = TestHelper.of();
		messenger = helper.createMessenger(DummyMessageSink.refOf(1, lineHandlerId, name, ServiceType.LineHandler));
		systemClock = helper.systemClock();
		
		allowStateTransitionFor(orderExecutor);
		allowStateTransitionFor(engine);
		allowStateTransitionFor(updateProcessor);
	}
	
	private static void allowStateTransitionFor(LineHandlerEngine mockEngine){
		when(mockEngine.active()).thenReturn(CompletableFuture.completedFuture(LifecycleState.ACTIVE));
		when(mockEngine.reset()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RESET));
		when(mockEngine.recover()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RECOVERY));
		when(mockEngine.stop()).thenReturn(CompletableFuture.completedFuture(LifecycleState.STOPPED));
		when(mockEngine.warmup()).thenReturn(CompletableFuture.completedFuture(LifecycleState.WARMUP));		
	}
	
	private static void allowStateTransitionFor(OrderExecutor mockEngine){
		when(mockEngine.active()).thenReturn(CompletableFuture.completedFuture(LifecycleState.ACTIVE));
		when(mockEngine.reset()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RESET));
		when(mockEngine.recover()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RECOVERY));
		when(mockEngine.stop()).thenReturn(CompletableFuture.completedFuture(LifecycleState.STOPPED));
		when(mockEngine.warmup()).thenReturn(CompletableFuture.completedFuture(LifecycleState.WARMUP));		
	}

	private static void allowStateTransitionFor(OrderUpdateProcessor mockEngine){
		when(mockEngine.active()).thenReturn(CompletableFuture.completedFuture(LifecycleState.ACTIVE));
		when(mockEngine.reset()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RESET));
		when(mockEngine.recover()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RECOVERY));
		when(mockEngine.stop()).thenReturn(CompletableFuture.completedFuture(LifecycleState.STOPPED));
		when(mockEngine.warmup()).thenReturn(CompletableFuture.completedFuture(LifecycleState.WARMUP));		
	}

	private static LineHandler createWithBuilder(LineHandlerConfig lineHandlerConfig, 
			MessageSinkRef omes, 
			OrderManagementContext managementContext, 
			Messenger messenger,
			SystemClock systemClock) throws Exception{
		LineHandler lineHandler = LineHandlerBuilder.of()
				.lineHandlerConfig(lineHandlerConfig)
				.orderCompletionSinkId(omes.sinkId())
				.orderManagementContext(managementContext)
				.messenger(messenger)
				.systemClock(systemClock)
			.build();
		assertNotNull(lineHandler);
		assertNotNull(lineHandler.engine());
		assertNotNull(lineHandler.orderExecutor());
		assertNotNull(lineHandler.updateProcessor());
		return lineHandler;
	}
	
	private static LineHandler createWithoutBuilder(LineHandlerConfig lineHandlerConfig, 
			MessageSinkRef omes, 
			OrderManagementContext managementContext, 
			Messenger messenger,
			OrderExecutor orderExecutor,
			OrderUpdateProcessor updateProcessor,
			LineHandlerEngine engine){
		LineHandler lineHandler = LineHandler.of(lineHandlerConfig, messenger, orderExecutor, updateProcessor, engine);
		assertNotNull(lineHandler);
		assertNotNull(lineHandler.engine());
		assertNotNull(lineHandler.orderExecutor());
		assertNotNull(lineHandler.updateProcessor());
		return lineHandler;
	}
	
	@Test
	public void testCreateWithBuilder() throws Exception{
		LineHandler lineHandler = createWithBuilder(lineHandlerConfig, omes, managementContext, messenger, systemClock);
		assertNotNull(lineHandler);
		assertNotNull(lineHandler.engine());
		assertNotNull(lineHandler.orderExecutor());
		assertNotNull(lineHandler.updateProcessor());
	}
	
	@Test
	public void testCreate() throws ConfigurationException{
		int throttle = 8;
		Duration throttleDuration = Duration.ofSeconds(1);
		int ordExecQueueSize = 128;
		boolean singleProducerToOrdExec = true;
		WaitStrategy ordExecWaitStrategy = WaitStrategy.BLOCKING_WAIT;
		int numOutstandingOrders = 1024;
		int ordUpdRecvQueueSize = 1024;
		boolean singleProducerToOrdUpdRecv = false;
		WaitStrategy ordUpdRecvWaitStrategy = WaitStrategy.YIELDING_WAIT;
		LineHandlerConfig lineHandlerConfig = LineHandlerConfig.of(lineHandlerId, 
				name, 
				name, 
				NullLineHandlerEngine.class, 
				throttle, 
				Optional.empty(),
				throttleDuration, 
				ordExecQueueSize, 
				singleProducerToOrdExec, 
				ordExecWaitStrategy, 
				numOutstandingOrders,
				ordUpdRecvQueueSize, 
				singleProducerToOrdUpdRecv, 
				ordUpdRecvWaitStrategy,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		assertNotNull(lineHandler);
		assertNotNull(lineHandler.engine());
		assertNotNull(lineHandler.orderExecutor());
		assertNotNull(lineHandler.updateProcessor());
		verify(engine, times(1)).lifecycleExceptionHandler(any());
	}
	
	@Test
	public void givenCreatedWhenInitThenOK() throws Exception{
		LineHandler lineHandler = createWithBuilder(lineHandlerConfig, omes, managementContext, messenger, systemClock);
		assertFalse(lineHandler.isInit());
		lineHandler.init();
		assertNotNull(lineHandler.disruptorForOrderUpdateProcessor());
		assertTrue(lineHandler.isInit());
	}
	
	/**
	 * Testing the interface only
	 * @throws InterruptedException
	 */
	@Test
	public void givenInitedWhenWarmupThenOK() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> warmup = lineHandler.warmup();
		warmup.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("warmup completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(warmup.isDone());
		assertFalse(warmup.isCompletedExceptionally());
		verify(updateProcessor, times(1)).warmup();
		verify(updateProcessor, times(1)).lifecycleExceptionHandler(any());
		verify(updateProcessor, times(1)).onStart();
		verifyNoMoreInteractions(updateProcessor);
		verify(orderExecutor, times(1)).warmup();
		verify(orderExecutor, times(1)).lifecycleExceptionHandler(any());
		verify(engine, times(1)).warmup();
		verify(engine, times(1)).init(any());
		verify(engine, times(1)).lifecycleExceptionHandler(any());
		verifyNoMoreInteractions(engine);
	}
	
	/**
	 * Testing the interface only
	 * @throws InterruptedException
	 */
	@Test
	public void givenInitedWhenActiveThenOK() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> active = lineHandler.active();
		active.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("active completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(active.isDone());
		assertFalse(active.isCompletedExceptionally());
	}
	
	@Test
	public void givenInitedWhenRecoverThenOK() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> recover = lineHandler.recover();
		recover.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("recover completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(recover.isDone());
		assertFalse(recover.isCompletedExceptionally());
		
		verify(updateProcessor, times(1)).recover();
		verify(updateProcessor, times(1)).lifecycleExceptionHandler(any());
		verify(updateProcessor, times(1)).onStart();
		verifyNoMoreInteractions(updateProcessor);
		verify(orderExecutor, times(1)).recover();
		verify(orderExecutor, times(1)).lifecycleExceptionHandler(any());
		verify(engine, times(1)).recover();
		verify(engine, times(1)).init(any());
		verify(engine, times(1)).lifecycleExceptionHandler(any());
		verifyNoMoreInteractions(engine);
	}
	
	@Test
	public void givenWarmupWhenIdleAndActiveThenOK() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> warmup = lineHandler.warmup();
		warmup.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("warmup completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(warmup.isDone());
		assertFalse(warmup.isCompletedExceptionally());

		// When
		latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> reset = lineHandler.reset();
		reset.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("idle completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(reset.isDone());
		assertFalse(reset.isCompletedExceptionally());
		
		latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> active = lineHandler.active();
		active.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("active completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(active.isDone());
		assertFalse(active.isCompletedExceptionally());
	}
	
	@Test
	public void givenActiveWhenIdleThenOK() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> active = lineHandler.active();
		active.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("active completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(active.isDone());
		assertFalse(active.isCompletedExceptionally());

		// When
		latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> idle = lineHandler.reset();
		idle.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("idle completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(idle.isDone());
		assertFalse(idle.isCompletedExceptionally());
		
	}
	
	@Test
	public void givenActiveWhenStopThenFail() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> active = lineHandler.active();
		active.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("active completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(active.isDone());
		assertFalse(active.isCompletedExceptionally());

		// When
		latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> stop = lineHandler.stop();
		stop.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("stop completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(stop.isDone());
		assertTrue(stop.isCompletedExceptionally());
		
	}
	
	@Test
	public void givenActiveWhenIdleAndStopThenOK() throws InterruptedException{
		// Given
		LineHandler lineHandler = createWithoutBuilder(lineHandlerConfig, 
				omes,
				managementContext,
				messenger, 
				orderExecutor, 
				updateProcessor, 
				engine);
		lineHandler.init();
		
		CountDownLatch latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> active = lineHandler.active();
		active.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("active completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(active.isDone());
		assertFalse(active.isCompletedExceptionally());

		// When
		latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> idle = lineHandler.reset();
		idle.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("idle completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(idle.isDone());
		assertFalse(idle.isCompletedExceptionally());
		
		// When
		latch = new CountDownLatch(1);
		CompletableFuture<LifecycleState> stop = lineHandler.stop();
		stop.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				return;
			}
			LOG.debug("stop completed");
		});
		
		latch.await(100, TimeUnit.MILLISECONDS);
		assertTrue(stop.isDone());
		assertFalse(stop.isCompletedExceptionally());
		
		verify(updateProcessor, times(1)).active();
		verify(updateProcessor, times(1)).reset();
		verify(updateProcessor, times(1)).stop();
		verify(updateProcessor, times(1)).lifecycleExceptionHandler(any());
		verify(updateProcessor, times(1)).onStart();
		verify(updateProcessor, times(1)).onShutdown();
		verifyNoMoreInteractions(updateProcessor);
		verify(orderExecutor, times(1)).active();
		verify(orderExecutor, times(1)).reset();
		verify(orderExecutor, times(1)).stop();
		verify(orderExecutor, times(1)).lifecycleExceptionHandler(any());
		verify(engine, times(1)).active();
		verify(engine, times(1)).reset();
		verify(engine, times(1)).stop();
		verify(engine, times(1)).init(any());
		verify(engine, times(1)).lifecycleExceptionHandler(any());
		verifyNoMoreInteractions(engine);
	}
	
	@Test
	public void givenIdleWhenWarmupThenStartDisruptor(){
		
	}
}

