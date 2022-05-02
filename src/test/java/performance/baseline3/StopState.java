package performance.baseline3;

import static performance.baseline3.Transitions.NO_TRANSITION;
import static performance.baseline3.Transitions.in_ANY_STATE_receive_FAIL;
import static performance.baseline3.Transitions.in_STOP_receive_THREAD_STOP;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.fsm.service.lunar.LunarService;

/**
 * Stop.  Derived class of {@link LunarService} should create another state
 * if it needs to do some asynchronous cleanup tasks before this state.
 * @author Calvin
 *
 */
final class StopState extends State {
	static final Logger LOG = LogManager.getLogger(StopState.class);

	@Override
	Transition enter(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.FAIL || event == StateTransitionEvent.TIMEOUT){
			return onEvent(service, service.serviceStopEnter());
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(BaseStateService service, StateTransitionEvent event) {
		service.serviceStopExit();
		return this;
	}

	@Override
	Transition onEvent(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		if (event == StateTransitionEvent.THREAD_STOP){
			return in_STOP_receive_THREAD_STOP;
		}
		return in_ANY_STATE_receive_FAIL;
	}
}
