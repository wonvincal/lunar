package com.lunar.fsm.request;

import com.lunar.fsm.request.RequestStateMachine.Transition;
import com.lunar.fsm.request.RequestStateMachine.Transitions;
import com.lunar.message.Response;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.TimerEventType;

import org.agrona.DirectBuffer;


interface State {
	
	/**
	 * Enter state with specified event
	 * @param context
	 * @param event
	 * @return
	 */
	default Transition enter(RequestContext reqContext, StateTransitionEvent enterOnEvent){
		return Transitions.in_ANY_STATE_receive_FAIL;
	}

	/**
	 * Exit state with specified event
	 * @param context
	 * @param event
	 * @return
	 */
	State exit(RequestContext reqContext, StateTransitionEvent exitOnEvent);

	/**
	 * Receive event in current state
	 * @param request
	 * @param event
	 * @return
	 */
	Transition onEvent(RequestContext reqContext, StateTransitionEvent resultingEvent);

	/**
	 * Receive response in current state
	 * @param request
	 * @param message
	 * @return
	 */
	Transition onMessage(RequestContext reqContext, Response message);

	/**
	 * Receive response in current state
	 * @param request
	 * @param message
	 * @return
	 */
	Transition onMessage(RequestContext reqContext, DirectBuffer buffer, int offset, ResponseSbeDecoder message);

	/**
	 * Receive timeout event in current state
	 * @param request
	 * @param timerEventType
	 * @return
	 */
	Transition onTimeout(RequestContext reqContext, TimerEventType timerEventType);

}