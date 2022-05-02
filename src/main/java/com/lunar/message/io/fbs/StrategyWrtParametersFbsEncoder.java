package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder.ParametersDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class StrategyWrtParametersFbsEncoder {
	static final Logger LOG = LogManager.getLogger(StrategyWrtParametersFbsEncoder.class);
	static private final int NULL_OFFSET = -1;

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, StrategyWrtParamsSbeDecoder params){
		int limit = params.limit();
        final ParametersDecoder parameters = params.parameters(); 
        final int numParams = parameters.count();
        final int[] paramEndOffsets = new int[numParams];
        int i = 0;
        for (ParametersDecoder param : parameters) {
            paramEndOffsets[i] = StrategyParamValueFbs.createStrategyParamValueFbs(builder, param.parameterId(), NULL_OFFSET, param.parameterValueLong());
            i++;
        }
        final int paramsOffset = StrategyWrtParametersFbs.createParametersVector(builder, paramEndOffsets);
        StrategyWrtParametersFbs.startStrategyWrtParametersFbs(builder);
        StrategyWrtParametersFbs.addStrategyId(builder, params.strategyId());
        StrategyWrtParametersFbs.addSecSid(builder, params.secSid());
        StrategyWrtParametersFbs.addParameters(builder, paramsOffset);
        params.limit(limit);
        return StrategyWrtParametersFbs.endStrategyWrtParametersFbs(builder);

	}
}
