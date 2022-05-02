package com.lunar.message.binary;

import java.nio.ByteBuffer;

import com.lmax.disruptor.EventFactory;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * TODO create a frame factory that works out frame size based on:
 * 1) configuration file
 * 2) max of order book related updates and market data
 * 3) max of all messages
 * @author wongca
 *
 */
public class MessageSinkEventFactory implements EventFactory<MutableDirectBuffer>{
	private int frameSize;
	public static MessageSinkEventFactory of(){
		return new MessageSinkEventFactory(ServiceConstant.MAX_MESSAGE_SIZE);
	}
	public static MessageSinkEventFactory of(int frameSize){
		return new MessageSinkEventFactory(frameSize);
	}
	public MessageSinkEventFactory(int frameSize){
		this.frameSize = frameSize;
	}
	public MutableDirectBuffer newInstance(){
		return new UnsafeBuffer(ByteBuffer.allocateDirect(frameSize));
	}
}
