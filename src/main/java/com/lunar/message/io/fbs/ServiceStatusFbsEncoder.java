package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;

public class ServiceStatusFbsEncoder {
	static final Logger LOG = LogManager.getLogger(ServiceStatusFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, ServiceStatusSbeDecoder status){
		int limit = status.limit();
		ServiceStatusFbs.startServiceStatusFbs(builder);
		ServiceStatusFbs.addModifyTimeAtOrigin(builder, status.modifyTimeAtOrigin());
		ServiceStatusFbs.addServiceStatusType(builder, status.statusType().value());
		ServiceStatusFbs.addServiceType(builder, status.serviceType().value());
		ServiceStatusFbs.addSinkId(builder, status.sinkId());
		ServiceStatusFbs.addSystemId(builder, status.systemId());
		ServiceStatusFbs.addSentTime(builder, status.sentTime());
		ServiceStatusFbs.addHealthCheckTime(builder, status.healthCheckTime());
		status.limit(limit);
		return ServiceStatusFbs.endServiceStatusFbs(builder); 
	}

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, ServiceStatus status){
		ServiceStatusFbs.startServiceStatusFbs(builder);
		ServiceStatusFbs.addModifyTimeAtOrigin(builder, status.modifyTimeAtOrigin());
		ServiceStatusFbs.addServiceStatusType(builder, status.statusType().value());
		ServiceStatusFbs.addServiceType(builder, status.serviceType().value());
		ServiceStatusFbs.addSinkId(builder, (byte)status.sinkId());
		ServiceStatusFbs.addSystemId(builder, (byte)status.systemId());
		ServiceStatusFbs.addSentTime(builder, status.sentTime());
		ServiceStatusFbs.addHealthCheckTime(builder, status.healthCheckTime());
		return ServiceStatusFbs.endServiceStatusFbs(builder); 
	}
}
