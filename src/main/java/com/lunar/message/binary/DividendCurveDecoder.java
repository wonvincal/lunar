package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DividendCurveSbeDecoder;
import com.lunar.message.io.sbe.DividendCurveSbeDecoder.PointsDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class DividendCurveDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(DividendCurveDecoder.class);
	private final DividendCurveSbeDecoder sbe = new DividendCurveSbeDecoder();
	private final HandlerList<DividendCurveSbeDecoder> handlerList;

	DividendCurveDecoder(Handler<DividendCurveSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<DividendCurveSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId) {
        sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, DividendCurveSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(DividendCurveSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("secSid:%d, dividendCount:%d", sbe.secSid(), sbe.points().count()));
        for (final PointsDecoder param : sbe.points()) {
            sb.append(", date:").append(param.date())
              .append(", amount:").append(param.amount());
        }
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public DividendCurveSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<DividendCurveSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static DividendCurveDecoder of(){
		return new DividendCurveDecoder(NULL_HANDLER);
	}

	static final Handler<DividendCurveSbeDecoder> NULL_HANDLER = new Handler<DividendCurveSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, DividendCurveSbeDecoder codec) {
			LOG.warn("message sent to null handler of DividendCurveDecoder");
		}
	};

}
