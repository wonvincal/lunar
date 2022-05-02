package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderAcceptedWithOrderInfoDecoder implements Decoder {
	private final OrderAcceptedWithOrderInfoSbeDecoder sbe = new OrderAcceptedWithOrderInfoSbeDecoder();
	private Handler<OrderAcceptedWithOrderInfoSbeDecoder> interceptor;
	private final HandlerList<OrderAcceptedWithOrderInfoSbeDecoder> handlerList;
	
	OrderAcceptedWithOrderInfoDecoder(Handler<OrderAcceptedWithOrderInfoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderAcceptedWithOrderInfoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderAcceptedWithOrderInfoDecoder interceptor(Handler<OrderAcceptedWithOrderInfoSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderAcceptedWithOrderInfoDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}
	
	@Override
	public String dumpWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int reponseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId) {
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderAcceptedWithOrderInfoSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("channelId:%d, channelSeq:%d, orderSid:%d, side:%s, secSid:%d, price:%d, leavesQty:%d, cumulativeQty:%d, status:%s, orderType:%s",
						sbe.channelId(),
						sbe.channelSeq(),
						sbe.orderSid(),
						sbe.side().name(),
						sbe.secSid(),
						sbe.price(),
						sbe.leavesQty(),
						sbe.cumulativeQty(),
						sbe.status(),
						sbe.orderType().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderAcceptedWithOrderInfoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderAcceptedWithOrderInfoDecoder of(){
		return new OrderAcceptedWithOrderInfoDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderAcceptedWithOrderInfoSbeDecoder> NULL_HANDLER = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderAcceptedWithOrderInfoDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	public static int length(OrderAcceptedWithOrderInfoSbeDecoder sbe){
		return OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
	}}
