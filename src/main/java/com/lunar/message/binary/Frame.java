package com.lunar.message.binary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Wrapper of message(s) being sent between actors
 * 
 * TODO Smart batching of messages into a frame
 * 
 * @author Calvin
 *
 */
public class Frame {
	@SuppressWarnings("unused")
	private static Logger LOG = LogManager.getLogger(Frame.class);
	private int offset = 0;
	// TODO to be used
	private int length = 0;
	private MutableDirectBuffer buffer;

	/**
	 * Use this constructor when you have direct access to the underlying bytes.
	 * TODO See if it is better to have a reference to byte[] directly instead of DirectBuffer;
	 * to save gc and allocation cost
	 * @param bytes
	 */
	public Frame(byte[] bytes){
		this.buffer = new UnsafeBuffer(bytes);
		this.offset = 0;
	}
	
	public Frame(boolean allocateDirect, int bufferSize) {
		if (allocateDirect)
		{
			buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder()));			
		}
		else
		{
			buffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize).order(ByteOrder.nativeOrder()));
		}
		this.offset = 0;
	}
	public Frame length(int length){
		this.length = length;
		return this;
	}
	public int length(){
		return this.length;
	}
	public Frame offset(int offset){
		this.offset = offset;
		return this;
	}
	public int offset(){
		return this.offset;
	}
	public void wrap(MutableDirectBuffer buffer){
		this.buffer = buffer;
		this.offset = 0;
	}
	public void wrap(MutableDirectBuffer buffer, int offset, int length){
		this.buffer = buffer;
		this.offset = offset;
		this.length = length;
	}	
	public byte[] byteArray(){
		if (buffer.byteArray() != null){
			return buffer.byteArray();
		}
		byte[] bytes = new byte[buffer.capacity()];
		buffer.getBytes(0, bytes);
		return bytes;
	}
	public MutableDirectBuffer buffer(){ return buffer;}
	
	/**
	 * Copy message.buffer[message.offset] to message.buffer[end] to this.buffer[0]
	 * @param message
	 */
	public void merge(Frame message){
		this.buffer.putBytes(0, message.buffer, message.offset, message.buffer.capacity());
		this.offset = 0;
		this.length = message.length;
	}
	
	public static Frame NULL_INSTANCE = new Frame(new byte[0]);
}
