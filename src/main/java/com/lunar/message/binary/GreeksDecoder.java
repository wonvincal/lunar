package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class GreeksDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(GreeksDecoder.class);
	private final GreeksSbeDecoder sbe = new GreeksSbeDecoder();
	private final HandlerList<GreeksSbeDecoder> handlerList;

	GreeksDecoder(Handler<GreeksSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<GreeksSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId) {
        sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, GreeksSbeDecoder.SCHEMA_VERSION);
        handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
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

	public static String decodeToString(GreeksSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("secSid:%d, delta: %d, vega: %d, impliedVol: %d, refSpot: %d", 
        		sbe.secSid(), sbe.delta(), sbe.vega(), sbe.impliedVol(), sbe.refSpot()));
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public GreeksSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<GreeksSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static GreeksDecoder of(){
		return new GreeksDecoder(NULL_HANDLER);
	}

	static final Handler<GreeksSbeDecoder> NULL_HANDLER = new Handler<GreeksSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, GreeksSbeDecoder codec) {
			LOG.warn("message sent to null handler of GreeksSbeDecoder");
		}
	};

}
