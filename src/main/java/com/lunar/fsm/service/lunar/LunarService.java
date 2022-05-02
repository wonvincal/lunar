package com.lunar.fsm.service.lunar;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lunar.config.ServiceConfig;
import com.lunar.core.SystemClock;
import com.lunar.exception.StateTransitionException;
import com.lunar.message.MessageFactory;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.PingDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PingSbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.service.ServiceBuilder;
import com.lunar.service.ServiceLifecycleAware;
import com.lunar.util.LogUtil;
import net.openhft.affinity.Affinity;
/**
 * Life cycle events
 * =================
 * 1. Actor.preStart(): let the constructor run 
 * 2. Actor.preRestart(): if service is still healthy, don't touch.  Else, kill the disruptor
 *    thread.
 * 3. Actor.postStop(): health check, reconciliation
 * 4. Disruptor.onStart(): waitForService
 * 5. Disruptor.onStop(): idleAndCleanUp
 * 6. Disruptor.onException(): idleAndCleanUp
 * 7. Disruptor.onEvent(): return any transition
 * 8. Exception.statemachine(): kill the disruptor thread
 * 9. Exception.general(): log it, call handler
 * @author Calvin
 *
 */
public class LunarService implements EventHandler<MutableDirectBuffer>, LifecycleAware {
	enum Mode {
		START,
		RECOVERY
	}
	
	static final Logger LOG = LogManager.getLogger(LunarService.class);

	protected final String name;
	protected final ServiceType serviceType;
	protected final Messenger messenger;
	protected final SystemClock systemClock;
	private Mode mode;
	private boolean warmup;
	private boolean warmupCompleted;
	private LunarServiceStateHook stateHook = LunarServiceStateHook.NULL_HOOK;
	
	// services
	private Handler<PingSbeDecoder> pingCompletionHandler;
	private EventExceptionHandler eventExceptionHandler;

	private ServiceLifecycleAware service;
	
	private State state;
	private StateTransitionEvent stateEvent;
	private final ServiceBuilder serviceBuilder;
	private final ServiceConfig serviceConfig;
	private final Optional<Integer> affinityLock;
	private AtomicBoolean threadStarted = new AtomicBoolean(false); 
			
	public static LunarService of(ServiceBuilder serviceBuilder, 
			ServiceConfig config,
			MessageFactory messageFactory, 
			Messenger messenger,
			SystemClock systemClock){
		return new LunarService(serviceBuilder, config, messageFactory, messenger, systemClock, Optional.empty());
	}
	
	public static LunarService of(ServiceBuilder serviceBuilder, 
			ServiceConfig config,
			MessageFactory messageFactory, 
			Messenger messenger,
			SystemClock systemClock,
			Optional<Integer> affinityLock){
		return new LunarService(serviceBuilder, config, messageFactory, messenger, systemClock, affinityLock);
	}

	public LunarService(ServiceBuilder serviceBuilder, 
			ServiceConfig config, 
			MessageFactory messageFactory, 
			Messenger messenger, 
			SystemClock systemClock,
			Optional<Integer> affinityLock){
		this.serviceBuilder = serviceBuilder;
		this.serviceConfig = config;
		this.name = config.name();
		this.serviceType = config.serviceType();
		this.warmup = config.warmup();
		this.warmupCompleted = false;
		this.messenger = messenger;
		this.systemClock = systemClock;
		this.pingCompletionHandler = PingDecoder.NULL_HANDLER;
		this.eventExceptionHandler = EventExceptionHandler.NULL_HANDLER;
		this.state = States.IDLE;
		this.stateEvent = StateTransitionEvent.NULL;
		this.mode = Mode.START;
		this.affinityLock = affinityLock;
		LogUtil.logService(LOG, this, "being constructed");
	}

	ServiceLifecycleAware service(){
		return service;
	}
	
	public Messenger messenger(){
		return this.messenger;
	}
	
	public SystemClock systemClock() {
		return this.systemClock;
	}
	
	protected StateTransitionEvent stateEvent(){
		return stateEvent;
	}
	
	public StateTransitionEvent stateEvent(StateTransitionEvent event){
		return (this.stateEvent = event);
	}

	public State state(){
		return state;
	}
	
	public String name(){
		return name;
	}
	
	State state(State state){
		return (this.state = state);
	}
	
	// state transitions must be done on the same disruptor thread
	public boolean mode(Mode mode){
		if (this.state != States.IDLE){
			LOG.warn("change mode can only be done in {}", States.IDLE);
			return false;
		}
		this.mode = mode;
		return true;
	}

