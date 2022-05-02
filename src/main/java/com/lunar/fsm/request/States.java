package com.lunar.fsm.request;

import java.util.concurrent.TimeoutException;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.exception.UnsupportedStateTransitionEventException;
import com.lunar.fsm.request.RequestStateMachine.Transition;
import com.lunar.fsm.request.RequestStateMachine.Transitions;
import com.lunar.message.Response;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sink.MessageSink;

/**
 * Different state of {@link RequestStateMachine}.  
 * Please see diagram <a href="https://sam.servebeer.com:453/centipede/lunar/wikis/message-flow-request-response">here</a>.
 * @author Calvin
 *
 */
enum States implements State {
	/**
	 * IDLE state of a request.
	 */
	IDLE {
		@Override
		public Transition enter(RequestContext context, StateTransitionEvent enterOnEvent) {
			return Transitions.NO_TRANSITION;
		}

		@Override
		public Transitions onEvent(RequestContext context, StateTransitionEvent resultingEvent){
			if (resultingEvent == StateTransitionEvent.SEND){
				return Transitions.in_IDLE_receive_SEND;
			}
			return Transitions.in_ANY_STATE_receive_FAIL;
		}		
	},
	
	/**
	 * Request has been sent and is now waiting for response
	 */
	SEND_AND_WAIT {
		@Override
		public Transition enter(RequestContext context, StateTransitionEvent enterOnEvent) {
			if (enterOnEvent == StateTransitionEvent.SEND){
				if (context.sendAndStartRequestTimeoutTimer() != MessageSink.OK){
					return Transitions.in_SEND_AND_WAIT_fail_to_send;
				}
			}
			else if (enterOnEvent == StateTransitionEvent.RECEIVE_RESPONSE){
				context.restartRequestTimeoutTimer();
			}
			return Transitions.NO_TRANSITION;
		}

		@Override
		public State exit(RequestContext context, StateTransitionEvent exitOnEvent) {
			context.cancelActiveRequestTimeout();
			return this;
		}

		@Override
		public Transitions onEvent(RequestContext context, StateTransitionEvent resultingEvent){
			if (resultingEvent == StateTransitionEvent.RETRY){
				return Transitions.in_SEND_AND_WAIT_receive_RETRY;
			}
			else if (resultingEvent == StateTransitionEvent.RECEIVE_RESPONSE){
				return Transitions.in_SEND_AND_WAIT_receive_PENDING;
			}
			else if (resultingEvent == StateTransitionEvent.FAIL){
				return Transitions.in_SEND_AND_WAIT_receive_FAIL;
			}
			else if (resultingEvent == StateTransitionEvent.COMPLETE){
				return Transitions.in_SEND_AND_WAIT_receive_COMPLETE;
			}
			else if (resultingEvent == StateTransitionEvent.NULL){
				return Transitions.NO_TRANSITION;
			}
			return Transitions.in_ANY_STATE_receive_FAIL;
		}	
		
		@Override
		public Transitions onTimeout(RequestContext context, TimerEventType timerEventType){
			// Ignore retry delay timeout; it must be an 'escaped' timeout from state "RETRY"
			if (timerEventType == TimerEventType.REQUEST_TIMEOUT){
				if (context.canRetry()){
					return Transitions.in_SEND_AND_WAIT_receive_RETRY;
				}
				return Transitions.in_SEND_AND_WAIT_receive_TIMEOUT;
			}
			return Transitions.NO_TRANSITION;
		}
	},
	
