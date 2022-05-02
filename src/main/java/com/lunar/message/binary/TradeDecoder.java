package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class TradeDecoder implements Decoder {
	private final TradeSbeDecoder sbe = new TradeSbeDecoder();
	private Handler<TradeSbeDecoder> interceptor;
	private final HandlerList<TradeSbeDecoder> handlerList;
	
	TradeDecoder(Handler<TradeSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<TradeSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public TradeDecoder interceptor(Handler<TradeSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public TradeDecoder clearInterceptor(){
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
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, TradeSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}
	
	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(TradeSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("type:trade, channelId:%d, channelSeq:%d, tradeSid:%d, orderSid:%d, secSid:%d, status:%s, tradeStatus:%s, side:%s, executionQty:%d, executionPrice:%d",
						sbe.channelId(),
						sbe.channelSnapshotSeq(),
						sbe.tradeSid(),
						sbe.orderSid(),
						sbe.secSid(),
						sbe.status().name(),
						sbe.tradeStatus().name(),
						sbe.side().name(),
						sbe.executionQty(),
						sbe.executionPrice()));
		return sb.toString();
	}
	
	public HandlerList<TradeSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static TradeDecoder of(){
		return new TradeDecoder(NULL_HANDLER);
	}

	static final Handler<TradeSbeDecoder> NULL_HANDLER = new Handler<TradeSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
			LOG.warn("Message sent to null handler of TradeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
	public static int length(TradeSbeDecoder codec){
		return TradeSbeDecoder.BLOCK_LENGTH;
	}
}
