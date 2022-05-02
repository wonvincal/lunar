package com.lunar.core;

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Paths;

import org.junit.Test;

import com.lunar.exception.ConfigurationException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class LunarSystemTest {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(LunarSystemTest.class);

	@Test
	public void testStartLunarOnly() throws UnsupportedEncodingException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8789;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		
		URL url = this.getClass().getClassLoader().getResource(Paths.get("config", "simple", "lunar.admin.conf").toString());		
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(url.getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort);
		system.close();
		 assertTrue(system.isCompletelyShutdown());
	}
	
	@Test(expected=com.typesafe.config.ConfigException.IO.class)
	public void testStartLunarWithInvalidConfigPath() {
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8790;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, Paths.get("config", "simple", "xlunar.only.conf").toString());
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		try (LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort)){
			
		}
		catch (ConfigurationException e){
			
		}
	}

	public void testStartLunarAndAdmin() throws InterruptedException {
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, Paths.get("config", "simple", "lunar.admin.conf").toString());
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		try (LunarSystem tiger = LunarSystem.launch(systemName, host, port, jmxPort)){
		}
		catch (ConfigurationException e){
			
		}
	}
	
	public void testStartLunarAndAdminAndPerfAndRefData() throws InterruptedException {
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, Paths.get("config", "simple", "LunarSystem.admin.perf.refData.conf").toString());
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		try (LunarSystem tiger = LunarSystem.launch(systemName, host, port, jmxPort)){
		}
		catch (ConfigurationException e){
			
		}
	}
	
	public void testStartLunarAndAdminAndPerf() throws InterruptedException {
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, Paths.get("config", "simple", "LunarSystem.admin.perf.conf").toString());
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		try (LunarSystem tiger = LunarSystem.launch(systemName, host, port, jmxPort)){
		}
		catch (ConfigurationException e){
			
		}
	}
	
	public void givenSystemIsUpWhenPerfActorReceivesGatherLatencyStatThenGatherFromAllLocalServices() throws InterruptedException {
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, Paths.get("config", "simple", "LunarSystem.admin.perf.refData.conf").toString());
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		try (LunarSystem tiger = LunarSystem.launch(systemName, host, port, jmxPort)){
		}
		catch (ConfigurationException e){
			
		}
	}

	public void testStopLunarSystem(){
		
		
	}
	public void testSuccessfullyBringUpAdminActorsInCluster(){
		
	}
	
	public void testThrowExceptionIfSvcIdIsFoundBeingUsedMoreThanOnce(){
		
	}
}
