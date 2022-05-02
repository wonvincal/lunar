package com.lunar.strategy;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategySwitchType;

public class StrategyCircuitSwitch extends CircuitSwitch implements StrategySwitch {
    public interface SwitchHandler {
        void onPerformTask(final long securitySid);
        void onStopTask(final long securitySid);
    }

    private static final SwitchHandler NULL_SWITCH_HANDLER = new SwitchHandler() {
        @Override
        public void onPerformTask(long securitySid) {
        }

        @Override
        public void onStopTask(long securitySid) {
        }        
    };
    
    final private StrategySwitchType switchType;
    final private StrategyParamSource source;
    final private long sourceSid;
    private final SwitchHandler m_handler;
    
    public StrategyCircuitSwitch(final int initialChildCircuits, final StrategySwitchType switchType, final StrategyParamSource source, final long sourceSid) {
        this(initialChildCircuits, switchType, source, sourceSid, NULL_SWITCH_HANDLER);
    }
    
    public StrategyCircuitSwitch(final int initialChildCircuits, final StrategySwitchType switchType, final StrategyParamSource source, final long sourceSid, final SwitchHandler handler) {
        super(initialChildCircuits);
        this.switchType = switchType;
        this.source = source;
        this.sourceSid = sourceSid;
        m_handler = handler;
    }
    
    protected boolean canStartTask() {
        return true;
    }
    
    protected void performTask() {
        m_handler.onPerformTask(sourceSid);
    }
    
    protected boolean canStopTask() {
        return true;
    }

    protected void stopTask() {
        m_handler.onStopTask(sourceSid);
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
        return this.isOn() ? BooleanType.TRUE : BooleanType.FALSE;
    }    
}
