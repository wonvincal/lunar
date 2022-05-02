package com.lunar.message;

import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;

public class ServiceInfo {
	private int sinkId;
	private ServiceType svcType;
	private String aeronChannel;
	private int aeronStreamId;
	private Optional<String> actorPath;

	public ServiceInfo(int sinkId, ServiceType svcType, String aeronChannel, int aeronStreamId){
		this.sinkId = sinkId;
		this.svcType = svcType;
		this.aeronChannel = aeronChannel;
		this.aeronStreamId = aeronStreamId;
		this.actorPath = Optional.empty();
	}

	public ServiceInfo(int sinkId, ServiceType svcType, String actorPath){
		this.sinkId = sinkId;
		this.svcType = svcType;
		this.actorPath = Optional.of(actorPath);
		this.aeronChannel = null;
		this.aeronStreamId = -1;
	}
	
	public int sinkId(){
		return this.sinkId;
	}
	
	public Optional<String> actorPath(){
		return this.actorPath;
	}
	
	public ServiceType svcType(){
		return this.svcType;
	}
	
	public String aeronChannel(){
		return this.aeronChannel;
	}
	
	public int aeronStreamId(){
		return this.aeronStreamId;
	}
	
	@Override
	public String toString() {
		return "sinkId:" + sinkId + ", serviceType:" + svcType + 
				", aeronChannel:" + aeronChannel + 
				", aeronStreamId:" + aeronStreamId +
				", actorPath:" + actorPath.orElse("none");
	}
}
