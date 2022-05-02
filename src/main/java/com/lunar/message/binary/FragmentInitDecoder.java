package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.FragmentInitSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class FragmentInitDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(FragmentInitDecoder.class);
	private final FragmentInitSbeDecoder sbe = new FragmentInitSbeDecoder();
	private final HandlerList<FragmentInitSbeDecoder> handlerList;

	FragmentInitDecoder(Handler<FragmentInitSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<FragmentInitSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(FragmentInitSbeDecoder sbe){
		return String.format("fragmentSeq:%d, payloadSize:%d",
				sbe.fragmentSeq(),
				sbe.payloadLength());
	}
	
	public FragmentInitSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<FragmentInitSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static FragmentInitDecoder of(){
		return new FragmentInitDecoder(NULL_HANDLER);
	}

	static final Handler<FragmentInitSbeDecoder> NULL_HANDLER = new Handler<FragmentInitSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, FragmentInitSbeDecoder codec) {
			LOG.warn("message sent to null handler of FragmentInitDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
}
