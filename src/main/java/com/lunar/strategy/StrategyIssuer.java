package com.lunar.strategy;

import com.lunar.entity.Issuer;

public class StrategyIssuer extends Issuer {
	private final int assignedThrottleTrackerIndex; 
	
    public static StrategyIssuer of(final long sid, final String code, final String name, int assignedThrottleTrackerIndex) {
        return new StrategyIssuer(sid, code, name, assignedThrottleTrackerIndex);
    }
    
	StrategyIssuer(long sid, String code, String name, int assignedThrottleTrackerIndex) {
		super(sid, code, name);
		this.assignedThrottleTrackerIndex = assignedThrottleTrackerIndex;
	}

	public int assignedThrottleTrackerIndex(){
		return assignedThrottleTrackerIndex;
	}
}
