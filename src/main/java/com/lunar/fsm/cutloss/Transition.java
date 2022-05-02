package com.lunar.fsm.cutloss;

import com.lunar.exception.StateTransitionException;

public class Transition {
	private final StateTransitionEvent event;
	private final State nextState;

	public static Transition of(State state /* not in use, just for clarity */, StateTransitionEvent event, State nextState){
		return new Transition(state, event, nextState);
	}
	
	public Transition(){
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
	public void proceed(TradeContext context) throws StateTransitionException {
		try {
			State currentState = context.state();
			if (currentState != nextState){
				currentState.exit(context, event);
				context.state(nextState);
				nextState.enter(context, event).proceed(context);
			}
		}
		catch (Exception e){
			throw new StateTransitionException(e);
		}
	}
}
