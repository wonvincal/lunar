package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder.ParametersDecoder;
import com.lunar.service.ServiceConstant;

public class StrategyWrtParamsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategyWrtParamsDecoder.class);
	private final StrategyWrtParamsSbeDecoder sbe = new StrategyWrtParamsSbeDecoder();
	private final HandlerList<StrategyWrtParamsSbeDecoder> handlerList;

	StrategyWrtParamsDecoder(Handler<StrategyWrtParamsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategyWrtParamsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategyWrtParamsSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(StrategyWrtParamsSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("strategyId:%d, secSid:%d, parametersCount:%d", sbe.strategyId(), sbe.secSid(), sbe.parameters().count()));
        for (final ParametersDecoder param : sbe.parameters()) {
            sb.append(", parameterId:").append(param.parameterId())
              .append(", parameterValueLong:").append(param.parameterValueLong());
        }
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public StrategyWrtParamsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategyWrtParamsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategyWrtParamsDecoder of(){
		return new StrategyWrtParamsDecoder(NULL_HANDLER);
	}

	static final Handler<StrategyWrtParamsSbeDecoder> NULL_HANDLER = new Handler<StrategyWrtParamsSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyWrtParamsSbeDecoder");
		}
	};

}
