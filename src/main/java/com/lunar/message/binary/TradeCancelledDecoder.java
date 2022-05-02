package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class TradeCancelledDecoder implements Decoder {
	private final TradeCancelledSbeDecoder sbe = new TradeCancelledSbeDecoder();
	private Handler<TradeCancelledSbeDecoder> interceptor;
	private final HandlerList<TradeCancelledSbeDecoder> handlerList;
	
	TradeCancelledDecoder(Handler<TradeCancelledSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<TradeCancelledSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public TradeCancelledDecoder interceptor(Handler<TradeCancelledSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public TradeCancelledDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, TradeCancelledSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(TradeCancelledSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, status:%s, executionQty:%d, executionPrice:%d",
						sbe.orderSid(),
						sbe.status().name(),
						sbe.cancelledExecutionQty(),
						sbe.cancelledExecutionPrice()));
		return sb.toString();
	}
	
	public HandlerList<TradeCancelledSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static TradeCancelledDecoder of(){
		return new TradeCancelledDecoder(NULL_HANDLER);
	}

	public static final Handler<TradeCancelledSbeDecoder> NULL_HANDLER = new Handler<TradeCancelledSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCancelledSbeDecoder codec) {
			LOG.warn("message sent to command null handler");
		}
	};

	public static int length(TradeCancelledSbeDecoder codec){
		return TradeCancelledSbeDecoder.BLOCK_LENGTH;
	}

}