	@Override
	public final void onStart() {
	    // Call once on disruptor thread start before first event is available.
		LogUtil.logService(LOG, this, "started");
		if (!this.threadStarted.compareAndSet(false, true)){
			throw new IllegalStateException(this.name + " thread is already running");
		}
		if (affinityLock.isPresent()){
			Affinity.setAffinity(affinityLock.get());
			LogUtil.logService(LOG, this, "started with affinity [tid:" + Affinity.getThreadId() + ", cpuId:" + Affinity.getCpu() + "]");
		}
		else {
			LogUtil.logService(LOG, this, "started [tid:" + Affinity.getThreadId() + ", cpuId:" + Affinity.getCpu() + "]");
		}
		if (!this.warmup){
			send(StateTransitionEvent.THREAD_START_NO_WARMUP);
		}
		else {
			send(StateTransitionEvent.THREAD_START_WARMUP);
		}
	}
	
	@Override
	public final void onShutdown() {
		// Call once on disruptor thread before the processor thread is shutdown
		LogUtil.logService(LOG, this, "stopped processing event");
		if (!this.threadStarted.compareAndSet(true, false)){
			throw new IllegalStateException(this.name + " thread has already stopped");
		}
		send(StateTransitionEvent.THREAD_STOP);
	}
	
	public boolean hasThreadStarted(){
		return this.threadStarted.get();
	}

	LunarService threadStarted(boolean value){
		this.threadStarted.set(true);
		return this;
	}
	
	public void pingCompletionHandler(Handler<PingSbeDecoder> handler){
		this.pingCompletionHandler = handler;
	}
	
	/**
	 * Handle ping - this is part of the base class because each service needs to implement this in order for
	 * us to gather performance data within the system
	 * @param frame
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param codec
	 */
	private void handlePing(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder codec){
		if (this.messenger.self().sinkId() == header.dstSinkId()){
			if (codec.isResponse() == BooleanType.TRUE){
				// receive ping back - measure the time now
				pingCompletionHandler.handle(buffer, offset, header, codec);
			}
			else {
				// send back - encode and append directly to input frame
				// doesn't feel safe to encode directly onto the receiving buffer!
//				this.messenger.pingSender().appendAndSendTimestampAndSend(
//						this.refMgr.get(senderSinkId),
//						buffer, 
//						offset,
//						BooleanType.TRUE,
//						codec.timestamps().count(),
//						ownSinkId,
//						messenger.timerService().nanoTime());
			}
		}
		else{
			// forwarding - keeping senderSinkId, ds)tSinkId and isResponse unchanged.
			// doesn't feel safe to encode directly onto the receiving buffer!
//			this.messenger.pingSender().appendAndSendTimestampAndSend(
//					senderSinkId,
//					this.refMgr.get(dstSinkId), 
//					buffer,
//					offset,
//					codec.isResponse(),
//					codec.timestamps().count(),
//					ownSinkId,
//					messenger.timerService().nanoTime());
		}
	}
	
