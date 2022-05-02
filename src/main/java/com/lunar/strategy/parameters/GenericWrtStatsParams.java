package com.lunar.strategy.parameters;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

import org.agrona.MutableDirectBuffer;

/*
 * Wrapper around SpeedArbWrtParams to serve for sending stats-only fields
 */
public class GenericWrtStatsParams extends ParamsSbeEncodable {
    private GenericWrtParams params;

    public GenericWrtStatsParams(final GenericWrtParams params) {
        this.params = params;
    }    

    @Override
    public GenericWrtStatsParams clone() {
        final GenericWrtStatsParams clone = new GenericWrtStatsParams(params);
        copyTo(clone);
        return clone;
    }

    @Override
    public void copyTo(final ParamsSbeEncodable other) {

    }

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeStrategyWrtParamsStatsOnlyWithoutHeader(buffer, offset, encoder.strategyWrtParamsSbeEncoder(), this.params);
    }

    @Override
    public int expectedEncodedLength() {
        return StrategySender.expectedEncodedLengthStatsOnly(this.params);
    }
    
    @Override
    public TemplateType templateType() {
        return params.templateType();
    }
    @Override
    public short blockLength() {
        return params.blockLength();
    }

    @Override
    public int schemaId() {
        return params.schemaId();
    }

    @Override
    public int schemaVersion() {
        return params.schemaVersion();
    }
    
    public GenericWrtParams params() {
        return this.params;
    }


}
