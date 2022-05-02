package com.lunar.strategy.cbbctest;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.StrategyExplainType;
import com.lunar.strategy.StrategyExplain;

public class CbbcTestExplain implements StrategyExplain {
    private static final Logger LOG = LogManager.getLogger(CbbcTestExplain.class);

	private static EventValueType[] VALUE_TYPES = {EventValueType.SECURITY_SID, EventValueType.ORDER_SID, EventValueType.STRATEGY_EXPLAIN, EventValueType.UNDERLYING_BID};
	private StrategyExplainType explainType;
	private final long[] values;
	private Security security;
	private long triggerSeqNum;
		
	public CbbcTestExplain() {
		this.values = new long[VALUE_TYPES.length];
		this.explainType = StrategyExplainType.PREDICTION_BY_BID_BUCKET_BUY_SIGNAL;
		values[2] = explainType.value();
	}
	
	public void security(final Security security) {		
		this.security = security;
		this.values[0] = security.sid();
	}

	@Override
	public StrategyExplainType strategyExplain() {
		return this.explainType;
	}

	@Override
	public EventValueType[] eventValueTypes() {
		return VALUE_TYPES;
	}

	@Override
	public long[] eventValues() {
		return values;
	}

	@Override
	public StrategyExplain orderSid(long orderSid) {
		values[1] = orderSid;
		return this;
	}
	
	public void setExplainValues(final int undBid) {
		values[3] = undBid;
	}

	@Override
	public void logExplainForBuyOrder() {
		LOG.info("Received buy order request response: ordSid {}, secCode {}, reason {}, undBid {}",
				box(values[1]), security.code(), box(values[2]), box(values[3]));
	}

	@Override
	public void logExplainForSellOrder() {
	}

    @Override
    public long triggerSeqNum() {
        return triggerSeqNum;
    }

    @Override
    public void triggerSeqNum(final long triggerSeqNum) {
        this.triggerSeqNum = triggerSeqNum;
    }
}
