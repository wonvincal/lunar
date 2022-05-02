package com.lunar.message.binary;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A utility class to facilitate encoding/decoding of messages using sbe.  User of 
 * this class can register different decoding handlers for each {@link #TemplateType }
 * 
 * Thread safety: no
 * @author Calvin
 *
 */
public class MessageCodec {
	private final Frame frame;
	private final MutableDirectBuffer buffer;
	private final MessageReceiver decoder;
	
	public static MessageCodec of(){
		return new MessageCodec(1024);
	}
	public static MessageCodec of(int capacity){
		return new MessageCodec(capacity);
	}
	MessageCodec(int capacity){
		this.frame = new Frame(true, capacity);
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(capacity));
		this.decoder = MessageReceiver.of();
	}
	
	public MessageReceiver decoder(){
		return decoder;
	}
	
	public Frame frame(){
		return this.frame;
	}
	
	public MutableDirectBuffer buffer(){
		return this.buffer;
	}
}
