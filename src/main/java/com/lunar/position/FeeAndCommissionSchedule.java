package com.lunar.position;

import static com.lunar.util.MathUtil.bps;

import java.text.DecimalFormat;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Fee structure 
 * @author Calvin
 *
 */
public class FeeAndCommissionSchedule {
	private static final Logger LOG = LogManager.getLogger(FeeAndCommissionSchedule.class);
	private static DecimalFormat THREE_DP = new DecimalFormat("#,##0.###");	
	private final double levyInDecimal;
	private final double stampInDecimal;
	private final double tradingFeeInDecimal;
	private final double tradingTariff;
	private final double clearingFeeInDecimal;
	private final double minClearingFee;
	private final double maxClearingFee;
	public final double commissionRateInDecimal;
	private final double totalNonClearingFeesInDecimal;
	
	public static FeeAndCommissionSchedule ZERO_FEES_AND_COMMISSION = FeeAndCommissionSchedule.of(); 
	
	public static FeeAndCommissionSchedule of(){
		return new FeeAndCommissionSchedule(0,
				0, 
				0, 
				0, 
				0, 
				0, 
				Double.MAX_VALUE,
				0);
	}
	
	public static FeeAndCommissionSchedule inBps(double levyInBps, 
			double stampInBps, 
			double tradingFeeInBps, 
			double tradingTariff,
			double clearingFeeInBps,			
			double minClearingFee,
			double maxClearingFee,
			double commissionRateInBps){
		return new FeeAndCommissionSchedule(bps(levyInBps), 
				bps(stampInBps), 
				bps(tradingFeeInBps), 
				tradingTariff,
				bps(clearingFeeInBps), 
				minClearingFee, 
				maxClearingFee,
				bps(commissionRateInBps));
	}

	FeeAndCommissionSchedule(double levyInDecimal, 
			double stampInDecimal, 
			double tradingFeeInDecimal,
			double tradingTariff,
			double clearingFeeInDecimal,
			double minClearingFee,
			double maxClearingFee,
			double commissionRateInDecimal){		
		this.levyInDecimal = levyInDecimal;
		this.stampInDecimal = stampInDecimal;
		this.tradingFeeInDecimal = tradingFeeInDecimal;
		this.tradingTariff = tradingTariff;
		this.clearingFeeInDecimal = clearingFeeInDecimal;
		this.minClearingFee = minClearingFee;
		this.maxClearingFee = maxClearingFee;
		this.commissionRateInDecimal = commissionRateInDecimal;
		this.totalNonClearingFeesInDecimal = levyInDecimal + stampInDecimal + tradingFeeInDecimal;
//		LOG.debug("levyInDecimal:{}, stampInDecimal:{}, tradingFeeInDecimal:{}", levyInDecimal, stampInDecimal, tradingFeeInDecimal);
	}
	
	double allFeesAndCommissionOf(double notional){
		double nonClearing = totalNonClearingFeesInDecimal * notional + tradingTariff;
		double clearing = Math.min(Math.max(clearingFeeInDecimal * notional, minClearingFee), maxClearingFee);
		return nonClearing + clearing + commissionOf(notional);
	}

	double tradingTariff() {
		return tradingTariff;
	}
	
	double stampOf(double notional){
		return stampInDecimal * notional;
	}
	
	double levyOf(double notional){
		return levyInDecimal * (notional);
	}

	double tradingFeeOf(double notional){
		return tradingFeeInDecimal * (notional);
	}
	
	double clearingFeeOf(double notional){
		double clearing = Math.max(clearingFeeInDecimal * (notional), minClearingFee);
		return Math.min(clearing, maxClearingFee); 	
	}

	public double feesOf(double notional){
		double nonClearing = totalNonClearingFeesInDecimal * notional + tradingTariff;
		double clearing = Math.min(Math.max(clearingFeeInDecimal * notional, minClearingFee), maxClearingFee);
		LOG.debug("[fees:{}, notional:{}, totalNonClearingFeesInDecimal:{}, nonClearing:{}, clearing:{}, max(clearing, minFee):{}, clearingFeeInDecimal*notional:{}, minCFee:{}]",
				nonClearing + clearing,
				notional,
				totalNonClearingFeesInDecimal, 
				nonClearing, 
				clearing, 
				Math.max(clearingFeeInDecimal * notional, minClearingFee),
				clearingFeeInDecimal * notional,
				minClearingFee);
		return nonClearing + clearing;
	}
	
	public double commissionOf(double notional){
		LOG.debug("[commission:{}]", this.commissionRateInDecimal * notional);
		return this.commissionRateInDecimal * notional;
	}
	
	public String explain(double notional){
		return "levy: " + THREE_DP.format(levyOf(notional)) + 
				", stamp: " + THREE_DP.format(stampOf(notional)) +
				", trading fee: " + THREE_DP.format(tradingFeeOf(notional)) +
				", trading tariff: " + THREE_DP.format(tradingTariff()) +
				", clearing: " + THREE_DP.format(clearingFeeOf(notional)) + 
				" (min: " + THREE_DP.format(this.minClearingFee) +
				" , max: " + THREE_DP.format(this.maxClearingFee) + ")";
	}
	
	@Override
	public String toString() {
		return super.toString();
	}
}
