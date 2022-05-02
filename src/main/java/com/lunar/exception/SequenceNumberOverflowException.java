package com.lunar.exception;

public class SequenceNumberOverflowException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3788905598574108511L;
	public SequenceNumberOverflowException(){
		super();
	}
	public SequenceNumberOverflowException(String message){
		super(message);
	}
	public SequenceNumberOverflowException(String message, Throwable cause){
		super(message, cause);
	}
	public SequenceNumberOverflowException(Throwable cause){
		super(cause);
	}

}
