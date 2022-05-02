package com.lunar.fsm.channelbuffer;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lunar.exception.OutsideOfBufferRangeException;
import com.lunar.exception.StateTransitionException;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.service.ServiceConstant;
import com.lunar.util.SequenceBasedObjectCircularBuffer;

public class ChannelBufferContext {
	private static final Logger LOG = LogManager.getLogger(ChannelBufferContext.class);
	private final String name;
	private final int messageBufferCapacity;
	private final int snapshotBufferCapacity;
	private State state;
	private ChannelMissingMessageRequester messageRequester = ChannelMissingMessageRequester.NULL_INSTANCE;
	
	private final EventHandler<ChannelEvent> eventHandler;
	private long expectedNextChannelSeq = ServiceConstant.NULL_SEQUENCE;
	private long expectedNextSnapshotSeq = ServiceConstant.NULL_SEQUENCE;
	private long sumOfSnapshotSeq = 0;
	private int expectedSenderSinkId = ServiceConstant.SERVICE_ID_NOT_APPLICABLE;

	private long channelSeqOfSnapshot = ServiceConstant.NULL_SEQUENCE;
	private long channelSeqOfCompletedSnapshot = ServiceConstant.NULL_SEQUENCE;
	private SequenceBasedObjectCircularBuffer<ChannelEvent> messageBuffer;
	private SequenceBasedObjectCircularBuffer<ChannelEvent> snapshotBuffer;
	private boolean ready;
	
	public static EventFactory<ChannelEvent> CHANNEL_EVENT_FACTORY = new EventFactory<ChannelEvent>() {
		@Override
		public ChannelEvent newInstance() {
			return new ChannelEvent(true);
		}
	};

	public static ChannelBufferContext of(String name, int id, int messageBufferCapacity, int snapshotBufferCapacity, EventHandler<ChannelEvent> eventHandler){
		return new ChannelBufferContext(name, id, messageBufferCapacity, snapshotBufferCapacity, eventHandler);
	}

	ChannelBufferContext(String name, int id, int messageBufferCapacity, int snapshotBufferCapacity, EventHandler<ChannelEvent> eventHandler){
		this.name = name + "-" + id;
		this.messageBufferCapacity = messageBufferCapacity;
		this.snapshotBufferCapacity = snapshotBufferCapacity;
		this.eventHandler = eventHandler;
		this.ready = false;
		this.state = States.INITIALIZATION; 
	}
	
	public ChannelBufferContext messageRequester(ChannelMissingMessageRequester messageRequester){
		this.messageRequester = messageRequester;
		return this;
	}

	public State state(){ return state;}

	public ChannelBufferContext state(State state){
		this.state = state;
		return this;
	}

	/**
	 * This method currently creates the underlying two {@link SequenceBasedObjectCircularBuffer} that
	 * are being used as buffer for messages and snapshots.  The memory allocation takes significant
	 * amount of time and the client of this class should call this method before attempting
	 * to send any orders.
	 *  
	 */
	public void init(boolean shouldBeReady){
		if (!this.ready){
			messageBuffer = SequenceBasedObjectCircularBuffer.of(messageBufferCapacity, CHANNEL_EVENT_FACTORY);
			snapshotBuffer = SequenceBasedObjectCircularBuffer.of(snapshotBufferCapacity, CHANNEL_EVENT_FACTORY);
			this.ready = true;
			
			if (shouldBeReady){
				LOG.warn("ChannelBufferContext is expected to be ready, but it is not. [name:{}]", this.name);
			}
		}
		clear();
	}
	
	public boolean isReady(){
		return ready;
	}
	
	/**
	 * Clear to up and include input sequence 
	 * @param sequence
	 * @throws OutsideOfBufferRangeException 
	 */
	public void clearUpTo(long sequence) throws OutsideOfBufferRangeException{
		messageBuffer.clearUpTo(sequence);
		if (channelSeqOfSnapshot <= sequence){
			snapshotBuffer.clearAndStartSequenceAt(ServiceConstant.START_RESPONSE_SEQUENCE);
		}
	}

	public void expectedNextSeq(long value){
		this.expectedNextChannelSeq = value;
	}
	
