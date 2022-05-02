package com.lunar.service;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class SimpleSocket {
	public static interface BinaryMessageHandler {
		void onMessage(ByteBuffer buffer);
		
	}
	public static interface TextMessageHandler {
		void onMessage(String text);
		
	}
	public static Supplier<Boolean> NULL_LATCH_RESULT_SUPPLIER = new Supplier<Boolean>() {
		@Override
		public Boolean get() {
			return true;
		}
	};
	public static BinaryMessageHandler NULL_BINARY_HANDLER = new BinaryMessageHandler() {
		@Override
		public void onMessage(ByteBuffer buffer) {
		}
	};
	public static TextMessageHandler NULL_TEXT_HANDLER = new TextMessageHandler() {
		@Override
		public void onMessage(String buffer) {
		}
	};
	private static final Logger LOG = LogManager.getLogger(SimpleSocket.class);
	
	private Session session;
	private AtomicInteger numWriteFailed;
	private CountDownLatch latch;
	private ConcurrentLinkedQueue<ByteBuffer> receivedBinaryMessages;
	private ConcurrentLinkedQueue<String> receivedTextMessages;
	private BinaryMessageHandler binaryMessageHandler;
	private TextMessageHandler textMessageHandler;
	private Supplier<Boolean> latchResultSupplier;
	
	public static SimpleSocket of(){
		return new SimpleSocket(NULL_LATCH_RESULT_SUPPLIER, NULL_BINARY_HANDLER, NULL_TEXT_HANDLER);
	}
	
    public SimpleSocket(Supplier<Boolean> latchResultSupplier, BinaryMessageHandler binaryMessageHandler, TextMessageHandler textMessageHandler) {
    	this.latchResultSupplier = latchResultSupplier;
    	this.numWriteFailed = new AtomicInteger(0);
    	this.latch = new CountDownLatch(1);
    	this.receivedBinaryMessages = new ConcurrentLinkedQueue<>();
    	this.receivedTextMessages = new ConcurrentLinkedQueue<>();
    	this.binaryMessageHandler = binaryMessageHandler;
    	this.textMessageHandler = textMessageHandler;
    }

	public SimpleSocket binaryMessageHandler(BinaryMessageHandler binaryMessageHandler){
		this.binaryMessageHandler = binaryMessageHandler;
		return this;
	}
	
	public SimpleSocket textMessageHandler(TextMessageHandler textMessageHandler){
		this.textMessageHandler = textMessageHandler;
		return this;
	}
	
	public SimpleSocket latchResultSupplier(Supplier<Boolean> latchResultSupplier){
		this.latchResultSupplier = latchResultSupplier;
		return this;
	}
    
    public CountDownLatch latch(){
    	return latch;
    }
    
    public CountDownLatch resetLatch(){
    	this.latch = new CountDownLatch(1);
    	return this.latch;
    }

    public ConcurrentLinkedQueue<ByteBuffer> receivedBinaryMessages(){
    	return this.receivedBinaryMessages;
    }
    
    public ConcurrentLinkedQueue<String> receivedTextMessages(){
    	return this.receivedTextMessages;
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        System.out.printf("SimpleSocket - Connection closed: %d - %s\n", statusCode, reason);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.printf("SimpleSocket - Connected: %s\n", session);
        this.session = session;
   }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        this.receivedTextMessages.add(msg);
        textMessageHandler.onMessage(msg);
        if (latchResultSupplier.get()){
        	latch.countDown();
        }
    }

    @OnWebSocketMessage
    public void onMessage(byte[] buf, int offset, int length){
    	ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(buf, offset, offset + length));
    	this.receivedBinaryMessages.add(buffer);
    	binaryMessageHandler.onMessage(buffer);
        if (latchResultSupplier.get()){
        	latch.countDown();
        }
    }
    
    public void send(String message){
    	try {
    		session.getRemote().sendString(message, new WriteCallback() {
				
				@Override
				public void writeSuccess() {
					LOG.info("writeSuccess for text");
				}
				
				@Override
				public void writeFailed(Throwable x) {
					LOG.error("writeFailed when trying to send text over", x);
					numWriteFailed.incrementAndGet();
				}
			});
    	}
    	catch (Throwable t){
    		t.printStackTrace();
    	}    	
    }
    
    public void send(ByteBuffer buffer){
    	try {
    		session.getRemote().sendBytes(buffer, new WriteCallback() {
				
				@Override
				public void writeSuccess() {
					LOG.info("writeSuccess for byteBuffer");
				}
				
				@Override
				public void writeFailed(Throwable x) {
					LOG.error("writeFailed when trying to send byteBuffer over", x);
					numWriteFailed.incrementAndGet();
				}
			});
    	}
    	catch (Throwable t){
    		t.printStackTrace();
    	}
    }
    
    public void closeSession(){
    	session.close(StatusCode.NORMAL, "Client initiated close");
    }
    
    public int numWriteFailed(){
    	return this.numWriteFailed.get();
    }
}
