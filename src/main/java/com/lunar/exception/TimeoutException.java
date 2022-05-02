package com.lunar.exception;

public class TimeoutException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2520205492349876683L;
	
	public TimeoutException(){
		super();
	}
	public TimeoutException(String message){
		super(message);
	}
	public TimeoutException(String message, Throwable cause){
		super(message, cause);
	}
	public TimeoutException(Throwable cause){
		super(cause);
	}
}
