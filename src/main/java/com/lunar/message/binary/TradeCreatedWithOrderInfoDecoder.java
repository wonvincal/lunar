package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class TradeCreatedWithOrderInfoDecoder implements Decoder {
	private final TradeCreatedWithOrderInfoSbeDecoder sbe = new TradeCreatedWithOrderInfoSbeDecoder();
	private Handler<TradeCreatedWithOrderInfoSbeDecoder> interceptor;
	private final HandlerList<TradeCreatedWithOrderInfoSbeDecoder> handlerList;
	
	TradeCreatedWithOrderInfoDecoder(Handler<TradeCreatedWithOrderInfoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<TradeCreatedWithOrderInfoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public TradeCreatedWithOrderInfoDecoder interceptor(Handler<TradeCreatedWithOrderInfoSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public TradeCreatedWithOrderInfoDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, TradeCreatedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(TradeCreatedWithOrderInfoSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, status:%s, orderType:%s, executionQty:%d, executionPrice:%d",
						sbe.orderSid(),
						sbe.status().name(),
						sbe.orderType(),
						sbe.executionQty(),
						sbe.executionPrice()));
		return sb.toString();
	}
	
	public HandlerList<TradeCreatedWithOrderInfoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static TradeCreatedWithOrderInfoDecoder of(){
		return new TradeCreatedWithOrderInfoDecoder(NULL_HANDLER);
	}

	public static final Handler<TradeCreatedWithOrderInfoSbeDecoder> NULL_HANDLER = new Handler<TradeCreatedWithOrderInfoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder codec) {
			LOG.warn("Message sent to null handler of TradeCreatedWithOrderInfoDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	public static int length(TradeCreatedWithOrderInfoSbeDecoder codec){
		return TradeCreatedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
	}

}