	public long expectedNextSeq(){
		return expectedNextChannelSeq;
	}

	public void expectedNextSnapshotSeq(long value){
		this.expectedNextSnapshotSeq = value;
	}
	
	public long expectedNextSnapshotSeq(){
		return expectedNextSnapshotSeq;
	}

	public void onMissingMessageRequestFailure(ResultType resultType){
		this.state.onMissingMessageRequestFailure(this, resultType);
	}
	
	/**
	 * Pass message into state machine
	 * @param channelId
	 * @param channelSeq
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param templateId
	 */
	public final void onMessage(int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action){
		try {
			if (ServiceConstant.SHOULD_SAFETY_CHECK){
				byte senderSinkId = header.senderSinkId();
				if (expectedSenderSinkId == ServiceConstant.SERVICE_ID_NOT_APPLICABLE){
					expectedSenderSinkId = senderSinkId; 
				}
				else if (expectedSenderSinkId != senderSinkId){
					throw new IllegalStateException("Received message from an unexpected sender [senderSinkId:" + senderSinkId + ", expectedSinkId:" + expectedSenderSinkId + "]");
				}
			}
			this.state.onMessage(this, channelId, channelSeq, buffer, offset, header, templateId, action).proceed(this);
		} 
		catch (StateTransitionException e) {
			LOG.error("Caught StateTransitionException when processign message [channelId:" + channelId + ", channelSeq:" + channelSeq + "]", e);
		}
	}

	/**
	 * Pass message into state machine
	 * @param channelId
	 * @param channelSeq
	 * @param isLast
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param templateId
	 */
	public final void onSequentialSnapshot(int channelId, long channelSeq, int snapshotSeq, BooleanType isLast, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action){
		try {
			byte senderSinkId = header.senderSinkId();
			if (ServiceConstant.SHOULD_SAFETY_CHECK){
				if (expectedSenderSinkId == ServiceConstant.SERVICE_ID_NOT_APPLICABLE){
					expectedSenderSinkId = senderSinkId; 
				}
				else if (expectedSenderSinkId != senderSinkId){
					throw new IllegalStateException("Received message from an unexpected sender [senderSinkId:" + senderSinkId + ", expectedSinkId:" + expectedSenderSinkId + "]");
				}
			}
			this.state.onSequentialSnapshot(this, channelId, channelSeq, snapshotSeq, isLast, buffer, offset, header, templateId, action).proceed(this);
		}
		catch (StateTransitionException e) {
			LOG.error("Caught StateTransitionException when processign message [channelId:" + channelId + ", channelSeq:" + channelSeq + "]", e);
		}
	}

	/**
	 * Pass message into state machine
	 * @param channelId
	 * @param snapshotSeq
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param templateId
	 */
	public final void onIndividualSnapshot(int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action){
		try {
			byte senderSinkId = header.senderSinkId();
			if (ServiceConstant.SHOULD_SAFETY_CHECK){
				if (expectedSenderSinkId == ServiceConstant.SERVICE_ID_NOT_APPLICABLE){
					expectedSenderSinkId = senderSinkId; 
				}
				else if (expectedSenderSinkId != senderSinkId){
					throw new IllegalStateException("Received message from an unexpected sender [senderSinkId:" + senderSinkId + ", expectedSinkId:" + expectedSenderSinkId + "]");
				}
			}
			this.state.onIndividualSnapshot(this, channelId, channelSeq, buffer, offset, header, templateId, action).proceed(this);
		} 
		catch (StateTransitionException e) {
			LOG.error("Caught StateTransitionException when processign message [channelId:" + channelId + ", channelSeq:" + channelSeq + "]", e);
		}
	}

	void requestMessage(int channelId, long expectedNextSeq, long fromSeq, long toSeq, int senderSinkId) {
		messageRequester.request(this, channelId, expectedNextSeq, fromSeq, toSeq, senderSinkId);
	}

