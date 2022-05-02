package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.Test;

import com.lunar.config.CommunicationConfig;
import com.lunar.core.LifecycleState;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.AssertUtil;
import com.lunar.util.TestHelper;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;

public class AeronMessageDistributionServiceTest {

	@Test
	public void testCreate(){
		TestHelper helper = TestHelper.of();
		String name = "test-aeron-dist";
		int sinkId = 3;
		ServiceType serviceType = ServiceType.ClientService;
		
		int system1Id = 1;
		int system1AdminSinkId = 1;
		int system1Port = 8001;
		int system1StreamId = 1;
		
		int system2Id = 2;
		int system2AdminSinkId = 2;
		int system2Port = 8002;
		int system2StreamId = 2;

		CommunicationConfig localConfig = createConfig(system1Id, system1Port, system1StreamId, system1AdminSinkId);
		CommunicationConfig remoteConfig = createConfig(system2Id, system2Port, system2StreamId, system2AdminSinkId);
		List<CommunicationConfig> configs = new ArrayList<CommunicationConfig>();
		configs.add(localConfig);
		configs.add(remoteConfig);
		
		MessageSinkRef self = DummyMessageSink.refOf(system1Id, sinkId, name, serviceType);
		
		@SuppressWarnings("resource")
		final MediaDriver.Context mediaContext = new MediaDriver.Context()
				.aeronDirectoryName(localConfig.aeronDir())
				.dirsDeleteOnStart(localConfig.deleteAeronDirOnStart())
				.threadingMode(ThreadingMode.DEDICATED)
				.conductorIdleStrategy(new BackoffIdleStrategy(1, 1, 1, 1))
				.receiverIdleStrategy(new NoOpIdleStrategy())
				.senderIdleStrategy(new NoOpIdleStrategy());
		try (final MediaDriver mediaDriver = MediaDriver.launch(mediaContext)){
			final Aeron.Context ctx = new Aeron.Context();
			ctx.aeronDirectoryName(mediaContext.aeronDirectoryName());
			try (final Aeron aeron = Aeron.connect(ctx)){
				AeronMessageDistributionService service = AeronMessageDistributionService.of(name, aeron, configs, helper.createMessenger(self));
				assertNotNull(service);
			}
		}
	}
	
	private static CommunicationConfig createConfig(int systemId, int port, int streamId, int adminSinkId){
		String transport = "udp";
		boolean deleteAeronDirOnStart = true;
		String hostname = "localhost";
		String aeronDir = "aeron";
		return CommunicationConfig.of(systemId, adminSinkId, transport, port, 
				streamId, aeronDir, hostname, deleteAeronDirOnStart);		
	}

	@Test
	public void testStartStop() throws InterruptedException, ExecutionException, TimeoutException{
		TestHelper helper = TestHelper.of();
		String name = "test-aeron-dist";
		int sinkId = 3;
		ServiceType serviceType = ServiceType.ClientService;
		
		int system1Id = 1;
		int system1AdminSinkId = 1;
		int system1Port = 8001;
		int system1StreamId = 1;
		
		int system2Id = 2;
		int system2AdminSinkId = 2;
		int system2Port = 8002;
		int system2StreamId = 2;

		CommunicationConfig localConfig = createConfig(system1Id, system1Port, system1StreamId, system1AdminSinkId);
		CommunicationConfig remoteConfig = createConfig(system2Id, system2Port, system2StreamId, system2AdminSinkId);
		List<CommunicationConfig> configs = new ArrayList<CommunicationConfig>();
		configs.add(localConfig);
		configs.add(remoteConfig);
		
		MessageSinkRef self = DummyMessageSink.refOf(system1Id, sinkId, name, serviceType);
		
		@SuppressWarnings("resource")
		final MediaDriver.Context mediaContext = new MediaDriver.Context()
				.aeronDirectoryName(localConfig.aeronDir())
				.dirsDeleteOnStart(localConfig.deleteAeronDirOnStart())
				.threadingMode(ThreadingMode.DEDICATED)
				.conductorIdleStrategy(new BackoffIdleStrategy(1, 1, 1, 1))
				.receiverIdleStrategy(new NoOpIdleStrategy())
				.senderIdleStrategy(new NoOpIdleStrategy());
		try (final MediaDriver mediaDriver = MediaDriver.launch(mediaContext)){
			final Aeron.Context ctx = new Aeron.Context();
			ctx.aeronDirectoryName(mediaContext.aeronDirectoryName());
			try (final Aeron aeron = Aeron.connect(ctx)){
				AeronMessageDistributionService service = AeronMessageDistributionService.of(name, aeron, configs, helper.createMessenger(self));
				assertNotNull(service);
				CompletableFuture<LifecycleState> active = service.active();
				AssertUtil.assertTrueWithinPeriod("check running", () -> { return service.running(); }, TimeUnit.SECONDS.toNanos(1l));
				assertEquals(LifecycleState.ACTIVE, active.get(1, TimeUnit.SECONDS));
				assertEquals(LifecycleState.RESET, service.reset().get(1, TimeUnit.SECONDS));
				CompletableFuture<LifecycleState> stop = service.stop();
				assertEquals(LifecycleState.STOPPED, stop.get(1, TimeUnit.SECONDS));
				AssertUtil.assertTrueWithinPeriod("check stop", () -> { return !service.running(); }, TimeUnit.SECONDS.toNanos(1l));
			}
		}
	}
}
