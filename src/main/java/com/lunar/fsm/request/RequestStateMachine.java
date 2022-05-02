package com.lunar.fsm.request;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Response;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.TimerEventType;

import org.agrona.DirectBuffer;

/**
 * State machine for a request lifecycle, which includes timeout, retry and responses.  Please 
 * see the diagram <a href="https://sam.servebeer.com:453/centipede/lunar/wikis/message-flow-request-response">here</a>.
 * @author Calvin
 *
 */
public final class RequestStateMachine {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(RequestStateMachine.class);
	
	enum Transitions implements Transition {
		// IDLE
		in_IDLE_receive_SEND(States.IDLE, StateTransitionEvent.SEND, States.SEND_AND_WAIT),
		
		// SEND_AND_WAIT
		in_SEND_AND_WAIT_receive_PENDING(States.SEND_AND_WAIT, StateTransitionEvent.RECEIVE_RESPONSE, States.SEND_AND_WAIT),
		in_SEND_AND_WAIT_receive_RETRY(States.SEND_AND_WAIT, StateTransitionEvent.RETRY, States.RETRY),
		in_SEND_AND_WAIT_receive_FAIL(States.SEND_AND_WAIT, StateTransitionEvent.FAIL, States.DONE),
		in_SEND_AND_WAIT_receive_COMPLETE(States.SEND_AND_WAIT, StateTransitionEvent.COMPLETE, States.DONE),
		in_SEND_AND_WAIT_receive_TIMEOUT(States.SEND_AND_WAIT, StateTransitionEvent.TIMEOUT, States.DONE),
		in_SEND_AND_WAIT_fail_to_send(States.SEND_AND_WAIT, StateTransitionEvent.FAIL, States.DONE),

		// RETRY
		in_RETRY_receive_SEND(States.RETRY, StateTransitionEvent.SEND, States.SEND_AND_WAIT),
		in_RETRY_receive_PENDING(States.RETRY, StateTransitionEvent.RECEIVE_RESPONSE, States.SEND_AND_WAIT),
		in_RETRY_receive_COMPLETE(States.RETRY, StateTransitionEvent.COMPLETE, States.DONE),
		in_RETRY_receive_FAIL(States.RETRY, StateTransitionEvent.FAIL, States.DONE),
		
		// else
		in_ANY_STATE_receive_FAIL(null, StateTransitionEvent.FAIL, States.DONE),
		NO_TRANSITION {
			@Override
			public void proceed(RequestContext reqContext) {
			}
		};

		@SuppressWarnings("unused")
		private final State state; // Not being used
		private final StateTransitionEvent event;
		private final State nextState;

		Transitions(){
			state = null;
			event = null;
			nextState = null;
		}
		
		Transitions(State state, StateTransitionEvent event, State nextState){
			this.state = state;
			this.event = event;
			this.nextState = nextState;
		}

		@Override
		public void proceed(RequestContext reqContext) {
			State currentState = reqContext.state();
			if (currentState != nextState)
			{
				currentState.exit(reqContext, event);
				reqContext.state(nextState);
				nextState.enter(reqContext, event).proceed(reqContext);
			}
		}
	}
	
	static interface Transition {
		/**
		 * Proceed with this transition.  Current state will be read from
		 * the context.
		 * @param request
		 * @return
		 */
		void proceed(RequestContext reqContext);
	}

	public static interface FinalStateReachedHandler {
		void whenReached(RequestContext reqContext);
		
		public static FinalStateReachedHandler NULL_INSTANCE = new FinalStateReachedHandler() {
			@Override
			public void whenReached(RequestContext reqContext) { }
		};
	}
	
	private static final State FINAL_STATE = States.DONE;
	
	private final FinalStateReachedHandler finalStateReachedHandler;
	
	public RequestStateMachine(FinalStateReachedHandler handler){
		this.finalStateReachedHandler = handler;
	}
	
	/**
	 * Bootstrap a request.  Enter into the {@link States#IDLE} state.
	 * @param context
	 * @return
	 */
	public void init(RequestContext reqContext){
		reqContext.state(States.IDLE).enter(reqContext, StateTransitionEvent.NULL).proceed(reqContext);
	}

	public void start(RequestContext reqContext){
		process(reqContext, StateTransitionEvent.SEND);
	}

	void enterSpecifcState(RequestContext reqContext, State state, StateTransitionEvent triggeringEvent){
		reqContext.state(state).enter(reqContext, triggeringEvent).proceed(reqContext);
	}
	
	public final void process(RequestContext reqContext, StateTransitionEvent event){
		reqContext.state().onEvent(reqContext, event).proceed(reqContext);
		if (reqContext.state() == FINAL_STATE){
			finalStateReachedHandler.whenReached(reqContext);
		}
	}
	
	public final void process(RequestContext reqContext, Response message){
		reqContext.state().onMessage(reqContext, message).proceed(reqContext);
		if (reqContext.state() == FINAL_STATE){
			finalStateReachedHandler.whenReached(reqContext);
		}
	}

	public final void process(RequestContext reqContext, DirectBuffer buffer, int offset, ResponseSbeDecoder message){
		reqContext.state().onMessage(reqContext, buffer, offset, message).proceed(reqContext);
		if (reqContext.state() == FINAL_STATE){
			finalStateReachedHandler.whenReached(reqContext);
		}
	}

	public final void process(RequestContext reqContext, TimerEventType timerEventType){
		reqContext.state().onTimeout(reqContext, timerEventType).proceed(reqContext);
		if (reqContext.state() == FINAL_STATE){
			finalStateReachedHandler.whenReached(reqContext);
		}
	}
}