	void storeMessageAndSetMissingSequence(int channelId, long missingChannelSeq, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId) throws OutsideOfBufferRangeException{
		messageBuffer.startSequence(missingChannelSeq);
		storeMessage(channelId, channelSeq, buffer, offset, header, templateId);
	}
	void storeMessage(int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId) throws OutsideOfBufferRangeException{
		messageBuffer.claim(channelSeq).merge(channelId,
				channelSeq,
				ChannelEvent.NULL_RESPONNSE_SEQ,
				templateId,
				buffer,
				offset,
				header.encodedLength() + header.payloadLength());
	}
	
	
	SequenceBasedObjectCircularBuffer<ChannelEvent> messageBuffer(){
		return messageBuffer;
	}
	
	/**
	 * Sum of 1..Last = Number of items * (1 + Last) / 2
	 * @param lastSnapshotSeq
	 * @return
	 */
	private static long sumOfSequence(int lastSnapshotSeq){
		return (lastSnapshotSeq * (1  + lastSnapshotSeq)) >> 1;
	}

	/**
	 * 
	 * @param channelId
	 * @param channelSeq
	 * @param snapshotSeq This is expected to be the response sequence that starts from 1
	 * @param isLast
	 * @param buffer
	 * @param offset
	 * @param length
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param templateId
	 * @throws OutsideOfBufferRangeException 
	 */
	void storeSnapshot(int channelId, long channelSeq, int snapshotSeq, BooleanType isLast, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId) throws OutsideOfBufferRangeException{
		if (channelSeq > channelSeqOfSnapshot){
			// Clear the buffer
			snapshotBuffer.clearAndStartSequenceAt(ServiceConstant.START_RESPONSE_SEQUENCE);
			channelSeqOfSnapshot = channelSeq;
			sumOfSnapshotSeq = 0;
		}
		sumOfSnapshotSeq += snapshotSeq;
		// Note: we claim by snapshotSeq, but we store details in the same way as storeMessage
		ChannelEvent event = snapshotBuffer.claim(snapshotSeq);
		event.merge(channelId,
				channelSeq,
				snapshotSeq,
				templateId,
				buffer,
				offset,
				header.encodedLength() + header.payloadLength());
		if (isLast == BooleanType.TRUE && sumOfSnapshotSeq == sumOfSequence(snapshotSeq)){
			channelSeqOfCompletedSnapshot = channelSeq;
		}
	}

	/**
	 * Flush all messages sequentially -
	 * 1) flush all snapshot
	 * 2) flush all messages
	 * @return true if all messages have been flushed, otherwise false
	 * @throws Exception 
	 * @throws Throwable 
	 */
	public boolean flush() throws Exception {
		// Flush snapshots if applicable
		if (channelSeqOfCompletedSnapshot >= expectedNextChannelSeq){
			snapshotBuffer.flushTillNull(eventHandler);
			snapshotBuffer.clearAndStartSequenceAt(ServiceConstant.START_RESPONSE_SEQUENCE);
			expectedNextChannelSeq = channelSeqOfCompletedSnapshot + 1;
			channelSeqOfCompletedSnapshot = ServiceConstant.NULL_SEQUENCE;
		}
		
		// Flush messages
		expectedNextChannelSeq = messageBuffer.flushTillNull(eventHandler);
		return messageBuffer.isEmpty();
	}
	
	public long channelSeqOfCompletedSnapshot(){
		return channelSeqOfCompletedSnapshot;
	}
	
	private void clear(){
		this.expectedNextChannelSeq = ServiceConstant.NULL_SEQUENCE;
		this.expectedNextSnapshotSeq = ServiceConstant.NULL_SEQUENCE;
		this.channelSeqOfSnapshot = ServiceConstant.NULL_SEQUENCE;
		this.channelSeqOfCompletedSnapshot = ServiceConstant.NULL_SEQUENCE;
		if (messageBuffer.sequence() != SequenceBasedObjectCircularBuffer.NULL){
			// We do not know the next expected sequence number for buffer
			messageBuffer.clearAndStartSequenceAt(0);
		}
		if (snapshotBuffer.sequence() != SequenceBasedObjectCircularBuffer.NULL){
			snapshotBuffer.clearAndStartSequenceAt(ServiceConstant.START_RESPONSE_SEQUENCE);
		}
	}
}
