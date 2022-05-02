package com.lunar.service;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.AdminServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.config.SystemConfig;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceStateHook;
import com.lunar.journal.JournalService;
import com.lunar.message.MessageFactory;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.order.OrderAndTradeSnapshotService;
import com.lunar.order.OrderManagementAndExecutionService;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class ServiceFactory {
	private static final Logger LOG = LogManager.getLogger(ServiceFactory.class);
	private final SystemClock systemClock;
	private final TimerService timerService;
	private final MessageSinkRefMgr messageSinkRefMgr;
	private final MessageFactory messageFactory;
	private static final Object2ObjectOpenHashMap<ServiceType, ServiceBuilder> BUILDERS;
	
	static {
		BUILDERS = new Object2ObjectOpenHashMap<>();
		BUILDERS.put(ServiceType.DashboardService, DashboardService::of);
		BUILDERS.put(ServiceType.DashboardStandAloneWebService, DashboardStandAloneWebService::of); 
		BUILDERS.put(ServiceType.MarketDataSnapshotService, MarketDataSnapshotService::of);
		BUILDERS.put(ServiceType.NotificationService, NotificationService::of);
		BUILDERS.put(ServiceType.OrderManagementAndExecutionService, OrderManagementAndExecutionService::of);
		BUILDERS.put(ServiceType.OrderAndTradeSnapshotService, OrderAndTradeSnapshotService::of);
		BUILDERS.put(ServiceType.PersistService, PersistService::of);
		BUILDERS.put(ServiceType.PingService, PingService::of);
		BUILDERS.put(ServiceType.PongService, PongService::of);
		BUILDERS.put(ServiceType.PortfolioAndRiskService, PortfolioAndRiskService::of);
		BUILDERS.put(ServiceType.RefDataService, ReferenceDataService::of);
		BUILDERS.put(ServiceType.WarmupService, WarmupService::of);
		BUILDERS.put(ServiceType.ScoreBoardService, ScoreBoardService::of);
	}
	
	public static ServiceFactory of(MessageSinkRefMgr messageSinkRefMgr, MessageFactory messageFactory, TimerService timerService, SystemClock systemClock){
		return new ServiceFactory(messageSinkRefMgr, messageFactory, timerService, systemClock);
	}
	
	ServiceFactory(MessageSinkRefMgr messageSinkRefMgr, MessageFactory messageFactory, TimerService timerService, SystemClock systemClock){
		this.messageSinkRefMgr = messageSinkRefMgr;
		this.timerService = timerService;
		this.messageFactory = messageFactory;
		this.systemClock = systemClock;
	}
	
	public LunarService createAdminService(SystemConfig systemConfig, AdminServiceConfig config, RingBuffer<MutableDirectBuffer> ringBuffer, CompletableFuture<Boolean> childrenStatusFuture, LunarServiceStateHook childStateHook){	
		LOG.info("Created admin service");
		
		// create a MessageSink		
		// register with MessageSinkRefMgr
		Messenger messenger = Messenger.create(messageSinkRefMgr, 
				timerService, 
				config.sinkId(), 
				config.serviceType(), 
				this.messageFactory,
				config.name(),
				ringBuffer);
		
		// create ServiceBuilder
		ServiceFactory self = this;
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return AdminService.of(systemConfig,
						config, 
						messageService, 
						self,
						childrenStatusFuture,
						childStateHook);
			}
		};
		
		// create a LunarService with a ServiceBuilder to an AdminService
		LunarService lunarService = LunarService.of(builder, 
													config, 
													messageFactory, 
													messenger,
													systemClock);
		return lunarService;
	}

	private static ServiceBuilder reflectionServiceBuilder = new ServiceBuilder() {
		@Override
		public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
			Method method;
			Object object = null;
			try {
				method = serviceConfig.serviceClass().get().getMethod("of", ServiceConfig.class, LunarService.class);
				object = method.invoke(null, serviceConfig, messageService);
			} 
			catch (Exception e) {
				LOG.error("Could not create service from reflectionServiceBuilder", e);
			}
			return (ServiceLifecycleAware)object;
		}
		
	};

	public static LunarService create(ServiceConfig config, 
			TimerService timerService, 
			SystemClock systemClock, 
			MessageSinkRefMgr referenceMgr,
			MessageFactory messageFactory, 
			RingBuffer<MutableDirectBuffer> queue, 
			ServiceBuilder builder,
			SenderBuilder senderBuilder) {
		Messenger messenger = Messenger.create(referenceMgr, 
				timerService, 
				config.sinkId(), 
				config.serviceType(), 
				messageFactory, 
				config.name(), 
				queue,
				senderBuilder);
		
		// create a LunarService with a ServiceBuilder to an AdminService
		LunarService messageService = LunarService.of(builder, 
													config, 
													messageFactory, 
													messenger,
                                                    systemClock);
		return messageService;
	}

	public static LunarService create(ServiceConfig config, 
			TimerService timerService, 
			SystemClock systemClock, 
			MessageSinkRefMgr referenceMgr, 
			MessageFactory messageFactory, 
			RingBuffer<MutableDirectBuffer> queue, 
			ServiceBuilder builder,
			Optional<Integer> affinityLock) {
		Messenger messenger = Messenger.create(referenceMgr,
				timerService, 
				config.sinkId(), 
				config.serviceType(), 
				messageFactory,
				config.name(),
				queue);
		
		LOG.info("Create service [name:{}, queueSize:{}]", config.name(), config.queueSize());
		// create a LunarService with a ServiceBuilder to an AdminService
		LunarService messageService = LunarService.of(builder, 
													config, 
													messageFactory, 
													messenger,
                                                    systemClock,
                                                    affinityLock);
		return messageService;
	}
	
	public static JournalService createJournalService(Path journalFolder, 
			Path journalArchiveFolder, 
			ServiceConfig config,
			TimerService timerService, 
			SystemClock systemClock){
		return JournalService.of(journalFolder, journalArchiveFolder, config, timerService, systemClock);
	}
	
	public static LunarService create(SystemConfig systemConfig, 
			ServiceConfig config, 
			TimerService timerService, 
			SystemClock systemClock, 
			MessageSinkRefMgr referenceMgr, 
			MessageFactory messageFactory, 
			RingBuffer<MutableDirectBuffer> queue,
			Optional<Integer> affinityLock) {
		ServiceBuilder builder = !config.serviceClass().isPresent() ? BUILDERS.get(config.serviceType()) : reflectionServiceBuilder;
		if (builder == null){
			throw new IllegalArgumentException("cannot define a service builder for " + config.name());
		}
		return create(config, timerService, systemClock, referenceMgr, messageFactory, queue, builder, affinityLock);
	}
	
	@SuppressWarnings("unchecked")
	public MessageServiceExecutionContext create(SystemConfig systemConfig, ServiceConfig config, ExecutorService commonExecutor, LunarServiceStateHook stateHook){
		Optional<ExecutorService> e = Optional.empty();
		ExecutorService executor = commonExecutor;
		WaitStrategy waitStrategy = null;
		if (config.requiredNumThread().isPresent()){
			int threadCount = (config.journal()) ? 2 : 1;
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
				this.messageFactory.eventFactory(),
				config.queueSize().orElseThrow(IllegalArgumentException::new), 
				executor, 
				ProducerType.MULTI, 
				waitStrategy);
		
		LunarService messageService = ServiceFactory.create(systemConfig, config, this.timerService, this.systemClock, this.messageSinkRefMgr, this.messageFactory, disruptor.getRingBuffer(), config.boundToCpu());
		messageService.stateHook(stateHook);
		
		Optional<JournalService> journalServiceOption;
		if (!config.journal()){
			disruptor.handleEventsWith(messageService);
			journalServiceOption = Optional.empty();
		}
		else{
			JournalService journalService = JournalService.of(Paths.get(systemConfig.journalFolder()), Paths.get(systemConfig.journalArchiveFolder()), config, timerService, systemClock);
			disruptor.handleEventsWith(messageService, journalService);
			journalServiceOption = Optional.of(journalService);
		}
		
		disruptor.handleExceptionsWith(new GeneralDisruptorExceptionHandler(config.name()));
		
		MessageServiceExecutionContext childService = MessageServiceExecutionContext.of(
				messageService, 
				journalServiceOption,
				disruptor, 
				e,
				config.stopTimeout(),
				timerService);
		return childService;
	}
	
	public static class GeneralDisruptorExceptionHandler implements ExceptionHandler<MutableDirectBuffer> {
		private static final Logger LOG = LogManager.getLogger(GeneralDisruptorExceptionHandler.class);
		private final String name;
		public GeneralDisruptorExceptionHandler(String name){
			this.name = name;
		}
		@Override
		public void handleEventException(Throwable ex, long sequence, MutableDirectBuffer event) {
			LOG.error("caught exception on processing event: " + name, ex);
		}
		@Override
		public void handleOnStartException(Throwable ex) {
			LOG.error("caught exception on start: " + name, ex);
		}
		@Override
		public void handleOnShutdownException(Throwable ex) {
			LOG.error("caught exception for: " + name, ex);
		}
	}
}
