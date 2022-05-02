package performance.baseline3;

import static performance.baseline3.Transitions.NO_TRANSITION;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class StoppedState extends State {
	static final Logger LOG = LogManager.getLogger(StoppedState.class);

	@Override
	Transition enter(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_STOP){
			service.serviceStoppedEnter();
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(BaseStateService service, StateTransitionEvent event) {
		service.serviceStoppedExit();
		return this;
	}
	@Override
	Transition onEvent(BaseStateService service, StateTransitionEvent event) {
		return NO_TRANSITION;
	}
}
