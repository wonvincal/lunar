package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SubscriberList;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.ServiceStatusSbeEncoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class ServiceStatusSender {
	static final Logger LOG = LogManager.getLogger(ServiceStatusSender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = ServiceStatusSbeEncoder.BLOCK_LENGTH;
	}
	private final int systemId;
	private final MessageSender msgSender;
	private final ServiceStatusSbeEncoder sbe = new ServiceStatusSbeEncoder();
	
	public static ServiceStatusSender of(int systemId, MessageSender msgSender){
		return new ServiceStatusSender(systemId, msgSender);
	}
	
	ServiceStatusSender(int systemId, MessageSender msgSender){
		this.systemId = systemId;
		this.msgSender = msgSender;
	}
	
	public void sendServiceStatus(MessageSinkRef sink, ServiceStatus serviceStatus){
		sendServiceStatus(sink, serviceStatus.systemId(), serviceStatus.sinkId(), serviceStatus.serviceType(), serviceStatus.statusType(), serviceStatus.modifyTimeAtOrigin(), serviceStatus.sentTime(), serviceStatus.healthCheckTime());
	}
	
	public void sendOwnServiceStatus(MessageSinkRef sink, ServiceStatusType statusType, long modifyTimeAtOrigin){
		sendServiceStatus(sink, systemId, msgSender.self().sinkId(), msgSender.self().serviceType(), statusType, modifyTimeAtOrigin, ServiceStatusSbeEncoder.sentTimeNullValue(), ServiceStatusSbeEncoder.healthCheckTimeNullValue());
	}
	
	public void sendOwnServiceStatus(MessageSinkRef sink, ServiceStatusType statusType, long modifyTimeAtOrigin, long sentTime, long healthCheckTime){
		sendServiceStatus(sink, systemId, msgSender.self().sinkId(), msgSender.self().serviceType(), statusType, modifyTimeAtOrigin, sentTime, healthCheckTime);
	}

	/**
	 * Send service status
	 * @param sender
	 * @param sink
	 * @param sinkId
	 * @param serviceType
	 * @param statusType
	 * @param modifyTimeAtOrigin
	 */
	private void sendServiceStatus(MessageSinkRef sink, int systemId, int sinkId, ServiceType serviceType, ServiceStatusType statusType, long modifyTimeAtOrigin, long sentTime, long healthCheckTime){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeServiceStatus(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					systemId,
					sinkId, 
					serviceType,
					statusType,
					modifyTimeAtOrigin,
					sentTime,
					healthCheckTime);
			bufferClaim.commit();
			return;
		}
		sendServiceStatusCopy(sink, systemId, sinkId, serviceType, statusType, modifyTimeAtOrigin, sentTime, healthCheckTime);
	}
	
	public long sendServiceStatusExcept(MessageSinkRef[] sinks, ServiceStatus serviceStatus, int[] except, long[] results){
		int size = encodeServiceStatus(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(),
				0,
				sbe, 
				serviceStatus.systemId(),
				serviceStatus.sinkId(), 
				serviceStatus.serviceType(), 
				serviceStatus.statusType(), 
				serviceStatus.modifyTimeAtOrigin(),
				serviceStatus.sentTime(),
				serviceStatus.healthCheckTime());
		return msgSender.send(sinks, msgSender.buffer(), 0, size, except, results);
	}
	
	/**
	 * 
	 * @param sinks
	 * @param serviceStatus
	 * @param results Array to hold send result for each sink
	 * @return Negative means at least a message couldn't be delivered,otherwise positive 
	 */
	public long sendServiceStatus(MessageSinkRef[] sinks, ServiceStatus serviceStatus, long[] results){
		int size = encodeServiceStatus(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(),
				0,
				sbe, 
				serviceStatus.systemId(),
				serviceStatus.sinkId(), 
				serviceStatus.serviceType(), 
				serviceStatus.statusType(), 
				serviceStatus.modifyTimeAtOrigin(),
				serviceStatus.sentTime(),
				serviceStatus.healthCheckTime());
		return msgSender.send(sinks, msgSender.buffer(), 0, size, results);
	}

	public long sendServiceStatus(SubscriberList subscribers, ServiceStatus serviceStatus, long[] results){
		int size = encodeServiceStatus(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(),
				0,
				sbe, 
				serviceStatus.systemId(),
				serviceStatus.sinkId(), 
				serviceStatus.serviceType(), 
				serviceStatus.statusType(), 
				serviceStatus.modifyTimeAtOrigin(),
				serviceStatus.sentTime(),
				serviceStatus.healthCheckTime());
		return msgSender.trySend(subscribers.elements(), subscribers.size(), msgSender.buffer(), 0, size, results);
	}
	
	/**
	 * 
	 * @param sinks
	 * @param serviceStatus
	 * @param except
	 * @return Caution - returned array is a data structure that will be reused by other send/trySend 
	 * 		   methods
	 * 		   <br>[0] - Negative means at least one message couldn't be delivered
	 *         <br>[N] - Negative means message couldn't be delivered to sinks[N - 1], otherwise positive 
	 */
	public long sendServiceStatusExcept(MessageSinkRef[] sinks, ServiceStatus serviceStatus, int except, long[] results){
		int size = encodeServiceStatus(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(),
				0,
				sbe, 
				serviceStatus.systemId(),
				serviceStatus.sinkId(), 
				serviceStatus.serviceType(), 
				serviceStatus.statusType(), 
				serviceStatus.modifyTimeAtOrigin(),
				serviceStatus.sentTime(),
				serviceStatus.healthCheckTime());
		return msgSender.send(sinks, msgSender.buffer(), 0, size, except, results);
	}

	private void sendServiceStatusCopy(MessageSinkRef sink, int systemId, int sinkId, ServiceType serviceType, ServiceStatusType statusType, long modifyTimeAtOrigin, long sentTime, long healthCheckTime){
		int size = encodeServiceStatus(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0,
				sbe, 
				systemId,
				sinkId, 
				serviceType, 
				statusType, 
				modifyTimeAtOrigin,
				sentTime,
				healthCheckTime);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + ServiceStatusSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + ServiceStatusSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + ServiceStatusSbeEncoder.BLOCK_LENGTH;
	}

	public static int encodeServiceStatusWithoutHeader(final MutableDirectBuffer buffer, 
			int offset, 
			ServiceStatusSbeEncoder sbe,
			int systemId,
			int sinkId, 
			ServiceType serviceType, 
			ServiceStatusType statusType, 
			long modifyTimeAtOrigin,
			long sentTime,
			long healthCheckTime){
		sbe.wrap(buffer, offset)
		.systemId((byte)systemId)
		.sinkId((byte)sinkId)
		.serviceType(serviceType)
		.statusType(statusType)
		.modifyTimeAtOrigin(modifyTimeAtOrigin)
		.sentTime(sentTime)
		.healthCheckTime(healthCheckTime);
		return sbe.encodedLength();		
	}

	
	static int encodeServiceStatus(final MessageSender sender,
									int dstSinkId,
									final MutableDirectBuffer buffer, 
									int offset, 
									ServiceStatusSbeEncoder sbe,
									int systemId,
									int sinkId, 
									ServiceType serviceType, 
									ServiceStatusType statusType, 
									long modifyTimeAtOrigin,
									long sentTime,
									long healthCheckTime){

		sbe.wrap(buffer, offset + sender.headerSize())
		.systemId((byte)systemId)
		.sinkId((byte)sinkId)
		.serviceType(serviceType)
		.statusType(statusType)
		.modifyTimeAtOrigin(modifyTimeAtOrigin)
		.sentTime(sentTime)
		.healthCheckTime(healthCheckTime);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				ServiceStatusSbeEncoder.BLOCK_LENGTH, 
				ServiceStatusSbeEncoder.TEMPLATE_ID, 
				ServiceStatusSbeEncoder.SCHEMA_ID, 
				ServiceStatusSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		
		return sbe.encodedLength() + sender.headerSize();
	}
	
	public int encodeSelfServiceStatus(MutableDirectBuffer dstBuffer, 
			int offset, 
			int dstSinkId,
			ServiceType serviceType, 
			ServiceStatusType statusType, 
			long modifyTimeAtOrigin,
			long sentTime,
			long healthCheckTime){

		return encodeServiceStatus(msgSender, 
				dstSinkId,
				dstBuffer,
				offset,
				sbe,
				systemId,
				msgSender.self().sinkId(),
				serviceType,
				statusType,
				modifyTimeAtOrigin,
				sentTime,
				healthCheckTime);
	}	
	
}
