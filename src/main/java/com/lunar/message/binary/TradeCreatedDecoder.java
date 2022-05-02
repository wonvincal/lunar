package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class TradeCreatedDecoder implements Decoder {
	private final TradeCreatedSbeDecoder sbe = new TradeCreatedSbeDecoder();
	private Handler<TradeCreatedSbeDecoder> interceptor;
	private final HandlerList<TradeCreatedSbeDecoder> handlerList;
	
	TradeCreatedDecoder(Handler<TradeCreatedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<TradeCreatedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public TradeCreatedDecoder interceptor(Handler<TradeCreatedSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public TradeCreatedDecoder clearInterceptor(){
		this.interceptor = this.handlerList;
		return this;
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		interceptor.handle(buffer, offset, header, sbe);		
	}
	
	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, TradeCreatedSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}
	
	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(TradeCreatedSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, status:%s, executionQty:%d, executionPrice:%d",
						sbe.orderSid(),
						sbe.status().name(),
						sbe.executionQty(),
						sbe.executionPrice()));
		return sb.toString();
	}
	
	public HandlerList<TradeCreatedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static TradeCreatedDecoder of(){
		return new TradeCreatedDecoder(NULL_HANDLER);
	}

	public static final Handler<TradeCreatedSbeDecoder> NULL_HANDLER = new Handler<TradeCreatedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder codec) {
			LOG.warn("Message sent to null handler of TradeCreatedDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
	public static int length(TradeCreatedSbeDecoder codec){
		return TradeCreatedSbeDecoder.BLOCK_LENGTH;
	}

}
