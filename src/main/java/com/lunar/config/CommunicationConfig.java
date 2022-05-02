package com.lunar.config;

import com.typesafe.config.Config;

/**
 * Aeron related configuration of the system base on system configuration file.
 * 
 * Thread safety: No
 * 
 * @author Calvin
 *
 */
public class CommunicationConfig {
	private static final String CONFIG_PATH = "lunar.aeron";
	private static final String TRANSPORT_KEY = "transport";
	private static final String PORT_KEY = "port";
	private static final String HOSTNAME_KEY = "hostname";
	private static final String STREAM_ID_KEY = "streamId";
	private static final String AERON_DIR_KEY = "aeronDir";
	private static final String DELETE_AERON_DIR_ON_START_KEY = "deleteAeronDirOnStart";

	private final int systemId;
	private final int adminSinkId;
	private final String transport;
	private final int port;
	private final int streamId;
	private final String aeronDir;
	private final String hostname;
	private final String channel;
	private final boolean deleteAeronLogDirOnStart;
	
	public static CommunicationConfig of(int systemId, int adminSinkId, Config config){
		Config ownConfig = config.getConfig(CONFIG_PATH);
		return new CommunicationConfig(systemId, 
				adminSinkId, 
				ownConfig.getString(TRANSPORT_KEY), 
				ownConfig.getInt(PORT_KEY), 
				ownConfig.getInt(STREAM_ID_KEY), 
				ownConfig.getString(AERON_DIR_KEY), 
				ownConfig.getString(HOSTNAME_KEY),
				ownConfig.getBoolean(DELETE_AERON_DIR_ON_START_KEY));
	}
	
	public static CommunicationConfig of(int systemId, 
			int adminSinkId, 
			String transport, 
			int port, 
			int streamId,
			String aeronDir, 
			String hostname,
			boolean deleteAeronDirOnStart){
		return new CommunicationConfig(systemId, 
				adminSinkId, 
				transport, 
				port, 
				streamId, 
				aeronDir, 
				hostname,
				deleteAeronDirOnStart);
	}

	CommunicationConfig(int systemId, 
			int adminSinkId, 
			String transport, 
			int port, 
			int streamId,
			String aeronDir, 
			String hostname,
			boolean deleteAeronDirOnStart){
		try {
			this.systemId = systemId;
			this.adminSinkId = adminSinkId;
			
			this.hostname = hostname;
			this.transport = transport;
			this.port = port;
			this.streamId = streamId;
			this.aeronDir = aeronDir;
			this.channel = createChannelStr(this.transport, this.hostname, this.port);
			this.deleteAeronLogDirOnStart = deleteAeronDirOnStart;
		}
		catch (Exception e){
			throw new IllegalArgumentException("Caught exception while creating aeron config", e);
		}
	}

	public int systemId(){
		return systemId;
	}

	public int adminSinkId(){
		return adminSinkId;
	}

	public int port(){
		return port;
	}
	
	public int streamId(){
		return streamId;
	}
	
	public String aeronDir(){
		return aeronDir;
	}
	
	public String transport(){
		return transport;
	}
	
	public String hostname(){
		return hostname;
	}
	
	public String channel(){
		return channel;
	}
	
	public boolean deleteAeronDirOnStart(){
		return deleteAeronLogDirOnStart;
	}

	public static String createChannelStr(String transport, String hostname, int port){
		return "aeron:" + transport + "?endpoint=" + hostname + ":" + port;		
	}
	
	@Override
	public String toString() {
		return channel();
	}
}
