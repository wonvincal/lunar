package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder.ParametersDecoder;

public class StrategyIssuerUndParametersFbsEncoder {
	static final Logger LOG = LogManager.getLogger(StrategyIssuerUndParametersFbsEncoder.class);
    static private final int NULL_OFFSET = -1;

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, StrategyIssuerUndParamsSbeDecoder params){
		int limit = params.limit();
        final ParametersDecoder parameters = params.parameters(); 
        final int numParams = parameters.count();
        final int[] paramEndOffsets = new int[numParams];
		int i = 0;
		for (ParametersDecoder param : parameters) {
		    paramEndOffsets[i] = StrategyParamValueFbs.createStrategyParamValueFbs(builder, param.parameterId(), NULL_OFFSET, param.parameterValueLong());
		    i++;
		}
		final int paramsOffset = StrategyIssuerUndParametersFbs.createParametersVector(builder, paramEndOffsets);
        StrategyIssuerUndParametersFbs.startStrategyIssuerUndParametersFbs(builder);
        StrategyIssuerUndParametersFbs.addStrategyId(builder, params.strategyId());
        StrategyIssuerUndParametersFbs.addIssuerUndSid(builder, params.issuerUndSid());
        StrategyIssuerUndParametersFbs.addIssuerSid(builder, params.issuerSid());
        StrategyIssuerUndParametersFbs.addUndSid(builder, params.undSid());
		StrategyIssuerUndParametersFbs.addParameters(builder, paramsOffset);
//        LOG.info("Encoded strategy issuer und into fbs [issuerSid:{}, undSid:{}, issuerUndSid:{}]", params.issuerSid(), params.undSid(), params.issuerUndSid());
		params.limit(limit);
		return StrategyIssuerUndParametersFbs.endStrategyIssuerUndParametersFbs(builder);
	}
}
