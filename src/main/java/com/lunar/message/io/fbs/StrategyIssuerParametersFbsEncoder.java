package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder.ParametersDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class StrategyIssuerParametersFbsEncoder {
	static final Logger LOG = LogManager.getLogger(StrategyIssuerParametersFbsEncoder.class);
    static private final int NULL_OFFSET = -1;

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, StrategyIssuerParamsSbeDecoder params){
		int limit = params.limit();
        final ParametersDecoder parameters = params.parameters(); 
        final int numParams = parameters.count();
        final int[] paramEndOffsets = new int[numParams];
		int i = 0;
		for (ParametersDecoder param : parameters) {
		    paramEndOffsets[i] = StrategyParamValueFbs.createStrategyParamValueFbs(builder, param.parameterId(), NULL_OFFSET, param.parameterValueLong());
		    i++;
		}
		final int paramsOffset = StrategyIssuerParametersFbs.createParametersVector(builder, paramEndOffsets);
		StrategyIssuerParametersFbs.startStrategyIssuerParametersFbs(builder);
		StrategyIssuerParametersFbs.addStrategyId(builder, params.strategyId());
		StrategyIssuerParametersFbs.addIssuerSid(builder, params.issuerSid());
		StrategyIssuerParametersFbs.addParameters(builder, paramsOffset);
		params.limit(limit);
		return StrategyIssuerParametersFbs.endStrategyIssuerParametersFbs(builder);
	}
}
