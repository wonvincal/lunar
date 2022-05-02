package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PingSbeDecoder;
import com.lunar.service.ServiceConstant;

public class PingDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(PingDecoder.class);
	private final PingSbeDecoder sbe = new PingSbeDecoder();
	private final HandlerList<PingSbeDecoder> handlerList;
	
	PingDecoder(Handler<PingSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<PingSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(PingSbeDecoder sbe){
		// TODO put this sb in message codec
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("isResponse:%s, startTime:%d\n",
				sbe.isResponse().name(),
				sbe.startTime()));
		int i = 1;
		for (PingSbeDecoder.TimestampsDecoder timestamps : sbe.timestamps()){
			sb.append(i).append(") sinkId:").append(timestamps.sinkId())
			  .append(", ts:").append(timestamps.timestamp())
			  .append("\n");
			i++;
		}
		return sb.toString();
	}
	
	public HandlerList<PingSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static PingDecoder of(){
		return new PingDecoder(NULL_HANDLER);
	}

	public static final Handler<PingSbeDecoder> NULL_HANDLER = new Handler<PingSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder codec) {
			LOG.warn("message sent to null handler of PingDecoder");
		}
	};

}
