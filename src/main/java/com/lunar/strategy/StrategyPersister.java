package com.lunar.strategy;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

public interface StrategyPersister {
    public void persistStrategySwitch(final String strategyName, final BooleanType onOff);
    public void persistUnderlyingSwitch(final String underlyingSymbol, final BooleanType onOff);
    public void persistWarrantSwitch(final String securitySymbol, final BooleanType onOff);
    public void persistIssuerSwitch(final String issuerCode, final BooleanType onOff);
    
    public void persistStrategyTypeParams(final String strategyName, final GenericStrategyTypeParams params);
    public void persistStrategyUndParams(final String strategyName, final String underlyingSymbol, final GenericUndParams params);
    public void persistStrategyIssuerParams(final String strategyName, final String issuerSymbol, final GenericIssuerParams params);
    public void persistStrategyIssuerUndParams(final String strategyName, final String issuerSymbol, final String undSymbol, final GenericIssuerUndParams params);
    public void persistStrategyWrtParams(final String strategyName, final String securitySymbol, final GenericWrtParams params);
    
    public void persistPricing(final String securitySymbol, final Greeks greeks);
    
    public void persistScoreBoard(final String securitySymbol, final ScoreBoard scoreBoard);

}
