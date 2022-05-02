package com.lunar.strategy.scoreboard;

import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategySwitch;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.parameters.GenericWrtStatsParams;
import com.lunar.strategy.parameters.ParamsSbeEncodable;

public class ScoreBoardStrategyInfoSender implements StrategyInfoSender {
    private final LongEntityManager<? extends StrategySecurity> securities;    

    public ScoreBoardStrategyInfoSender(final LongEntityManager<? extends StrategySecurity> securities) {
        this.securities = securities;
    }
    
    @Override
    public void sendStrategyType(StrategyType strategyType, MessageSinkRef sink) {
    }

    @Override
    public void sendStrategyType(StrategyType strategyType, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
    }

    @Override
    public void broadcastStrategyType(StrategyType strategyType) {
    }

    @Override
    public void sendSwitch(StrategySwitch strategySwitch, MessageSinkRef sink) {
    }

    @Override
    public void sendSwitch(StrategySwitch strategySwitch, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
    }

    @Override
    public void broadcastSwitch(StrategySwitch strategySwitch) {
    }

    @Override
    public void sendStrategyParams(ParamsSbeEncodable params, MessageSinkRef sink) {
    }

    @Override
    public void sendStrategyParams(ParamsSbeEncodable params, MessageSinkRef sink, RequestSbeDecoder request, int seqNum) {
    }

    @Override
    public void broadcastStrategyParams(ParamsSbeEncodable params) {
        updateScoreBoard(params);
    }

    @Override
    public void broadcastStrategyParamsNoPersist(ParamsSbeEncodable params) {
        updateScoreBoard(params);
    }

    @Override
    public boolean broadcastStrategyParamsBatched(ParamsSbeEncodable params) {
        updateScoreBoard(params);
        return true;
    }

    @Override
    public boolean broadcastStrategyParamsNoPersistBatched(ParamsSbeEncodable params) {
        updateScoreBoard(params);
        return true;
    }

    @Override
    public boolean broadcastStrategyParamsNoPersistThrottled(ParamsSbeEncodable params) {
        updateScoreBoard(params);
        return true;
    }

    @Override
    public boolean sendEventBatched(StrategyType strategyType, Security warrant, EventType eventType, long nanoOfDay, EventValueType valueType, long longValue) {
        final ScoreBoardSecurityInfo securityInfo = (ScoreBoardSecurityInfo)warrant;
        if (securityInfo.volatilityChangedHandler() != null) {
            if (eventType.equals(EventType.VOL_DOWN_SIGNAL)) {
                securityInfo.volatilityChangedHandler().onVolDropped(nanoOfDay);
            }
            else if (eventType.equals(EventType.VOL_UP_SIGNAL)) {
                securityInfo.volatilityChangedHandler().onVolRaised(nanoOfDay);
            }
        }
        return true;
    }

    @Override
    public int broadcastAllBatched() {
        return 0;
    }

    @Override
    public int broadcastAllThrottled() {
        return 0;
    }

    private void updateScoreBoard(ParamsSbeEncodable params) {
        if (params instanceof GenericWrtStatsParams) {
            params = ((GenericWrtStatsParams)params).params();
        }
        if (params instanceof GenericWrtParams) {
            final GenericWrtParams wrtParams = (GenericWrtParams)params;
            final ScoreBoardSecurityInfo warrant = (ScoreBoardSecurityInfo)(securities.get(wrtParams.secSid()));
            final ScoreBoard scoreBoard = warrant.scoreBoard();
            scoreBoard.marketStats().setIssuerSmoothing(wrtParams.issuerSmoothing());
            if (wrtParams.tickSensitivity() != 0) {
                scoreBoard.marketStats().setTickSensitivity(wrtParams.tickSensitivity());
            }        
        }
    }

}
