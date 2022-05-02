package com.lunar.strategy.speedarbhybrid;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.strategy.StrategyContext;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyManager;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.parameters.IssuerInputParams;
import com.lunar.strategy.parameters.IssuerUndInputParams;
import com.lunar.strategy.parameters.UndInputParams;
import com.lunar.strategy.parameters.WrtInputParams;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class SpeedArbBtParams {
    // NOTE: for BT testing, most of these parameters are overridden by specific issuer parameters in the setupIssuerInputParams method
    public static final int STRATEGY_ID = 1;
    private static final int PROFIT_RUN_TICKS = 10;
    private static final int MM_SIZE = 600000;
    private static final int TICK_SENSITIVITY_THRESHOLD = 400;
    private static final int ALLOWED_MAX_SPREAD = 3;
    private static final long BAN_TO_DOWNVOL_PERIOD = 10_000_000_000L;
    private static final long BAN_TO_TOMAKE_PERIOD = 60_000_000_000L;
    private static final long SPREAD_OBSERVE_PERIOD = 60_000_000_000L;
    private static final int BASE_ORDER_SIZE = 100_000;
    private static final int ORDER_SIZE = 100_000;
    private static final int MAX_ORDER_SIZE = 500_000;
    private static final int ORDER_SIZE_INCREMENT = 50_000;
    private static final int TURNOVER_MAKING_QUANTITY = 1_000_000;
    private static final long TURNOVER_MAKING_PERIOD = 30_000_000_000L;
    private static final long ISSUER_MAX_LAG = 100_000_000L;
    private static final boolean SELL_ON_VOL_DOWN = false;
    private static final long STOP_PROFIT = 2000000;
    private static final long SELLING_BAN_PERIOD = 1_000_000_000L;
    private static final long SELL_ON_VOLDOWN_BAN_PERIOD = 2_000_000_000L;
    private static final boolean ALLOW_STOPLOSS_ON_FLASHING_BID = false;
    private static final boolean RESET_STOPLOSS_ON_VOL_DOWN = true;
    private static MarketOutlookType OUTLOOK = MarketOutlookType.NORMAL;
    private static final PricingMode PRICING_MODE = PricingMode.WEIGHTED;
    private static final long ISSUER_MAX_LAG_CAP = 500_000_000L;
    private static final StrategyTriggerType TRIGGER_TYPE = StrategyTriggerType.VELOCITY_5MS;
    private static final boolean SELL_TO_NON_ISSUER = true;
    private static final int WIDE_SPREAD_BUFFER = 1;
    private static final boolean SELL_AT_QUICK_PROFIT = false;
    private static final boolean USE_HOLD_BID_BAN = false;
    private static final long TRADES_VOLUME_THRESHOLD = 0;
    private static final int ORDER_SIZE_REMAINDER = 50000;
    
    private class UnderlyingParams {
        long velocityThreshold;
        
        public UnderlyingParams(final long velocityThreshold) {
            this.velocityThreshold = velocityThreshold;
        }
    }
    
    private class WarrantParams {
        int mmBidSize;
        int mmAskSize;
        int delta;
        
        public WarrantParams(final int mmBidSize, final int mmAskSize, final int delta) {
            this.mmBidSize = mmBidSize;
            this.mmAskSize = mmAskSize;
            this.delta = delta;
        }
    }
    
    private final Long2ObjectOpenHashMap<UnderlyingParams> underlyingParams = new Long2ObjectOpenHashMap<UnderlyingParams>();
    private final Long2ObjectOpenHashMap<WarrantParams> warrantParams = new Long2ObjectOpenHashMap<WarrantParams>();
    
    public SpeedArbBtParams() {
        setDefaultUnderlyingParams();
    }
    
    public boolean isTradable(final long underlyingSid) {
        return underlyingParams.containsKey(underlyingSid);
    }
    
    private void setDefaultUnderlyingParams() {
        underlyingParams.put(1L, new UnderlyingParams(1000000000L));
        underlyingParams.put(5L, new UnderlyingParams(10000000000L));
        underlyingParams.put(8L, new UnderlyingParams(1000000000L));
        underlyingParams.put(12L, new UnderlyingParams(2000000000L));
        underlyingParams.put(16L, new UnderlyingParams(3000000000L));
        underlyingParams.put(17L, new UnderlyingParams(1000000000L));
        underlyingParams.put(27L, new UnderlyingParams(1000000000L));
        underlyingParams.put(123L, new UnderlyingParams(1000000000L));
        underlyingParams.put(135L, new UnderlyingParams(1000000000L));
        underlyingParams.put(165L, new UnderlyingParams(1000000000L));
        underlyingParams.put(175L, new UnderlyingParams(1000000000L));
        underlyingParams.put(267L, new UnderlyingParams(1000000000L));
        underlyingParams.put(288L, new UnderlyingParams(1000000000L));
        underlyingParams.put(358L, new UnderlyingParams(1000000000L));
        underlyingParams.put(386L, new UnderlyingParams(1000000000L));
        underlyingParams.put(388L, new UnderlyingParams(2000000000L));
        underlyingParams.put(390L, new UnderlyingParams(1000000000L));
        underlyingParams.put(489L, new UnderlyingParams(3000000000L));
        underlyingParams.put(494L, new UnderlyingParams(2000000000L));
        underlyingParams.put(656L, new UnderlyingParams(1000000000L));
        underlyingParams.put(688L, new UnderlyingParams(3000000000L));
        underlyingParams.put(699L, new UnderlyingParams(1000000000L));
        underlyingParams.put(700L, new UnderlyingParams(1500000000L));
        underlyingParams.put(728L, new UnderlyingParams(2000000000L));
        underlyingParams.put(762L, new UnderlyingParams(1000000000L));
        underlyingParams.put(763L, new UnderlyingParams(1000000000L));
        underlyingParams.put(823L, new UnderlyingParams(1000000000L));
        underlyingParams.put(857L, new UnderlyingParams(2000000000L));
        underlyingParams.put(883L, new UnderlyingParams(1000000000L));
        underlyingParams.put(902L, new UnderlyingParams(1000000000L));
        underlyingParams.put(914L, new UnderlyingParams(3000000000L));
        underlyingParams.put(939L, new UnderlyingParams(1500000000L));
        underlyingParams.put(941L, new UnderlyingParams(2000000000L));
        underlyingParams.put(968L, new UnderlyingParams(1000000000L));
        underlyingParams.put(981L, new UnderlyingParams(5000000000L));
        underlyingParams.put(992L, new UnderlyingParams(3000000000L));
        underlyingParams.put(998L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1038L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1044L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1088L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1099L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1109L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1113L, new UnderlyingParams(3000000000L));
        underlyingParams.put(1128L, new UnderlyingParams(1500000000L));
        underlyingParams.put(1171L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1186L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1211L, new UnderlyingParams(3000000000L));
        underlyingParams.put(1288L, new UnderlyingParams(2000000000L));
        underlyingParams.put(1299L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1336L, new UnderlyingParams(2000000000L));
        underlyingParams.put(1359L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1388L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1398L, new UnderlyingParams(3000000000L));
        underlyingParams.put(1766L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1776L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1800L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1816L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1928L, new UnderlyingParams(1000000000L));
        underlyingParams.put(1988L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2007L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2018L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2020L, new UnderlyingParams(2000000000L));
        underlyingParams.put(2202L, new UnderlyingParams(2000000000L));
        underlyingParams.put(2238L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2318L, new UnderlyingParams(2000000000L));
        underlyingParams.put(2319L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2328L, new UnderlyingParams(2000000000L));
        underlyingParams.put(2333L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2338L, new UnderlyingParams(3000000000L));
        underlyingParams.put(2382L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2388L, new UnderlyingParams(5000000000L));
        underlyingParams.put(2600L, new UnderlyingParams(1500000000L));
        underlyingParams.put(2601L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2628L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2800L, new UnderlyingParams(5000000000L));
        underlyingParams.put(2822L, new UnderlyingParams(3000000000L));
        underlyingParams.put(2823L, new UnderlyingParams(3000000000L));
        underlyingParams.put(2840L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2888L, new UnderlyingParams(1000000000L));
        underlyingParams.put(2899L, new UnderlyingParams(2000000000L));
        underlyingParams.put(3188L, new UnderlyingParams(1000000000L));
        underlyingParams.put(3323L, new UnderlyingParams(1000000000L));
        underlyingParams.put(3328L, new UnderlyingParams(3000000000L));
        underlyingParams.put(3333L, new UnderlyingParams(3000000000L));
        underlyingParams.put(3699L, new UnderlyingParams(1000000000L));
        underlyingParams.put(3800L, new UnderlyingParams(1000000000L));
        underlyingParams.put(3888L, new UnderlyingParams(3000000000L));
        underlyingParams.put(3898L, new UnderlyingParams(1500000000L));
        underlyingParams.put(3908L, new UnderlyingParams(1000000000L));
        underlyingParams.put(3968L, new UnderlyingParams(3000000000L));
        underlyingParams.put(3988L, new UnderlyingParams(5000000000L));
        underlyingParams.put(6030L, new UnderlyingParams(1000000000L));
        underlyingParams.put(6837L, new UnderlyingParams(1000000000L));
        underlyingParams.put(6881L, new UnderlyingParams(1000000000L));
        underlyingParams.put(6886L, new UnderlyingParams(1000000000L));
        underlyingParams.put(SpeedArbHybridTestHelper.HSI_SID, new UnderlyingParams(0L));
        underlyingParams.put(SpeedArbHybridTestHelper.HSCEI_SID, new UnderlyingParams(0L));
    }
    
    public void setWarrantParams(final long secSid, final int mmBidSize, final int mmAskSize, final int delta) {
        warrantParams.put(secSid, new WarrantParams(mmBidSize, mmAskSize, delta));
    }
    
    public void setupStrategyManager(final StrategyManager strategyManager) throws Exception {
        final long strategyId = STRATEGY_ID;
        strategyManager.registerStrategyFactory(new SpeedArbHybridFactory());
        strategyManager.addStrategyType(strategyId, SpeedArbHybridFactory.STRATEGY_NAME);
        final StrategyContext context = strategyManager.getStrategyContext(strategyId);
        setupDefaultParams(context);
        setupUndInputParams(context);
        setupIssuerInputParams(context);
        setupWrtInputParams(context);
        setupIssuerUndParams(context);
        switchOnSwitches(strategyManager);
    }
    
    
    private void setupDefaultParams(final StrategyContext context) {
        setupDefaultUndInputParams(context.getStrategyTypeParams().defaultUndInputParams());
        setupDefaultIssuerInputParams(context.getStrategyTypeParams().defaultIssuerInputParams());
        setupDefaultWrtInputParams(context.getStrategyTypeParams().defaultWrtInputParams());
    }
    
    private void setupDefaultUndInputParams(final UndInputParams params) {
        // nothing to do...sizeThreshold is useless and velocityThreshold is unique per underlying
    }
    
    private void setupDefaultIssuerInputParams(final IssuerInputParams params) {        
        params.issuerMaxLag(ISSUER_MAX_LAG);
        params.allowStopLossOnFlashingBid(ALLOW_STOPLOSS_ON_FLASHING_BID);
        params.resetStopLossOnVolDown(RESET_STOPLOSS_ON_VOL_DOWN);
        params.issuerMaxLagCap(ISSUER_MAX_LAG_CAP);
        params.defaultPricingMode(PRICING_MODE);
        params.strategyTriggerType(TRIGGER_TYPE);
        params.runTicksThreshold(PROFIT_RUN_TICKS);    
        params.sellToNonIssuer(SELL_TO_NON_ISSUER);
        params.sellOnVolDown(SELL_ON_VOL_DOWN);
        params.sellOnVolDownBanPeriod(SELL_ON_VOLDOWN_BAN_PERIOD);
        params.sellAtQuickProfit(SELL_AT_QUICK_PROFIT);
        params.baseOrderSize(BASE_ORDER_SIZE);
        params.currentOrderSize(ORDER_SIZE);
        params.maxOrderSize(MAX_ORDER_SIZE);
        params.orderSizeIncrement(ORDER_SIZE_INCREMENT);
        params.useHoldBidBan(USE_HOLD_BID_BAN);
        params.tradesVolumeThreshold(TRADES_VOLUME_THRESHOLD);
        params.orderSizeRemainder(ORDER_SIZE_REMAINDER);
    }

    private void setupDefaultWrtInputParams(final WrtInputParams params) {
        setupDefaultIssuerInputParams(params);
        params.mmBidSize(MM_SIZE);
        params.mmAskSize(MM_SIZE);
        params.tickSensitivityThreshold(TICK_SENSITIVITY_THRESHOLD);
        params.stopProfit(STOP_PROFIT);
        params.allowedMaxSpread(ALLOWED_MAX_SPREAD);
        params.turnoverMakingSize(TURNOVER_MAKING_QUANTITY);
        params.turnoverMakingPeriod(TURNOVER_MAKING_PERIOD);
        params.banPeriodToDownVol(BAN_TO_DOWNVOL_PERIOD);
        params.banPeriodToTurnoverMaking(BAN_TO_TOMAKE_PERIOD);
        params.sellingBanPeriod(SELLING_BAN_PERIOD);
        params.spreadObservationPeriod(SPREAD_OBSERVE_PERIOD);
        params.marketOutlook(OUTLOOK);
        params.wideSpreadBuffer(WIDE_SPREAD_BUFFER);
    }
    
    private void setupUndInputParams(final StrategyContext context) {
        for (final StrategySecurity underlying : context.getUnderlyings().values()) {
            final UndInputParams params = context.getStrategyUndParams(underlying.sid(), true);
            final UnderlyingParams specificParams = underlyingParams.get(underlying.sid());
            if (specificParams != null) {
                params.velocityThreshold(specificParams.velocityThreshold);
            }
        }
    }
    
    private void setupIssuerInputParams(final StrategyContext context) {
        for (final StrategyIssuer issuer : context.getIssuers().entities()) {
            final IssuerInputParams params = context.getStrategyIssuerParams(issuer.sid(), true);
            final String issuerCode = issuer.code();
            if (issuerCode.equals("SC")) {
                //params.sellingBanPeriod(10_000_000_000L);
            }
            else if (issuerCode.equals("MB")) {
                params.issuerMaxLag(100_000_000L);
                params.issuerMaxLagCap(500_000_000L);
                params.tradesVolumeThreshold(800_000);
                params.defaultPricingMode(PricingMode.MID);
                params.sellOnVolDown(true);
                params.strategyTriggerType(StrategyTriggerType.VELOCITY_10MS);
                params.maxOrderSize(240000);
            }
            else if (issuerCode.equals("SG")) {
                params.issuerMaxLag(20_000_000L);
                params.issuerMaxLagCap(200_000_000L);
                params.sellOnVolDown(false);
                params.maxOrderSize(500000);
                params.sellAtQuickProfit(true);
                //params.tickBuffer(700);
                //params.stopLossTickBuffer(700);
            }
            else if (issuerCode.equals("JP")) {
                params.issuerMaxLag(20_000_000L);
                params.issuerMaxLagCap(200_000_000L);
                params.sellOnVolDown(false);
                params.maxOrderSize(300000);
                params.sellAtQuickProfit(true);
//                params.strategyTriggerType(StrategyTriggerType.VELOCITY_10MS);
                //params.tickBuffer(700);
                //params.stopLossTickBuffer(700);
                //params.allowStopLossOnFlashingBid(true);
            }
            else if (issuerCode.equals("BI")) {
                params.issuerMaxLag(20_000_000L);
                params.issuerMaxLagCap(200_000_000L);
                params.sellOnVolDown(true);
                params.maxOrderSize(500000);
            }
            else if (issuerCode.equals("CS")) {
                params.issuerMaxLag(100_000_000L);
                params.issuerMaxLagCap(500_000_000L);
                params.sellOnVolDown(true);
                params.useHoldBidBan(true);
                params.maxOrderSize(400000);
            }
            else if (issuerCode.equals("GS")) {
                params.issuerMaxLag(20_000_000L);
                params.issuerMaxLagCap(200_000_000L);
                params.tradesVolumeThreshold(800_000);
                params.sellOnVolDown(true);
                params.useHoldBidBan(true);
                params.maxOrderSize(600000);
                params.sellAtQuickProfit(true);
            }
            else if (issuerCode.equals("BP")) {
                params.issuerMaxLag(20_000_000L);
                params.issuerMaxLagCap(200_000_000L);
                params.sellOnVolDown(true);
                params.maxOrderSize(500000);
            }
            else if (issuerCode.equals("HS")) {
                params.issuerMaxLag(100_000_000L);
                params.issuerMaxLagCap(500_000_000L);
                params.sellOnVolDown(true);
                params.useHoldBidBan(true);
                params.maxOrderSize(200000);
            }
            else if (issuerCode.equals("HT")) {
                params.issuerMaxLag(100_000_000L);
                params.issuerMaxLagCap(500_000_000L);
                params.sellOnVolDown(true);
                params.useHoldBidBan(true);
                params.maxOrderSize(300000);
            }
            else if (issuerCode.equals("UB")) {
                params.issuerMaxLag(100_000_000L);
                params.issuerMaxLagCap(500_000_000L);
                params.sellOnVolDown(true);
                params.useHoldBidBan(true);
                params.maxOrderSize(300000);
            }
            else if (issuerCode.equals("EA")) {
                params.issuerMaxLag(100_000_000L);
                params.issuerMaxLagCap(500_000_000L);
                params.sellOnVolDown(true);
                params.maxOrderSize(200000);
            }

        }
    }
    
    private void setupWrtInputParams(final StrategyContext context) {
        for (final StrategySecurity security : context.getWarrants().values()) {
            final WrtInputParams params = context.getStrategyWrtParams(security.sid(), true);
            // TODO - hackey because sellingBanPeriod isn't an issuer parameter although it should be...
            final StrategyIssuer issuer = context.getIssuers().get(security.issuerSid());
            final String issuerCode = issuer.code();
            if (issuerCode.equals("SC")) {
                params.sellingBanPeriod(10_000_000_000L);
            }
            final WarrantParams specificParams = warrantParams.get(security.sid());
            if (specificParams != null) {
                params.mmBidSize(specificParams.mmBidSize);
                params.mmAskSize(specificParams.mmAskSize);
            }
            if (security.underlying().securityType().equals(SecurityType.INDEX)) {
                params.defaultPricingMode(PricingMode.MID);
                params.tickSensitivityThreshold(1);
                params.tickBuffer(0);
                params.stopLossTickBuffer(0);
            }
        }
    }
    
    private void setupIssuerUndParams(final StrategyContext context) {
        for (final StrategyIssuer issuer : context.getIssuers().entities()) {
            final String issuerCode = issuer.code();
            if (issuerCode.equals("GS")) {
                for (final StrategySecurity underlying : context.getUnderlyings().values()) {
                    final IssuerUndInputParams params = context.getStrategyIssuerUndParams(issuer.sid(), underlying.sid(), true);
                    params.undTradeVolThreshold(1_000_000L);
                }
            }
        }
    }
    
    private void switchOnSwitches(final StrategyManager strategyManager) throws Exception {
        for (final StrategySecurity underlying : strategyManager.underlyings().values()) {
            strategyManager.flipSwitchForUnderlying(underlying.sid(), BooleanType.TRUE, StrategyExitMode.NULL_VAL, false);
        }
        for (final StrategyIssuer issuer : strategyManager.issuers().entities()) {
            strategyManager.flipSwitchForIssuer(issuer.sid(), BooleanType.TRUE, StrategyExitMode.NULL_VAL, false);
        }
        for (final StrategySecurity warrant : strategyManager.warrants().values()) {
            strategyManager.flipSwitchForSecurity(warrant.sid(), BooleanType.TRUE, StrategyExitMode.NULL_VAL, false);
        }
    }
    
    public int getDelta(final long secSid) {
        final WarrantParams specificParams = warrantParams.get(secSid);
        if (specificParams != null) {
            return specificParams.delta;
        }
        return 0;
    }

}
