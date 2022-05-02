package com.lunar.message.sink;

import com.lmax.disruptor.RingBuffer;
import com.lunar.message.io.sbe.ServiceType;

import org.agrona.MutableDirectBuffer;

public class TestMessageSinkBuilder {
	public static RingBufferMessageSink createRingBufferMessageSink(int systemId, int sinkId, ServiceType serviceType, String name, RingBuffer<MutableDirectBuffer> buffer){
		return new RingBufferMessageSink(systemId, sinkId, serviceType, name, buffer);
	}
	public static RingBufferMessageSinkPoller createRingBufferMessageSinkPoller(int systemId, int sinkId, ServiceType serviceType, String name, RingBuffer<MutableDirectBuffer> buffer){
		return RingBufferMessageSinkPoller.of(new RingBufferMessageSink(systemId, sinkId, serviceType, name, buffer));
	}
}
