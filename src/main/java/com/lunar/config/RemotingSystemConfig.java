package com.lunar.config;

import com.typesafe.config.Config;

public class RemotingSystemConfig {
	private static String AKKA_TRANSPORT_PATH = "akka.transport";
	private static String HOSTNAME_PATH = "hostname";
	private static String AKKA_PORT_PATH = "akka.port";
	private static String AERON_TRANSPORT_PATH = "aeron.transport";
	private static String AERON_STREAM_PATH = "aeron.streamId";
	private static String AERON_PORT_PATH = "aeron.port";
	static String NAME_KEY = "name";
	private final String name;
	private final String hostname;
	private final String akkaTransport;
	private final int akkaPort;
	private final String aeronTransport;
	private final int aeronPort;
	private final int aeronStreamId;

	public RemotingSystemConfig(Config config){
		this.name = config.getString(NAME_KEY);
		this.akkaTransport = config.getString(AKKA_TRANSPORT_PATH);
		this.akkaPort = config.getInt(AKKA_PORT_PATH);
		this.hostname = config.getString(HOSTNAME_PATH);
		this.aeronPort = config.getInt(AERON_PORT_PATH);
		this.aeronStreamId = config.getInt(AERON_STREAM_PATH);
		this.aeronTransport = config.getString(AERON_TRANSPORT_PATH);
	}
	
	public String name(){
		return name;
	}

	public String hostname(){
		return hostname;
	}

	public String akkaTransport(){
		return akkaTransport;
	}

	public int akkaPort(){
		return akkaPort;
	}

	public String aeronTransport(){
		return aeronTransport;
	}

	public int aeronPort(){
		return aeronPort;
	}

	public int aeronStreamId(){
		return aeronStreamId;
	}
}
