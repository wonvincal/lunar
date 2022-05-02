package com.lunar.strategy;

import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategySwitchSbeEncoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

import org.agrona.MutableDirectBuffer;

public interface StrategySwitch extends SbeEncodable {
    public StrategySwitchType switchType();
    
    public StrategyParamSource source();
    
    public long sourceSid();
    
    public BooleanType onOff();

    @Override
    default public int encode(final EntityEncoder encoder, final MutableDirectBuffer buffer, final int offset, final MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeStrategySwitchWithoutHeader(buffer, offset, encoder.strategySwitchSbeEncoder(), switchType(), source(), sourceSid(), onOff());
    }
    @Override
    default public TemplateType templateType() {
        return TemplateType.StrategySwitch;
    }
    @Override
    default public short blockLength() {
        return StrategySwitchSbeEncoder.BLOCK_LENGTH;
    }
    @Override
    default public int expectedEncodedLength() {
        return StrategySwitchSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    default public int schemaId() {
        return StrategySwitchSbeEncoder.SCHEMA_ID;
    }

    @Override
    default public int schemaVersion() {
        return StrategySwitchSbeEncoder.SCHEMA_VERSION;
    }    
    
}
