package com.lunar.strategy;

import com.lunar.core.SystemClock;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class DbStrategyPersister implements StrategyPersister {
    final SystemClock m_systemClock;
    private final DbConnection m_connection;
    
    public DbStrategyPersister(final SystemClock systemClock, final DbConnection connection) {
        m_systemClock = systemClock;
        m_connection = connection;
    }
    
    @Override
    public void persistStrategySwitch(final String strategyName, final BooleanType onOff) {
        m_connection.executeNonQuery(LunarQueries.updateStrategySwitchesForStrategy(strategyName, onOff == BooleanType.TRUE));        
    }

    @Override
    public void persistUnderlyingSwitch(final String underlyingSymbol, final BooleanType onOff) {
        m_connection.executeNonQuery(LunarQueries.updateUnderlyingSwitchesForStrategy(m_systemClock.dateString(), underlyingSymbol, onOff == BooleanType.TRUE));        
    }

    @Override
    public void persistWarrantSwitch(final String securitySymbol, final BooleanType onOff) {
        m_connection.executeNonQuery(LunarQueries.updateWarrantSwitchesForStrategy(m_systemClock.dateString(), securitySymbol, onOff == BooleanType.TRUE));
    }

    @Override
    public void persistIssuerSwitch(final String issuerCode, final BooleanType onOff) {
        m_connection.executeNonQuery(LunarQueries.updateIssuerSwitchesForStrategy(issuerCode, onOff == BooleanType.TRUE));        
    }
    
    @Override
    public void persistStrategyTypeParams(final String strategyName, final GenericStrategyTypeParams params) {
        //TODO nothing to persist
        //m_connection.executeNonQuery(LunarQueries.updateSpeedArbParams(strategyName));
    }
    
    @Override
    public void persistStrategyUndParams(final String strategyName, final String underlyingSymbol, final GenericUndParams params) {
        m_connection.executeNonQuery(LunarQueries.updateSpeedArbUndParams(m_systemClock.dateString(), strategyName, underlyingSymbol, params.sizeThreshold(), params.velocityThreshold()));
    }

    @Override
    public void persistStrategyIssuerParams(final String strategyName, String issuerName, GenericIssuerParams params) {
        m_connection.executeNonQuery(LunarQueries.updateSpeedArbIssuerParams(m_systemClock.dateString(), strategyName, issuerName, params.issuerMaxLag(), params.allowStopLossOnFlashingBid(), params.resetStopLossOnVolDown(), params.issuerMaxLagCap(), params.strategyTriggerType(), params.defaultPricingMode(), params.runTicksThreshold(), params.sellToNonIssuer(), params.tickBuffer(), params.stopLossTickBuffer(), params.sellOnVolDown(), params.sellOnVolDownBanPeriod(), params.sellAtQuickProfit(), params.currentOrderSize(), params.useHoldBidBan(), params.tradesVolumeThreshold(), params.baseOrderSize(), params.maxOrderSize(), params.orderSizeIncrement(), params.orderSizeRemainder()));
    }

    @Override
    public void persistStrategyIssuerUndParams(final String strategyName, final String issuerName, final String undSymbol, final GenericIssuerUndParams params){
    	m_connection.executeNonQuery(LunarQueries.updateSpeedArbIssuerUndParams(m_systemClock.dateString(), strategyName, issuerName, undSymbol, params.undTradeVolThreshold()));
    }
    
    @Override
    public void persistStrategyWrtParams(final String strategyName, final String securitySymbol, final GenericWrtParams params) {
        m_connection.executeNonQuery(LunarQueries.updateSpeedArbWrtParams(m_systemClock.dateString(), strategyName, securitySymbol,
                params.mmBidSize(), params.mmAskSize(), params.currentOrderSize(), params.runTicksThreshold(), params.tickSensitivityThreshold(),
                params.allowedMaxSpread(), params.banPeriodToDownVol(), params.banPeriodToTurnoverMaking(), params.spreadObservationPeriod(), params.turnoverMakingSize(), params.turnoverMakingPeriod(),
                params.sellOnVolDown(), params.issuerMaxLag(), params.stopProfit(), params.marketOutlook(),
                params.sellingBanPeriod(), params.holdingPeriod(), params.allowStopLossOnFlashingBid(), params.resetStopLossOnVolDown(),
                params.issuerMaxLagCap(), params.strategyTriggerType(), params.defaultPricingMode(), params.sellToNonIssuer(), params.tickBuffer(), params.stopLossTickBuffer(), params.wideSpreadBuffer(), params.sellOnVolDownBanPeriod(), params.sellAtQuickProfit(), params.useHoldBidBan(), params.tradesVolumeThreshold(), params.baseOrderSize(), params.maxOrderSize(), params.orderSizeIncrement(), params.orderSizeRemainder()));
    }

    @Override
    public void persistPricing(final String securitySymbol, final Greeks greeks) {
        m_connection.executeNonQuery(LunarQueries.updateGreeks(m_systemClock.dateString(), securitySymbol, greeks.impliedVol(), greeks.delta(), greeks.gamma(), greeks.vega(), greeks.bidImpliedVol(), greeks.askImpliedVol()));
    }

    @Override
    public void persistScoreBoard(String securitySymbol, ScoreBoard scoreBoard) {
        m_connection.executeNonQuery(LunarQueries.updateScoreBoard(m_systemClock.dateString(), securitySymbol, scoreBoard.speedArbHybridStats().getOurScoreWithPunter()));
    }

}
