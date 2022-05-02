package com.lunar.strategy;

import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

public interface StrategyLoader {
    public interface Loadable<T> {
        void loadInfo(T obj);
    }
    
    public interface LoadableWithSidKey<T> {
        void loadInfo(long sid, T obj);
    }
    
    public interface LoadableWithDualSidKeys<T> {
        void loadInfo(long sidA, long sidB, T obj);
    }
    
	void loadStrategyTypes(final Loadable<StrategyType> loadable) throws Exception;
	
	void loadStrategySwitchesForStrategies(final LoadableWithSidKey<BooleanType> loadable) throws Exception;
	void loadStrategySwitchesForActiveUnderlyings(final LoadableWithSidKey<BooleanType> loadable) throws Exception;
	void loadStrategySwitchesForActiveIssuers(final LoadableWithSidKey<BooleanType> loadable) throws Exception;
	void loadStrategySwitchesForActiveInstruments(final LoadableWithSidKey<BooleanType> loadable) throws Exception;
	
    void loadStrategyTypeParams(final String strategyName, final Loadable<GenericStrategyTypeParams> loadable) throws Exception;
    void loadStrategyUndParams(final String strategyName, final LoadableWithSidKey<GenericUndParams> loadable) throws Exception;
    void loadStrategyIssuerParams(final String strategyName, final LoadableWithSidKey<GenericIssuerParams> loadable) throws Exception;
    void loadStrategyWrtParams(final String strategyName, final LoadableWithSidKey<GenericWrtParams> loadable) throws Exception;
    void loadStrategyIssuerUndParams(final String strategyName, final LoadableWithDualSidKeys<GenericIssuerUndParams> loadable) throws Exception;

    void loadScoreBoards(final LoadableWithSidKey<ScoreBoard> loadable) throws Exception;
    

}
