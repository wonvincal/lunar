package com.lunar.it;

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
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.io.fbs.CommandAckFbs;
import com.lunar.message.io.fbs.CommandAckTypeFbs;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessageFbsEncoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.ResponseFbs;
import com.lunar.message.io.fbs.ServiceStatusFbs;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.service.ServiceConstant;
import com.lunar.service.SimpleSocket;
import com.lunar.simple.LunarAdminDummyServicesIT;
import com.lunar.util.AssertUtil;

public class LunarServicesIT {
	private static final Logger LOG = LogManager.getLogger(LunarAdminDummyServicesIT.class);
	
	@Test
	@Ignore
	public void testStart() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "lunar.services.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, systemClock, readyFuture);
		readyFuture.get(20, TimeUnit.SECONDS);
		system.close();
		
		AssertUtil.assertTrueWithinPeriod("System is not in STOPPED state", () ->{
			return system.state() == LunarSystemState.STOPPED;
		}, TimeUnit.SECONDS.toNanos(5l));
		
		AssertUtil.assertTrueWithinPeriod("Cannot shutdown system", () ->{
			return system.isCompletelyShutdown();
		}, TimeUnit.SECONDS.toNanos(5l));
	}

	@Test
	public void testStartAndStop() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "lunar.services.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, systemClock, readyFuture);
		readyFuture.get(30, TimeUnit.SECONDS);
		
		// Create command
		final int anySinkId = 1;
		int refClientKey = 5656565;
		CommandType refCommandType = CommandType.STOP;
		final BooleanType refToSend = BooleanType.FALSE;
		final int rdsSinkId = 3;
		ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
				Parameter.of(ParameterType.SINK_ID, rdsSinkId));
		Command command = Command.of(anySinkId,
				 refClientKey,
				 refCommandType,
				 refParameters,
				 refToSend);
		MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
		ByteBuffer suppliedBuffer = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE); 
		ByteBuffer buffer = messageFbsEncoder.encodeCommand(suppliedBuffer, command);

		// Create socket
		final AtomicBoolean receivedAck = new AtomicBoolean(false);
		SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
			@Override
			public Boolean get() {
				return receivedAck.get();
			}
		}, new SimpleSocket.BinaryMessageHandler() {
			
			@Override
			public void onMessage(ByteBuffer buffer) {
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(buffer)){
				case MessagePayloadFbs.CommandAckFbs:
					CommandAckFbs commandAckFbs = decoder.asCommandAckFbs();
					LOG.debug("Received command ack");
					if (commandAckFbs.ackType() == CommandAckTypeFbs.OK){
						receivedAck.set(true);
					}
					break;
				}
			}
		}, SimpleSocket.NULL_TEXT_HANDLER);

		// Start web client and send request to web service
		String wsPath = "ws";
		int wsPort = 1234;
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
		WebSocketClient client = new WebSocketClient();
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
			socket.send(buffer.slice());
			socket.latch().await(10, TimeUnit.SECONDS);
		}
		catch (Throwable t){
			LOG.error("Caught throwable", t);
			throw new AssertionError("Caught throwable", t);
		}
		
		// Send another command to start service
		refClientKey++;
		refCommandType = CommandType.START;
		command = Command.of(anySinkId,
				 refClientKey,
				 refCommandType,
				 refParameters,
				 refToSend);
		buffer = messageFbsEncoder.encodeCommand(suppliedBuffer, command);
		socket.send(buffer.slice());
		
        try {
        	LOG.info("Stopping web client");
            client.stop();
        } 
        catch (Exception e) {
            e.printStackTrace();
        }

		system.close();
		AssertUtil.assertTrueWithinPeriod("Cannot shutdown system", () ->{
			return system.isCompletelyShutdown();
		}, TimeUnit.SECONDS.toNanos(5l));
	}
	
	@Ignore
	@Test
	public void testGetResponse() throws InterruptedException, ExecutionException, TimeoutException, UnsupportedEncodingException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.all.services.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(10, TimeUnit.SECONDS);
		
		// Create request 
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;
		ImmutableList<Parameter> refParameters = Parameters.listOf(Parameter.of(TemplateType.RESPONSE));
