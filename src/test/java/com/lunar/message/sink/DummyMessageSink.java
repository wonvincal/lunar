package com.lunar.message.sink;

import org.agrona.DirectBuffer;

import com.lunar.message.SinkInfo;
import com.lunar.message.io.sbe.ServiceType;

public class DummyMessageSink implements MessageSink {
	private final int systemId;
	private final int sinkId;
	private final String name;
	private final ServiceType serviceType;
	
	private DummyMessageSink(int systemId, int sinkId, String name, ServiceType serviceType){
		this.systemId = systemId;
		this.sinkId = sinkId;
		this.name = name;
		this.serviceType = serviceType;
	}
	
	public static DummyMessageSink of(int systemId,
			int sinkId, 
			String name, 
			ServiceType serviceType){
		return new DummyMessageSink(systemId, sinkId, name, serviceType);
	}
	
	public static DummyMessageSink of(int sinkId,
			String name, 
			ServiceType serviceType){
		return new DummyMessageSink(-1, sinkId, name, serviceType);
	}

	public static MessageSinkRef refOf(int systemId,
			int sinkId, 
			String name, 
			ServiceType serviceType){
		return MessageSinkRef.of(new DummyMessageSink(systemId, sinkId, name, serviceType));
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
	
	@Override
	public int backPressureIntensity() {
		return 0;
	}

	@Override
	public SinkInfo processingRate() {
		return SinkInfo.NULL_INSTANCE;
	}

	@Override
	public long publish(DirectBuffer buffer, int offset, int length) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public long tryPublish(DirectBuffer buffer, int offset, int length) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public long tryClaim(int length, MessageSinkBufferClaim buffer) {
		// TODO Auto-generated method stub
		return -1;
	}
	
	@Override
	public String toString() {
		return "name:" + name() + ", sinkId:" + sinkId();
	}
}
