package com.lunar.util;

import java.nio.ByteBuffer;

import com.lmax.disruptor.EventFactory;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MutableDirectBufferEvent {
	private int length;
	private MutableDirectBuffer buffer;
	
	MutableDirectBufferEvent(boolean allocateDirect){
		this.length = 0;
		this.buffer = new UnsafeBuffer((allocateDirect) ? ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE) : ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	}

	public int length(){
		return length;
	}

	public MutableDirectBuffer buffer(){
		return buffer;
	}
	
	public MutableDirectBufferEvent size(int value){
		this.length = value;
		return this;
	}
	
	public static class MutableDirectBufferEventFactory implements EventFactory<MutableDirectBufferEvent>{

		@Override
		public MutableDirectBufferEvent newInstance() {
			return new MutableDirectBufferEvent(true);
		}
		
	}
	
	public static MutableDirectBufferEventFactory FACTORY_INSTANCE = new MutableDirectBufferEventFactory();
}
