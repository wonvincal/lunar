package com.lunar.message.sender;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.FragmentCreator;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Responsible providing methods to send out a message to a message sink.
 * Automatically breaks a message into fragments if necessary.
 * 
 * @author wongca
 *
 */
public final class MessageSender {
	public final static int AGGREGATED_SEND_RESULT_INDEX = 0;
	private final MessageSinkRef self;
	
	/**
	 * Unique for each instance of MessageSender
	 */
	private final AtomicInteger seq;
	private final int fragmentSize;
	private final MessageSinkBufferClaim bufferClaim;
	private final MutableDirectBuffer buffer;
	private final MessageHeaderEncoder header;
	private final MutableDirectBuffer stringBuffer;
	private final FragmentCreator fragmentCreator;
	
	public static MessageSender of(int fragmentLength, MessageSinkRef self){
		return new MessageSender(fragmentLength, self, MessageSinkBufferClaim.of());
	}
	
	public static MessageSender of(int fragmentLength, MessageSinkRef self, MessageSinkBufferClaim bufferClaim){
		return new MessageSender(fragmentLength, self, bufferClaim);
	}
	
	MessageSender(int fragmentSize, MessageSinkRef self, MessageSinkBufferClaim bufferClaim){
		this.self = self;
		this.fragmentSize = fragmentSize;
		this.bufferClaim = bufferClaim;
		this.seq = new AtomicInteger();
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_NOTE_SIZE));
		this.header = new MessageHeaderEncoder();
		this.fragmentCreator = FragmentCreator.of(fragmentSize);
	}
	
	public MessageSinkRef self(){
		return self;
	}
	
	int overrideNextSeq(int value){
		seq.set(value);
		return value;
	}
	
	int peekNextSeq(){
		return seq.get();
	}
	
	public int getAndIncSeq(){
		return seq.getAndIncrement();
	}
	
	public MessageHeaderEncoder header(){
		return header;
	}
	
	public int headerSize(){
		return MessageHeaderEncoder.ENCODED_LENGTH;
	}

	public int encodeHeader(int sinkId, MutableDirectBuffer buffer, int offset, int blockLength, int templateId, int schemaId, int schemaVersion, int payloadLength){
		int seqNum = seq.getAndIncrement();
		header.wrap(buffer, offset)
				.blockLength(blockLength)
				.templateId(templateId)
				.schemaId(schemaId)
				.version(schemaVersion)
				.senderSinkId((byte)self.sinkId())
				.dstSinkId((byte)sinkId)
				.seq(seqNum)
				.payloadLength(payloadLength);
		return seqNum;
	}
	
	public static int encodeHeader(MessageHeaderEncoder header, byte senderSinkId, int sinkId, MutableDirectBuffer buffer, int offset, int blockLength, int templateId, int schemaId, int schemaVersion, int seq, int payloadLength){
		header.wrap(buffer, offset)
		.blockLength(blockLength)
		.templateId(templateId)
		.schemaId(schemaId)
		.version(schemaVersion)
		.senderSinkId(senderSinkId)
		.dstSinkId((byte)sinkId)
		.seq(seq)
		.payloadLength(payloadLength);
		return seq;
	}

	private AtomicReference<Thread> userThread = new AtomicReference<>();
	
	private void multiThreadCheck(){
		if (ServiceConstant.SHOULD_MULTITHREAD_ISSUE_CHECK){
			Thread thread = userThread.get();
			if (thread != null){
				if (!thread.equals(Thread.currentThread())){
					throw new IllegalStateException("MessageSender is being used by more than one thread [prevThread: " + thread.getName() + "(" + thread.getId() + "), currentThread: " + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ")]");
				}
			}
			else {
				userThread.set(Thread.currentThread());
			}
		}		
	}
	
	/**
	 * Publish to {@link MessageSinkRef}.  Split buffer into multiple frames if necessary.
	 * @param sink
	 * @param buffer
	 * @param offset
	 * @param length
	 * @return Anything non zero is a problem.  Above zero is back pressure score.  Below zero is error code.
	 */
	public long send(MessageSinkRef sink, DirectBuffer buffer, int offset, int length){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK && length > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
		}
		multiThreadCheck();
		
		if (length <= this.fragmentSize){
			return sink.publish(buffer, offset, length);
		}
		
		// Send in fragments
		// buffer includes messageHeader, something the system needs to find the right template
		
		int index = fragmentCreator.wrap(buffer, offset, length);
		MutableDirectBuffer[] buffers = fragmentCreator.buffers();
		for (int i = 0; i < index; i++){
			// TODO try to reduce this if statement here later
			long result = sink.publish(buffers[i], 
					0, 
					fragmentSize /* may send more bytes, but we don't care */);
			if (result != MessageSink.OK){
				return result;
			}
		}
		return MessageSink.OK;
	}
	
	/**
	 * 
	 * @param sink
	 * @param buffer
	 * @param offset
	 * @param length
	 * @return
	 */
	static final Logger LOG = LogManager.getLogger(MessageSender.class);
	public long trySend(MessageSinkRef sink, DirectBuffer buffer, int offset, int length){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK && length > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
		}
		multiThreadCheck();

		if (length <= this.fragmentSize){
			return sink.tryPublish(buffer, offset, length);
		}

		// 1. Check length against frame size
		// 2. If within frame size, call sink.send()
		// 3. If exceed frame size, for loop, send individual frames with fragment id, fragment seq id, is last		
		// 4. automatically assign fragment group and fragment id
		int index = fragmentCreator.wrap(buffer, offset, length);
		MutableDirectBuffer[] buffers = fragmentCreator.buffers();
		for (int i = 0; i < index; i++){
			// TODO try to reduce this if statement here later
			long result = sink.tryPublish(buffers[i], 
					0, 
					fragmentSize /* may send more bytes, but we don't care */);
			if (result != MessageSink.OK){
				return result;
			}
		}
		return MessageSink.OK;
	} 
	
	/**
	 * Publish to {@link MessageSinkRef}.  Split buffer into multiple frames if necessary.
	 * @param sinks
	 * @param buffer must be a standalone buffer.  must not use buffer of ring buffer or aeron! 
	 * @param offset
	 * @param length
	 * @param
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long send(MessageSinkRef[] sinks, MutableDirectBuffer buffer, int offset, int length, long[] results){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK){
			if (length > ServiceConstant.MAX_MESSAGE_SIZE){
				throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
			}
			if (sinks.length > results.length){
				throw new IllegalArgumentException("Number of sinks exceed size of results array [numSinks:" + sinks.length + ", resultsArraySize:" + results.length + "]");
			}
		}
		multiThreadCheck();

		long aggResult = MessageSink.OK; 
		if (length <= this.fragmentSize){
			for (int i = 0; i < sinks.length; i++){
				MessageEncoder.encodeDstSinkId(buffer, offset, header, sinks[i].sinkId());
				results[i] = send(sinks[i], buffer, offset, length);
				aggResult += results[i];
			}
			return (aggResult == 0) ? MessageSink.OK : MessageSink.FAILURE;
		}
		
		// Send in fragments
		int index = fragmentCreator.wrap(buffer, offset, length);
		MutableDirectBuffer[] buffers = fragmentCreator.buffers();
		for (int i = 0; i < sinks.length; i++){
			for (int j = 0; j < index; j++){
				MessageEncoder.encodeDstSinkId(buffers[j], 0, header, sinks[i].sinkId());
				results[i] = send(sinks[i], buffers[j], 0, fragmentSize);
				// Stop sending fragments to a particular sink if any one send is failed
				if (results[i] != MessageSink.OK){
					aggResult = MessageSink.FAILURE;
					break;
				}
			}
		}
		return aggResult;
	}

	public long trySend(MessageSinkRef[] sinks,
			int numSinks,
			int blockLength,
			int templateId,
			int schemaId,
			int schemaVersion,
			DirectBuffer payloadBuffer, 
			int payloadOffset, 
			int payloadLength, 
			long[] results){
		// Encode header
		this.encodeHeader(ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				this.buffer,
				0, 
				blockLength, templateId, schemaId, schemaVersion, payloadLength);
		
		// Copy payload over
		this.buffer.putBytes(MessageHeaderEncoder.ENCODED_LENGTH, 
				payloadBuffer, 
				payloadOffset, 
				payloadLength);
		
		return trySend(sinks, numSinks, buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + payloadLength, results);
	}
	
	public long trySend(MessageSinkRef[] sinks,
	        int numSinks,
	        MessageSinkRef individualSink,
	        int blockLength,
	        int templateId,
	        int schemaId,
	        int schemaVersion,
	        DirectBuffer payloadBuffer, 
	        int payloadOffset, 
	        int payloadLength, 
	        long[] results){
	    // Encode header
	    this.encodeHeader(ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
	            this.buffer,
	            0, 
	            blockLength, templateId, schemaId, schemaVersion, payloadLength);

	    // Copy payload over
	    this.buffer.putBytes(MessageHeaderEncoder.ENCODED_LENGTH, 
	            payloadBuffer, 
	            payloadOffset, 
	            payloadLength);

	    return trySend(sinks, numSinks, individualSink, buffer, 0, MessageHeaderEncoder.ENCODED_LENGTH + payloadLength, results);
	}

	/**
	 * Side Effect: DstSinkId will be changed to whichever sink this message will be delivered to  
	 * @param sinks
	 * @param buffer
	 * @param offset
	 * @param length
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long trySend(MessageSinkRef[] sinks, MutableDirectBuffer buffer, int offset, int length, long[] results){
		return trySend(sinks, sinks.length, buffer, offset, length, results);
	}

	public long trySend(MessageSinkRef[] sinks, int size, MutableDirectBuffer buffer, int offset, int length, long[] results){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK){
			if (length > ServiceConstant.MAX_MESSAGE_SIZE){
				throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
			}
			if (size > results.length){
				throw new IllegalArgumentException("Input size exceeds size of results array [size:" + size + ", resultsArraySize:" + results.length + "]");
			}
			if (size > sinks.length){
				throw new IllegalArgumentException("Input size exceeds size of input array [size:" + size + ", numSinks:" + sinks.length + ", resultsArraySize:" + results.length + "]");
			}
		}
		multiThreadCheck();

		long aggResult = MessageSink.OK;
		if (length <= this.fragmentSize){
			for (int i = 0; i < size; i++){
				MessageEncoder.encodeDstSinkId(buffer, offset, header, sinks[i].sinkId());
				results[i] = trySend(sinks[i], buffer, offset, length);
				aggResult += results[i]; 
			}
			return (aggResult == 0) ? MessageSink.OK : MessageSink.FAILURE;
		}

		// Send in fragments
		int index = fragmentCreator.wrap(buffer, offset, length);
		MutableDirectBuffer[] buffers = fragmentCreator.buffers();
		for (int i = 0; i < size; i++){
			for (int j = 0; j < index; j++){
				MessageEncoder.encodeDstSinkId(buffers[j], 0, header, sinks[i].sinkId());
				results[i] = trySend(sinks[i], buffers[j], 0, fragmentSize);
				// Stop sending fragments to a particular sink if any one send is failed
				if (results[i] != MessageSink.OK){
					aggResult = MessageSink.FAILURE;
					break;
				}		
			}
		}
		return aggResult;
	}

    public long trySend(MessageSinkRef[] sinks, int size, MessageSinkRef individualSink, MutableDirectBuffer buffer, int offset, int length, long[] results){
        if (ServiceConstant.SHOULD_BOUNDS_CHECK){
            if (length > ServiceConstant.MAX_MESSAGE_SIZE){
                throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
            }
            if (size > results.length){
                throw new IllegalArgumentException("Input size exceeds size of results array [size:" + size + ", resultsArraySize:" + results.length + "]");
            }
            if (size > sinks.length){
                throw new IllegalArgumentException("Input size exceeds size of input array [size:" + size + ", numSinks:" + sinks.length + ", resultsArraySize:" + results.length + "]");
            }
        }
        multiThreadCheck();

        long aggResult = MessageSink.OK;
        if (length <= this.fragmentSize){
            for (int i = 0; i < size; i++){
                MessageEncoder.encodeDstSinkId(buffer, offset, header, sinks[i].sinkId());
                results[i] = trySend(sinks[i], buffer, offset, length);
                aggResult += results[i]; 
            }
            MessageEncoder.encodeDstSinkId(buffer, offset, header, individualSink.sinkId());
            results[size] = trySend(individualSink, buffer, offset, length);
            aggResult += results[size];
            return (aggResult == 0) ? MessageSink.OK : MessageSink.FAILURE;
        }

        // Send in fragments
        int index = fragmentCreator.wrap(buffer, offset, length);
        MutableDirectBuffer[] buffers = fragmentCreator.buffers();
        for (int i = 0; i < size; i++){
            for (int j = 0; j < index; j++){
                MessageEncoder.encodeDstSinkId(buffers[j], 0, header, sinks[i].sinkId());
                results[i] = trySend(sinks[i], buffers[j], 0, fragmentSize);
                
                // Stop sending fragments to a particular sink if any one send is failed
                if (results[i] != MessageSink.OK){
                    aggResult = MessageSink.FAILURE;
                    break;
                }       
            }
        }
        for (int j = 0; j < index; j++){
            MessageEncoder.encodeDstSinkId(buffers[j], 0, header, individualSink.sinkId());
            results[size] = trySend(individualSink, buffer, offset, length);
            if (results[size] != MessageSink.OK){
                aggResult = MessageSink.FAILURE;
                break;
            }
        }
        return aggResult;
    }
	
	/**
	 * Note: 
	 * 1) This is slower than the other method without 'except'
	 * 2) If you are sure that this method will be called many times, please use the version without 'except' by
	 *    build a new array of sinks, java escape analysis should be able to avoid heap allocation
	 * @param sinks
	 * @param buffer
	 * @param offset
	 * @param length
	 * @param except
	 * @return Negative means at least one message couldn't be delivered
	 */
	public long send(MessageSinkRef[] sinks, MutableDirectBuffer buffer, int offset, int length, int except, long[] results){
		return send(sinks, sinks.length, buffer, offset, length, except, results);
	}

	public long send(MessageSinkRef[] sinks, int size, MutableDirectBuffer buffer, int offset, int length, int except, long[] results){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK){
			if (length > ServiceConstant.MAX_MESSAGE_SIZE){
				throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
			}
			if (size > results.length){
				throw new IllegalArgumentException("Input size exceeds size of results array [size:" + size + ", resultsArraySize:" + results.length + "]");
			}
			if (size > sinks.length){
				throw new IllegalArgumentException("Input size exceeds size of input array [size:" + size + ", numSinks:" + sinks.length + ", resultsArraySize:" + results.length + "]");
			}
		}
		multiThreadCheck();

		long aggResult = MessageSink.OK;
		if (length <= this.fragmentSize){
			for (int i = 0; i < sinks.length; i++){
				// No need to add to aggResult for the 'excepted' sink because MessageSink.OK == 0
				results[i] = MessageSink.OK;
				if (sinks[i].sinkId() != except){
					MessageEncoder.encodeDstSinkId(buffer, offset, header, sinks[i].sinkId());
					results[i] = send(sinks[i], buffer, offset, length);
					aggResult += results[i];
				}
			}
			return (aggResult == 0) ? MessageSink.OK : MessageSink.FAILURE;
		}

		// Send in fragments
		int index = fragmentCreator.wrap(buffer, offset, length);
		MutableDirectBuffer[] buffers = fragmentCreator.buffers();
		for (int i = 0; i < sinks.length; i++){
			results[i] = MessageSink.OK;
			if (sinks[i].sinkId() != except){
				// Send out fragments, skip sending remaining to a sink if anyone of them is failed to be delivered
				for (int j = 0; j < index; j++){
					MessageEncoder.encodeDstSinkId(buffers[j], 0, header, sinks[i].sinkId());
					results[i] = send(sinks[i], buffers[j], 0, fragmentSize);
					if (results[i] != MessageSink.OK){
						aggResult = MessageSink.FAILURE;
						break;
					}
				}				
			}
		}
		return aggResult;
	}
	/**
	 * 
	 * @param sinks
	 * @param buffer
	 * @param offset
	 * @param length
	 * @param except
	 * @param results
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long send(MessageSinkRef[] sinks, MutableDirectBuffer buffer, int offset, int length, int[] except, long[] results){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK){
			if (length > ServiceConstant.MAX_MESSAGE_SIZE){
				throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
			}
			if (sinks.length > results.length){
				throw new IllegalArgumentException("Number of sinks exceed size of results array [numSinks:" + sinks.length + ", resultsArraySize:" + results.length + "]");
			}
		}
		multiThreadCheck();

		Arrays.sort(except);
		long aggResult = MessageSink.OK;
		if (length <= this.fragmentSize){
			for (int i = 0; i < sinks.length; i++){
				results[i] = MessageSink.OK;
				if (Arrays.binarySearch(except, sinks[i].sinkId()) < 0){
					MessageEncoder.encodeDstSinkId(buffer, offset, header, sinks[i].sinkId());
					results[i] = send(sinks[i], buffer, offset, length);
					aggResult += results[i];
				}
			}
			return (aggResult == 0) ? MessageSink.OK : MessageSink.FAILURE;
		}
		
		// Send in fragments
		int index = fragmentCreator.wrap(buffer, offset, length);
		MutableDirectBuffer[] buffers = fragmentCreator.buffers();
		for (int i = 0; i < sinks.length; i++){
			results[i] = MessageSink.OK;
			if (Arrays.binarySearch(except, sinks[i].sinkId()) < 0){
				// Send out fragments, skip sending remaining to a sink if anyone of them is failed to be delivered
				for (int j = 0; j < index; j++){
					MessageEncoder.encodeDstSinkId(buffers[j], 0, header, sinks[i].sinkId());
					results[i] = send(sinks[i], buffers[j], 0, fragmentSize);
					if (results[i] != MessageSink.OK){
						aggResult = MessageSink.FAILURE;
						break;
					}
				}
			}
		}
		return aggResult;
	}
	

	// if this returns boolean, meaning not suitable, publisher should use write to a separate
	// buffer and use one of the publish() methods
	// if we are distributing to multiple sinks, we should always go for making a copy first
	// SBE doesn't work with writing across multiple buffers without gap.  For example,
	// one original thought was to produce a WrapperBuffer of N buffer of fixed size, then writes
	// transparently to it.  However, SBE needs to wrap a MutableDirectBuffer, and we will need to
	// keep checking if we are going to exceed the limit...
	public long tryClaim(int length, MessageSinkRef sink, MessageSinkBufferClaim bufferClaim){
		if (ServiceConstant.SHOULD_BOUNDS_CHECK && length > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Length exceeds max message size [length:" + length + ", max:" + ServiceConstant.MAX_MESSAGE_SIZE + "]");
		}
		multiThreadCheck();

		// If length > fragmentSize
		// 1) we break them up
		// 2) we call tryClaim on each
		if (length <= fragmentSize){
			return sink.tryClaim(length, bufferClaim);
		}
		return MessageSink.LENGTH_EXCEEDS_FRAME_SIZE;
	}
	
	public MutableDirectBuffer buffer(){
		return buffer;
	}

	public MutableDirectBuffer stringBuffer(){
		return this.stringBuffer;
	}

	public MessageSinkBufferClaim bufferClaim(){
		return bufferClaim;
	}
}
