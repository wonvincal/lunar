package com.lunar.exception;

public class OutsideOfBufferRangeException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8428409190484132816L;
	public OutsideOfBufferRangeException(){
		super();
	}
	public OutsideOfBufferRangeException(String message){
		super(message);
	}
	public OutsideOfBufferRangeException(String message, Throwable cause){
		super(message, cause);
	}
	public OutsideOfBufferRangeException(Throwable cause){
		super(cause);
	}

}
