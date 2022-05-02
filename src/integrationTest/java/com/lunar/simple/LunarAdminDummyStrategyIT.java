package com.lunar.simple;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

import org.junit.Ignore;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.LunarSystem;
import com.lunar.core.RealSystemClock;
import com.lunar.exception.ConfigurationException;
import com.lunar.core.LunarSystem.LunarSystemState;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.ValidNullMessageSink;
import com.lunar.util.ConcurrentUtil;
import com.lunar.util.ServiceTestHelper;

public class LunarAdminDummyStrategyIT {
	private static final Logger LOG = LogManager.getLogger(LunarAdminDummyStrategyIT.class);

	@Ignore
	@Test
	public void testStart() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(10, TimeUnit.SECONDS);
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

	@Ignore
	@Test
	public void testStartAndStopIndividualService() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(10, TimeUnit.SECONDS);
		
		MessageSinkRef adminSinkRef = system.adminService().messenger().self();
		
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		Messenger selfMessenger = testHelper.createMessenger(ValidNullMessageSink.of(1, ServiceType.DashboardService), "self");
		selfMessenger.sendCommand(adminSinkRef, 
				Command.of(selfMessenger.self().sinkId(), 
						selfMessenger.getNextClientKeyAndIncrement(),
						CommandType.STOP,
						Arrays.asList(Parameter.of(ParameterType.SINK_ID, 2))));
		
		ConcurrentUtil.sleep(1000);
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
