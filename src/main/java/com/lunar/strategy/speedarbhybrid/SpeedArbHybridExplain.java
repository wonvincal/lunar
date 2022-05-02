package com.lunar.strategy.speedarbhybrid;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.StrategyExplainType;
import com.lunar.strategy.StrategyExplain;

public class SpeedArbHybridExplain implements StrategyExplain {
    private static final Logger LOG = LogManager.getLogger(SpeedArbHybridExplain.class);

    public class Flags {
        public static final int VOL_DOWN = 1;
        public static final int TO_MAKING = 2;
        public static final int WIDE = 4;
    }
    
	private static EventValueType[] VALUE_TYPES = {EventValueType.SECURITY_SID, EventValueType.ORDER_SID, EventValueType.STRATEGY_EXPLAIN,
			EventValueType.PREV_UNDERLYING_BID, EventValueType.PREV_UNDERLYING_ASK, EventValueType.UNDERLYING_BID, EventValueType.UNDERLYING_ASK,
			EventValueType.PREV_WARRANT_BID, EventValueType.PREV_WARRANT_ASK, EventValueType.WARRANT_BID, EventValueType.WARRANT_ASK,
			EventValueType.VELOCITY, EventValueType.DELTA, EventValueType.WARRANT_SPREAD, EventValueType.TICK_SENSITIVITY, EventValueType.HIGH_WARRANT_BID,
			EventValueType.BEST_SPOT, EventValueType.WAVG_SPOT, EventValueType.PREV_WAVG_SPOT, EventValueType.BUCKET_SIZE,
			EventValueType.PRICING_MODE, EventValueType.GENERIC_EVENT_FLAGS};
	private StrategyExplainType explainType;
	private final long[] values;
	private final Security security;
	private final StringBuilder[] extraSbs = new StringBuilder[2];  
	private long triggerSeqNum;
		
	public SpeedArbHybridExplain(final Security security) {
		this.values = new long[VALUE_TYPES.length];
		this.values[0] = security.sid();
		this.security = security;
		for (int i = 0; i < extraSbs.length; i++){
			extraSbs[i] = new StringBuilder(21);
		}
	}

	public SpeedArbHybridExplain strategyExplain(final StrategyExplainType explainType) {
		this.explainType = explainType;
		values[2] = explainType.value();
		return this;
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
	
	public void setExplainValues(final int prevUndBid, final int prevUndAsk, final int undBid, final int undAsk, final int prevWrtBid, final int prevWrtAsk, final int wrtBid, final int wrtAsk, final long velocity, final float delta, final int warrantSpread, final int tickSensitivity, final int highWrtBid, final long bestSpot, final long wavgSpot, final long prevWAvgSpot, final int bucketSize, final PricingMode pricingMode, final int flags) {
		values[3] = prevUndBid;
		values[4] = prevUndAsk;
		values[5] = undBid;
		values[6] = undAsk;
		values[7] = prevWrtBid;
		values[8] = prevWrtAsk;
		values[9] = wrtBid;
		values[10] = wrtAsk;
		values[11] = velocity;
		values[12] = (long)delta;
		values[13] = warrantSpread;
		values[14] = tickSensitivity;
		values[15] = highWrtBid;
		values[16] = bestSpot;
		values[17] = wavgSpot;
		values[18] = prevWAvgSpot;
		values[19] = bucketSize;
		values[20] = pricingMode.value();
		values[21] = flags;
	}

	@Override
	public void logExplainForBuyOrder() {
	    // DO NOT ADD ANYMORE TO THIS MESSAGE - BOX HAS A LIMIT OF 16 USES
		LOG.info("Received buy order request response: ordSid {}, secCode {}, reason {}, prevUndBid {}, prevUndAsk {}, prevWAvg {}, undBid {}, undAsk {}, wavg {}, prevWrtBid {}, prevWrtAsk {}, wrtBid {}, wrtAsk {}, velocity {}, delta {}, wrtSpread {}, tickSens {}, highWrtBid {}, bestSpot {}", 
				box(values[1]), security.code(), box(values[2]), box(values[3]), box(values[4]),
				localBox(extraSbs[0], values[18]),
				box(values[5]), box(values[6]),
				localBox(extraSbs[1], values[17]),
				box(values[7]), box(values[8]), box(values[9]), box(values[10]), box(values[11]), box(values[12]), box(values[13]), box(values[14]), box(values[15]), box(values[16]));
		
	}

	@Override
	public void logExplainForSellOrder() {
        // DO NOT ADD ANYMORE TO THIS MESSAGE - BOX HAS A LIMIT OF 16 USES
		LOG.info("Received sell order request response: ordSid {}, secCode {}, reason {}, prevUndBid {}, prevUndAsk {}, prevWAvg {}, undBid {}, undAsk {}, wavg {}, prevWrtBid {}, prevWrtAsk {}, wrtBid {}, wrtAsk {}, velocity {}, delta {}, wrtSpread {}, tickSens {}, highWrtBid {}, bestSpot {}, hasVolDown {}, hasToMake {}, hasWiden {}", 
		        box(values[1]), security.code(), box(values[2]), box(values[3]), box(values[4]), 
		        localBox(extraSbs[0], values[18]), 
		        box(values[5]), box(values[6]), 
		        localBox(extraSbs[1], values[17]),
		        box(values[7]), box(values[8]), box(values[9]), box(values[10]), box(values[11]), box(values[12]), box(values[13]), box(values[14]), box(values[15]), box(values[16]),
		        (values[21] & Flags.VOL_DOWN) != 0, (values[21] & Flags.TO_MAKING) != 0, (values[21] & Flags.WIDE) != 0);
	}

	private static StringBuilder localBox(StringBuilder builder, long value){
		builder.setLength(0);
		return builder.append(value);
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
