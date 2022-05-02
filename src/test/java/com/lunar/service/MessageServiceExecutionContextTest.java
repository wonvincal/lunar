package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.ServiceConfig;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.AssertUtil;
import com.lunar.util.ConcurrentUtil;
import com.lunar.util.TestHelper;

import org.agrona.MutableDirectBuffer;

@RunWith(MockitoJUnitRunner.class)
public class MessageServiceExecutionContextTest {
	@Mock
	private ServiceStatusSender serviceStatusSender;

	@SuppressWarnings("unchecked")
	@Test
	public void testStartStopWithCommonExecutor() throws TimeoutException{
		ServiceConfig serviceConfig = new ServiceConfig(1,
				"test", 
				"testLunar", 
				"testing lunar base service", 
				ServiceType.AdminService,
				Optional.empty(),
				1, 
				Optional.of(1024),
				Optional.of(1024),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(1),
				false,
				"");

		TestSenderBuilder senderBuilder = new TestSenderBuilder();
		senderBuilder.serviceStatusSender(serviceStatusSender);
		Mockito.doNothing().when(serviceStatusSender).sendOwnServiceStatus(any(MessageSinkRef.class), any(ServiceStatusType.class), anyLong());
		
		MessageSinkRef self = DummyMessageSink.refOf(serviceConfig.systemId(), serviceConfig.sinkId(), serviceConfig.name(), serviceConfig.serviceType());
		
		TestHelper helper = TestHelper.of();

		helper = TestHelper.of();
		
		ExecutorService commonExecutor = Executors.newCachedThreadPool();
		
		Disruptor<MutableDirectBuffer> disruptor =  new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				128,
				commonExecutor);
		
		LunarService messageService = LunarService.of(DummyService.BUILDER, 
				serviceConfig, 
				helper.messageFactory(), 
				helper.createMessenger(self, senderBuilder),
				helper.systemClock());

		disruptor.handleExceptionsWith(new ServiceFactory.GeneralDisruptorExceptionHandler(serviceConfig.name()));
		disruptor.handleEventsWith(messageService);

		MessageServiceExecutionContext context = MessageServiceExecutionContext.of(
				messageService, 
				disruptor, 
				Optional.empty(),
				Duration.ofMillis(1000),
				helper.timerService());
		
		assertEquals(false, context.isStopped());
		context.start();
		ConcurrentUtil.sleep(10);
		context.shutdown();
		assertEquals(true, context.isStopped());
		assertFalse(commonExecutor.isShutdown());
		commonExecutor.shutdownNow();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStartStop() throws TimeoutException{
		ServiceConfig serviceConfig = new ServiceConfig(1,
				"test", 
				"testLunar", 
				"testing lunar base service", 
				ServiceType.AdminService, 
				Optional.empty(),
				1, 
				Optional.of(1024),
				Optional.of(1024),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(1),
				false,
				"");

		MessageSinkRef self = DummyMessageSink.refOf(serviceConfig.systemId(), serviceConfig.sinkId(), serviceConfig.name(), serviceConfig.serviceType());

		TestSenderBuilder senderBuilder = new TestSenderBuilder();
		senderBuilder.serviceStatusSender(serviceStatusSender);
		Mockito.doNothing().when(serviceStatusSender).sendOwnServiceStatus(any(MessageSinkRef.class), any(ServiceStatusType.class), anyLong());
		
		TestHelper helper = TestHelper.of();

		helper = TestHelper.of();
		
		ExecutorService executor = Executors.newCachedThreadPool();
		
		Disruptor<MutableDirectBuffer> disruptor =  new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				128,
				executor);
		
		LunarService messageService = LunarService.of(DummyService.BUILDER, 
				serviceConfig, 
				helper.messageFactory(), 
				helper.createMessenger(self, senderBuilder),
				helper.systemClock());

		disruptor.handleExceptionsWith(new ServiceFactory.GeneralDisruptorExceptionHandler(serviceConfig.name()));
		disruptor.handleEventsWith(messageService);

		MessageServiceExecutionContext context = MessageServiceExecutionContext.of(
				messageService, 
				disruptor, 
				Optional.of(executor),
				Duration.ofMillis(1000),
				helper.timerService());
		assertEquals(false, context.isStopped());
		context.start();
		AssertUtil.assertTrueWithinPeriod("Service cannot get past IDLE state", () -> {
			return messageService.state() != States.IDLE;
		}, TimeUnit.SECONDS.toNanos(2l));
		context.shutdown();
		assertEquals(true, context.isStopped());
		assertTrue(executor.isShutdown());
	}
}
