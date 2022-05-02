package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventSbeEncoder;
import com.lunar.message.io.sbe.EventSbeEncoder.EventValuesEncoder;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class EventSender {
	static final Logger LOG = LogManager.getLogger(EventSender.class);

	private final MessageSender msgSender;
	private final EventSbeEncoder sbe = new EventSbeEncoder();

	public static EventSender of(MessageSender msgSender){
		return new EventSender(msgSender);
	}
	
	EventSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}

	/**
	 * @param sink
	 * @param category
	 * @param level
	 * @param sinkId
	 * @param eventTime
	 * @param description
	 * @return
	 */
	public long sendEvent(MessageSinkRef sink,
			EventCategory category,  
			EventLevel level, 
			int sinkId, 
			long eventTime,
			String description){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(0, description.length()), sink, bufferClaim) >= 0L){
			encodeEvent(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe, 
					category, level, EventType.NULL_VAL, sinkId, eventTime, description);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeEvent(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, category, level, EventType.NULL_VAL, sinkId, eventTime, description);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendEvent(MessageSinkRef sink,
			EventCategory category,  
			EventLevel level,
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(0, description.length()), sink, bufferClaim) >= 0L){
			encodeEvent(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe, 
					category, level, eventType, sinkId, eventTime, description);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeEvent(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, category, level, eventType, sinkId, eventTime, description);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	public long sendEventWithOneValue(MessageSinkRef sink,
			EventCategory category,  
			EventLevel level, 
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1){
	    return sendEventWithOneValue(sink, category, level, EventType.NULL_VAL, sinkId, eventTime, description, eventValueType1, eventValue1);
	}
	
   public long sendEventWithOneValue(MessageSinkRef sink,
            EventCategory category,  
            EventLevel level, 
            EventType eventType,
            int sinkId, 
            long eventTime,
            String description,
            EventValueType eventValueType1,
            long eventValue1){
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(expectedEncodedLength(1, description.length()), sink, bufferClaim) >= 0L){
            encodeEvent(msgSender, 
                    sink.sinkId(), 
                    bufferClaim.buffer(), 
                    bufferClaim.offset(), 
                    sbe, 
                    category, level, eventType, sinkId, eventTime, description, eventValueType1, eventValue1);
            bufferClaim.commit();
            return MessageSink.OK;
        }
        int size = encodeEvent(msgSender, 
                sink.sinkId(), 
                msgSender.buffer(), 
                0, 
                sbe, category, level, eventType, sinkId, eventTime, description, eventValueType1, eventValue1);
        return msgSender.trySend(sink, msgSender.buffer(), 0, size);
    }
	   
	public long sendEventWithTwoValues(MessageSinkRef sink,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1,
			EventValueType eventValueType2,
			long eventValue2){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(1, description.length()), sink, bufferClaim) >= 0L){
			encodeEvent(msgSender, 
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe, 
					category, level, eventType, sinkId, eventTime, description, eventValueType1, eventValue1, eventValueType2, eventValue2);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeEvent(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, category, level, eventType, sinkId, eventTime, description, eventValueType1, eventValue1, eventValueType2, eventValue2);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendEventWithThreeValues(MessageSinkRef sink,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1,
			EventValueType eventValueType2,
			long eventValue2,
			EventValueType eventValueType3,
			long eventValue3){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(1, description.length()), sink, bufferClaim) >= 0L){
			encodeEvent(msgSender, 
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe, 
					category, level, eventType, sinkId, eventTime, description, eventValueType1, eventValue1, eventValueType2, eventValue2, eventValueType3, eventValue3);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeEvent(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, category, level, eventType, sinkId, eventTime, description, eventValueType1, eventValue1, eventValueType2, eventValue2, eventValueType3, eventValue3);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendEventWithMultipleValues(MessageSinkRef sink,
			EventCategory category,  
			EventLevel level,
            EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType[] eventValueTypes,
			long[] eventValues){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(eventValueTypes.length, description.length()), sink, bufferClaim) >= 0L){
			encodeEvent(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe, 
					category, level, eventType, sinkId, eventTime, description, eventValueTypes, eventValues);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeEvent(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, category, level, eventType, sinkId, eventTime, description, eventValueTypes, eventValues);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}	
	
	public static int expectedEncodedLength(int numValues, int descriptionLength){
		int size = encodedLength(numValues, descriptionLength);
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + EventSbeEncoder.BLOCK_LENGTH + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}
	
	public static int encodedLength(int numValues, int descriptionLength){
		int total = MessageHeaderEncoder.ENCODED_LENGTH + 
				EventSbeEncoder.BLOCK_LENGTH +
				descriptionLength + 1 /* size of length field */ +
				EventSbeEncoder.EventValuesEncoder.sbeHeaderSize() + EventSbeEncoder.EventValuesEncoder.sbeBlockLength() * numValues;
		return total;
	}

	/**
	 * Truncate description
	 * @param sender
	 * @param dstSinkId
	 * @param buffer
	 * @param offset
	 * @param sbe
	 * @param category
	 * @param level
	 * @param sinkId
	 * @param eventTime
	 * @param description
	 * @return
	 */
	public static int encodeEvent(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description){
		
		int payloadLength = encodeEventOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				category, level, eventType, sinkId, eventTime, description);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				EventSbeEncoder.BLOCK_LENGTH, 
				EventSbeEncoder.TEMPLATE_ID, 
				EventSbeEncoder.SCHEMA_ID, 
				EventSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeEvent(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType[] eventValueTypes,
			long[] eventValues){
		
		int payloadLength = encodeEventOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				category, level, eventType, sinkId, eventTime, description, eventValueTypes, eventValues);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				EventSbeEncoder.BLOCK_LENGTH, 
				EventSbeEncoder.TEMPLATE_ID, 
				EventSbeEncoder.SCHEMA_ID, 
				EventSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}

	public static int encodeEventOnly(MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description){
		sbe.wrap(buffer, offset)
		.category(category)
		.level(level)
		.eventType(eventType)
		.sinkId(sinkId)
		.time(eventTime)
		.eventValuesCount(0);
		sbe.description(description);

		return sbe.encodedLength();
	}
	
	public static int encodeEvent(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1){
		
		int payloadLength = encodeEventOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				category, level, eventType, sinkId, eventTime, description,
				eventValueType1,
				eventValue1);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				EventSbeEncoder.BLOCK_LENGTH, 
				EventSbeEncoder.TEMPLATE_ID, 
				EventSbeEncoder.SCHEMA_ID, 
				EventSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeEvent(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1,
			EventValueType eventValueType2,
			long eventValue2){
		
		int payloadLength = encodeEventOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				category, level, eventType, sinkId, eventTime, description,
				eventValueType1,
				eventValue1,
				eventValueType2,
				eventValue2);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				EventSbeEncoder.BLOCK_LENGTH, 
				EventSbeEncoder.TEMPLATE_ID, 
				EventSbeEncoder.SCHEMA_ID, 
				EventSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeEvent(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1,
			EventValueType eventValueType2,
			long eventValue2,
			EventValueType eventValueType3,
			long eventValue3){
		
		int payloadLength = encodeEventOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				category, level, eventType, sinkId, eventTime, description,
				eventValueType1,
				eventValue1,
				eventValueType2,
				eventValue2,
				eventValueType3,
				eventValue3);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				EventSbeEncoder.BLOCK_LENGTH, 
				EventSbeEncoder.TEMPLATE_ID, 
				EventSbeEncoder.SCHEMA_ID, 
				EventSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeEventOnly(MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1){
		sbe.wrap(buffer, offset)
		.category(category)
		.level(level)
		.eventType(eventType)
		.sinkId(sinkId)
		.time(eventTime)
		.eventValuesCount(1)
		.next()
			.type(eventValueType1)
			.value(eventValue1);
		sbe.description(description);
		return sbe.encodedLength();
	}

	public static int encodeEventOnly(MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1,
			EventValueType eventValueType2,
			long eventValue2){
		sbe.wrap(buffer, offset)
		.category(category)
		.level(level)
		.eventType(eventType)
		.sinkId(sinkId)
		.time(eventTime)
		.eventValuesCount(2)
		.next()
			.type(eventValueType1)
			.value(eventValue1)
		.next()
			.type(eventValueType2)
			.value(eventValue2);
		
		sbe.description(description);
		return sbe.encodedLength();
	}
	
	public static int encodeEventOnly(MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType1,
			long eventValue1,
			EventValueType eventValueType2,
			long eventValue2,
			EventValueType eventValueType3,
			long eventValue3){
		sbe.wrap(buffer, offset)
		.category(category)
		.level(level)
		.eventType(eventType)
		.sinkId(sinkId)
		.time(eventTime)
		.eventValuesCount(3)
		.next()
			.type(eventValueType1)
			.value(eventValue1)
		.next()
			.type(eventValueType2)
			.value(eventValue2)
		.next()
		.type(eventValueType3)
		.value(eventValue3);
		
		sbe.description(description);
		return sbe.encodedLength();
	}
	
	public static int encodeEventOnly(MutableDirectBuffer buffer, 
			int offset,
			EventSbeEncoder sbe,
			EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType[] eventValueTypes,
			long[] eventValues){
		EventValuesEncoder encoder = sbe.wrap(buffer, offset)
				.category(category)
				.level(level)
				.eventType(eventType)
				.sinkId(sinkId)
				.time(eventTime)
				.eventValuesCount(eventValueTypes.length);
		for (int i = 0; i < eventValueTypes.length; i++) {
			encoder = encoder.next().type(eventValueTypes[i]).value(eventValues[i]);
		}
		sbe.description(description);

		return sbe.encodedLength();
	}	

}
