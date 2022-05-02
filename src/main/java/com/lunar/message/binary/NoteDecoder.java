package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.service.ServiceConstant;

public class NoteDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(NoteDecoder.class);
	private final NoteSbeDecoder sbe = new NoteSbeDecoder();
	private final HandlerList<NoteSbeDecoder> handlerList;

	NoteDecoder(Handler<NoteSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<NoteSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, NoteSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset, header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(NoteSbeDecoder sbe){
		int origLimit = sbe.limit();
		sbe.limit(sbe.offset() + NoteSbeDecoder.BLOCK_LENGTH);
		String description = sbe.description();
		String msg = String.format("entitySid:%d, createDate:%d, createTime:%d, updateDate:%d, updateTime:%d, isDeleted:%s, isArchived:%s, description:%s",
				sbe.entitySid(),
				sbe.createDate(),
				sbe.createTime(),
				sbe.updateDate(),
				sbe.updateTime(),
				sbe.isDeleted().name(),
				sbe.isArchived().name(),
				description);
		sbe.limit(origLimit);
		return msg;
	}

	public NoteSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<NoteSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static NoteDecoder of(){
		return new NoteDecoder(NULL_HANDLER);
	}

	static final Handler<NoteSbeDecoder> NULL_HANDLER = new Handler<NoteSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder codec) {
			LOG.warn("Message sent to null handler of NoteDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
}
