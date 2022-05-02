package com.lunar.message.sink;

import com.lunar.message.SinkInfo;
import com.lunar.message.io.sbe.ServiceType;

import io.aeron.Publication;
import org.agrona.DirectBuffer;

public class AeronMessageSink implements MessageSink {
	private final String name;
	private final ServiceType serviceType;
	private final int sinkId;
	private final int systemId;
	private final Publication publication; // aeron publication is thread safe
	@SuppressWarnings("unused")
	private long backPressureCount;

	public AeronMessageSink(int systemId, int sinkId, ServiceType serviceType, Publication publication){
		this.systemId = systemId;
		this.name = "aeron-sink-" + serviceType.name() + "-" + sinkId;
		this.sinkId = sinkId;
		this.serviceType = serviceType;
		this.publication = publication;
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

	@Override
	public long tryClaim(int length, MessageSinkBufferClaim buffer) {
		return buffer.tryClaim(length, publication);
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
	public long tryPublish(DirectBuffer buffer, int offset, int length) {
		return publish(buffer, offset, length);
	}
	
	@Override
	public long publish(DirectBuffer buffer, int offset, int length) {
		long result;
		do{
			result = publication.offer(buffer, offset, length);
			if (result == Publication.BACK_PRESSURED){
				backPressureCount++;
				// TOOD we may want to let this sleep for a very small period of time
			}
			else if (result == Publication.NOT_CONNECTED){
				return result;
			}
		}
		while (result <= 0);
		return result;
	}
	
	Publication publication(){
		return publication;
	}
}