//		ImmutableList<Parameter> refParameters = Parameters.listOf(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.RESPONSE),
//				Parameter.of(ParameterType.PARAM_VALUE, 5000l));
		Request request = Request.of(anySinkId,
				 refRequestType,
				 refParameters).clientKey(refClientKey);
		MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
		ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);

		// Create socket
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

		// Start web client and send request to web service
		String wsPath = "ws";
		int wsPort = 1234;
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
		WebSocketClient client = new WebSocketClient();
		boolean latchResult = false;
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
			socket.send(buffer.slice());
			latchResult = socket.latch().await(20, TimeUnit.SECONDS);
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
		int numResponse = 0;
		int numOther = 0;
		int numTrade = 0;
		int numOrder = 0;
		MessageFbsDecoder decoder = MessageFbsDecoder.of();
		for (ByteBuffer message : socket.receivedBinaryMessages()){
			switch (decoder.init(message)){
			case MessagePayloadFbs.ResponseFbs:
				ResponseFbs responseFbs = decoder.asResponseFbs();
				switch (responseFbs.messagePayloadType()){
				case MessagePayloadFbs.OrderFbs:
					LOG.debug("Received embedded order");
					numOrder++;
					break;
				case MessagePayloadFbs.TradeFbs:
					LOG.debug("Received embedded trade");
					numTrade++;
					break;
				case MessagePayloadFbs.NONE:
					numResponse++;
					break;
				default:
					numOther++;
					break;
				}
				break;
			default:
				LOG.error("Got unexpected message back");
				numOther++;
				break;
			}
		}
		LOG.info("Received messages [numResponse:{}, numOther:{}, numOrder:{}, numTrade:{}]", numResponse, numOther, numOrder, numTrade);
		assertEquals(0, numOther);
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
	public void testStartAndGetStatus() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, URLDecoder.decode(this.getClass().getClassLoader().getResource(Paths.get("config", "it", "simple", "lunar.all.services.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		RealSystemClock systemClock = new RealSystemClock();
		LunarSystem system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture);
		readyFuture.get(10, TimeUnit.SECONDS);
		
		// Create request 
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;
		ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(
				Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SERVICE_STATUS.value()));
		Request request = Request.of(anySinkId,
				 refRequestType,
				 refParameters).clientKey(refClientKey);
		MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
		ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);

		// Create socket
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

		// Start web client and send request to web service
		String wsPath = "ws";
		int wsPort = 1234;
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
		WebSocketClient client = new WebSocketClient();
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
		int numResponse = 0;
		int numOther = 0;
		MessageFbsDecoder decoder = MessageFbsDecoder.of();
		ServiceStatusFbs embeddedServiceStatusFbs = new ServiceStatusFbs();
		for (ByteBuffer message : socket.receivedBinaryMessages()){
			switch (decoder.init(message)){
			case MessagePayloadFbs.ResponseFbs:
				ResponseFbs responseFbs = decoder.asResponseFbs();
				switch (responseFbs.messagePayloadType()){
				case MessagePayloadFbs.ServiceStatusFbs:
					responseFbs.messagePayload(embeddedServiceStatusFbs);
					LOG.debug("Received embedded service status [systemId:{}, sinkId:{}, service:{}, status:{}, modified:{}]",
							embeddedServiceStatusFbs.systemId(),
							embeddedServiceStatusFbs.sinkId(),
							ServiceType.get(embeddedServiceStatusFbs.serviceType()).name(),
							ServiceStatusType.get(embeddedServiceStatusFbs.serviceStatusType()).name(),
							embeddedServiceStatusFbs.modifyTimeAtOrigin());
					numResponse++;
					break;
				case MessagePayloadFbs.NONE:
					numResponse++;
					break;
				default:
					numOther++;
					break;
				}
				break;
			default:
				LOG.error("Got unexpected message back");
				numOther++;
				break;
			}
		}
		LOG.info("Received messages [numResponse:{}, numOther:{}]", numResponse, numOther);
		assertEquals(0, numOther);
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
