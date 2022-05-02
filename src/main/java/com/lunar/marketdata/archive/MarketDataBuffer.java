package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.util.UnsafeUtil;

import sun.misc.Unsafe;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MarketDataBuffer {
	@SuppressWarnings("unused")
	private final Unsafe unsafe;
	private Frame[] frames;
	@SuppressWarnings("unused")
	private volatile int nextAvailableSeq;
	
	MarketDataBuffer(int size, int frameSize){
		unsafe = UnsafeUtil.getUnsafe();
		frames = new Frame[size];
		for (int i = 0; i < size; i++){
			frames[i] = new Frame(true, frameSize);
		}
	}
	
	public void put(AggregateOrderBookUpdateSbeEncoder snapshot){
		// write this snapshot into our buffer
	}
	
	private static class Frame{
		private AtomicBoolean dirty;
		private MutableDirectBuffer buffer;
		
		Frame(boolean allocateDirect, int bufferSize){
			dirty = new AtomicBoolean();
			if (allocateDirect)
			{
				buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder()));			
			}
			else
			{
				buffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize).order(ByteOrder.nativeOrder()));
			}
		}
		
		@SuppressWarnings("unused")
		public DirectBuffer buffer(){
			return buffer;
		}
		
		@SuppressWarnings("unused")
		public boolean compareAndSet(boolean value){
			return dirty.compareAndSet(!value, value);
		}
		
		@SuppressWarnings("unused")
		public void putBytes(DirectBuffer sourceBuffer, int offset, int length){
			buffer.putBytes(0, sourceBuffer, offset, length);
		}
	}
}
