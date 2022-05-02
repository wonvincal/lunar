package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class ChartDataDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(ChartDataDecoder.class);
	private final ChartDataSbeDecoder sbe = new ChartDataSbeDecoder();
	private final HandlerList<ChartDataSbeDecoder> handlerList;

	ChartDataDecoder(Handler<ChartDataSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<ChartDataSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, ChartDataSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset, header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(ChartDataSbeDecoder sbe){
		int origLimit = sbe.limit();
		sbe.limit(sbe.offset() + ChartDataSbeDecoder.BLOCK_LENGTH);
		String msg = String.format("secSid:%d, dataTime:%d, open:%d, high:%d, close:%s, low:%s",
				sbe.secSid(),
				sbe.dataTime(),
				sbe.open(),
				sbe.high(),
				sbe.close(),
				sbe.low());
		sbe.limit(origLimit);
		return msg;
	}

	public ChartDataSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<ChartDataSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static ChartDataDecoder of(){
		return new ChartDataDecoder(NULL_HANDLER);
	}

	static final Handler<ChartDataSbeDecoder> NULL_HANDLER = new Handler<ChartDataSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ChartDataSbeDecoder codec) {
			LOG.warn("Message sent to null handler of ChartDataDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
}
