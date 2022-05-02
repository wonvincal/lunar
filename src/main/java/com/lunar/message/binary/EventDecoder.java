package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.sender.EventSender;
import com.lunar.service.ServiceConstant;

public class EventDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(EventDecoder.class);
	private final EventSbeDecoder sbe = new EventSbeDecoder();
	private final HandlerList<EventSbeDecoder> handlerList;

	EventDecoder(Handler<EventSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<EventSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, EventSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset, header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(EventSbeDecoder sbe){
		sbe.limit(sbe.offset() + EventSbeDecoder.BLOCK_LENGTH);
		int numValues = sbe.eventValues().count();
		String description = sbe.description();
		String msg = String.format("sinkId:%d, time:%d, category:%s, level:%s, description:%s, numValues:%d",
				sbe.sinkId(),
				sbe.time(),
				sbe.category().name(),
				sbe.level().name(),
				description,
				numValues);
		sbe.limit(sbe.offset() + EventSbeDecoder.BLOCK_LENGTH);
		return msg;
	}

	public static int getEncodedLength(EventSbeDecoder sbe){
		int origLimit = sbe.limit();
		int descriptionLength = sbe.descriptionLength();
		sbe.limit(origLimit + 1 + descriptionLength);
		int eventValuesCount = sbe.eventValues().count();
		sbe.limit(origLimit);
		return EventSender.encodedLength(eventValuesCount, descriptionLength);
	}
	
	public EventSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<EventSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static EventDecoder of(){
		return new EventDecoder(NULL_HANDLER);
	}

	static final Handler<EventSbeDecoder> NULL_HANDLER = new Handler<EventSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
			LOG.warn("Message sent to null handler of EventDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
