package com.lunar.strategy;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class NullStrategyPersister implements StrategyPersister  {

    @Override
    public void persistStrategySwitch(String strategyName, BooleanType onOff) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistUnderlyingSwitch(String underlyingSymbol, BooleanType onOff) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistWarrantSwitch(String securitySymbol, BooleanType onOff) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistIssuerSwitch(String issuerCode, BooleanType onOff) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistStrategyTypeParams(String strategyName, GenericStrategyTypeParams params) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistStrategyUndParams(String strategyName, String underlyingSymbol,
            GenericUndParams params) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistStrategyIssuerParams(String strategyName, String issuerSymbol,
            GenericIssuerParams params) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistStrategyWrtParams(String strategyName, String securitySymbol, GenericWrtParams params) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistPricing(String securitySymbol, Greeks greeks) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistScoreBoard(String securitySymbol, ScoreBoard scoreBoard) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistStrategyIssuerUndParams(final String strategyName, final String issuerSymbol, final String undSymbol, final GenericIssuerUndParams params){
    	
    }
}
