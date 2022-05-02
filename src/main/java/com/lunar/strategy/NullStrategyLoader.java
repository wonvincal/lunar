package com.lunar.strategy;

import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class NullStrategyLoader implements StrategyLoader {

    @Override
    public void loadStrategyTypes(Loadable<StrategyType> loadable) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategySwitchesForStrategies(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategySwitchesForActiveUnderlyings(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategySwitchesForActiveIssuers(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategySwitchesForActiveInstruments(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategyTypeParams(String strategyName, Loadable<GenericStrategyTypeParams> loadable)
            throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategyUndParams(String strategyName, LoadableWithSidKey<GenericUndParams> loadable)
            throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategyIssuerParams(String strategyName, LoadableWithSidKey<GenericIssuerParams> loadable)
            throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategyWrtParams(String strategyName, LoadableWithSidKey<GenericWrtParams> loadable)
            throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadStrategyIssuerUndParams(String strategyName, LoadableWithDualSidKeys<GenericIssuerUndParams> loadable)
            throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void loadScoreBoards(LoadableWithSidKey<ScoreBoard> loadable) throws Exception {
        // TODO Auto-generated method stub
        
    }

}