	@Override
	public void onEvent(MutableDirectBuffer buffer, long sequence, boolean endOfBatch) {
		stateEvent = StateTransitionEvent.NULL; // reset state event result on each event
   		try {
   			messenger.receiver().receive(buffer, 0);
   			state.onEvent(this, stateEvent).proceed(this);
   		}
   		catch (StateTransitionException e){
   			LOG.error("Caught state transition exception when processing event " + messenger.receiver().dump(buffer, 0) + ", terminate service ", e);
   			messenger.trySendEvent(EventCategory.CORE, EventLevel.CRITICAL, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Stopping service due to state transition exception");
   			state(States.STOP).enter(this, StateTransitionEvent.FAIL); // since state transition threw exception, we enter STOP state directly
   		}
   		catch (Exception e){
   			LOG.error("Caught and ignore exception when processing event " + messenger.receiver().dump(buffer, 0), e);
   			eventExceptionHandler.onException(buffer, sequence, endOfBatch, e);
   		}
	}
	
	protected final void killServiceThread(){
//		self.tell(KillServiceThread.of(ownSinkId).seq(messagingContext.getAndIncMsgSeq()));
	}
	
	final void buildService(){
		service = serviceBuilder.build(serviceConfig, this);
	}
	
	// -- IDLE State --
	// at this state, the service's disruptor hasn't started
	final void serviceIdleStart(){
		buildService();
		if (service == null){
			throw new IllegalStateException("Service could not be built [name:" + this.name + "]");
		}
		if (mode == Mode.START){
			service.idleStart();
		}
		else {
			service.idleRecover();		
		}		
	}
	
	final void serviceIdleExit(){
		service.idleExit();
	}
	
	final StateTransitionEvent serviceWaitingForWarmupServicesEnter(){
		LogUtil.logService(LOG, this);
		messenger.registerEvents();
		return service.waitingForWarmupServicesEnter();
	}
	
	final void serviceWaitingForWarmupServicesExit(){
		service.waitingForWarmupServicesExit();
	}
	
	// -- WARMUP --
	final StateTransitionEvent serviceWarmupEnter(){
		LogUtil.logService(LOG, this);
		messenger.notifyAdminOnServiceWarmup();
		messenger.useWarmupRefMgr();
		return service.warmupEnter();
	}
	final void serviceWarmupExit(){
		messenger.useNormalRefMgr();
		System.gc();
		warmupCompleted = true;
		service.warmupExit();
	}
	
	// -- RESET --
	final StateTransitionEvent serviceResetEnter(){
		LogUtil.logService(LOG, this);
		return service.resetEnter();
	}
	final void serviceResetExit(){
		service.resetExit();
		stateHook.onResetExit(this.serviceConfig.sinkId(), this.name);
	}
	
	// -- WAITING_FOR_SERVICES --
	// Service is considered initializing in this state
	final StateTransitionEvent serviceWaitingForServicesEnter(){
		LogUtil.logService(LOG, this);
		messenger.registerEvents();
		messenger.notifyAdminOnServiceInit();
		return service.waitingForServicesEnter();
	}
	final void serviceWaitingForServicesExit(){
		service.waitingForServicesExit();
	}
	
	// -- READY State --
	// Service is considered initializing in this state
	final StateTransitionEvent serviceReadyEnter(){
		LogUtil.logService(LOG, this);
		messenger.registerEvents();
		messenger.notifyAdminOnServiceInit();
		return service.readyEnter();
	}
	final void serviceReadyExit(){
		service.readyExit();
	}
	
	// -- RECOVERY State --
	final StateTransitionEvent serviceRecoveryEnter(){
        LogUtil.logService(LOG, this);
        return service.recoveryEnter();	    
	}
	final void serviceRecoveryExit(){
	    service.recoveryExit();
	}
	
	// -- ACTIVE State --
	// Service is considered up in this state
	final StateTransitionEvent serviceActiveEnter(){
		LogUtil.logService(LOG, this);
		stateHook.onActiveEnter(serviceConfig.sinkId(), name);
		messenger.notifyAdminOnServiceUp();
		return service.activeEnter();
	}
	final void serviceActiveExit(){
		service.activeExit();
	}

	// -- STOP State --
	// Service is considered down in this state
	/**
	 * Calls {@link stopEnter}.  If it returns {@link StateTransitionEvent.NULL}, kill the service thread.
	 * Else, it will be the service's responsibility to kill the service thread later.
	 * @return
	 */
	public final StateTransitionEvent serviceStopEnter(){
		messenger.notifyAdminOnServiceDown();
		messenger.unregisterEvents();
		unregisterHandlers();
		StateTransitionEvent event = StateTransitionEvent.FAIL;
		if (service != null){
			event = service.stopEnter();
			if (event == StateTransitionEvent.NULL){
				killServiceThread();
			}
		}
		return event;
	}
	public final void serviceStopExit(){
		if (service != null){
			service.stopExit();
		}
	}

	// -- STOPPED State --
	// Service is considered down in this state
	public final void serviceStoppedEnter(){
		LogUtil.logService(LOG, this);
		messenger.notifyAdminOnServiceDown();
		messenger.unregisterEvents();
		if (service != null){
			service.stoppedEnter();
		}
	}
	public final void serviceStoppedExit(){
		if (service != null){
			service.stoppedExit();
		}
	}
	
	@SuppressWarnings("unused")
	private void registerHandlers(){
		messenger.receiver().pingHandlerList().add(this::handlePing);
	}
	
	private void unregisterHandlers(){
		messenger.receiver().pingHandlerList().remove(this::handlePing);
	}
	
	/**
	 * If any exception is caught during state transition, we terminate the service
	 * by going to STOP state directly
	 * 
	 * Note: This is a finite state machine entry point
	 * 
	 * @param event
	 * @return
	 */
   	final void send(StateTransitionEvent event) {
   		try {
   			this.state.onEvent(this, event).proceed(this);
   		}
   		catch (Exception e){
   			LOG.error("got state transition exception when processing state transition event " + event.name() + ", terminate service ", e);
   			this.state(States.STOP).enter(this, StateTransitionEvent.FAIL);
   		}
	}

   	public boolean isStopped(){
   		return state == States.STOPPED && (service == null || service.isStopped()); 
   	}
   	
   	public boolean warmupCompleted(){
   	    return warmupCompleted;
   	}
   	
   	public LunarService stateHook(LunarServiceStateHook stateHook){
   	    this.stateHook = stateHook;
   	    return this;
   	}
}
