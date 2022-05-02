package com.lunar.exception;

public class SessionDisconnectedException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6732869063144794027L;
	public SessionDisconnectedException(){
		super();
	}
	public SessionDisconnectedException(String message){
		super(message);
	}
	public SessionDisconnectedException(String message, Throwable cause){
		super(message, cause);
	}
	public SessionDisconnectedException(Throwable cause){
		super(cause);
	}
}
