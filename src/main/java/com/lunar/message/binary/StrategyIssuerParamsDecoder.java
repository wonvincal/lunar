package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder.ParametersDecoder;
import com.lunar.service.ServiceConstant;

public class StrategyIssuerParamsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategyIssuerParamsDecoder.class);
	private final StrategyIssuerParamsSbeDecoder sbe = new StrategyIssuerParamsSbeDecoder();
	private final HandlerList<StrategyIssuerParamsSbeDecoder> handlerList;

	StrategyIssuerParamsDecoder(Handler<StrategyIssuerParamsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategyIssuerParamsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategyIssuerParamsSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(StrategyIssuerParamsSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("strategyId:%d, secSid:%d, parametersCount:%d", sbe.strategyId(), sbe.issuerSid(), sbe.parameters().count()));
        for (final ParametersDecoder param : sbe.parameters()) {
            sb.append(", parameterId:").append(param.parameterId())
              .append(", parameterValueLong:").append(param.parameterValueLong());
        }
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public StrategyIssuerParamsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategyIssuerParamsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategyIssuerParamsDecoder of(){
		return new StrategyIssuerParamsDecoder(NULL_HANDLER);
	}

	static final Handler<StrategyIssuerParamsSbeDecoder> NULL_HANDLER = new Handler<StrategyIssuerParamsSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyIssuerParamsSbeDecoder");
		}
	};

}
