package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.FragmentSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class FragmentDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(FragmentDecoder.class);
	private final FragmentSbeDecoder sbe = new FragmentSbeDecoder();
	private final HandlerList<FragmentSbeDecoder> handlerList;

	FragmentDecoder(Handler<FragmentSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<FragmentSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(FragmentSbeDecoder sbe){
		return String.format("fragmentSeq:%d, isLast:%s, payloadSize:%d",
				sbe.fragmentSeq(),
				sbe.isLast().name(),
				sbe.payloadLength());
	}
	
	public FragmentSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<FragmentSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static FragmentDecoder of(){
		return new FragmentDecoder(NULL_HANDLER);
	}

	static final Handler<FragmentSbeDecoder> NULL_HANDLER = new Handler<FragmentSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, FragmentSbeDecoder codec) {
			LOG.warn("message sent to null handler of FragmentDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
}
