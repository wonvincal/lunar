package performance.baseline3;

import static performance.baseline3.Transitions.NO_TRANSITION;
import static performance.baseline3.Transitions.in_ACTIVE_receive_FAIL;
import static performance.baseline3.Transitions.in_ACTIVE_receive_THREAD_STOP;
import static performance.baseline3.Transitions.in_ACTIVE_receive_WAIT;
import static performance.baseline3.Transitions.in_ANY_STATE_receive_FAIL;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.Frame;

class ActiveState extends State{
	static final Logger LOG = LogManager.getLogger(ActiveState.class);

	@Override
	Transition enter(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_START || 
			event == StateTransitionEvent.ACTIVATE){
			return onEvent(service, service.serviceActiveEnter());
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(BaseStateService service, StateTransitionEvent event) {
		service.serviceActiveExit();
		return this;
	}

	@Override
	Transition onEvent(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		else if (event == StateTransitionEvent.FAIL){
			return in_ACTIVE_receive_FAIL;
		}
		else if (event == StateTransitionEvent.WAIT){
			return in_ACTIVE_receive_WAIT;
		}
		else if (event == StateTransitionEvent.THREAD_STOP){
			return in_ACTIVE_receive_THREAD_STOP;
		}
		return in_ANY_STATE_receive_FAIL;
	}

	@Override
	Transition onEvent(BaseStateService service, Frame event, long sequence, boolean endOfBatch, StateTransitionEventAccessor accessor) {
		service.serviceActiveOnEvent(event, sequence, endOfBatch);
		return onEvent(service, accessor.event());
	}
}
