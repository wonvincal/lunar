package performance.baseline3;

import static performance.baseline3.Transitions.in_ANY_STATE_receive_FAIL;

import com.lunar.message.binary.Frame;

public abstract class State {
	Transition enter(BaseStateService service, StateTransitionEvent event){
		return in_ANY_STATE_receive_FAIL;
	}
	State exit(BaseStateService service, StateTransitionEvent event){
		return this;
	}
	Transition onEvent(BaseStateService service, StateTransitionEvent event){
		return in_ANY_STATE_receive_FAIL;
	}
	Transition onEvent(BaseStateService service, Frame event, long sequence, boolean endOfBatch, StateTransitionEventAccessor accessor){
		return in_ANY_STATE_receive_FAIL;
	}
}
