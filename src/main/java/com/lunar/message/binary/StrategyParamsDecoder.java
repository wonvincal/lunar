package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder.ParametersDecoder;
import com.lunar.service.ServiceConstant;

public class StrategyParamsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategyParamsDecoder.class);
	private final StrategyParamsSbeDecoder sbe = new StrategyParamsSbeDecoder();
	private final HandlerList<StrategyParamsSbeDecoder> handlerList;

	StrategyParamsDecoder(Handler<StrategyParamsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategyParamsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}
	
    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategyParamsSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(StrategyParamsSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("strategyId:%d, parametersCount:%d", sbe.strategyId(), sbe.parameters().count()));
        for (final ParametersDecoder param : sbe.parameters()) {
            sb.append(", parameterId:").append(param.parameterId())
              .append(", parameterValueLong:").append(param.parameterValueLong());
        }
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public StrategyParamsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategyParamsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategyParamsDecoder of(){
		return new StrategyParamsDecoder(NULL_HANDLER);
	}

	static final Handler<StrategyParamsSbeDecoder> NULL_HANDLER = new Handler<StrategyParamsSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyParams");
		}
	};

}
