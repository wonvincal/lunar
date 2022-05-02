package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderDecoder implements Decoder<OrderSbeDecoder> {
	static final Logger LOG = LogManager.getLogger(OrderDecoder.class);
	private final OrderSbeDecoder sbe = new OrderSbeDecoder();
	private Handler<OrderSbeDecoder> interceptor;
	private final HandlerList<OrderSbeDecoder> handlerList;
	
	OrderDecoder(Handler<OrderSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderDecoder interceptor(Handler<OrderSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderDecoder clearInterceptor(){
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
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, OrderSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}
	
	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("type:order, channelId:%d, channelSeq:%d, orderSid:%d, secSid:%d, orderId:%d, orderType:%s, quantity:%d, side:%s, status:%s, createTime:%d, isAlgoOrder:%s, limitTickLevel:%d",
						sbe.channelId(),
						sbe.channelSnapshotSeq(),
						sbe.orderSid(),
						sbe.secSid(),
						sbe.orderId(),
						sbe.orderType().name(),
						sbe.quantity(),
						sbe.side().name(),
						sbe.status().name(),
						sbe.createTime(),
						sbe.isAlgoOrder().name(),
						sbe.limitPrice()));
		return sb.toString();
	}
	
	public static OrderDecoder of(){
		return new OrderDecoder(NULL_HANDLER);
	}

	static final Handler<OrderSbeDecoder> NULL_HANDLER = new Handler<OrderSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
			LOG.warn("message sent to null handler of OrderDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	@Override
	public boolean registerHandler(Handler<OrderSbeDecoder> handler){
		this.handlerList.add(handler);
		return true;
	}

	@Override
	public boolean unregisterHandler(Handler<OrderSbeDecoder> handler){
		this.handlerList.remove(handler);
		return true;
	}
	
	HandlerList<OrderSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static int length(OrderSbeDecoder codec){
		return OrderSbeDecoder.BLOCK_LENGTH;
	}

}
