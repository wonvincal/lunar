package com.lunar.message.io.fbs;

import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RiskControlSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;

public class SbeDecoderManager {
	private final EventSbeDecoder eventDecoder = new EventSbeDecoder();
	private final IssuerSbeDecoder issuerDecoder = new IssuerSbeDecoder();
	private final OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
	private final PositionSbeDecoder positionDecoder = new PositionSbeDecoder();
	private final RiskStateSbeDecoder riskStateDecoder = new RiskStateSbeDecoder();
	private final RiskControlSbeDecoder riskControlDecoder = new RiskControlSbeDecoder();
	private final SecuritySbeDecoder securityDecoder = new SecuritySbeDecoder();
	private final StrategyTypeSbeDecoder strategyTypeDecoder = new StrategyTypeSbeDecoder();
	private final ServiceStatusSbeDecoder serviceStatusDecoder = new ServiceStatusSbeDecoder();
	private final TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
	private final StrategyParamsSbeDecoder stratParamsSbeDecoder = new StrategyParamsSbeDecoder();
	private final StrategyWrtParamsSbeDecoder stratWrtParamsSbeDecoder = new StrategyWrtParamsSbeDecoder();
    private final StrategyUndParamsSbeDecoder stratUndParamsSbeDecoder = new StrategyUndParamsSbeDecoder();
	private final StrategySwitchSbeDecoder stratSwitchSbeDecoder = new StrategySwitchSbeDecoder();
	private final StrategyIssuerParamsSbeDecoder stratIssuerParamsSbeDecoder = new StrategyIssuerParamsSbeDecoder();
	private final StrategyIssuerUndParamsSbeDecoder stratIssuerUndParamsSbeDecoder = new StrategyIssuerUndParamsSbeDecoder();
	private final OrderBookSnapshotSbeDecoder orderBookSnapshotSbeDecoder = new OrderBookSnapshotSbeDecoder();
	private final MarketStatsSbeDecoder marketStatsSbeDecoder = new MarketStatsSbeDecoder();
    private final ScoreBoardSchemaSbeDecoder scoreBoardSchemaSbeDecoder = new ScoreBoardSchemaSbeDecoder();
    private final ScoreBoardSbeDecoder scoreBoardSbeDecoder = new ScoreBoardSbeDecoder();
    private final NoteSbeDecoder noteSbeDecoder = new NoteSbeDecoder();
    private final ChartDataSbeDecoder chartDataSbeDecoder = new ChartDataSbeDecoder();
	
	public static SbeDecoderManager of(){
		return new SbeDecoderManager();
	}
	SbeDecoderManager(){}
	
	public EventSbeDecoder eventDecoder(){
		return eventDecoder;
	}
	
	public OrderSbeDecoder orderDecoder(){
		return orderDecoder;
	}
	
	public PositionSbeDecoder positionDecoder(){
		return positionDecoder;
	}
	
	public RiskControlSbeDecoder riskControlDecoder(){
		return riskControlDecoder;
	}
	
	public RiskStateSbeDecoder riskStateDecoder(){
		return riskStateDecoder;
	}
	
	public SecuritySbeDecoder securityDecoder(){
		return securityDecoder;
	}
	
	public IssuerSbeDecoder issuerDecoder(){
		return issuerDecoder;
	}
	
	public StrategyTypeSbeDecoder strategyTypeDecoder(){
		return strategyTypeDecoder;
	}
	
	public ServiceStatusSbeDecoder serviceStatusDecoder(){
		return serviceStatusDecoder;
	}

	public TradeSbeDecoder tradeDecoder(){
		return tradeDecoder;
	}

    public StrategyParamsSbeDecoder stratParamsSbeDecoder() {
        return stratParamsSbeDecoder;
    }

	public StrategyWrtParamsSbeDecoder stratWrtParamsSbeDecoder() {
		return stratWrtParamsSbeDecoder;
	}

	public StrategyUndParamsSbeDecoder stratUndParamsSbeDecoder() {
	    return stratUndParamsSbeDecoder;
	}

	public StrategySwitchSbeDecoder stratSwitchSbeDecoder() {
		return stratSwitchSbeDecoder;
	}
	
	public StrategyIssuerParamsSbeDecoder stratIssuerParamsSbeDecoder() {
	    return stratIssuerParamsSbeDecoder;
    }

	public StrategyIssuerUndParamsSbeDecoder stratIssuerUndParamsSbeDecoder() {
	    return stratIssuerUndParamsSbeDecoder;
    }

	public OrderBookSnapshotSbeDecoder orderBookSnapshotSbeDecoder() {
		return orderBookSnapshotSbeDecoder;
	}
	
	public MarketStatsSbeDecoder marketStatsSbeDecoder() {
	    return marketStatsSbeDecoder;
	}
	
    public ScoreBoardSchemaSbeDecoder scoreBoardSchemaSbeDecoder() {
        return scoreBoardSchemaSbeDecoder;
    }

    public ScoreBoardSbeDecoder scoreBoardSbeDecoder() {
        return scoreBoardSbeDecoder;
    }
    
    public NoteSbeDecoder noteSbeDecoder(){
    	return noteSbeDecoder;
    }
    
    public ChartDataSbeDecoder chartDataSbeDecoder(){
    	return chartDataSbeDecoder;
    }
}
