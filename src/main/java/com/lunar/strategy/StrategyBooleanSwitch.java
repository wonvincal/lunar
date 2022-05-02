package com.lunar.strategy;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategySwitchType;

public class StrategyBooleanSwitch implements StrategySwitch {
    final private StrategySwitchType switchType;
    final private StrategyParamSource source;
    final private long sourceSid;
    private BooleanType onOff;
    
    static public StrategyBooleanSwitch of(final StrategySwitchType switchType, final StrategyParamSource source, final long sourceSid, final BooleanType onOff) {
        return new StrategyBooleanSwitch(switchType, source, sourceSid, onOff);
    }
    
    public StrategyBooleanSwitch(final StrategySwitchType switchType, final StrategyParamSource source, final long sourceSid, final BooleanType onOff) {
        this.switchType = switchType;
        this.source = source;
        this.sourceSid = sourceSid;
        this.onOff = onOff;
    }
    
    @Override
    public StrategySwitchType switchType() {
        return switchType;
    }
    
    @Override
    public StrategyParamSource source() {
        return source;
    }
    
    @Override
    public long sourceSid() {
        return sourceSid;
    }
    
    @Override
    public BooleanType onOff() {
        return onOff;
    }
    
    public StrategyBooleanSwitch onOff(final BooleanType onOff) {
        this.onOff = onOff;
        return this;
    }    
    
}
