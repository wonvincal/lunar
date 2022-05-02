package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class IssuerDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(IssuerDecoder.class);
	private static final int BYTE_ARRAY_SIZE = 128;
	private final IssuerSbeDecoder sbe = new IssuerSbeDecoder();
	private final HandlerList<IssuerSbeDecoder> handlerList;

	IssuerDecoder(Handler<IssuerSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<IssuerSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}
	
   @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
        sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, IssuerSbeDecoder.SCHEMA_VERSION);
        handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
    }

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(IssuerSbeDecoder sbe){
		byte[] bytes = new byte[BYTE_ARRAY_SIZE];
		sbe.getCode(bytes, 0);
		String code = new String(bytes);
		sbe.getName(bytes, 0);
		String name = new String(bytes);
		return String.format("lastUpdateSeq:%d, sid:%d, code:%s, name:%s",
				sbe.lastUpdateSeq(),
				sbe.sid(),
				code,
				name);
	}
	
	public IssuerSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<IssuerSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static IssuerDecoder of(){
		return new IssuerDecoder(NULL_HANDLER);
	}

	static final Handler<IssuerSbeDecoder> NULL_HANDLER = new Handler<IssuerSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, IssuerSbeDecoder codec) {
			LOG.warn("message sent to null handler of IssuerSbeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
