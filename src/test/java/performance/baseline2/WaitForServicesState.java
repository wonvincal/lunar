package performance.baseline2;

import static performance.baseline2.Transitions.NO_TRANSITION;
import static performance.baseline2.Transitions.in_ANY_STATE_receive_FAIL;
import static performance.baseline2.Transitions.in_WAITING_FOR_SERVICES_receive_ACTIVATE;
import static performance.baseline2.Transitions.in_WAITING_FOR_SERVICES_receive_FAIL;
import static performance.baseline2.Transitions.in_WAITING_FOR_SERVICES_receive_THREAD_STOP;
import static performance.baseline2.Transitions.in_WAITING_FOR_SERVICES_receive_TIMEOUT;
import static performance.baseline2.Transitions.in_WAITING_FOR_SERVICES_receive_WAIT;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.Frame;

class WaitForServicesState extends State {
	static final Logger LOG = LogManager.getLogger(WaitForServicesState.class);

	@Override
	Transition enter(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_START ||
			event == StateTransitionEvent.WAIT){
			return onEvent(service, service.serviceWaitingForServicesEnter());
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(BaseStateService service, StateTransitionEvent event) {
		service.serviceWaitingForServicesExit();
		return this;
	}

	@Override
	Transition onEvent(BaseStateService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		if (event == StateTransitionEvent.ACTIVATE){
			return in_WAITING_FOR_SERVICES_receive_ACTIVATE;
		}
		else if (event == StateTransitionEvent.FAIL){
			return in_WAITING_FOR_SERVICES_receive_FAIL;
		}
		else if (event == StateTransitionEvent.TIMEOUT){
			return in_WAITING_FOR_SERVICES_receive_TIMEOUT;
		}
		else if (event == StateTransitionEvent.THREAD_STOP){
			return in_WAITING_FOR_SERVICES_receive_THREAD_STOP;
		}
		else if (event == StateTransitionEvent.WAIT){
			return in_WAITING_FOR_SERVICES_receive_WAIT;
		}
		LOG.warn("unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
	
	@Override
	Transition onEvent(BaseStateService service, Frame event, long sequence, boolean endOfBatch, StateTransitionEventAccessor accessor) {
		service.serviceWaitingForServicesOnEvent(event, sequence, endOfBatch);		
		return onEvent(service, accessor.event());
	}
}
