package com.lunar.message.binary;

import com.lunar.message.io.sbe.BoobsSbeEncoder;
import com.lunar.message.io.sbe.ChartDataSbeEncoder;
import com.lunar.message.io.sbe.DividendCurveSbeEncoder;
import com.lunar.message.io.sbe.ExchangeSbeEncoder;
import com.lunar.message.io.sbe.GreeksSbeEncoder;
import com.lunar.message.io.sbe.IssuerSbeEncoder;
import com.lunar.message.io.sbe.MarketStatsSbeEncoder;
import com.lunar.message.io.sbe.NoteSbeEncoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeEncoder;
import com.lunar.message.io.sbe.PositionSbeEncoder;
import com.lunar.message.io.sbe.RiskControlSbeEncoder;
import com.lunar.message.io.sbe.RiskStateSbeEncoder;
import com.lunar.message.io.sbe.ScoreBoardSbeEncoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeEncoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategySwitchSbeEncoder;
import com.lunar.message.io.sbe.StrategyTypeSbeEncoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeEncoder;

public class EntityEncoder {
	private final ExchangeSbeEncoder exchangeSbeEncoder = new ExchangeSbeEncoder();
	private final IssuerSbeEncoder issuerSbeEncoder = new IssuerSbeEncoder();
	private final RiskControlSbeEncoder riskControlSbeEncoder = new RiskControlSbeEncoder();
	private final RiskStateSbeEncoder riskStateSbeEncoder = new RiskStateSbeEncoder();
	private final SecuritySbeEncoder securitySbeEncoder = new SecuritySbeEncoder();
	private final StrategyTypeSbeEncoder strategyTypeSbeEncoder = new StrategyTypeSbeEncoder();
	private final PositionSbeEncoder positionSbeEncoder = new PositionSbeEncoder();
	private final StrategySwitchSbeEncoder strategySwitchSbeEncoder = new StrategySwitchSbeEncoder();
	private final StrategyParamsSbeEncoder strategyParamsSbeEncoder = new StrategyParamsSbeEncoder();
	private final StrategyUndParamsSbeEncoder strategyUndParamsSbeEncoder = new StrategyUndParamsSbeEncoder();
	private final StrategyWrtParamsSbeEncoder strategyWrtParamsSbeEncoder = new StrategyWrtParamsSbeEncoder();
	private final StrategyIssuerParamsSbeEncoder strategyIssuerParamsSbeEncoder = new StrategyIssuerParamsSbeEncoder();
	private final StrategyIssuerUndParamsSbeEncoder strategyIssuerUndParamsSbeEncoder = new StrategyIssuerUndParamsSbeEncoder();
	private final DividendCurveSbeEncoder dividendCurveSbeEncoder = new DividendCurveSbeEncoder();
	private final GreeksSbeEncoder greeksSbeEncoder = new GreeksSbeEncoder();
	private final OrderBookSnapshotSbeEncoder orderBookSnapshotSbeEncoder = new OrderBookSnapshotSbeEncoder(); 
	private final BoobsSbeEncoder boobsSbeEncoder = new BoobsSbeEncoder();
	private final MarketStatsSbeEncoder marketStatsSbeEncoder = new MarketStatsSbeEncoder();
	private final ScoreBoardSchemaSbeEncoder scoreBoardSchemaSbeEncoder = new ScoreBoardSchemaSbeEncoder();
	private final ScoreBoardSbeEncoder scoreBoardSbeEncoder = new ScoreBoardSbeEncoder();
	private final NoteSbeEncoder noteSbeEncoder = new NoteSbeEncoder();
	private final ChartDataSbeEncoder chartDataSbeEncoder = new ChartDataSbeEncoder();
	
	public static EntityEncoder of(){
		return new EntityEncoder();
	}
	public ExchangeSbeEncoder exchangeSbeEncoder(){ return exchangeSbeEncoder;}
    public IssuerSbeEncoder issuerSbeEncoder(){ return issuerSbeEncoder;}
	public RiskControlSbeEncoder riskControlSbeEncoder(){ return riskControlSbeEncoder;}
	public RiskStateSbeEncoder riskStateSbeEncoder(){ return riskStateSbeEncoder;}
	public SecuritySbeEncoder securitySbeEncoder(){ return securitySbeEncoder;}
	public StrategyTypeSbeEncoder strategyTypeSbeEncoder(){ return strategyTypeSbeEncoder;}
	public PositionSbeEncoder positionSbeEncoder(){ return positionSbeEncoder;}
	public StrategySwitchSbeEncoder strategySwitchSbeEncoder(){ return strategySwitchSbeEncoder;}
	public StrategyParamsSbeEncoder strategyParamsSbeEncoder(){ return strategyParamsSbeEncoder;}
	public StrategyUndParamsSbeEncoder strategyUndParamsSbeEncoder(){ return strategyUndParamsSbeEncoder;}
	public StrategyWrtParamsSbeEncoder strategyWrtParamsSbeEncoder(){ return strategyWrtParamsSbeEncoder;}
	public StrategyIssuerParamsSbeEncoder strategyIssuerParamsSbeEncoder(){ return strategyIssuerParamsSbeEncoder;}
	public StrategyIssuerUndParamsSbeEncoder strategyIssuerUndParamsSbeEncoder(){ return strategyIssuerUndParamsSbeEncoder;}
	public DividendCurveSbeEncoder dividendCurveSbeEncoder() { return dividendCurveSbeEncoder; }
	public GreeksSbeEncoder greeksSbeEncoder() { return greeksSbeEncoder; }
	public OrderBookSnapshotSbeEncoder orderBookSnapshotEncoder() { return orderBookSnapshotSbeEncoder; }
	public BoobsSbeEncoder boobsSbeEncoder() { return boobsSbeEncoder; }
	public MarketStatsSbeEncoder marketStatsSbeEncoder() { return marketStatsSbeEncoder; }
	public ScoreBoardSchemaSbeEncoder scoreBoardSchemaSbeEncoder() { return scoreBoardSchemaSbeEncoder; }
	public ScoreBoardSbeEncoder scoreBoardSbeEncoder() { return scoreBoardSbeEncoder; }
	public NoteSbeEncoder noteSbeEncoder() { return noteSbeEncoder; }
	public ChartDataSbeEncoder chartDataSbeEncoder() { return chartDataSbeEncoder; }
}
