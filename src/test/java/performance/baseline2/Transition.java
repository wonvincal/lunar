package performance.baseline2;

import com.lunar.exception.StateTransitionException;

class Transition {
	private final StateTransitionEvent event;
	private final State nextState;

	static Transition of(State state /* not in use, just for clarity */, StateTransitionEvent event, State nextState){
		return new Transition(state, event, nextState);
	}
	
	Transition(){
		event = null;
		nextState = null;
	}
	
	Transition(State state /* not in use, just for clarity */, StateTransitionEvent event, State nextState){
		this.event = event;
		this.nextState = nextState;
	}

	/**
	 * Proceed with this transition.  Current state will be read from
	 * the context.
	 * @param request
	 * @return
	 * @throws StateTransitionException 
	 */
	public void proceed(BaseStateService service) throws StateTransitionException {
		try {
			State currentState = service.state();
			if (currentState != nextState){
				currentState.exit(service, event);
				service.state(nextState);
				nextState.enter(service, event).proceed(service);
			}
		}
		catch (Exception e){
			throw new StateTransitionException(e);
		}
	}
}
