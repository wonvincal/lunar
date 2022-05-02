package com.lunar.message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.ServiceStatusSbeEncoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;

public class ServiceStatus extends Message {
	private static final int INVALID_SYSTEM_ID = -1;
	static final Logger LOG = LogManager.getLogger(ServiceStatus.class);
	private int senderSinkId;
	private int sinkId;
	private int systemId;
	private ServiceType serviceType;
	private ServiceStatusType statusType;
	private long modifyTimeAtOrigin;
	private long sentTime;
	private long healthCheckTime;
	
	public static ServiceStatus cloneForDifferentSender(int senderSinkId, ServiceStatus status){
		ServiceStatus s = new ServiceStatus(status.systemId(), senderSinkId, status.sinkId(), status.serviceType(), status.statusType(), status.modifyTimeAtOrigin(), status.sentTime(), status.healthCheckTime());
		s.seq(status.seq());
		return s;
	}
	
	public static ServiceStatus of(int senderSinkId, int sinkId, ServiceType serviceType, ServiceStatusType statusType, long modifyTimeAtOrigin){
		return new ServiceStatus(INVALID_SYSTEM_ID, senderSinkId, sinkId, serviceType, statusType, modifyTimeAtOrigin, ServiceStatusSbeEncoder.sentTimeNullValue(), ServiceStatusSbeEncoder.healthCheckTimeNullValue());
	}
	
	public static ServiceStatus of(int systemId, int senderSinkId, int sinkId, ServiceType serviceType, ServiceStatusType statusType, long modifyTimeAtOrigin){
		return new ServiceStatus(systemId, senderSinkId, sinkId, serviceType, statusType, modifyTimeAtOrigin, ServiceStatusSbeEncoder.sentTimeNullValue(), ServiceStatusSbeEncoder.healthCheckTimeNullValue());
	}
	
	public static ServiceStatus of(int systemId, int senderSinkId, int sinkId, ServiceType serviceType, ServiceStatusType statusType, long modifyTimeAtOrigin, long sentTime, long healthCheckTime){
		return new ServiceStatus(systemId, senderSinkId, sinkId, serviceType, statusType, modifyTimeAtOrigin, sentTime, healthCheckTime);
	}

	ServiceStatus(int systemId, int senderSinkId, int sinkId, ServiceType serviceType, ServiceStatusType statusType, long modifyTimeAtOrigin, long sentTime, long healthCheckTime){
		this.systemId = systemId;
		this.senderSinkId = senderSinkId;
		this.sinkId = sinkId;
		this.serviceType = serviceType;
		this.statusType = statusType;
		this.modifyTimeAtOrigin = modifyTimeAtOrigin;
		this.sentTime = sentTime;
		this.healthCheckTime = healthCheckTime;
	}
	
	public int systemId(){
		return systemId;
	}

	public int sinkId(){
		return sinkId;
	}
	
	public ServiceType serviceType(){
		return serviceType;
	}

	public ServiceStatusType statusType(){
		return statusType;
	}
	
	public long modifyTimeAtOrigin(){
		return modifyTimeAtOrigin;
	}

	public long sentTime(){
		return sentTime;
	}
	
	public long healthCheckTime(){
		return healthCheckTime;
	}
	
	@Override
	public int senderSinkId() {
		return senderSinkId;
	}
	
	@Override
	public String toString() {
		return String.format("senderSinkId:%d, systemId:%d, sinkId:%d, serviceType:%s, status:%s, lastUpdate:%d, sentTime:%d, healthCheckTime:%d", 
								this.senderSinkId,
								this.systemId,
								this.sinkId,
								this.serviceType,
								this.statusType.name(), 
								this.modifyTimeAtOrigin,
								this.sentTime,
								this.healthCheckTime);
	}
}