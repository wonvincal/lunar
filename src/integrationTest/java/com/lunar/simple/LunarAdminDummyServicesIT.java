package com.lunar.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.lunar.core.LunarSystem;
import com.lunar.core.LunarSystem.LunarSystemState;
import com.lunar.core.RealSystemClock;
import com.lunar.exception.ConfigurationException;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessageFbsEncoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.ResponseFbs;
import com.lunar.message.io.fbs.SecurityFbs;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.service.SimpleSocket;

public class LunarAdminDummyServicesIT {
	private static final Logger LOG = LogManager.getLogger(LunarAdminDummyServicesIT.class);
	
	@Ignore
	@Test
	public void testStart() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.mds.omes.rds.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
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
	public void testStartDashboard() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.mds.omes.rds.dashboard.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
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
	public void testStartRdsDashboard() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.mds.omes.rds.dashboard.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
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
	public void testStartRdsDashboardWithWebClientTesting() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.mds.omes.rds.dashboard.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(10, TimeUnit.SECONDS);
		
		String wsPath = "ws";
		int wsPort = 8123;
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
		WebSocketClient client = new WebSocketClient();
		final AtomicBoolean receivedOKResponse = new AtomicBoolean(false);
		
		SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
			@Override
			public Boolean get() {
				return receivedOKResponse.get();
			}
		}, new SimpleSocket.BinaryMessageHandler() {
			
			@Override
			public void onMessage(ByteBuffer buffer) {
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(buffer)){
				case MessagePayloadFbs.ResponseFbs:
					ResponseFbs responseFbs = decoder.asResponseFbs();
					if (responseFbs.isLast()){
						receivedOKResponse.set(true);
					}
					break;
				}
			}
		}, new SimpleSocket.TextMessageHandler() {
			
			@Override
			public void onMessage(String text) {
				
			}
		});
		
		ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
				Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK"));
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;

		Request request = Request.of(anySinkId,
				 refRequestType,
				 refParameters).clientKey(refClientKey);

		MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
		ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);
		
		// When
		boolean latchResult = false;
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
			socket.send(buffer.slice());
			latchResult = socket.latch().await(10, TimeUnit.SECONDS);
		}
		catch (Throwable t){
			LOG.error("Caught throwable", t);
			throw new AssertionError("Caught throwable", t);
		}
		finally {
            try {
            	LOG.info("Stopping web client");
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
		}
		
		LOG.info("Received number of binary messages [count:{}]", socket.receivedBinaryMessages().size());
		int numSecurity = 0;
		int numResponse = 0;
		int numOther = 0;
		MessageFbsDecoder decoder = MessageFbsDecoder.of();
		SecurityFbs embeddedSecurityFbs = new SecurityFbs();
		for (ByteBuffer message : socket.receivedBinaryMessages()){
			switch (decoder.init(message)){
			case MessagePayloadFbs.SecurityFbs:
				SecurityFbs securityFbs = decoder.asSecurityFbs();
				LOG.debug("Received security [sid:{}, undSid:{}, maturity:{}, issuerSid:{}]",
						securityFbs.sid(),
						securityFbs.undSid(),
						securityFbs.maturity(),
						securityFbs.issuerSid());
				numSecurity++;
				break;
			case MessagePayloadFbs.ResponseFbs:
				ResponseFbs responseFbs = decoder.asResponseFbs();
				switch (responseFbs.messagePayloadType()){
				case MessagePayloadFbs.SecurityFbs:
					responseFbs.messagePayload(embeddedSecurityFbs);
					LOG.debug("Received embedded security [sid:{}, undSid:{}, maturity:{}, issuerSid:{}, code:{}, securityType:{}]",
							embeddedSecurityFbs.sid(),
							embeddedSecurityFbs.undSid(),
							embeddedSecurityFbs.maturity(),
							embeddedSecurityFbs.issuerSid(),
							embeddedSecurityFbs.code(),
							SecurityType.get(embeddedSecurityFbs.securityType()).name());
					break;
				}
				numResponse++;
				break;
			default:
				LOG.error("Got unexpected message back");
				numOther++;
				break;
			}
		}
		LOG.info("Received messages [numSecurity:{}, numResponse:{}, numOther:{}]", numSecurity, numResponse, numOther);		
		assertTrue("Did not receive expected messages or number of messages", latchResult);
		
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
	public void testStartRdsDashboardWithWebClientTestingMarketData() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.admin.dummy.strat.mds.omes.rds.dashboard.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(10, TimeUnit.SECONDS);
		
		String wsPath = "ws";
		int wsPort = 8123;
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
		WebSocketClient client = new WebSocketClient();
		final AtomicInteger expectedMessages = new AtomicInteger(100);
		
		SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
			@Override
			public Boolean get() {
				return expectedMessages.get() == 0;
			}
		}, new SimpleSocket.BinaryMessageHandler() {
			
			@Override
			public void onMessage(ByteBuffer buffer) {
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(buffer)){
				case MessagePayloadFbs.AggregateOrderBookUpdateFbs:
					expectedMessages.decrementAndGet();
					LOG.debug("Current remaining count: {}", expectedMessages.get());
					break;
				}
			}
		}, new SimpleSocket.TextMessageHandler() {
			
			@Override
			public void onMessage(String text) {
				
			}
		});
		
		ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
				Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ORDERBOOK_SNAPSHOT.value()));
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.SUBSCRIBE;

		Request request = Request.of(anySinkId,
				 refRequestType,
				 refParameters).clientKey(refClientKey);

		MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
		ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);
		
		// When
		boolean latchResult = false;
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
			socket.send(buffer.slice());
			latchResult = socket.latch().await(100, TimeUnit.SECONDS);
		}
		catch (Throwable t){
			LOG.error("Caught throwable", t);
			throw new AssertionError("Caught throwable", t);
		}
		finally {
            try {
            	LOG.info("Stopping web client");
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
		}
		
		LOG.info("Received number of binary messages [count:{}]", socket.receivedBinaryMessages().size());
		int numSnapshot = 0;
		int numResponse = 0;
		int numOther = 0;
		MessageFbsDecoder decoder = MessageFbsDecoder.of();
		for (ByteBuffer message : socket.receivedBinaryMessages()){
			switch (decoder.init(message)){
			case MessagePayloadFbs.AggregateOrderBookUpdateFbs:
				numSnapshot++;
				break;
			case MessagePayloadFbs.ResponseFbs:
				numResponse++;
				break;
			default:
				LOG.error("Got unexpected message back");
				numOther++;
				break;
			}
		}
		LOG.info("Received messages [numSnapshot:{}, numResponse:{}, numOther:{}]", numSnapshot, numResponse, numOther);
		assertTrue("Did not receive expected messages or number of messages", latchResult);
		
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
