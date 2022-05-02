package com.lunar.fsm.channelbuffer;

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
	 * 
	 * @param context
	 * @throws StateTransitionException
	 */
	public void proceed(ChannelBufferContext context) throws StateTransitionException{
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
