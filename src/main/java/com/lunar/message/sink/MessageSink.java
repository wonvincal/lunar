package com.lunar.message.sink;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.SinkInfo;
import com.lunar.message.io.sbe.ServiceType;

import org.agrona.DirectBuffer;

/**
 * 
 * @author Calvin
 *
 */
public interface MessageSink {
	public static final long OK = 0;
	public static final long INSUFFICIENT_SPACE = -1;
	public static final long FAILURE = -2;
	public static final long LENGTH_EXCEEDS_MESSAGE_SIZE = -3;
	public static final long LENGTH_EXCEEDS_FRAME_SIZE = -4;
	public static final long EXCEEDS_MAX_SUBSCRIBER_SIZE = -5;
	
	public static String getSendResultMessage(long value){
		switch ((int)value){
		case (int)MessageSink.OK:
			return "OK";
		case (int)MessageSink.INSUFFICIENT_SPACE:
			return "INSUFFICIENT_SPACE";
		case (int)MessageSink.FAILURE:
			return "FAILURE";
		case (int)MessageSink.LENGTH_EXCEEDS_MESSAGE_SIZE:
			return "LENGTH_EXCEEDS_MESSAGE_SIZE";
		case (int)MessageSink.LENGTH_EXCEEDS_FRAME_SIZE:
			return "LENGTH_EXCEEDS_FRAME_SIZE";
		case (int)MessageSink.EXCEEDS_MAX_SUBSCRIBER_SIZE:
			return "EXCEEDS_MAX_SUBSCRIBER_SIZE";
		default:
			return "UNKNOWN_RESULT";
		}
	}
	
	public static enum Status {
		UP,
		DOWN
	}
	
	static final Logger LOG = LogManager.getLogger(MessageSink.class);

	int systemId();
	
	int sinkId();
	
	String name();
	
	ServiceType serviceType();
	
	long publish(DirectBuffer buffer, int offset, int length);
	
	long tryPublish(DirectBuffer buffer, int offset, int length);
	
	/**
	 * 
	 * @param length Required length
	 * @param buffer 
	 * @return true, claimed a buffer 
	 *         false, cannot claim a buffer
	 */
	long tryClaim(int length, MessageSinkBufferClaim buffer); 
	
	/**
	 * TODO
	 * @return
	 */
	int backPressureIntensity();
	
	/**
	 * TODO
	 * @return
	 */
	SinkInfo processingRate();
	
	/**
	 * Nothing should ever be written to {@link NullMessageSink}
	 * TODO Publish to a file with unlimited space to serve as a DeadLetter mailbox 
	 * @author Calvin
	 *
	 */
	public static MessageSink NULL_INSTANCE = new MessageSink() {
		@Override
		public int sinkId(){
			throw new IllegalAccessError("sinkId() method of NULL message sink should not be accessed");
		}
		@Override
		public int systemId(){
			throw new IllegalAccessError("systemId() method of NULL message sink should not be accessed");
		}
		@Override
		public SinkInfo processingRate() {
			return SinkInfo.NULL_INSTANCE;
		}
		@Override
		public int backPressureIntensity() {
			return 0;
		}
		@Override
		public String name() {
			return "null-message-sink";
		}
		@Override
		public ServiceType serviceType(){
			return ServiceType.NULL_VAL;
		}

		@Override
		public String toString() {
			return "[name:" + name() + ", sinkId: null]";
		};

		@Override
		public long tryClaim(int length, MessageSinkBufferClaim buffer) {
			return MessageSink.OK;
		}

		@Override
		public long publish(DirectBuffer buffer, int offset, int length) {
			LOG.warn("publish message to NULL_INSTANCE");
			return MessageSink.OK;
		}

		@Override
		public long tryPublish(DirectBuffer buffer, int offset, int length) {
			LOG.warn("publish message to NULL_INSTANCE");
			return MessageSink.OK;
		}
	};
	
	public static MessageSink NA_INSTANCE = new MessageSink() {
		@Override
		public int systemId(){
			return -1;
		}
		@Override
		public int sinkId(){
			return -1;
		}

		@Override
		public SinkInfo processingRate() {
			return SinkInfo.NULL_INSTANCE;
		}
		@Override
		public int backPressureIntensity() {
			return 0;
		}
		@Override
		public String name() {
			return "na-message-sink";
		}
		@Override
		public ServiceType serviceType(){
			return ServiceType.NULL_VAL;
		}

		@Override
		public String toString() {
			return "[name: na-message-sink, sinkId: -1]";
		};

		@Override
		public long tryClaim(int length, MessageSinkBufferClaim buffer) {
			return MessageSink.OK;
		}

		@Override
		public long publish(DirectBuffer buffer, int offset, int length) {
			LOG.warn("publish message to NA_INSTANCE");
			return MessageSink.OK;
		}

		@Override
		public long tryPublish(DirectBuffer buffer, int offset, int length) {
			LOG.warn("publish message to NA_INSTANCE");
			return MessageSink.OK;
		}
		
	};
}