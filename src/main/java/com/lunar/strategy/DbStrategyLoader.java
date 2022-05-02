package com.lunar.strategy;

import java.sql.ResultSet;

import com.lunar.core.SystemClock;
import com.lunar.database.DbConnection;
import com.lunar.database.LunarQueries;
import com.lunar.entity.SidManager;
import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class DbStrategyLoader implements StrategyLoader {
    private final SystemClock systemClock;
    private final DbConnection connection;
    private final SidManager<String> strategyTypeSidManager;
    private final SidManager<String> securitySidManager;
    private final SidManager<String> issuerSidManager;

    public DbStrategyLoader(final SystemClock systemClock, final DbConnection connection, final SidManager<String> strategyTypeSidManager, final SidManager<String> securitySidManager, final SidManager<String> issuerSidManager) {
        this.systemClock = systemClock;
        this.connection = connection;
        this.strategyTypeSidManager = strategyTypeSidManager;
        this.securitySidManager = securitySidManager;
        this.issuerSidManager = issuerSidManager;
    }

    @Override
    public void loadStrategyTypes(Loadable<StrategyType> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getAllStrategies());
        try {
            if (rs != null) {
                while (rs.next()) {
                    final int sid = rs.getInt(1);                    
                    final String strategyName = rs.getString(2);
                    strategyTypeSidManager.setSidForKey(sid, strategyName);
                    loadable.loadInfo(StrategyType.of(sid, strategyName));
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }        
    }

    @Override
    public void loadStrategySwitchesForStrategies(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getStrategySwitchesForStrategies());
        try {
            if (rs != null) {
                while (rs.next()) {
                    final String strategyName = rs.getString(1);
                    final byte switchInt = rs.getByte(2);
                    final long sid = strategyTypeSidManager.getSidForKey(strategyName);
                    loadable.loadInfo(sid, BooleanType.get(switchInt));
                }
            }
        }
        finally {
            rs.close();
        }
    }

    @Override
    public void loadStrategySwitchesForActiveUnderlyings(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getStrategySwitchesForActiveUnderlyings(systemClock.dateString()));
        try {
            if (rs != null) {
                while (rs.next()) {
                    final String underlyingCode = rs.getString(1);
                    final byte switchInt = rs.getByte(2);
                    final long sid = securitySidManager.getSidForKey(underlyingCode);
                    loadable.loadInfo(sid, BooleanType.get(switchInt));
                }
            }
        }
        finally {
            rs.close();
        }
    }

    @Override
    public void loadStrategySwitchesForActiveIssuers(LoadableWithSidKey<BooleanType> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getStrategySwitchesForActiveIssuers(systemClock.dateString()));
        try {
            if (rs != null) {
                while (rs.next()) {
                    final String issuerCode = rs.getString(1);
                    final byte switchInt = rs.getByte(2);
                    final long sid = issuerSidManager.getSidForKey(issuerCode);
                    loadable.loadInfo(sid, BooleanType.get(switchInt));
                }
            }
        }
        finally {
            rs.close();
        }        
    }

    @Override
    public void loadStrategySwitchesForActiveInstruments(final LoadableWithSidKey<BooleanType> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getStrategySwitchesForActiveInstruments(systemClock.dateString()));
        try {
            if (rs != null) {
                while (rs.next()) {
                    final String securityCode = rs.getString(1);
                    final byte switchInt = rs.getByte(2);
                    final long sid = securitySidManager.getSidForKey(securityCode);
                    loadable.loadInfo(sid, BooleanType.get(switchInt));
                }
            }
        }
        finally {
            rs.close();
        }
    }
    
    @Override
    public void loadStrategyTypeParams(final String strategyName, final Loadable<GenericStrategyTypeParams> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getSpeedArbParams(strategyName));
        try {
            if (rs != null) {
                while (rs.next()) {
                    final GenericStrategyTypeParams strategyTypeParams = new GenericStrategyTypeParams();
                    int idx = 1;
                    strategyTypeParams.defaultUndInputParams().sizeThreshold(rs.getInt(idx++));
                    strategyTypeParams.defaultUndInputParams().velocityThreshold(rs.getLong(idx++));                    
                    strategyTypeParams.defaultWrtInputParams().mmBidSize(rs.getInt(idx++));
                    strategyTypeParams.defaultWrtInputParams().mmAskSize(rs.getInt(idx++));
                    strategyTypeParams.defaultWrtInputParams().baseOrderSize(rs.getInt(idx++));
                    strategyTypeParams.defaultWrtInputParams().runTicksThreshold(rs.getInt(idx++));   
                    strategyTypeParams.defaultWrtInputParams().tickSensitivityThreshold(rs.getInt(idx++));
                    strategyTypeParams.defaultWrtInputParams().allowedMaxSpread(rs.getInt(idx++));
                    strategyTypeParams.defaultWrtInputParams().banPeriodToDownVol(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().banPeriodToTurnoverMaking(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().spreadObservationPeriod(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().turnoverMakingSize(rs.getInt(idx++));
                    strategyTypeParams.defaultWrtInputParams().turnoverMakingPeriod(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().sellOnVolDown(rs.getInt(idx++) == 1 ? true : false);
                    strategyTypeParams.defaultWrtInputParams().stopProfit(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().marketOutlook(MarketOutlookType.get(rs.getByte(idx++)));
                    strategyTypeParams.defaultWrtInputParams().sellingBanPeriod(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().holdingPeriod(rs.getLong(idx++));
                    strategyTypeParams.defaultWrtInputParams().wideSpreadBuffer(rs.getInt(idx++));
                    loadable.loadInfo(strategyTypeParams);
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }
    }

    @Override
    public void loadStrategyUndParams(final String strategyName, LoadableWithSidKey<GenericUndParams> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getSpeedArbUnderlyingParamsForAll(systemClock.dateString(), strategyName));
        try {
            if (rs != null) {
                while (rs.next()) {
                    int idx = 1;
                    final String symbol = rs.getString(idx++);
                    final GenericUndParams params = new GenericUndParams();
                    params.sizeThreshold(rs.getInt(idx++));
                    params.velocityThreshold(rs.getLong(idx++));
                    final long sid = securitySidManager.getSidForKey(symbol);
                    loadable.loadInfo(sid, params);
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }
    }

    public void loadStrategyIssuerUndParams(final String strategyName, LoadableWithDualSidKeys<GenericIssuerUndParams> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getSpeedArbIssuerUndParamsForAll(systemClock.dateString(), strategyName));
        try {
            if (rs != null) {
                while (rs.next()) {
                    int idx = 1;
                    final String issuerCode = rs.getString(idx++);
                    final String undSymbol = rs.getString(idx++);
                    final GenericIssuerUndParams params = new GenericIssuerUndParams();
                    params.undTradeVolThreshold(rs.getInt(idx));
                    final long issuerSid = issuerSidManager.getSidForKey(issuerCode);
                    final long undSid = securitySidManager.getSidForKey(undSymbol);
                    loadable.loadInfo(issuerSid, undSid, params);
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }
    }

    
    @Override
    public void loadStrategyIssuerParams(final String strategyName, final LoadableWithSidKey<GenericIssuerParams> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getSpeedArbIssuerParamsForAll(systemClock.dateString(), strategyName));
        try {
            if (rs != null) {
                while (rs.next()) {
                    int idx = 1;
                    final String issuerCode = rs.getString(idx++);
                    final GenericIssuerParams params = new GenericIssuerParams();
                    params.issuerMaxLag(rs.getLong(idx++));
                    params.allowStopLossOnFlashingBid(rs.getInt(idx++) != 0);
                    params.resetStopLossOnVolDown(rs.getLong(idx++) != 0);
                    params.issuerMaxLagCap(rs.getLong(idx++));
                    params.strategyTriggerType(StrategyTriggerType.get(rs.getByte(idx++)));
                    params.defaultPricingMode(PricingMode.get(rs.getByte(idx++)));
                    params.runTicksThreshold(rs.getInt(idx++));
                    params.sellToNonIssuer(rs.getInt(idx++) != 0);
                    params.tickBuffer(rs.getInt(idx++));
                    params.stopLossTickBuffer(rs.getInt(idx++));
                    params.sellOnVolDown(rs.getInt(idx++) != 0);
                    params.sellOnVolDownBanPeriod(rs.getLong(idx++));
                    params.sellAtQuickProfit(rs.getInt(idx++) != 0);
                    params.currentOrderSize(rs.getInt(idx++));
                    params.useHoldBidBan(rs.getInt(idx++) != 0);
                    params.tradesVolumeThreshold(rs.getLong(idx++));
                    params.maxOrderSize(rs.getInt(idx++));
                    params.orderSizeIncrement(rs.getInt(idx++));
                    params.baseOrderSize(rs.getInt(idx++));
                    params.orderSizeRemainder(rs.getInt(idx++));
                    final long sid = issuerSidManager.getSidForKey(issuerCode);
                    loadable.loadInfo(sid, params);
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }  
    }

    @Override
    public void loadStrategyWrtParams(final String strategyName, LoadableWithSidKey<GenericWrtParams> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getSpeedArbWarrantParamsForAll(systemClock.dateString(), strategyName));
        try {
            if (rs != null) {
                while (rs.next()) {
                    int idx = 1;
                    final String symbol = rs.getString(idx++);
                    final GenericWrtParams params = new GenericWrtParams();
                    params.mmBidSize(rs.getInt(idx++));
                    params.mmAskSize(rs.getInt(idx++));
                    params.currentOrderSize(rs.getInt(idx++));
                    params.runTicksThreshold(rs.getInt(idx++));
                    params.tickSensitivityThreshold(rs.getInt(idx++));
                    params.allowedMaxSpread(rs.getInt(idx++));
                    params.banPeriodToDownVol(rs.getLong(idx++));
                    params.banPeriodToTurnoverMaking(rs.getLong(idx++));
                    params.spreadObservationPeriod(rs.getLong(idx++));
                    params.turnoverMakingSize(rs.getInt(idx++));
                    params.turnoverMakingPeriod(rs.getLong(idx++));
                    params.sellOnVolDown(rs.getInt(idx++) != 0);
                    params.issuerMaxLag(rs.getLong(idx++));
                    params.stopProfit(rs.getLong(idx++));
                    params.marketOutlook(MarketOutlookType.get(rs.getByte(idx++)));
                    params.sellingBanPeriod(rs.getLong(idx++));
                    params.holdingPeriod(rs.getLong(idx++));
                    params.allowStopLossOnFlashingBid(rs.getInt(idx++) != 0);
                    params.resetStopLossOnVolDown(rs.getInt(idx++) != 0);
                    params.issuerMaxLagCap(rs.getLong(idx++));
                    params.strategyTriggerType(StrategyTriggerType.get(rs.getByte(idx++)));
                    params.defaultPricingMode(PricingMode.get(rs.getByte(idx++)));
                    params.sellToNonIssuer(rs.getInt(idx++) != 0);
                    params.tickBuffer(rs.getInt(idx++));
                    params.stopLossTickBuffer(rs.getInt(idx++));
                    params.wideSpreadBuffer(rs.getInt(idx++));
                    params.sellOnVolDownBanPeriod(rs.getLong(idx++));
                    params.sellAtQuickProfit(rs.getInt(idx++) != 0);
                    params.useHoldBidBan(rs.getInt(idx++) != 0);
                    params.tradesVolumeThreshold(rs.getLong(idx++));
                    params.maxOrderSize(rs.getInt(idx++));
                    params.orderSizeIncrement(rs.getInt(idx++));
                    params.baseOrderSize(rs.getInt(idx++));
                    params.orderSizeRemainder(rs.getInt(idx++));
                    params.greeks().delta(rs.getInt(idx++));
                    final long sid = securitySidManager.getSidForKey(symbol);
                    loadable.loadInfo(sid, params);
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }
    }

    @Override
    public void loadScoreBoards(LoadableWithSidKey<ScoreBoard> loadable) throws Exception {
        final ResultSet rs = connection.executeQuery(LunarQueries.getScoreBoardForAll(systemClock.dateString()));
        try {
            if (rs != null) {
                while (rs.next()) {
                    int idx = 1;
                    final ScoreBoard scoreBoard = ScoreBoard.of();
                    final String symbol = rs.getString(idx++);
                    scoreBoard.speedArbHybridStats().setOurPrevScoreWithPunter(rs.getInt(idx++));
                    scoreBoard.marketStats().setPrevDayOsPercent(rs.getInt(idx++));
                    scoreBoard.marketStats().setPrevDayOsPercentChange(rs.getInt(idx++));
                    scoreBoard.marketStats().setPrevDayOutstanding(rs.getLong(idx++));
                    scoreBoard.marketStats().setPrevDayNetSold(rs.getLong(idx++));
                    scoreBoard.marketStats().setPrevDayNetVegaSold(rs.getLong(idx++));                    
                    scoreBoard.marketStats().setPrevDayImpliedVol(rs.getInt(idx++));
                    final long sid = securitySidManager.getSidForKey(symbol);
                    loadable.loadInfo(sid, scoreBoard);
                }
            }
        }
        finally {
            connection.closeQuery(rs);
        }        
    }

}
