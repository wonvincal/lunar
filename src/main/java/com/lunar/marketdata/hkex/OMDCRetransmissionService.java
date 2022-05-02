package com.lunar.marketdata.hkex;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.OMDPacketHeaderSbeDecoder;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * TCP full duplex - it's safe to read and write on the same socket simultaneously
 * Thread 1: Listen to a TCP socket for heartbeat and retransmission response
 * Thread 2: Send heartbeat response and retransmission request to a TCP socket
 * 
 * Note: do not use Selector, people say that produces lots of garbages
 * 
 * Implementation:
 * 1. Implement disruptor's event handler
 * 2. onEvent of ring buffer
 *    a) send heartbeat response
 *    b) send retransmission request
 *    c) start
 *       i) create socket
 *       ii) create listening thread
 *    d) stop
 * 3. listening thread
 *    a) on heartbeat, send an heartbeat response message to ring buffer
 *    b) on retransmission request, send response message to line arbitrator 
 * 4. constructor / builder
 *    a) line arbitrator
 * 5. Interface
 *    Event:
 *      command type: start, stop, send hb, send rr
 *      parameter: begin, end
 *      
 *  Let's think how can i convert a command type to a function.  switch ...
 *  
 * @author wongca
 *
 */
@SuppressWarnings("unused")
final class OMDCRetransmissionService implements EventHandler<RetransmissionCommand>, Runnable {
	private static final Logger LOG = LogManager.getLogger(OMDCRetransmissionService.class);
	private static final int LAST_NUMBER_MSG_AVAIL_PER_CHANNEL_ID = 50_000;
	private static final int MAX_SEQ_RANGE_THAT_CAN_BE_REQUESTED = 10_000;
	private static final int MAX_NUM_REQ_PER_DAY = 1_000;
	private static final int HEARTBEAT_INTERVAL_IN_SEC = 30_000;
	private static final int HEARTBEAT_RESPONSE_TIMEOUT_IN_MS = 5000;

	private final Int2ObjectHashMap<OMDCChannel> channels;
	private final RingBuffer<RetransmissionCommand> self;
	private final MessageCodec codecForReceiving;
	private final UnsafeBuffer receiveBuffer;

	public static OMDCRetransmissionService of(){
		return null;
	}
	
	OMDCRetransmissionService(Int2ObjectHashMap<OMDCChannel> channels, RingBuffer<RetransmissionCommand> self, int receivingBufferSize){
		this.channels = channels;
		this.self = self;
		this.codecForReceiving = MessageCodec.of(128);
		this.receiveBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(receivingBufferSize));
	}
	
	@Override
	public void onEvent(RetransmissionCommand event, long sequence, boolean endOfBatch) throws Exception {
		switch (event.commandType()){
		case SEND_HEARTBEAT:
			sendHeartbeat();
			break;
		case SEND_REQUEST:
			sendRequest(event.channelId(), event.beginSeq(), event.endSeq());
			break;
		case START:
			start();
			break;
		case STOP:
			stop();
			break;
		default:
			break;
		}
	}

	private Thread receiveThread;
	private void start(){
		receiveThread = new Thread(this, "omd-retrans");
		receiveThread.start();
	}
	
	private void stop(){
		receiveThread.interrupt();
	}
	
	private void sendHeartbeat(){
		// send heart beat
		// OMDHeartbeatResponse message
	}
	
	private void sendRequest(int channelId, long beginSeq, long endSeq){
		// send a request
		// get the result
		// publish it to the channel
	}

	/**
	 * listening thread
	 */
	@Override
	public void run() {
		MessageCodec codec = MessageCodec.of(128); 
		Thread thread = Thread.currentThread();
		OMDPacketHeaderSbeDecoder packetHeaderSbe = new OMDPacketHeaderSbeDecoder();
		int length = 100;
		try{
			while (!thread.isInterrupted()){
				// no header
				int offset = 0;
				packetHeaderSbe.wrap(receiveBuffer, 0, OMDPacketHeaderSbeDecoder.BLOCK_LENGTH, OMDPacketHeaderSbeDecoder.SCHEMA_VERSION);
				int seq = packetHeaderSbe.seqNum();
				int msgCount = packetHeaderSbe.msgCount();
				
				// read the messages
				// get the binary data
				int channelId = 20;
				UnsafeBuffer srcBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(100));
				this.channels.get(channelId).publish(0, msgCount, srcBuffer, offset, length, codec);
			}
			LOG.info("omdc retrans stopped receiving");			
		}
		catch (Exception e){
			LOG.error("omdc retrans stopped receiving with exception", e);
		}
	}
}
