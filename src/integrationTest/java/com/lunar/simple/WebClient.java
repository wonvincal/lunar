package com.lunar.simple;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
import com.lunar.message.io.fbs.ResultTypeFbs;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;
import com.lunar.service.SimpleSocket;

public class WebClient {
	private static final Logger LOG = LogManager.getLogger(WebClient.class);
	private final AtomicInteger clientKeySeq = new AtomicInteger(900000);
	private final int webClientSinkId = 12;
	private final MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
	private final WebSocketClient client = new WebSocketClient();
	private final SimpleSocket socket = SimpleSocket.of();
	private final ByteBuffer buffer = ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE);
	private final MessageSinkRef owner = MessageSinkRef.of(DummyMessageSink.of(webClientSinkId, "client", ServiceType.ClientService));

	WebClient(){}
	
	public void sendRequest(String action, RequestType requestType, Parameter... parameters) throws InterruptedException{
		final int refClientKey = clientKeySeq.getAndIncrement();
		Request request = Request.of(webClientSinkId,
				requestType,
				Parameters.listOf(parameters)).clientKey(refClientKey);
		messageFbsEncoder.encodeRequest(buffer, request);
		socket.resetLatch();
		final AtomicBoolean receivedExpectedUpdateOK = new AtomicBoolean(false);
		socket.binaryMessageHandler((b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			switch (decoder.init(b)){
			case MessagePayloadFbs.ResponseFbs:
				ResponseFbs responseFbs = decoder.asResponseFbs();
				if (responseFbs.clientKey() == refClientKey && responseFbs.resultType() == ResultTypeFbs.OK){
					receivedExpectedUpdateOK.set(true);
					LOG.debug("Completed {} successfully", action);
				}
				break;
			}
		}).latchResultSupplier(() -> receivedExpectedUpdateOK.get());
		LOG.debug("Sent request for {}", action);
		
		socket.send(buffer.slice());
		boolean latchResult = socket.latch().await(2, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to " + action + " within time limit");
		}		
	}
	
	public void sendCommand(String action, CommandType commandType, Parameter... parameters) throws InterruptedException{
		final int refCommandClientKey = clientKeySeq.getAndIncrement();
		Command command = Command.of(webClientSinkId, 
				refCommandClientKey, 
				commandType, 
				Parameters.listOf(parameters));
		messageFbsEncoder.encodeCommand(buffer, command);
		socket.resetLatch();
		final AtomicBoolean receivedExpectedCommandAck = new AtomicBoolean(false);
		socket.binaryMessageHandler((b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			byte payloadType = decoder.init(b);
			switch (payloadType){
			case MessagePayloadFbs.CommandAckFbs:
				CommandAckFbs commandAckFbs = decoder.asCommandAckFbs();
				if (commandAckFbs.clientKey() == refCommandClientKey && 
						commandAckFbs.ackType() == CommandAckTypeFbs.OK){
					receivedExpectedCommandAck.set(true);
					LOG.debug("Completed {} successfully", action);
					return;
				}
				LOG.error("Got command nack message [commandAckType:{}]", CommandAckType.get(commandAckFbs.ackType()));
				break;
			default:
				LOG.error("Got unexpected message [payloadType:{}]", MessagePayloadFbs.name(payloadType));
				return;
			}
		}).latchResultSupplier(() -> receivedExpectedCommandAck.get());
		LOG.debug("Sent command to {}", action);
		
		socket.send(buffer.slice());
		boolean latchResult = socket.latch().await(2000, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to " + action + " within time limit");
		}
	}

	public void start(){
		// Start web client and send request to web service
		String wsPath = "ws";
		int wsPort = 1234;
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath; 
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
		}
		catch (Throwable t){
			LOG.error("Caught throwable", t);
			throw new AssertionError("Caught throwable", t);
		}
	}
	
	public void stop(){
        try {
        	LOG.info("Stopping web client");
            client.stop();
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public AtomicInteger clientKeySeq(){
		return clientKeySeq;
	}
	
	public MessageSinkRef owner(){
		return owner;
	}
	
	public MessageFbsEncoder messageFbsEncoder(){
		return messageFbsEncoder;
	}
	
	public ByteBuffer buffer(){
		return buffer; 
	}

	public SimpleSocket socket(){
		return socket;
	}
	
	public WebSocketClient client(){
		return client;
	}

	public int sinkId(){
		return webClientSinkId;
	}
}
