package com.lunar.strategy;

import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyStatusType;

abstract public class Strategy {
    private final long m_strategySid;
	private final StrategySecurity m_security;
	
	public Strategy(final long strategySid, final StrategySecurity security) {
	    m_strategySid = strategySid;
		m_security = security;
	}

	public long getStrategySid() {
	    return m_strategySid;
	}

	public StrategySecurity getSecurity() {
		return m_security;
	}
	
	abstract public StrategyStatusType getStatus();
	
    abstract public void pendingSwitchOn();
    
    abstract public void cancelSwitchOn();
    
    abstract public void proceedSwitchOn() throws Exception;
    
    abstract public void switchOn() throws Exception;

    abstract public void switchOff() throws Exception;

    abstract public void switchOff(final StrategyExitMode exitMode) throws Exception;

    abstract public void captureProfit() throws Exception;
    
    abstract public void placeSellOrder() throws Exception;
    
    abstract public void start() throws Exception;

    abstract public void reset() throws Exception;
	
    abstract public boolean isOn();

}
