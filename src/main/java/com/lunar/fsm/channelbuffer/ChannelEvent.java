package com.lunar.fsm.channelbuffer;

import java.nio.ByteBuffer;

import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ChannelEvent {
	public static final int NULL_RESPONNSE_SEQ = -1;
	public static final int OFFSET = 0;
	private int channelId;
	private long channelSeq;
	private int templateId;
	private int messageLength;
	/**
	 * Used when message belongs to part of a response
	 */
	private int responseSeq;
	private MutableDirectBuffer buffer;

	ChannelEvent(boolean allocateDirect){
		this.templateId = -1;
		this.buffer = new UnsafeBuffer((allocateDirect) ? ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE) : ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	}
	
	public int channelId(){
		return channelId;
	}

	public long channelSeq(){
		return channelSeq;
	}

	public int templateId(){
		return templateId;
	}
	
	/**
	 * Length of buffer
	 * @return
	 */
	public int length(){
		return messageLength;
	}

	/**
	 * Buffer that holds the whole message
	 * @return
	 */
	public DirectBuffer buffer(){
		return buffer;
	}
	
	public ChannelEvent merge(ChannelEvent source){
		this.channelSeq = source.channelSeq;
		this.templateId = source.templateId;
		this.messageLength = source.messageLength;
		this.responseSeq = source.responseSeq;
		this.buffer.putBytes(0, source.buffer, 0, source.messageLength);
		return this;
	}
	
	public ChannelEvent merge(int channelId,
			long channelSeq,
			int responseSeq,
			int templateId,
			DirectBuffer sourceBuffer,
			int offset,
			int messageLength){
		this.channelId = channelId;
		this.channelSeq = channelSeq;
		this.responseSeq = responseSeq;
		this.templateId = templateId;
		this.messageLength = messageLength;
		this.buffer.putBytes(0, sourceBuffer, offset, messageLength);
		return this;
	}
}

