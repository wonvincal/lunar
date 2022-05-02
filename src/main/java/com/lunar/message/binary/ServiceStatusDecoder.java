package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Message;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.service.ServiceConstant;

public class ServiceStatusDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(ServiceStatusDecoder.class);
	private final ServiceStatusSbeDecoder sbe = new ServiceStatusSbeDecoder();
	private final HandlerList<ServiceStatusSbeDecoder> handlerList;
	
	ServiceStatusDecoder(Handler<ServiceStatusSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<ServiceStatusSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	@Override
	public Message decodeAsMessage(DirectBuffer buffer, int offset, MessageHeaderDecoder decoder) {
		throw new UnsupportedOperationException();
	}
	
	public static String decodeToString(ServiceStatusSbeDecoder sbe){
		return String.format("systemId:%d, sinkId:%d, serviceType:%s, statusType:%s, modifyTimeAtOrigin:%d, healthCheckTime:%d",
				sbe.systemId(),
				sbe.sinkId(),
				sbe.serviceType().name(),
				sbe.statusType().name(),
				sbe.modifyTimeAtOrigin(),
				sbe.healthCheckTime());
	}

	public HandlerList<ServiceStatusSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static ServiceStatusDecoder of(){
		return new ServiceStatusDecoder(NULL_HANDLER);
	}

	static final Handler<ServiceStatusSbeDecoder> NULL_HANDLER = new Handler<ServiceStatusSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
			LOG.warn("Message sent to null handler of ServiceStatusDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
}
