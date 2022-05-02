package com.lunar.message.sink;

import org.agrona.DirectBuffer;

import com.lunar.message.SinkInfo;
import com.lunar.message.io.sbe.ServiceType;

public class ValidNullMessageSink implements MessageSink {
	public static int VALID_NULL_SYSTEM_ID = 0;
	public static int VALID_NULL_INSTANCE_ID = 0;
	private final ServiceType serviceType;
	private final int systemId;
	private final int sinkId;
	private boolean suppressWarning;
	
	public static ValidNullMessageSink of(int systemId, ServiceType serviceType){
		return new ValidNullMessageSink(systemId, VALID_NULL_INSTANCE_ID, serviceType, false);
	}

	public static ValidNullMessageSink of(int systemId, int sinkId, ServiceType serviceType){
		return new ValidNullMessageSink(systemId, sinkId, serviceType, false);
	}

	public ValidNullMessageSink(int systemId, int sinkId, ServiceType serviceType, boolean suppressWarning){
		this.systemId = systemId;
		this.serviceType = serviceType;
		this.sinkId = sinkId;
		this.suppressWarning = suppressWarning;
	}
	@Override
	public int systemId(){
		return systemId;
	}

	@Override
	public int sinkId(){
		return sinkId;
	}

	@Override
	public String name() {
		return "valid-null-message-sink";
	}

	@Override
	public ServiceType serviceType() {
		return serviceType;
	}

	@Override
	public long publish(DirectBuffer buffer, int offset, int length) {
		if (!suppressWarning){
			LOG.warn("publish message to ValidNullMessageSink [sinkId:{}, serviceType:{}]", sinkId, serviceType.name());
		}
		return MessageSink.OK;
	}

	@Override
	public long tryPublish(DirectBuffer buffer, int offset, int length) {
		if (!suppressWarning){
			LOG.warn("try publish message to ValidNullMessageSink [sinkId:{}, serviceType:{}]", sinkId, serviceType.name());
		}
		return MessageSink.OK;
	}

	@Override
	public long tryClaim(int length, MessageSinkBufferClaim buffer) {
		if (!suppressWarning){
			LOG.warn("try claim in ValidNullMessageSink [sinkId:{}, serviceType:{}]", sinkId, serviceType.name());
		}
		return MessageSink.FAILURE;
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
	public String toString() {
		return "systemId:" + systemId + ", name:" + name() + ", sinkId:" + sinkId() + ", serviceType:" + serviceType.name();
	};
	
	public ValidNullMessageSink suppressWarning(boolean suppressWarning){
		this.suppressWarning = suppressWarning;
		return this; 
	}
}
