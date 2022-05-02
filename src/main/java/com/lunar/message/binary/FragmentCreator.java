package com.lunar.message.binary;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.exception.SequenceNumberOverflowException;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.FragmentInitSbeEncoder;
import com.lunar.message.io.sbe.FragmentSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.service.ServiceConstant;
import com.lunar.util.BitUtil;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Break a message into multiple fragments
 * Input message includes header, something that we need to replicate
 * 
 * For each message [ Message Header | Template Id ][ Payload ]
 * breaks it into
 * [Message Header | Fragment Init Template Id][ Fragment Init Seq | Fragment Init Is Last | Payload (include header) / N ]
 * [Message Header | Fragment Template Id][ Fragment Seq | Fragment Is Last | Payload / N ]
 * 
 * @author wongca
 *
 */
public class FragmentCreator {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(FragmentCreator.class);
	@SuppressWarnings("unused")
	private final int fragmentSize;
	private final int fragmentInitPayloadSize;
	private final int fragmentPayloadSize;
	private final MutableDirectBuffer[] buffers;
	private final FragmentInitSbeEncoder fragmentInitEncoder;
	private final FragmentSbeEncoder fragmentEncoder;
	private final MessageHeaderEncoder messageHeaderEncoder;
	private final MessageHeaderDecoder messageHeaderDecoder;
	private int limit;
	private int seq;

	public static FragmentCreator of(int fragmentSize){
		return new FragmentCreator(fragmentSize);
	}
	
	FragmentCreator(int fragmentSize){
		if (fragmentSize <= (FragmentSbeEncoder.BLOCK_LENGTH + 1)){
			throw new IllegalArgumentException("Invalid argment. [fragmentSize:" + fragmentSize + ", exepected: >" + (FragmentSbeEncoder.BLOCK_LENGTH + 1));
		}
		this.fragmentSize = fragmentSize;
		this.fragmentInitPayloadSize = fragmentSize - (FragmentInitSbeEncoder.BLOCK_LENGTH + 1) - MessageHeaderDecoder.ENCODED_LENGTH;
		this.fragmentPayloadSize = fragmentSize - (FragmentSbeEncoder.BLOCK_LENGTH + 1) - MessageHeaderDecoder.ENCODED_LENGTH;
		int minPayloadSize = BitUtil.min(this.fragmentInitPayloadSize, this.fragmentPayloadSize);
		int numBufferRequired = ServiceConstant.MAX_MESSAGE_SIZE / minPayloadSize + (ServiceConstant.MAX_MESSAGE_SIZE % minPayloadSize == 0 ? 0 : 1);
		this.buffers = new MutableDirectBuffer[numBufferRequired];
		for (int i = 0; i < numBufferRequired; i++)
			this.buffers[i] = new UnsafeBuffer(ByteBuffer.allocateDirect(fragmentSize));
		this.fragmentEncoder = new FragmentSbeEncoder();
		this.fragmentInitEncoder = new FragmentInitSbeEncoder();
		this.messageHeaderDecoder = new MessageHeaderDecoder();
		this.messageHeaderEncoder = new MessageHeaderEncoder();
		this.limit = 0;
		this.seq = 0;
		
		// Pre-encode header
		this.messageHeaderEncoder.wrap(this.buffers[0], 0)
			.blockLength(FragmentInitSbeEncoder.BLOCK_LENGTH)
			.templateId(FragmentInitSbeEncoder.TEMPLATE_ID)
			.schemaId(FragmentInitSbeEncoder.SCHEMA_ID)
			.version(FragmentInitSbeEncoder.SCHEMA_VERSION);		
		for (int i = 1; i < numBufferRequired; i++){
			this.messageHeaderEncoder.wrap(buffers[i], 0)
			.blockLength(FragmentSbeEncoder.BLOCK_LENGTH)
			.templateId(FragmentSbeEncoder.TEMPLATE_ID)
			.schemaId(FragmentSbeEncoder.SCHEMA_ID)
			.version(FragmentSbeEncoder.SCHEMA_VERSION);
		}
		
	}

