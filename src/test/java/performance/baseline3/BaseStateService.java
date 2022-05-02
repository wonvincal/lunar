package performance.baseline3;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lunar.core.CommandTracker;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.exception.StateTransitionException;
import com.lunar.message.MessageBuffer;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;

import performance.baseline3.SbePingPongSequencedLatencyTest.ServiceLifecycleAwareInstanceBuilder;

public class BaseStateService implements EventHandler<Frame>, LifecycleAware {
	enum Mode {
		START,
		RECOVERY
	}
	static final Logger LOG = LogManager.getLogger(BaseStateService.class);

//	private final MessagingContext messagingContext;
	private final MessageCodec messageCodec;
	protected final MessageBuffer messageBuffer;
	private final Frame frameBuffer;
	
	private State state;
	private StateTransitionEvent stateEvent;
	private StateTransitionEventAccessor stateEventAccessor;
	private final ServiceLifecycleAware service;
	private Mode mode;
	private final int ownSinkId;
	
	final ServiceStatusTracker serviceTracker;
	private final RequestTracker requestTracker;
	private final CommandTracker commandTracker;

//	MessagingContext messagingContext(){
//		return this.messagingContext;
//	}

	MessageCodec messageCodec(){
		return this.messageCodec;
	}

	Frame frameBuffer(){
		return this.frameBuffer;
	}

	MessageBuffer messageBuffer(){
		return this.messageBuffer;
	}

	public BaseStateService(int sinkId, ServiceLifecycleAwareInstanceBuilder builder){
//		this.messagingContext = messagingContext;
		this.ownSinkId = sinkId;
		this.messageCodec = null; // this.messagingContext.createMessageCodec();
		this.messageBuffer = null; // this.messagingContext.createMessageBuffer(helper.bufferSize());
		this.frameBuffer = this.messageBuffer.frameBuffer();
		this.service = builder.build(this.messageCodec, this.messageBuffer.frameBuffer()); 

		this.state = States.IDLE;
		this.stateEvent = StateTransitionEvent.NULL;
		this.stateEventAccessor = new StateTransitionEventAccessor(this);
		this.mode = Mode.START;
		
		this.serviceTracker = null;
		this.requestTracker = null;
		this.commandTracker = null;
	}
	
	State state(){
		return state;
	}
	State state(State newState){
		this.state = newState;
		return this.state;
	}
	protected StateTransitionEvent stateEvent(){
		return stateEvent;
	}
	
	protected StateTransitionEvent stateEvent(StateTransitionEvent event){
		return (this.stateEvent = event);
	}
	
	@Override
	public void onStart() {
		LOG.info("onStart - sink {}", ownSinkId);
		send(StateTransitionEvent.THREAD_START);
	}

	@Override
	public void onShutdown() {
		send(StateTransitionEvent.THREAD_STOP);
	}

	@Override
	public void onEvent(Frame event, long sequence, boolean endOfBatch) throws Exception {
//		LOG.info("onEvent - sink {} received event", ownSinkId);
		stateEvent = StateTransitionEvent.NULL; // reset state event result on each event
   		try {
   			this.state.onEvent(this, event, sequence, endOfBatch, stateEventAccessor).proceed(this);
   		}
   		catch (StateTransitionException e){
   			this.state(States.STOP).enter(this, StateTransitionEvent.FAIL); // since state transition threw exception, we enter STOP state directly
   		}
   		catch (Exception e){
   		}
	}

	// -- IDLE State --
	final void serviceIdleStart(){
		registerHandlers();
		if (mode == Mode.START){
			service.idleStart(this, serviceTracker, requestTracker, commandTracker);
		}
		else {
			service.idleRecover(serviceTracker, requestTracker, commandTracker);		
		}		
	}
	
	final void serviceIdleExit(){
		service.idleExit();
	}
	
	// -- WAITING_FOR_SERVICES --
	final StateTransitionEvent serviceWaitingForServicesEnter(){
		return service.waitingForServicesEnter(serviceTracker, requestTracker, commandTracker);
	}
	final void serviceWaitingForServicesExit(){
		service.waitingForServicesExit();
	}
	
	void serviceWaitingForServicesOnEvent(Frame event, long sequence, boolean endOfBatch) {
//		messageCodec.decoder().receive(event);
	}
	
	// -- ACTIVE State --
	final StateTransitionEvent serviceActiveEnter(){
		return service.activeEnter(serviceTracker, requestTracker, commandTracker);
	}
	final void serviceActiveExit(){
		service.activeExit(serviceTracker, requestTracker, commandTracker);
	}

	void serviceActiveOnEvent(Frame event, long sequence, boolean endOfBatch) {
//		messageCodec.decoder().receive(event);
	}

	public final StateTransitionEvent serviceStopEnter(){
		StateTransitionEvent event = service.stopEnter(serviceTracker, requestTracker, commandTracker);
		if (event == StateTransitionEvent.NULL){
			// no nothing
		}
		return event;
	}
	
	public final void serviceStopExit(){
		service.stopExit();
	}

	// -- STOPPED State --
	public final void serviceStoppedEnter(){
		service.stoppedEnter(serviceTracker, requestTracker, commandTracker);
	}

	public final void serviceStoppedExit(){
		service.stoppedExit(serviceTracker, requestTracker, commandTracker);
	}

	private void registerHandlers(){
		
	}

   	final void send(StateTransitionEvent event) {
   		try {
   			this.state.onEvent(this, event).proceed(this);
   		}
   		catch (Exception e){
   			this.state(States.STOP).enter(this, StateTransitionEvent.FAIL);
   		}
	}
}
