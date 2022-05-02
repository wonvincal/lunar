package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder.ParametersDecoder;
import com.lunar.service.ServiceConstant;

public class StrategyIssuerUndParamsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategyIssuerUndParamsDecoder.class);
	private final StrategyIssuerUndParamsSbeDecoder sbe = new StrategyIssuerUndParamsSbeDecoder();
	private final HandlerList<StrategyIssuerUndParamsSbeDecoder> handlerList;

	StrategyIssuerUndParamsDecoder(Handler<StrategyIssuerUndParamsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategyIssuerUndParamsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategyIssuerUndParamsSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(StrategyIssuerUndParamsSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("strategyId:%d, issuerSid:%d, undSid:%d, parametersCount:%d", sbe.strategyId(), sbe.issuerSid(), sbe.undSid(), sbe.parameters().count()));
        for (final ParametersDecoder param : sbe.parameters()) {
            sb.append(", parameterId:").append(param.parameterId())
              .append(", parameterValueLong:").append(param.parameterValueLong());
        }
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public StrategyIssuerUndParamsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategyIssuerUndParamsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategyIssuerUndParamsDecoder of(){
		return new StrategyIssuerUndParamsDecoder(NULL_HANDLER);
	}

	static final Handler<StrategyIssuerUndParamsSbeDecoder> NULL_HANDLER = new Handler<StrategyIssuerUndParamsSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyIssuerUndParamsSbeDecoder");
		}
	};
}
