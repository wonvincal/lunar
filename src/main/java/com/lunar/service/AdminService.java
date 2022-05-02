package com.lunar.service;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.AdminServiceConfig;
import com.lunar.config.CommunicationConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.config.SystemConfig;
import com.lunar.core.LifecycleState;
import com.lunar.core.ServiceStatusTracker.AggregatedServiceStatusChangeHandler;
import com.lunar.core.ServiceStatusTracker.ServiceStatusChangeHandler;
import com.lunar.core.SubscriberList;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.entity.Pair;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceStateHook;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Parameter;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.CommandDecoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class AdminService implements ServiceLifecycleAware {
	private static final int AERON_LIFECYCLE_TRANSITION_TIMEOUT_IN_SEC = 5;
	private static final int ADMIN_SINK_ID = 1;
	private static final Logger LOG = LogManager.getLogger(AdminService.class);
	private final SystemConfig systemConfig;
	private final AdminServiceConfig adminServiceConfig;
	private final LunarService messageService;
	private final MessageSinkRefMgr refMgr;
	private final Messenger messenger;
	private final String name;
	private final ServiceFactory serviceFactory;
	private ConcurrentHashMap<Integer, MessageServiceExecutionContext> children;
	private final ExecutorService commonExecutor;
	private final ExecutorService serviceActionExecutor;
	private final RemoteContext remoteContext;
	private final CompletableFuture<Boolean> childrenStatusFuture;
	private final LunarServiceStateHook childrenStateHook;
    private Duration commonPeriodicTaskFreq;
    private final AtomicReference<TimeoutHandlerTimerTask> commonPeriodicTask;
    private final static int CLIENT_KEY_FOR_COMMON_PERIODIC_TASK = 1;
    public final static int CLIENT_KEY_FOR_START_DASHBOARD = 2;
    public final static int CLIENT_KEY_FOR_STOP_DASHBOARD = 3;
    private final SubscriberList allServiceStatusSubs;
    private long[] sinkSendResults = new long[ServiceConstant.MAX_SUBSCRIBERS];

	// Aeron stuff
	public static class RemoteContext {
		private MediaDriver.Context mediaContext;
		private MediaDriver mediaDriver;
		private final CommunicationConfig localConfig;
		private final List<CommunicationConfig> remoteConfigs;
		private Aeron aeron;
		private Subscription subscription;
		private Int2ObjectOpenHashMap<Publication> publications;
		private boolean started;
		private boolean enableAeron;
		private Duration aeronStartTimeout;
		private final String name;
		private AeronMessageDistributionService distService;
		
		RemoteContext(String name, CommunicationConfig localConfig, List<CommunicationConfig> remoteConfigs, boolean enableAeron, Duration aeronStartTimeout){
			this.name = name;
			this.localConfig = localConfig;
			this.remoteConfigs = remoteConfigs;
			this.started = false;
			this.enableAeron = enableAeron;
			this.aeronStartTimeout = aeronStartTimeout;
			
			if (enableAeron){
				if (localConfig == null){
					throw new IllegalArgumentException("Local config must not be null");
				}
			}
		}
		@SuppressWarnings("resource")
		public void start(Messenger messenger, MessageSinkRefMgr refMgr) throws TimeoutException, InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
			if (!enableAeron){
				LOG.info("Aeron has not been enabled [serviceName:{}]", name);
				return;
			}
			
			if (!started){
				mediaContext = new MediaDriver.Context()
						.aeronDirectoryName(localConfig.aeronDir())
						.dirsDeleteOnStart(localConfig.deleteAeronDirOnStart())
						.threadingMode(ThreadingMode.DEDICATED)
						.conductorIdleStrategy(new BackoffIdleStrategy(1, 1, 1, 1))
						.receiverIdleStrategy(new NoOpIdleStrategy())
						.senderIdleStrategy(new NoOpIdleStrategy());
				mediaDriver = MediaDriver.launch(mediaContext);
				
				final Aeron.Context ctx = new Aeron.Context();
				ctx.aeronDirectoryName(mediaContext.aeronDirectoryName());
				aeron = Aeron.connect(ctx);
				
				subscription = aeron.addSubscription(localConfig.channel(), localConfig.streamId());
				publications = new Int2ObjectOpenHashMap<>(this.remoteConfigs.size());
				for (int i = 0; i < remoteConfigs.size(); i++){
					CommunicationConfig aeronConfig = remoteConfigs.get(i);
					Publication publication = this.aeron.addPublication(aeronConfig.channel(), aeronConfig.streamId());
					publications.put(aeronConfig.systemId(), publication);
					
					// Create Admin sink for remote system
					refMgr.createAndRegisterAeronMessageSink(aeronConfig.systemId(),
							ADMIN_SINK_ID, 
							ServiceType.AdminService, 
							"remote-admin-" + aeronConfig.systemId(), 
							publication);
				}
				distService = AeronMessageDistributionService.of(name + "-aeron-dist-service", aeron, remoteConfigs, messenger.createChildMessenger());
				distService.active().get(AERON_LIFECYCLE_TRANSITION_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
				
				long startNs = messenger.timerService().nanoTime();
				long timeoutNs = aeronStartTimeout.toNanos();
				final long parkNs = TimeUnit.MILLISECONDS.toNanos(100l);
				while (LifecycleState.ACTIVE != distService.state()){
					if (messenger.timerService().nanoTime() - startNs > timeoutNs){
						throw new TimeoutException("Cannot start aeron with timeout [aeronStartTimeout: " + timeoutNs + " ns]");
					}
					LOG.info("Waiting for aeron message distribution service to start up... [nanoTime:{}]", messenger.timerService().nanoTime());
					LockSupport.parkNanos(parkNs);
				}
				started = true;
				LOG.info("Started aeron context [serviceName:{}]", name);
			}
			else {
				LOG.warn("Aeron context has already been started [name:{}]", name);
			}
		}
		
		public void stop(MessageSinkRefMgr refMgr) throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
			if (started){
				LOG.info("Stopping aeron");
				// Deactivate all aeron message sinks, so that messages will be delivered to 
				// a NullSink (or a sink that writes to log...)
				for (Entry<Publication> entry : publications.int2ObjectEntrySet()){
					refMgr.deactivateBySystem(entry.getIntKey());
				}

				// Stop distribution service
				distService.reset().get(AERON_LIFECYCLE_TRANSITION_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
				distService.stop().get(AERON_LIFECYCLE_TRANSITION_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
				
				// Stop aeron subscription
				subscription.close();

				// Deactivate message sinks, then close corresponding publication
				for (Entry<Publication> entry : publications.int2ObjectEntrySet()){
					entry.getValue().close();
				}

				aeron.close();
				mediaDriver.close();
				started = false;
				LOG.info("Stopped aeron");
			}
			else{
				LOG.warn("Aeron has been stopped or hasn't been started at all [name:{}]", name);				
			}
		}
		
		public Subscription subscription(){
			return subscription;
		}

		public Int2ObjectOpenHashMap<Publication> publications(){
			return publications;
		}
		
		public boolean running(){
			return started && (LifecycleState.ACTIVE == distService.state());
		}
		
		public boolean enableAeron(){
			return enableAeron;
		}
		
		public AeronMessageDistributionService distributionService(){
			return distService;
		}
	}
	
	public static AdminService of(SystemConfig systemConfig, AdminServiceConfig adminServiceConfig, LunarService messageService, ServiceFactory serviceFactory){
		return new AdminService(systemConfig, adminServiceConfig, messageService, serviceFactory, new CompletableFuture<Boolean>(), LunarServiceStateHook.NULL_HOOK);
	}

	public static AdminService of(SystemConfig systemConfig, AdminServiceConfig adminServiceConfig, LunarService messageService, ServiceFactory serviceFactory, CompletableFuture<Boolean> childrenStatusFuture, LunarServiceStateHook childrenStateHook){
		return new AdminService(systemConfig, adminServiceConfig, messageService, serviceFactory, childrenStatusFuture, childrenStateHook);
	}
	
	AdminService(SystemConfig systemConfig, AdminServiceConfig adminServiceConfig, LunarService messageService, ServiceFactory serviceFactory, CompletableFuture<Boolean> childrenStatusFuture, LunarServiceStateHook childrenStateHook){
		this.systemConfig = systemConfig;
		this.adminServiceConfig = adminServiceConfig;
		this.name = adminServiceConfig.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.refMgr = this.messenger.referenceManager();
		this.serviceFactory = serviceFactory;
		this.children = new ConcurrentHashMap<>(adminServiceConfig.childServiceConfigs().size() /* a remote as well */);
		this.commonExecutor = Executors.newCachedThreadPool(new NamedThreadFactory(this.name, this.name + "-common"));
		this.serviceActionExecutor = Executors.newFixedThreadPool(2, new NamedThreadFactory(this.name, this.name + "-service-action"));
		this.remoteContext = new RemoteContext(this.name, 
				adminServiceConfig.localAeronConfig(), 
				adminServiceConfig.remoteAeronConfigs(), 
				adminServiceConfig.enableAeron(),
				adminServiceConfig.aeronStartTimeout());
		this.childrenStatusFuture = childrenStatusFuture;
		this.childrenStateHook = childrenStateHook;
		this.commonPeriodicTask = new AtomicReference<>();
		this.commonPeriodicTaskFreq = Duration.ofSeconds(10l);
		this.allServiceStatusSubs = SubscriberList.of();
	}

	RemoteContext remoteContext(){
		return remoteContext;
	}
	
	ConcurrentMap<Integer, MessageServiceExecutionContext> children(){
		return children;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		try {
			startAeron();
			createChildren();
			messenger.serviceStatusTracker().trackAnyServiceStatusChange(anyServiceStatusHandler);
			if (children.size() > 0){
				messenger.serviceStatusTracker().trackAggregatedServiceStatus(allStatusChangeHandler);
			}
		}
		catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException | TimeoutException e) {
			throw new IllegalStateException(e);
		}
		return StateTransitionEvent.NULL;
	}

	private final AggregatedServiceStatusChangeHandler allStatusChangeHandler = new AggregatedServiceStatusChangeHandler() {
		@Override
		public void handle(boolean status) {
			LOG.info("All children services are up");
			if (status){
				messenger.trySendEvent(EventCategory.CORE, EventLevel.INFO, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "All services are up");
				childrenStatusFuture.complete(true);
			}
			else{
				messenger.trySendEvent(EventCategory.CORE, EventLevel.CRITICAL, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Not all services are up");
			}
		}
	};
	
	final ServiceStatusChangeHandler anyServiceStatusHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus receivedMessage) {
			LOG.trace("Receive service status change {}", receivedMessage);
			final ServiceStatus message = ServiceStatus.cloneForDifferentSender(messageService.messenger().self().sinkId(), receivedMessage); // modify sender id before sending out
			
			// Send non-heartbeat updates to all local sinks except this admin and the sender
			if (message.statusType() != ServiceStatusType.HEARTBEAT){
				messenger.sendServiceStatusToLocalSinksExcept(message,
						new int[]{
								messageService.messenger().self().sinkId(),
								message.sinkId()
								});
			}
			
			// If this message is from a remote system, we need to make sure that
			// 1) a message sink has been created if none is available
			// 2) the old message sink will be replaced by the new one 
			if (message.systemId() != refMgr.systemId()){
				MessageSinkRef messageSinkRef = messenger.sinkRef(message.senderSinkId());
				if (message.statusType() == ServiceStatusType.UP){
					if (messageSinkRef.systemId() != message.systemId()){
						refMgr.createAndRegisterAeronMessageSink(message.systemId(), 
								message.sinkId(), 
								message.serviceType(), 
								message.serviceType().name(), 
								remoteContext.publications.get(message.systemId()));
					}
				}
				else if (message.statusType() == ServiceStatusType.HEARTBEAT){
					// noop
				}
				else {
					refMgr.deactivate(messageSinkRef);
				}
				// do nothing else if this is from a remote sink
				return;
			}

			// send status to remote admin service
			messenger.sendServiceStatusToRemoteAdmin(message);

			if (message.sinkId() == messenger.self().sinkId()){ // do nothing if this is my own admin status 
				return;
			}
			
			// Send all current latest statuses to the message sender if the sender is not going DOWN
			// Also, no need to send update back to sender if sender is only sending HEARTBEAT over
			if (message.statusType() != ServiceStatusType.DOWN && message.statusType() != ServiceStatusType.HEARTBEAT){ 
				for (ServiceStatus status : messenger.serviceStatusTracker().statuses()){
					if (status.sinkId() != message.sinkId()){
						// modify sender id before sending out
						messenger.serviceStatusSender().sendServiceStatus(messenger.sinkRef(message.sinkId()), status);
					}
				}
			}
		}
	};
	
	/**
	 * Blocking call, exit only when aeron has been started successfully 
	 * @throws TimeoutException 
	 * @throws java.util.concurrent.TimeoutException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	private void startAeron() throws TimeoutException, InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		LOG.info("Starting aeron media driver");
		remoteContext.start(messenger, refMgr);
	}
	
	private void stopAeron() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		LOG.info("Stopping aeron");
		remoteContext.stop(refMgr);
		LOG.info("Stopped aeron");
	}
	
	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		return StateTransitionEvent.ACTIVATE;
	}

	@Override
	public StateTransitionEvent readyEnter() {
		LOG.warn("{} should not be in ready state", this.name);
		return StateTransitionEvent.ACTIVATE;
	}

	@Override
	public StateTransitionEvent activeEnter() {
		startChildren();
		messenger.receiver().commandHandlerList().add(commandHandler);
		messenger.receiver().requestHandlerList().add(requestHandler);
		messenger.receiver().timerEventHandlerList().add(timerEventHandler);
		this.commonPeriodicTask.set(messenger.timerService().createTimerTask(commonPeriodicTaskTimer, "admin-common-periodic-task"));
		messenger.timerService().newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq.toMillis(), TimeUnit.MILLISECONDS);
		return StateTransitionEvent.NULL;		
	}

	@Override
	public void activeExit() {
		messenger.receiver().commandHandlerList().remove(commandHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
		messenger.receiver().timerEventHandlerList().remove(timerEventHandler);
	}

	private final Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder command) {
			byte senderSinkId = header.senderSinkId();
			final ImmutableListMultimap<ParameterType, Parameter> parameters;
			try {
				parameters = CommandDecoder.generateParameterMap(messenger.stringBuffer(), command);
			} 
			catch (UnsupportedEncodingException e) {
				messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.FAILED, command.commandType());
				return;
			}
			switch (command.commandType()){
			case STOP: {
				ImmutableList<Parameter> sinkIds = parameters.get(ParameterType.SINK_ID);
				if (sinkIds.size() == 0){
					LOG.error("Missing SINK_ID parameter");
					messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.INVALID_PARAMETER, command.commandType());
					return;
				}
				messenger.sendCommandAck(senderSinkId, 
						command.clientKey(),
						CommandAckType.OK, 
						command.commandType());
				for (Parameter sinkId : sinkIds){
					handleStopCommand(sinkId.valueLong().intValue());				
				}
				break;
			}
			case START: {
				ImmutableList<Parameter> sinkIds = parameters.get(ParameterType.SINK_ID);
				if (sinkIds.size() == 0){
					LOG.error("Missing SINK_ID parameter");
					messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.INVALID_PARAMETER, command.commandType());
					return;
				}
				messenger.sendCommandAck(senderSinkId, 
						command.clientKey(),
						CommandAckType.OK, 
						command.commandType());
				for (Parameter sinkId : sinkIds){
					handleStartCommand(sinkId.valueLong().intValue());				
				}
				break;
			}
			default:
				break;
			}
		}
	};

	private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
    		byte senderSinkId = header.senderSinkId();
            LOG.info("Received request [senderSinkId:{}, clientKey:{}, requestType:{}]", senderSinkId, request.clientKey(), request.requestType().name());
	    	try {
				ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
				switch (request.requestType()){
				case SUBSCRIBE:
					handleSubscriptionRequest(senderSinkId, request, parameters);
					break;
				case UNSUBSCRIBE:
					handleUnsubscriptionRequest(senderSinkId, request, parameters);
					break;
				default:
					LOG.error("Unsupported operation [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
					messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
					break;
				}			
			} 
	    	catch (UnsupportedEncodingException e) {
	    		LOG.error("Caught exception while processing request [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
	    		messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			}
		}
	};
	
    private void handleSubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
    	Optional<DataType> parameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
		if (!parameter.isPresent()){
			LOG.error("Invalid parameter. Expect only one data type");
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		
		MessageSinkRef sink = refMgr.get(senderSinkId);
		DataType dataType = parameter.get();
		switch (dataType){
		case ALL_SERVICE_STATUS:
			LOG.debug("Added subscriber for ALL_SERVICE_STATUS [subscriber:{}]", sink);
			allServiceStatusSubs.add(sink);
			break;
		default:
			LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
		
		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		return;
	}
    
    private void handleUnsubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
    	try {
			// Get the snapshots and send them back
			Optional<DataType> parameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
			if (!parameter.isPresent()){
				LOG.error("Invalid parameter. Expect only one data type");
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
				return;
			}

			// Get the snapshots and send them back
    		MessageSinkRef sink = refMgr.get(senderSinkId);
    		DataType dataType = parameter.get();
    		switch (dataType){
    		case ALL_SERVICE_STATUS:
    			allServiceStatusSubs.remove(sink);
    			break;
    		default:
    			LOG.error("Unsupported subscription type [clientKey:{}, subscriptionType:{}]", request.clientKey(), dataType.name());
    			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
    			return;
    		}
    		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
    		return;    		
    	}
		catch (IllegalArgumentException e){
			LOG.error("Caught IllegalArgumentException while handling unsubscription request", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		catch (Exception e){
			LOG.error("Caught unexpected exception while handling unsubscription request [clientKey:" + request.clientKey() + "]", e);
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}
    
	private void handleStopCommand(int sinkId){
		stopChildAsync(sinkId);
	}
	
	private void handleStartCommand(int sinkId){
		startExistingChildAsync(sinkId);
	}
	
	void startDashboard(){
		startExistingChildAsync(refMgr.dashboard().sinkId());
	}
	
	void stopDashboard(){
		stopChildAsync(refMgr.dashboard().sinkId());
	}
	
	@Override
	public StateTransitionEvent stopEnter() {
		stopChildrenAsync();
		return StateTransitionEvent.NULL;
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		stopChildrenAsync();
		return StateTransitionEvent.NULL;
	}

	void createChildren(){
		LOG.info("Creating {} children", adminServiceConfig.childServiceConfigs().size());
		for (ServiceConfig serviceConfig : adminServiceConfig.childServiceConfigs()){
			this.children.put(serviceConfig.sinkId(), serviceFactory.create(systemConfig, serviceConfig, commonExecutor, childrenStateHook));
			messenger.serviceStatusTracker().trackSinkId(serviceConfig.sinkId(), nullHandler);
		}
	}
	
	private final ServiceStatusChangeHandler nullHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus status) {
			if (status.statusType() != ServiceStatusType.HEARTBEAT){
				LOG.info("Admin detected change in service status [{}]", status.sinkId(), status.serviceType().name(), status.statusType().name());
			}
		}
	};
	
	void startChildren(){
		LOG.info("Starting {} children", this.children.size());
		this.children.values().forEach(s -> { s.start();});
	}
	
	boolean startExistingChildAsync(int sinkId){
		MessageServiceExecutionContext context = children.get(sinkId);
		if (!context.isStopped()){
			LOG.warn("Cannot stop sink, it is still active. [sinkId:{}, name:{}]", sinkId, context.messageService().name());
			return false;
		}
		this.serviceActionExecutor.execute(() -> {
			MessageServiceExecutionContext newContext = null;
			for (ServiceConfig serviceConfig : adminServiceConfig.childServiceConfigs()){
				if (serviceConfig.sinkId() == sinkId){
					newContext = serviceFactory.create(systemConfig, serviceConfig, commonExecutor, childrenStateHook);
					if (this.children.put(serviceConfig.sinkId(), newContext) != null){
						LOG.info("Start an existing child [sinkId:{}]", sinkId);
					}
				}
			}
			if (newContext != null){
				newContext.start();
			}
			else {
				LOG.error("Cannot start sink.  SinkId not exist [sinkId:{}]", sinkId);
			}
		});
		return true;
	}
	
	boolean stopChildAsync(int sinkId){
		this.serviceActionExecutor.execute(() -> {
			// can starting of a child be asynchronous
			MessageServiceExecutionContext context = children.get(sinkId);
			try {
				context.shutdown();
			} 
			catch (TimeoutException e) {
				LOG.warn("Caught timeout exception when shutting down sink {}", sinkId);
			}			
		});
		return true;
	}
	
	/**
	 * Shutdown each service
	 */
	boolean stopChildrenAsync(){
		if (stopInProgress.compareAndSet(false, true)){
			LOG.info("Stopping children");
			this.serviceActionExecutor.execute(this::stopChildren);
			return true;
		}
		else {
			LOG.info("Stopping children in already progress");
			return false;
		}
	}
	
	private AtomicBoolean stopInProgress = new AtomicBoolean(false);
	void stopChildren(){
		try {
			stopAeron();
		} 
		catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e1) {
			LOG.error("Could not stop aeron", e1);
		}
		
		LOG.info("Stopping {} children", children.size());
		ObjectArrayList<Pair<MessageServiceExecutionContext, Future<Boolean>>> futures = new ObjectArrayList<>(children.size());
		for (java.util.Map.Entry<Integer, MessageServiceExecutionContext> child : children.entrySet()){
			//TODO this is messed up
			//Future<Boolean> future = this.commonExecutor.submit(child.getValue()::shutdown);
			Future<Boolean> future = this.serviceActionExecutor.submit(() -> { return child.getValue().shutdown();});
			futures.add(Pair.of(child.getValue(), future));
		}
		// Poll for status
		boolean allChildrenStopped = true;
		for (Pair<MessageServiceExecutionContext, Future<Boolean>> item: futures){
			try {
				LOG.debug("waiting for {}", item.first.messageService().name());
				item.second.get();
			} 
			catch (InterruptedException e){
				LOG.error("Caught InterruptedException when stopping a child service", e);
				allChildrenStopped = false;
			}
			catch (ExecutionException e) {
				for (Throwable suppressed : e.getSuppressed()){
					LOG.error("Caught exception when stopping a child service", suppressed);
				}
				allChildrenStopped = false;
			}
		}
		LOG.debug("Check if all children have stopped");

		// At this point, if a service is not yet stopped, it is because:
		// 1) disruptor is still busy after certain timeout
		// 2) bug
		// Either way, we don't know what we should do next.
		// Need to move on.
		// TODO: Need to spend more time to decide the correct stop procedure
		if (!allChildrenStopped){
			LOG.error("{} couldn't stop all child services", this.name);
			for (java.util.Map.Entry<Integer, MessageServiceExecutionContext> child : children.entrySet()){
				if (!child.getValue().isStopped()){
					LOG.error("{} is still alive", child.getValue().messageService().name());
					List<Runnable> runnables = child.getValue().shutdownNow();
					for (Runnable runnable : runnables){
						LOG.error("{} is still active", runnable);
					}
				}
			}
		}
		else{
			LOG.info("{} - all children have been stopped", this.name);
		}
		LOG.info("{} - stop common executor", this.name);
		this.commonExecutor.shutdown();
		try {
			if (!this.commonExecutor.awaitTermination(2, TimeUnit.SECONDS)){
				LOG.debug("Force shutdown common executor");
				this.commonExecutor.shutdownNow();
			}
		} 
		catch (InterruptedException e) {
			LOG.error("Caught InterruptedException", e);
		}
	}
	
	@Override
	public boolean isStopped() {
		for (java.util.Map.Entry<Integer, MessageServiceExecutionContext> child : children.entrySet()){
			if (!child.getValue().isStopped()){
				return false;
			}
		}
		return true;
	}
	
    private void commonPeriodicTask(){
    	LOG.trace("Common periodic task");
    	for (ServiceStatus status : this.messenger.serviceStatusTracker().statuses()){
    		long result = this.messenger.serviceStatusSender().sendServiceStatus(this.allServiceStatusSubs, status, sinkSendResults);
    		if (result != MessageSink.OK){
				LOG.error("Could not deliver status to all subscribers");
    			for (int i = 0; i < this.allServiceStatusSubs.size(); i++){
    				if (sinkSendResults[i] != MessageSink.OK){
    					LOG.error("Could not deliver status [sinkId:{}, name:{}]",
    							this.allServiceStatusSubs.elements()[i].sinkId(),
    							this.allServiceStatusSubs.elements()[i].name());
    				}
    			}
    		}
    	}
    }
    
    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
	    	if (codec.timerEventType() == TimerEventType.TIMER){
	    		if (codec.clientKey() == CLIENT_KEY_FOR_COMMON_PERIODIC_TASK){
	    			commonPeriodicTask();
	    			messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
	    		}
	    		else if (codec.clientKey() == CLIENT_KEY_FOR_START_DASHBOARD){
	    			handleStartCommand(refMgr.dashboard().sinkId());
	    		}
	    		else if (codec.clientKey() == CLIENT_KEY_FOR_STOP_DASHBOARD){
	    			handleStopCommand(refMgr.dashboard().sinkId());
	    		}
	    	}
		}
	};

    private final TimeoutHandler commonPeriodicTaskTimer = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_COMMON_PERIODIC_TASK, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
			}
		}
	};
}
