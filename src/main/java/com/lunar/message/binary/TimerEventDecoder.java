package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.service.ServiceConstant;

public class TimerEventDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(TimerEventDecoder.class);
	private final TimerEventSbeDecoder sbe = new TimerEventSbeDecoder();
	private final HandlerList<TimerEventSbeDecoder> handlerList;
	
	TimerEventDecoder(Handler<TimerEventSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<TimerEventSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(TimerEventSbeDecoder sbe){
		return String.format("clientKey:%d, type:%s, startTime:%d, expiryTime:%d",
				sbe.clientKey(),
				sbe.timerEventType().name(),
				sbe.startTime(),
				sbe.expiryTime());
	}

	public HandlerList<TimerEventSbeDecoder> handlerList(){
		return handlerList;
	}
	
	public static TimerEventDecoder of(){
		return new TimerEventDecoder(NULL_HANDLER);
	}

	static final Handler<TimerEventSbeDecoder> NULL_HANDLER = new Handler<TimerEventSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
			LOG.warn("message sent to null handler of TimeEventDecoder");
		}
	};
}
