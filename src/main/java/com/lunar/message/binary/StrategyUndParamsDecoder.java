package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder.ParametersDecoder;
import com.lunar.service.ServiceConstant;

public class StrategyUndParamsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategyUndParamsDecoder.class);
	private final StrategyUndParamsSbeDecoder sbe = new StrategyUndParamsSbeDecoder();
	private final HandlerList<StrategyUndParamsSbeDecoder> handlerList;

	StrategyUndParamsDecoder(Handler<StrategyUndParamsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategyUndParamsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategyUndParamsSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(StrategyUndParamsSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("strategyId:%d, undSid:%d, parametersCount:%d", sbe.strategyId(), sbe.undSid(), sbe.parameters().count()));
        for (final ParametersDecoder param : sbe.parameters()) {
            sb.append(", parameterId:").append(param.parameterId())
              .append(", parameterValueLong:").append(param.parameterValueLong());
        }
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public StrategyUndParamsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategyUndParamsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategyUndParamsDecoder of(){
		return new StrategyUndParamsDecoder(NULL_HANDLER);
	}

	static final Handler<StrategyUndParamsSbeDecoder> NULL_HANDLER = new Handler<StrategyUndParamsSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyUndParamsSbeDecoder");
		}
	};

}
