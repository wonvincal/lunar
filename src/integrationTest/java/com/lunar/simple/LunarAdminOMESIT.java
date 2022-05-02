package com.lunar.simple;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.concurrent.locks.LockSupport;

import org.junit.Ignore;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.LunarSystem;
import com.lunar.core.LunarSystem.LunarSystemState;
import com.lunar.exception.ConfigurationException;
import com.lunar.util.ConcurrentUtil;

/**
 * Integration test for 
 * Admin, 
 * OMES, 
 * OCG Line Handler
 * Dummy Exchange
 * 
 * What do I want to do?
 * 1. Start up all services
 * 2. Send different order requests to OMES
 * 3. Get result backs
 * 
 * How to send orders?
 * Dummy Strategy
 * 
 * 
 * @author wongca
 *
 */
public class LunarAdminOMESIT {
	private static final Logger LOG = LogManager.getLogger(LunarAdminOMESIT.class);
	
	@Ignore
	@Test
	public void testStart() throws UnsupportedEncodingException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.omes.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort);
		ConcurrentUtil.sleep(4000_000);
		system.close();
		
		while (system.state() != LunarSystemState.STOPPED){
			LOG.info("current state: {}, waiting...", system.state());
			LockSupport.parkNanos(500_000_000);
		}
		LOG.info("current state: {}, done...", system.state());
		assertEquals(system.state(), LunarSystemState.STOPPED);
		
		while (!system.isCompletelyShutdown()){
			LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
			LockSupport.parkNanos(500_000_000);
		}

	}

}