	int fragmentInitPayloadSize(){
		return fragmentInitPayloadSize;
	}

	int fragmentPayloadSize(){
		return fragmentPayloadSize;
	}
	
	int limit(){
		return limit;
	}
	
	int peekSeq(){
		return seq;
	}
	
	/**
	 * Return index (zero-based) of the last buffer used
	 * @param srcBuffer
	 * @param srcOffset
	 * @param length
	 * @return
	 */
	public int wrap(DirectBuffer srcBuffer, int srcOffset, int length){
		// Our message buffer can not exceed the system max size (e.g. Each disruptor event is a byte buffer of 1500 bytes).  
		// We won't be able to accommodate the assembled fragments to disruptor event
		if (ServiceConstant.SHOULD_BOUNDS_CHECK){
			messageHeaderDecoder.wrap(srcBuffer, srcOffset);
			if (messageHeaderDecoder.blockLength() <= 0){
				throw new IllegalArgumentException("Message must contain non zero block length");
			}

			if (length > ServiceConstant.MAX_MESSAGE_SIZE){
				throw new IllegalArgumentException("Message size exceeds the maximum [messageSize:" + length + ", maximum:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
			}
		}
		
		this.limit = 0;
		try {
			int seq = getAndIncrement();

			// Decoder header
			this.messageHeaderDecoder.wrap(srcBuffer, srcOffset);
			int senderSinkId = messageHeaderDecoder.senderSinkId();
			int dstSinkId = messageHeaderDecoder.dstSinkId();
			int headerSeq = messageHeaderDecoder.seq();
			
			// Encode header
			this.messageHeaderEncoder.wrap(buffers[this.limit], 0)
				.senderSinkId((byte)senderSinkId)
				.dstSinkId((byte)dstSinkId)
				.seq(headerSeq);
			
			if (length > fragmentInitPayloadSize){
				// Encode the only payload - first fragment
				fragmentInitEncoder.wrap(buffers[this.limit], MessageHeaderEncoder.ENCODED_LENGTH)
				.fragmentSeq(seq)
				.isLast(BooleanType.FALSE)
				.putPayload(srcBuffer, srcOffset, fragmentInitPayloadSize);
				int encodedLength = fragmentInitPayloadSize;

				// Encode the remaining fragments
				while (encodedLength < length){
					this.limit++;
					seq = getAndIncrement();
					int size = BitUtil.min(length - encodedLength, fragmentPayloadSize);
					
					// Encode header
					// TODO make copy of this
					this.messageHeaderEncoder.wrap(buffers[this.limit], 0)
					.senderSinkId((byte)senderSinkId)
					.dstSinkId((byte)dstSinkId)
					.seq(headerSeq);
					
					fragmentEncoder.wrap(buffers[this.limit], MessageHeaderEncoder.ENCODED_LENGTH)
					.fragmentSeq(seq)
					.isLast(BooleanType.FALSE)
					.putPayload(srcBuffer, srcOffset + encodedLength, size);
					encodedLength += size;
				}
				fragmentEncoder.isLast(BooleanType.TRUE);
				return this.limit;
			}
			
			// Encode only one fragment
			fragmentInitEncoder.wrap(buffers[this.limit], MessageHeaderEncoder.ENCODED_LENGTH)
			.fragmentSeq(seq)
			.isLast(BooleanType.TRUE)
			.putPayload(srcBuffer, srcOffset, length);
			return this.limit;
		} 
		catch (SequenceNumberOverflowException e) {
			// Start over if sequence number is overflow
			this.seq = 0;
			this.limit = wrap(srcBuffer, srcOffset, length);
		}
		return this.limit;
	}
	
	private int getAndIncrement() throws SequenceNumberOverflowException{
		if (seq >= 0){
			return seq++;
		}
		throw new SequenceNumberOverflowException();
	}
	
	public MutableDirectBuffer[] buffers(){
		return this.buffers;
	}
}
