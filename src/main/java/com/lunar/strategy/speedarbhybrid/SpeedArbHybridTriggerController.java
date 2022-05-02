package com.lunar.strategy.speedarbhybrid;

import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.TriggerHandler;

public class SpeedArbHybridTriggerController {
    final private SpeedArbHybridContext m_context;
    
    public SpeedArbHybridTriggerController(final SpeedArbHybridContext context) {
        m_context = context;
    }
    
    public void subscribe(final StrategySecurity security, final StrategyTriggerType triggerType, final TriggerHandler handler) {
        switch (triggerType) {
        case VELOCITY_10MS:
            m_context.getVelocityTriggerGenerator10ms(security.underlying()).registerHandler(handler);
            break;
        case VELOCITY_5MS:
            m_context.getVelocityTriggerGenerator5ms(security.underlying()).registerHandler(handler);
            break;
        case ALLOW_ALL:
            m_context.getAllowAllTriggerGenerator().registerHandler(handler);
        default:
            break;
        }
    }
    
    public void resetAllTriggers(final StrategySecurity security) {
        m_context.getVelocityTriggerGenerator10ms(security.underlying()).reset();
        m_context.getVelocityTriggerGenerator5ms(security.underlying()).reset();
        m_context.getAllowAllTriggerGenerator().reset();
    }

}
