package com.lunar.service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.agrona.MutableDirectBuffer;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.ServiceConfig;
import com.lunar.config.SystemConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceStateHook;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.TestHelper;

public class TestServiceFactory extends ServiceFactory {
	private final TestHelper helper;
	
	public static TestServiceFactory of(TestHelper helper){
		return new TestServiceFactory(helper);
	}
	
	TestServiceFactory(TestHelper helper) {
		super(helper.messageSinkRefMgr(), 
				helper.messageFactory(), 
				helper.timerService(), 
				helper.systemClock());
		this.helper = helper;
	}

	@SuppressWarnings("unchecked")
	@Override
	public MessageServiceExecutionContext create(SystemConfig systemConfig, ServiceConfig config, ExecutorService commonExecutor, LunarServiceStateHook stateHook){
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return DummyService.of(config, 
									   messageService);
			}
		}; 
		
		Optional<ExecutorService> e = Optional.empty();
		ExecutorService executor = commonExecutor;
		WaitStrategy waitStrategy = null;
		if (config.requiredNumThread().isPresent()){
			int threadCount = config.requiredNumThread().get();
			if (threadCount == 1){
				executor = Executors.newSingleThreadExecutor(new NamedThreadFactory(config.name(), config.name() + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
				e = Optional.of(executor);
			}
			else{
				executor = Executors.newFixedThreadPool(threadCount, new NamedThreadFactory(config.name(), config.name() + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
				e = Optional.of(executor);
			}
			waitStrategy = new BusySpinWaitStrategy();
		}
		else {
			waitStrategy = new BlockingWaitStrategy();
		}
		
		Disruptor<MutableDirectBuffer> disruptor =  new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				config.queueSize().orElseThrow(IllegalArgumentException::new),
				executor,
				ProducerType.MULTI, 
				waitStrategy);

		// create a LunarService with a ServiceBuilder to an AdminService
		MessageSinkRef self = DummyMessageSink.refOf(config.systemId(),
				config.sinkId(), 
				config.name(), 
				config.serviceType());
		
		LunarService messageService = LunarService.of(builder, 
													config, 
													helper.messageFactory(), 
													helper.createMessenger(self),
													helper.systemClock());
		messageService.stateHook(stateHook);
		
		disruptor.handleEventsWith(messageService);
		disruptor.handleExceptionsWith(new GeneralDisruptorExceptionHandler(config.name()));

		MessageServiceExecutionContext childService = MessageServiceExecutionContext.of(
				messageService, 
				disruptor, 
				e,
				config.stopTimeout(),
				helper.timerService());
		
		return childService;
	}
}
