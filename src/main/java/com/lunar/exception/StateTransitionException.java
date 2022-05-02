package com.lunar.exception;

/**
 * Exception that happened during state transition.
 * @author Calvin
 *
 */
public class StateTransitionException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3368077100864377514L;
	
	public StateTransitionException(){
		super();
	}
	public StateTransitionException(String message){
		super(message);
	}
	public StateTransitionException(String message, Throwable cause){
		super(message, cause);
	}
	public StateTransitionException(Throwable cause){
		super(cause);
	}
}
