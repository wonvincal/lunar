package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Note;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.NoteSbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class NoteSender {
	static final Logger LOG = LogManager.getLogger(NoteSender.class);

	private final MessageSender msgSender;
	private final NoteSbeEncoder sbe = new NoteSbeEncoder();
	public final static int MAX_DESC_LENGTH = 254;

	public static NoteSender of(MessageSender msgSender){
		return new NoteSender(msgSender);
	}
	
	NoteSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public static int expectedEncodedLength(int descriptionLength){
		int size = encodedLength(descriptionLength);
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + NoteSbeEncoder.BLOCK_LENGTH + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}

	public static int encodedLength(int descriptionLength){
		int total = MessageHeaderEncoder.ENCODED_LENGTH + 
				NoteSbeEncoder.BLOCK_LENGTH +
				descriptionLength + 1 /* size of length field */;
		return total;
	}

	public long sendNote(MessageSinkRef sink,
			int noteSid,
			long entitySid,
			int createDate,
			long createTime,
			int updateDate,
			long updateTime,
			BooleanType isDeleted,
			BooleanType isArchived,
			String description){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(description.length()), sink, bufferClaim) >= 0L){
			encodeNote(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					noteSid,
					entitySid,
					createDate,
					createTime,
					updateDate,
					updateTime,
					isDeleted,
					isArchived,
					description);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeNote(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, 
				noteSid,
				entitySid,
				createDate,
				createTime,
				updateDate,
				updateTime,
				isDeleted,
				isArchived,
				description);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public static int encodeNote(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			NoteSbeEncoder sbe,
			int noteSid,
			long entitySid,
			int createDate,
			long createTime,
			int updateDate,
			long updateTime,
			BooleanType isDeleted,
			BooleanType isArchived,
			String description){
		
		int payloadLength = encodeNoteOnly(buffer, 
				offset + sender.headerSize(), 
				sbe,
				noteSid,
				entitySid, createDate, createTime, updateDate, updateTime, 
				isDeleted, isArchived, description);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				NoteSbeEncoder.BLOCK_LENGTH, 
				NoteSbeEncoder.TEMPLATE_ID, 
				NoteSbeEncoder.SCHEMA_ID, 
				NoteSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeNoteOnly(MutableDirectBuffer buffer, 
			int offset,
			NoteSbeEncoder sbe,
			int noteSid,
			long entitySid,
			int createDate,
			long createTime,
			int updateDate,
			long updateTime,
			BooleanType isDeleted,
			BooleanType isArchived,
			String description){
		
		if (description.length() > MAX_DESC_LENGTH){
			description = description.substring(0, MAX_DESC_LENGTH); 
		}

		sbe.wrap(buffer, offset)
		.noteSid(noteSid)
		.entitySid(entitySid)
		.createDate(createDate)
		.createTime(createTime)
		.updateDate(updateDate)
		.updateTime(updateTime)
		.isDeleted(isDeleted)
		.isArchived(isArchived);
		sbe.description(description);

		return sbe.encodedLength();
	}
	
	public static int encodeNoteOnly(MutableDirectBuffer buffer, 
			int offset,
			NoteSbeEncoder sbe,
			Note note){
		
		String description = note.description(); 
		if (note.description().length() > MAX_DESC_LENGTH){
			description = note.description().substring(0, MAX_DESC_LENGTH); 
		}
		
		sbe.wrap(buffer, offset)
		.noteSid((int)note.sid())
		.entitySid(note.entitySid())
		.createDate(note.createDate())
		.createTime(note.createTime())
		.updateDate(note.updateDate())
		.updateTime(note.updateTime())
		.isDeleted(note.isDeleted())
		.isArchived(note.isArchived());
		sbe.description(description);

		return sbe.encodedLength();
	}
}