	/**
	 * Retry
	 */
	RETRY {
		@Override
		public Transition enter(RequestContext context, StateTransitionEvent enterOnEvent) {
			context.startRetryDelayTimer();
			return Transitions.NO_TRANSITION;
		}
		
		@Override
		public Transitions onEvent(RequestContext context, StateTransitionEvent resultingEvent) {
			if (resultingEvent == StateTransitionEvent.SEND){
				return Transitions.in_RETRY_receive_SEND;
			}
			else if (resultingEvent == StateTransitionEvent.RECEIVE_RESPONSE){
				return Transitions.in_RETRY_receive_PENDING;
			}
			else if (resultingEvent == StateTransitionEvent.COMPLETE){
				return Transitions.in_RETRY_receive_COMPLETE;
			}
			else if (resultingEvent == StateTransitionEvent.FAIL){
				return Transitions.in_RETRY_receive_FAIL;
			}
			return Transitions.in_ANY_STATE_receive_FAIL;
		}
		
		@Override
		public State exit(RequestContext context, StateTransitionEvent enterOnEvent) {
			context.cancelActiveRetryDelayTimeout();
			return this;
		}

		@Override
		public Transitions onTimeout(RequestContext context, TimerEventType timerEventType){
			if (timerEventType == TimerEventType.REQUEST_RETRY_DELAY_TIMEOUT){
				context.reqState().incAndGetRetryCount();
				return Transitions.in_RETRY_receive_SEND;
			}
			return Transitions.NO_TRANSITION;
		}

	},
	
	/**
	 * Complete
	 */
	DONE {
		@Override
		public Transition enter(RequestContext context, StateTransitionEvent enterOnEvent) {
			if (enterOnEvent == StateTransitionEvent.COMPLETE){
				context.result().complete(context.request().resultType(ResultType.OK));
			}
			else if (enterOnEvent == StateTransitionEvent.TIMEOUT){
				context.request().resultType(ResultType.TIMEOUT);
				context.result().completeExceptionally(context.reqState().cause().orElse(new TimeoutException("Request timeout [" + context.request().toString() + "]")));
			}
			else if (enterOnEvent == StateTransitionEvent.FAIL){
				context.request().resultType(ResultType.FAILED);
				context.result().completeExceptionally(context.reqState().cause().orElse(new Exception("Request failure [" + context.request().toString() + "]")));
			}
			else {
				context.request().resultType(ResultType.FAILED);
				context.result().completeExceptionally(new UnsupportedStateTransitionEventException(enterOnEvent.name()));
			}
			return Transitions.NO_TRANSITION;
		}

		@Override
		public Transitions onTimeout(RequestContext context, TimerEventType timerEventType){
			return Transitions.NO_TRANSITION;
		}
	};

	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(States.class);

	@Override
	public State exit(RequestContext context, StateTransitionEvent exitOnEvent){
		return this;
	}

	@Override
	public Transitions onEvent(RequestContext context, StateTransitionEvent resultingEvent){
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	@Override
	public Transitions onMessage(RequestContext context, Response message){
		try {
			if (message.resultType() == ResultType.OK){
				context.handleResponse(message);
				if (message.isLast() == BooleanType.TRUE){
					return onEvent(context, StateTransitionEvent.COMPLETE);
				}
				else{
					return onEvent(context, StateTransitionEvent.RECEIVE_RESPONSE);
				}
			}
			else {
				return onEvent(context, StateTransitionEvent.FAIL);
			}
		}
		catch (Exception ex){
			context.reqState().cause(ex);
			return Transitions.in_ANY_STATE_receive_FAIL;
		}
	}

	@Override
	public Transitions onMessage(RequestContext context, DirectBuffer buffer, int offset, ResponseSbeDecoder message){
		try {
			if (message.resultType() == ResultType.OK){
				// allow client to handle the response
				context.handleResponse(buffer, offset, message);
				if (message.isLast() == BooleanType.TRUE){
					return onEvent(context, StateTransitionEvent.COMPLETE);
				}
				else{
					return onEvent(context, StateTransitionEvent.RECEIVE_RESPONSE);
				}
			}
			else {
				return onEvent(context, StateTransitionEvent.FAIL);
			}
		}
		catch (Exception ex){
			context.reqState().cause(ex);
			return Transitions.in_ANY_STATE_receive_FAIL;
		}
	}
	
	@Override
	public Transitions onTimeout(RequestContext context, TimerEventType timerEventType){
		return Transitions.in_ANY_STATE_receive_FAIL;
	}
}
