 package performance.baseline3;

import static performance.baseline3.Transitions.NO_TRANSITION;
import static performance.baseline3.Transitions.in_ANY_STATE_receive_FAIL;
import static performance.baseline3.Transitions.in_IDLE_receive_THREAD_START;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.Frame;

/**
 * Idle State is when the disruptor thread is not alive.
 * @author Calvin
 *
 */
final class IdleState extends State {
	static final Logger LOG = LogManager.getLogger(IdleState.class);
	
	@Override
	final Transition enter(BaseStateService service, StateTransitionEvent event) {
		// the thread entering this method may not be the disruptor thread,
		// so nothing should be done when entering IdleState 
		return NO_TRANSITION;
	}
	
	@Override
	State exit(BaseStateService service, StateTransitionEvent event) {
		service.serviceIdleExit();
		return this;
	}
	
	@Override
	Transition onEvent(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_START){
			service.serviceIdleStart();
			return in_IDLE_receive_THREAD_START;
		}
		return in_ANY_STATE_receive_FAIL;
	}
	
	@Override
	Transition onEvent(BaseStateService service, Frame event, long sequence, boolean endOfBatch, StateTransitionEventAccessor accessor) {
		LOG.debug("received unexpected event, skipped | event:{}, sequence:{}", event, sequence);
		return NO_TRANSITION;
	}
	
	@Override
	public String toString() {
		return "IdleState";
	}
}
