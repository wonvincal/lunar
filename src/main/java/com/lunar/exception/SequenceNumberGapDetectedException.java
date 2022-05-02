package com.lunar.exception;

public class SequenceNumberGapDetectedException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4214964369690398788L;
	public SequenceNumberGapDetectedException(){
		super();
	}
	public SequenceNumberGapDetectedException(String message){
		super(message);
	}
	public SequenceNumberGapDetectedException(String message, Throwable cause){
		super(message, cause);
	}
	public SequenceNumberGapDetectedException(Throwable cause){
		super(cause);
	}

}
