package com.lunar.message;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;

import com.lunar.message.binary.Frame;

public class MessageBuffer {
	private final Frame frame;
	private final UnsafeBuffer unsafeBuffer;
	
	private MessageBuffer(int bufferSize){
		this.frame = new Frame(true, bufferSize);
		this.unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferSize));
	}
	
	public UnsafeBuffer aeronBuffer(){
		return this.unsafeBuffer;
	}
	
	public Frame frameBuffer(){
		return this.frame;
	}
	
	public static Frame frameBuferOnly(int bufferSize){
		return new Frame(true, bufferSize);
	}
	
	public static MessageBuffer ofSize(int bufferSize){
		return new MessageBuffer(bufferSize);
	}
}
