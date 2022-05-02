package com.lunar.strategy.scoreboard;

import static org.apache.logging.log4j.util.Unbox.box;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.fsm.cutloss.MarketContext;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.StrategyCircuitSwitch;
import com.lunar.strategy.StrategyCircuitSwitch.SwitchHandler;
import com.lunar.strategy.StrategyContext;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyManager;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.parameters.UndInputParams;
import com.lunar.strategy.parameters.WrtInputParams;
import com.lunar.strategy.scoreboard.StrategySwitchController.SwitchControlHandler;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridFactory;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class ScoreBoardManager {
    private static final Logger LOG = LogManager.getLogger(ScoreBoardManager.class);

    private static final int DEFAULT_ISSUER_SIZE = 100;
    private static final int DEFAULT_WARRANT_SIZE = 5000;
    private static final int DEFAULT_WARRANT_PER_ISSUER_SIZE = 1000;

    private final LongEntityManager<? extends StrategySecurity> securities;
    private final LongEntityManager<StrategyIssuer> issuers;
    private final Long2ObjectOpenHashMap<ScoreBoardSecurityInfo> warrants;
    private final StrategyManager strategyManager;
    private final SwitchControlHandler switchControlHandler;
    private final IssuerScoreCalculatorFactory issuerScoreCalculatorFactory;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> issuerSwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> warrantSwitches;
    private final StrategyInfoSender strategyInfoSender;
    private final SwitchHandler warrantSwitchHandler = new SwitchHandler() {
        @Override
        public void onPerformTask(final long securitySid) {
            final ScoreBoardSecurityInfo security = (ScoreBoardSecurityInfo)securities.get(securitySid);
            security.strategySwitchController().enableAutoControl();
        }

        @Override
        public void onStopTask(final long securitySid) {
            final ScoreBoardSecurityInfo security = (ScoreBoardSecurityInfo)securities.get(securitySid);
            security.strategySwitchController().disableAutoControl();
        }
    };
    
    public ScoreBoardManager(final LongEntityManager<? extends StrategySecurity> securities, final LongEntityManager<StrategyIssuer> issuers, final StrategyManager strategyManager, final StrategyInfoSender strategyInfoSender, final SwitchControlHandler switchControlHandler) {
        this.securities = securities;
        this.issuers = issuers;
        this.strategyManager = strategyManager;
        this.strategyInfoSender = strategyInfoSender;
        this.switchControlHandler = switchControlHandler;
        this.warrants = new Long2ObjectOpenHashMap<ScoreBoardSecurityInfo>(DEFAULT_WARRANT_SIZE);
        this.issuerScoreCalculatorFactory = new IssuerScoreCalculatorFactory(this.issuers);
        this.issuerSwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(DEFAULT_ISSUER_SIZE);
        this.warrantSwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(DEFAULT_WARRANT_SIZE);
    }
    
    public void onEntitiesLoaded() {
        for (final Issuer issuer : issuers.entities()) {
            issuerSwitches.put(issuer.sid(), new StrategyCircuitSwitch(DEFAULT_WARRANT_PER_ISSUER_SIZE, StrategySwitchType.SCOREBOARD_PERSIST, StrategyParamSource.ISSUER_SID, issuer.sid()));
        }
        for (final StrategySecurity security : securities.entities()) {
            final ScoreBoardSecurityInfo scoreBoardSecurity = (ScoreBoardSecurityInfo)security; 
            if (scoreBoardSecurity.securityType().equals(SecurityType.WARRANT)) {
                this.warrants.put(security.sid(), scoreBoardSecurity);
                final ScoreBoardSecurityInfo underlying = (ScoreBoardSecurityInfo)securities.get(scoreBoardSecurity.underlyingSid());
                scoreBoardSecurity.underlying(underlying);
                scoreBoardSecurity.pricingInstrument(securities.get(security.pricingSecSid()));
                scoreBoardSecurity.issuer(issuers.get(security.issuerSid()));
                
                final StrategyCircuitSwitch circuitSwitch = new StrategyCircuitSwitch(0, StrategySwitchType.SCOREBOARD_PERSIST, StrategyParamSource.SECURITY_SID, security.sid(), warrantSwitchHandler);
                warrantSwitches.put(security.sid(), circuitSwitch);                
                final StrategyCircuitSwitch issuerSwitch = issuerSwitches.get(security.issuerSid());
                issuerSwitch.hookChildSwitch(circuitSwitch);

                // Create MarketMakingChangeTracker and register with security (in constructor)
                MarketMakingChangeTracker mmChangeTracker = MarketMakingChangeTracker.of(scoreBoardSecurity);
                
                // Create MarketContext and register with MarketMakingChangeTracker 
                final int initialHandlerCapacity = 2;
                MarketContext marketContext = MarketContext.of(scoreBoardSecurity, 
                		underlying, 
                		PutOrCall.NULL_VAL, 
                		scoreBoardSecurity.spreadTable(), 
                		underlying.spreadTable(),
                		Integer.MIN_VALUE, 
                		Integer.MIN_VALUE, 
                		initialHandlerCapacity);
                mmChangeTracker.registerChangeHandler(marketContext.secMMChangeListener());
                underlying.registerUnderlyingOrderBookUpdateHandler(marketContext.undOrderBookChangeHandler());
                
                // Create VanillaCutLossStateMachine and register with MarketContext
//                CutLossFsm fsm = CutLossFsm.createVanillaFsm(marketContext);
//                fsm.registerSignalHandler(commonSignalHandler);
//                fsm.init();
//                scoreBoardSecurity.addCutLossFsm(fsm);
                
//                fsm = CutLossFsm.createMmBidSlideFsm(marketContext);
//                fsm.registerSignalHandler(commonSignalHandler);
//                fsm.init();
//                scoreBoardSecurity.addCutLossFsm(fsm);
                scoreBoardSecurity.marketContext(marketContext);
                
                scoreBoardSecurity.scoreBoardCalculator(ScoreBoardCalculator.of(scoreBoardSecurity, issuerScoreCalculatorFactory.getIssuerScoreCalculator(scoreBoardSecurity.issuerSid())));
                new WarrantTradesAnalyser(scoreBoardSecurity);
                new ScoreBoardGreeksUpdateHandler(scoreBoardSecurity);
                new WarrantBehaviourAnalyser(scoreBoardSecurity, underlying);
                new StrategySwitchController(scoreBoardSecurity, switchControlHandler);
                new OurTriggerAnalyser(scoreBoardSecurity, underlying);
                
                scoreBoardSecurity.scoreBoardCalculator().initializeScore();
            }
            else if (scoreBoardSecurity.securityType().equals(SecurityType.STOCK)) {
                new UnderlyingSignalGenerator(scoreBoardSecurity);
            }
        }
    }
    
//    private final static SignalHandler commonSignalHandler = new SignalHandler() {
//		@Override
//		public void handleCutLossSignal(TradeContext context, int tickLevel, long nanoOfDay) {
//			LOG.info("Suggest to sell [secSid:{}, price:{}, quantity:{}, tag:{}, time:{}]", 
//					context.market().security().sid(), context.market().secSpreadTable().tickToPrice(tickLevel), context.totalBoughtQuantity(), context.tag(), nanoOfDay);
//		}
//	};
    
    
    public void setupScoreBoards() {
        final long strategyId = strategyManager.getStrategyId(SpeedArbHybridFactory.STRATEGY_NAME);
        final StrategyContext context = strategyManager.getStrategyContext(strategyId);
        for (final StrategySecurity warrant : context.getWarrants().values()) {
            final ScoreBoardSecurityInfo scoreBoardSecurity = ((ScoreBoardSecurityInfo)warrant);
            final WrtInputParams wrtParams = context.getStrategyWrtParams(warrant.sid(), false);
            if (wrtParams != null) {
                scoreBoardSecurity.wrtParams(wrtParams);
            }
            final UndInputParams undParams = context.getStrategyUndParams(warrant.underlyingSid(), false);
            if (undParams != null) {
                scoreBoardSecurity.undParams(undParams);
            }            
        }
        
        // TODO remove when switches are fully controlled by GUI and persisted to db
        for (final StrategyCircuitSwitch strategySwitch : issuerSwitches.values()) {
            strategySwitch.switchOff();
        }
        for (final StrategyCircuitSwitch strategySwitch : warrantSwitches.values()) {
            strategySwitch.switchOn();
        }
    }
    
    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategySwitchSbeDecoder strategySwitch) throws Exception {
        final StrategySwitchType switchType = strategySwitch.switchType();
        final StrategyParamSource source = strategySwitch.switchSource();
        if (source == StrategyParamSource.ISSUER_SID && switchType == StrategySwitchType.SCOREBOARD_PERSIST) {
            flipSwitchForIssuer(strategySwitch.sourceSid(), strategySwitch.onOff(), false);
        }
        else if (source == StrategyParamSource.SECURITY_SID) {
            if (switchType == StrategySwitchType.SCOREBOARD_PERSIST) {
                flipSwitchForSecurity(strategySwitch.sourceSid(), strategySwitch.onOff(), false);
            }
            else if (switchType == StrategySwitchType.STRATEGY_DAY_ONLY) {
                updateStrategySwitchState(strategySwitch.sourceSid(), strategySwitch.onOff());
            }
        }
    }
    
    public void handleIssuerSwitchUpdateRequest(final long issuerSid, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        flipSwitchForIssuer(issuerSid, BooleanType.get(onOffInt), true);
    }
    
    public void handleWarrantSwitchUpdateRequest(final long secSid, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        flipSwitchForSecurity(secSid, BooleanType.get(onOffInt), true);
    }
    
    public void flipSwitchForIssuer(final long issuerSid, final BooleanType isOn, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = issuerSwitches.get(issuerSid);
        if (strategySwitch == null) {
            throw new Exception("Cannot find switch for issuer with sid " + issuerSid);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on issuer {}", box(issuerSid));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off issuer {}", box(issuerSid));
            strategySwitch.switchOff();
        }
        if (broadcast) {
            strategyInfoSender.broadcastSwitch(strategySwitch);
        }
    }
    
    public void flipSwitchForSecurity(final long securitySid, final BooleanType isOn, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = warrantSwitches.get(securitySid);
        if (strategySwitch == null) {
            throw new Exception("Cannot find switch for security with sid " + securitySid);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on security {}", box(securitySid));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off security {}", box(securitySid));
            strategySwitch.switchOff();
        }
        if (broadcast) {
            strategyInfoSender.broadcastSwitch(strategySwitch);
        }
    }

    public int sendSwitchesSnapshots(final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyCircuitSwitch strategySwitch : issuerSwitches.values()) {
            strategyInfoSender.sendSwitch(strategySwitch, sender, request, seqNum++);
        }
        for (final StrategyCircuitSwitch strategySwitch : warrantSwitches.values()) {
            strategyInfoSender.sendSwitch(strategySwitch, sender, request, seqNum++);
        }
        return seqNum;
    }

    public void updateStrategySwitchState(final long securitySid, final BooleanType isOn) {
        final ScoreBoardSecurityInfo security = (ScoreBoardSecurityInfo)securities.get(securitySid);
        security.strategySwitchController().updateStrategyState(isOn == BooleanType.TRUE);
    }
    
    public Long2ObjectOpenHashMap<ScoreBoardSecurityInfo> warrants() {
        return warrants;
    }

}
