package com.lunar.message.sink;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.SinkInfo;
import com.lunar.message.io.sbe.ServiceType;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class RingBufferMessageSink implements MessageSink {
	private final int systemId;
	private final String name;
	private final int sinkId;
	private final ServiceType serviceType;
	private final RingBuffer<MutableDirectBuffer> ringBuffer;
	private int backPressureCount;

	RingBufferMessageSink(int systemId, int sinkId, ServiceType serviceType, String name, RingBuffer<MutableDirectBuffer> ringBuffer){
		if (serviceType == ServiceType.NULL_VAL){
			throw new IllegalArgumentException("Illegal service type: " + ServiceType.NULL_VAL.name());
		}
		this.systemId = systemId;
		this.name = "rb-sink-" + name + "-" + sinkId;
		this.sinkId = sinkId;
		this.serviceType = serviceType;
		this.ringBuffer = ringBuffer;
		this.backPressureCount = 0;
	}
	
	@Override
	public int systemId() {
		return systemId;
	}
	
	@Override
	public int sinkId() {
		return sinkId;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public ServiceType serviceType() {
		return serviceType;
	}

	/**
	 * Blocking
	 */
	@Override
	public long publish(DirectBuffer srcBuffer, int offset, int length) {
		long sequence = ringBuffer.next();
		try {
			MutableDirectBuffer buffer = ringBuffer.get(sequence);
			buffer.putBytes(0, srcBuffer, offset, length);
		} 
		finally 
		{
			ringBuffer.publish(sequence);
//			LOG.info("Published message to disruptor [sinkId:{}, sequence:{}]", sinkId, sequence);
		}
		return MessageSink.OK;
	}
	
	/**
	 * Non blocking
	 */
	@Override
	public long tryPublish(DirectBuffer srcBuffer, int offset, int length) {
		long sequence = -1;
		try {
			sequence = ringBuffer.tryNext();
			MutableDirectBuffer buffer = ringBuffer.get(sequence);
			buffer.putBytes(0, srcBuffer, offset, length);
		} 
		catch (InsufficientCapacityException e){
			return MessageSink.INSUFFICIENT_SPACE;
		}
		finally {
			// cannot think of any other way beside than this
			if (sequence != -1){
				ringBuffer.publish(sequence);
//				LOG.info("Try published message to disruptor [sinkId:{}, sequence:{}]", sinkId, sequence);
			}
//			else{
//				LOG.info("Try published message to disruptor but sequence is -1 [sinkId:{}, sequence:{}]", sinkId, sequence);
//			}
		}
		return MessageSink.OK;
	}

	@Override
	public int backPressureIntensity() {
		return backPressureCount;
	}

	@Override
	public SinkInfo processingRate() {
		return SinkInfo.NULL_INSTANCE;
	}

	@Override
	public long tryClaim(int length, MessageSinkBufferClaim buffer) {
		return buffer.tryClaim(sinkId, length, ringBuffer);
	}
	
	@Override
	public String toString() {
		return "systemId:" + systemId + ", name:" + name + ", sinkId:" + sinkId;
	}

	RingBuffer<MutableDirectBuffer> ringBuffer(){
		return ringBuffer;
	}
	
	
}
