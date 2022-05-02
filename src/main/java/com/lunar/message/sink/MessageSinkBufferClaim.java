package com.lunar.message.sink;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.MutableDirectBuffer;

public class MessageSinkBufferClaim {
	private static final Logger LOG = LogManager.getLogger(MessageSinkBufferClaim.class);

	// these fields won't change
	private final RingBufferCommitter rbCommitter = new RingBufferCommitter();
	private final BufferClaim bufferClaim = new BufferClaim();
	private final AeronBufferCommitter aeronCommitter = new AeronBufferCommitter();
	
	// TODO add some paddings here
	
	// these fields change only when changes between aeron and ring buffer
	private BufferCommitter committer;
	/**
	 * TODO if we find no use of length(), we should remove it in the future
	 */
	private int length;
	private int offset;
	
	// TODO add some paddings here

	// this field changes very often
	private MutableDirectBuffer buffer;
	
	public static MessageSinkBufferClaim of(){
		return new MessageSinkBufferClaim();
	}
	
	MessageSinkBufferClaim(){}
	
	/**
	 * RingBuffer
	 * Work on exception case 
	 * TODO: if anything happens such that commit is not called at the end, the ring buffer will be
	 * blocked....need to think about this
	 * @param length
	 * @param rb
	 * @return
	 */
	public long tryClaim(int sinkId, int length, RingBuffer<MutableDirectBuffer> rb){
		long sequence;
		try {
			sequence = rb.tryNext();
		} 
		catch (InsufficientCapacityException e) {
			LOG.error("Caught exception [dest sinkId:{}]", sinkId, e);
			return MessageSink.INSUFFICIENT_SPACE;
		}
		this.buffer = rb.get(sequence);
		this.offset = 0;
		this.length = length;
		rbCommitter.wrap(sinkId, sequence, rb);
		committer = rbCommitter;
		return MessageSink.OK;
	}
	
	/**
	 * Publication of an Aeron buffer
	 * TODO work on exception cases
	 * Note: This is a non blocking call
	 * @param length
	 * @param publication
	 * @return
	 */
	public long tryClaim(int length, Publication publication){
		if (publication.tryClaim(length, bufferClaim) == MessageSink.OK){
			this.buffer = bufferClaim.buffer();
			this.offset = bufferClaim.offset();
			this.length = length;
			this.committer = aeronCommitter;
			return MessageSink.OK;
		}
		return MessageSink.FAILURE;
	}
	
	class RingBufferCommitter implements BufferCommitter {
		@SuppressWarnings("unused")
		private int sinkId;
		private long sequence;
		private RingBuffer<MutableDirectBuffer> ringBuffer = null;
		void wrap(int sinkId, long sequence, RingBuffer<MutableDirectBuffer> ringBuffer){
			this.sinkId = sinkId;
			this.sequence = sequence;
			this.ringBuffer = ringBuffer;
		}
		@Override
		public void commit() {
//			LOG.info("Commit published message to disruptor [sinkId:{}, sequence:{}]", sinkId, sequence);
			this.ringBuffer.publish(sequence);
		}		
	}
	
	private class AeronBufferCommitter implements BufferCommitter {
		AeronBufferCommitter(){}
		@Override
		public void commit() {
			bufferClaim.commit();
		}
	}
	public void commit(){
		this.committer.commit();
	}
	public MutableDirectBuffer buffer(){
		return this.buffer;
	}
	public int length(){
		return this.length;
	}
	public int offset(){
		return this.offset;
	}
}
