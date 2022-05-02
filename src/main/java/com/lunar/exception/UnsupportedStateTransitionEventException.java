package com.lunar.exception;

public class UnsupportedStateTransitionEventException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3368077100864377514L;
	
	public UnsupportedStateTransitionEventException(){
		super();
	}
	public UnsupportedStateTransitionEventException(String message){
		super(message);
	}
	public UnsupportedStateTransitionEventException(String message, Throwable cause){
		super(message, cause);
	}
	public UnsupportedStateTransitionEventException(Throwable cause){
		super(cause);
	}
}
