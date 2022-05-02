package com.lunar.strategy.speedarbhybrid;

import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.strategy.AllowAllTriggerGenerator;
import com.lunar.strategy.DeltaLimitAlertGenerator;
import com.lunar.strategy.IssuerResponseTimeGenerator;
import com.lunar.strategy.StrategyContext;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategyScheduler;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.TurnoverMakingSignalGenerator;
import com.lunar.strategy.UnderlyingSpotObserver;
import com.lunar.strategy.VelocityTriggerGenerator;
import com.lunar.strategy.parameters.BucketOutputParams;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.statemachine.StateMachineEventBus;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class SpeedArbHybridContext extends StrategyContext {
    private static final int DEFAULT_NUM_OF_UNDERLYINGS = 4000; // make this a parameter if we care about this later
    private static final int DEFAULT_NUM_OF_WARRANTS = 40000; // make this a parameter if we care about this later
    private static final int DEFAULT_NUM_OF_ISSUERS = 10;
    public static final int DELTA_ALLOWANCE = 1100;
    
    private class ContextByWarrant {
        final StateMachineEventBus m_wrtStratEventBus;
        SpeedArbHybridStrategySignalHandler m_strategySignalHandler;
        SpeedArbHybridWrtSignalGenerator m_warrantSignalGenerator;
        SpreadTableScaleFormulaBridge m_spreadTableScaleFormulaBridge;
        TurnoverMakingSignalGenerator m_turnoverMakingSignalGenerator;
        IssuerResponseTimeGenerator m_issuerResponseLagMonitor;
        
        public ContextByWarrant() {
            m_wrtStratEventBus = new StateMachineEventBus.SingleStateMachineEventBus();
        }

        StateMachineEventBus getWrtStratEventBus() {
            return m_wrtStratEventBus;
        }

        SpeedArbHybridStrategySignalHandler getStrategySignalHandler() {
            return m_strategySignalHandler;
        }

        void setStrategySignalHandler(final SpeedArbHybridStrategySignalHandler strategySignalHandler) {
            m_strategySignalHandler = strategySignalHandler;
        }

        SpeedArbHybridWrtSignalGenerator getWarrantSignalGenerator() {
            return m_warrantSignalGenerator;
        }

        void setWarrantSignalGenerator(final SpeedArbHybridWrtSignalGenerator warrantSignalGenerator) {
            m_warrantSignalGenerator = warrantSignalGenerator;
        }
        
        SpreadTableScaleFormulaBridge getSpreadTableScaleFormulaBridge() {
            return m_spreadTableScaleFormulaBridge;
        }
        
        void setSpreadTableScaleFormulaBridge(final SpreadTableScaleFormulaBridge spreadTableScaleFormulaBridge) {
            m_spreadTableScaleFormulaBridge = spreadTableScaleFormulaBridge;
        }
        
        TurnoverMakingSignalGenerator getTurnoverMakingSignalGenerator() {
            return m_turnoverMakingSignalGenerator;
        }
        
        void setTurnoverMakingSignalGenerator(final TurnoverMakingSignalGenerator turnoverMakingSignalGenerator) {
            m_turnoverMakingSignalGenerator = turnoverMakingSignalGenerator;
        }
        
        IssuerResponseTimeGenerator getIssuerResponseTimeGenerator() {
            return m_issuerResponseLagMonitor;
        }
        
        void setIssuerResponseTimeGenerator(final IssuerResponseTimeGenerator issuerResponseTimeGenerator) {
            m_issuerResponseLagMonitor = issuerResponseTimeGenerator;
        }
        

    }

    private class ContextByUnderlying {        
        final StateMachineEventBus m_callWrtStratEventBuses;
        final StateMachineEventBus m_putWrtStratEventBuses;
        SpeedArbHybridUndSignalGenerator m_underlyingSignalGenerator;
        ObjectArrayList<UnderlyingSpotObserver> m_callSpotObservers;
        ObjectArrayList<UnderlyingSpotObserver> m_putSpotObservers;
        VelocityTriggerGenerator m_velocityTriggerGenerator5ms;
        VelocityTriggerGenerator m_velocityTriggerGenerator10ms;

        public ContextByUnderlying() {
            m_callWrtStratEventBuses = new StateMachineEventBus.StaticStateMachineEventBus();
            m_putWrtStratEventBuses = new StateMachineEventBus.StaticStateMachineEventBus();
        }

        StateMachineEventBus getCallWrtStratEventBuses() {
            return m_callWrtStratEventBuses;
        }

        StateMachineEventBus getPutWrtStratEventBuses() {
            return m_putWrtStratEventBuses;
        }

        SpeedArbHybridUndSignalGenerator getUnderlyingSignalGenerator() {
            return m_underlyingSignalGenerator;
        }

        void setUnderlyingSignalGenerator(SpeedArbHybridUndSignalGenerator underlyingSignalGenerator) {
            m_underlyingSignalGenerator = underlyingSignalGenerator;
        }

        ObjectArrayList<UnderlyingSpotObserver> getCallSpotObservers() {
            return m_callSpotObservers;
        }

        void setCallSpotObservers(final ObjectArrayList<UnderlyingSpotObserver> spotObservers) {
            m_callSpotObservers = spotObservers;
        }
        
        ObjectArrayList<UnderlyingSpotObserver> getPutSpotObservers() {
            return m_putSpotObservers;
        }

        void setPutSpotObservers(final ObjectArrayList<UnderlyingSpotObserver> spotObservers) {
            m_putSpotObservers = spotObservers;
        }
        
        VelocityTriggerGenerator getVelocityTriggerGenerator5ms() {
            return m_velocityTriggerGenerator5ms;
        }
        
        void setVelocityTriggerGenerator5ms(final VelocityTriggerGenerator triggerGenerator) {
            m_velocityTriggerGenerator5ms = triggerGenerator;
        }
        
        VelocityTriggerGenerator getVelocityTriggerGenerator10ms() {
            return m_velocityTriggerGenerator10ms;
        }
        
        void setVelocityTriggerGenerator10ms(final VelocityTriggerGenerator triggerGenerator) {
            m_velocityTriggerGenerator10ms = triggerGenerator;
        }
    }	

    private class ContextByIssuerAndUnderlying {
        DeltaLimitAlertGenerator m_deltaLimitAlertGenerator;
        
        DeltaLimitAlertGenerator getDeltaLimitAlertGenerator() {
            return m_deltaLimitAlertGenerator;
        }
        
        void setDeltaLimitAlertGenerator(final DeltaLimitAlertGenerator deltaLimitAlertGenerator) {
            this.m_deltaLimitAlertGenerator = deltaLimitAlertGenerator;
        }
    }
    
    private final Long2ObjectOpenHashMap<ContextByWarrant> m_warrantContexts;
    private final Long2ObjectOpenHashMap<ContextByUnderlying> m_underlyingContexts;
    private final Long2ObjectOpenHashMap<ContextByIssuerAndUnderlying> m_issuerUndContexts;
    private final StrategyScheduler m_strategyScheduler;
    private final SpeedArbHybridFactory m_factory;
    private final SpeedArbHybridTriggerController m_triggerController = new SpeedArbHybridTriggerController(this);
    private final AllowAllTriggerGenerator m_allowAllTriggerGenerator = new AllowAllTriggerGenerator();

    public SpeedArbHybridContext(final StrategyType strategyType, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final LongEntityManager<StrategyIssuer> issuers, final StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender, final StrategyScheduler scheduler, final SpeedArbHybridFactory factory) {        
        super(strategyType, underlyings, warrants, issuers, orderService, strategyInfoSender);
        m_strategyScheduler = scheduler;
        m_factory = factory;
        m_warrantContexts = new Long2ObjectOpenHashMap<ContextByWarrant>(DEFAULT_NUM_OF_WARRANTS);
        m_underlyingContexts = new Long2ObjectOpenHashMap<ContextByUnderlying>(DEFAULT_NUM_OF_UNDERLYINGS);
        m_issuerUndContexts = new Long2ObjectOpenHashMap<ContextByIssuerAndUnderlying>(DEFAULT_NUM_OF_UNDERLYINGS * DEFAULT_NUM_OF_ISSUERS);
    }

    public SpeedArbHybridStrategySignalHandler getStrategySignalHandler(final StrategySecurity security) {
        final long securitySid = security.sid();
        if (m_warrantContexts.containsKey(securitySid)) {
            return m_warrantContexts.get(securitySid).getStrategySignalHandler();
        }
        return null;
    }
    
    public SpeedArbHybridWrtSignalGenerator getWrtSignalGenerator(final StrategySecurity security) {
        final long securitySid = security.sid();
        if (m_warrantContexts.containsKey(securitySid)) {
            return m_warrantContexts.get(securitySid).getWarrantSignalGenerator();
        }
        return null;
    }

    public SpeedArbHybridUndSignalGenerator getUndSignalGenerator(final StrategySecurity security) {
        final long securitySid = security.sid();
        if (m_underlyingContexts.containsKey(securitySid)) {
            return m_underlyingContexts.get(securitySid).getUnderlyingSignalGenerator();
        }
        return null;
    }
    
    public TurnoverMakingSignalGenerator getTurnoverMakingSignalGenerator(final StrategySecurity security) {
        final long securitySid = security.sid();
        if (m_warrantContexts.containsKey(securitySid)) {
            return m_warrantContexts.get(securitySid).getTurnoverMakingSignalGenerator();
        }
        return null;
    }

    public SpeedArbHybridTriggerController getSpeedArbHybridTriggerController() {
        return m_triggerController;
    }
    
    public VelocityTriggerGenerator getVelocityTriggerGenerator5ms(final StrategySecurity security) {
        final long securitySid = security.sid();
        if (m_underlyingContexts.containsKey(securitySid)) {
            return m_underlyingContexts.get(securitySid).getVelocityTriggerGenerator5ms();
        }
        return null;
    }

    public VelocityTriggerGenerator getVelocityTriggerGenerator10ms(final StrategySecurity security) {
        final long securitySid = security.sid();
        if (m_underlyingContexts.containsKey(securitySid)) {
            return m_underlyingContexts.get(securitySid).getVelocityTriggerGenerator10ms();
        }
        return null;
    }
    
    public DeltaLimitAlertGenerator getDeltaLimitAlertGenerator(final Issuer issuer, final StrategySecurity underlying) {
        final long issuerUndSid = convertToIssuerUndSid(issuer.sid(), underlying.sid());
        if (m_issuerUndContexts.containsKey(issuerUndSid)) {
            return m_issuerUndContexts.get(issuerUndSid).getDeltaLimitAlertGenerator();
        }
        return null;
    }
    
    public AllowAllTriggerGenerator getAllowAllTriggerGenerator() {
        return m_allowAllTriggerGenerator;
    }

    void initializeContextForSecurity(final StrategySecurity security) {    
        final long securitySid = security.sid();
        final long underlyingSid = security.underlyingSid();
        final int issuerSid = security.issuerSid();
        final long issuerUndSid = convertToIssuerUndSid(issuerSid, underlyingSid);

        final StrategySecurity pricingInstrument = security.pricingInstrument();
        if (m_warrantContexts.containsKey(securitySid)) {
            return;
        }
        
        final GenericStrategyTypeParams speedArbParams = getStrategyTypeParams();
        final GenericUndParams speedArbUndParams = getStrategyUndParams(underlyingSid, true);
        final GenericWrtParams speedArbWrtParams = getStrategyWrtParams(securitySid, true);
        final GenericIssuerParams speedArbIssuerParams = getStrategyIssuerParams(issuerSid, true);
        final GenericIssuerUndParams speedArbIssuerUndParams = getStrategyIssuerUndParams(issuerSid, underlyingSid, true);
        final BucketOutputParams bucketOutputParams = getBucketUpdateParams(securitySid, true);

        ContextByUnderlying contextByUnderlying = m_underlyingContexts.get(underlyingSid);
        if (contextByUnderlying == null) {
            contextByUnderlying = new ContextByUnderlying();
            final ObjectArrayList<UnderlyingSpotObserver> callSpotObservers = ObjectArrayList.wrap(new UnderlyingSpotObserver[ObjectArrayList.DEFAULT_INITIAL_CAPACITY]);
            callSpotObservers.size(0);
            final ObjectArrayList<UnderlyingSpotObserver> putSpotObservers = ObjectArrayList.wrap(new UnderlyingSpotObserver[ObjectArrayList.DEFAULT_INITIAL_CAPACITY]);
            putSpotObservers.size(0);
            final SpeedArbHybridUndSignalGenerator undSignalGenerator = new SpeedArbHybridUndSignalGenerator(getStrategyType(), pricingInstrument, getStrategyInfoSender(), speedArbUndParams, speedArbParams, callSpotObservers, putSpotObservers);
            contextByUnderlying.setUnderlyingSignalGenerator(undSignalGenerator);
            contextByUnderlying.setCallSpotObservers(callSpotObservers);
            contextByUnderlying.setPutSpotObservers(putSpotObservers);
            contextByUnderlying.setVelocityTriggerGenerator5ms(m_factory.createVelocityTriggerGenerator(pricingInstrument, speedArbUndParams, 5_000_000L, 8192));
            contextByUnderlying.setVelocityTriggerGenerator10ms(m_factory.createVelocityTriggerGenerator(pricingInstrument, speedArbUndParams, 10_000_000L, 16384));
            m_underlyingContexts.put(underlyingSid, contextByUnderlying);
        }
        
        ContextByIssuerAndUnderlying contextByIssuerAndUnderlying = m_issuerUndContexts.get(issuerUndSid);
        if (contextByIssuerAndUnderlying == null) {
            contextByIssuerAndUnderlying = new ContextByIssuerAndUnderlying();
            contextByIssuerAndUnderlying.setDeltaLimitAlertGenerator(new DeltaLimitAlertGenerator(security.underlying(), security.issuer(), contextByUnderlying.getUnderlyingSignalGenerator(), speedArbIssuerUndParams, getStrategyInfoSender()));
            m_issuerUndContexts.put(issuerUndSid, contextByIssuerAndUnderlying);
        }
        
        final ContextByWarrant contextByWarrant = new ContextByWarrant();
        contextByWarrant.setSpreadTableScaleFormulaBridge(SpreadTableScaleFormulaBridge.of(security.underlying().securityType()));
        contextByWarrant.setTurnoverMakingSignalGenerator(new TurnoverMakingSignalGenerator(security, speedArbWrtParams));
        contextByWarrant.setIssuerResponseTimeGenerator(new IssuerResponseTimeGenerator(security, m_strategyScheduler));

        final SpeedArbHybridWrtSignalGenerator warrantSignalGenerator = new SpeedArbHybridWrtSignalGenerator(getStrategyType(), security,
                contextByWarrant.getSpreadTableScaleFormulaBridge(), contextByUnderlying.getUnderlyingSignalGenerator(), contextByWarrant.getWrtStratEventBus(), getStrategyInfoSender(), speedArbWrtParams, speedArbParams, contextByWarrant.getIssuerResponseTimeGenerator(), bucketOutputParams);
        contextByWarrant.setWarrantSignalGenerator(warrantSignalGenerator);				

        final SpeedArbHybridStrategySignalHandler strategySignalHandler = new SpeedArbHybridStrategySignalHandler(getStrategyType(), security,
                contextByUnderlying.getUnderlyingSignalGenerator(),
                warrantSignalGenerator,
                contextByWarrant.getSpreadTableScaleFormulaBridge(),
                getOrderService(),
                getStrategyInfoSender(),
                speedArbWrtParams,
                speedArbParams,
                speedArbUndParams,
                speedArbIssuerParams,
                speedArbIssuerUndParams,
                m_triggerController,
                contextByWarrant.getIssuerResponseTimeGenerator(),
                contextByWarrant.getTurnoverMakingSignalGenerator(),
                contextByIssuerAndUnderlying.getDeltaLimitAlertGenerator(),
                bucketOutputParams);
        contextByWarrant.setStrategySignalHandler(strategySignalHandler);

        if (security.putOrCall().equals(PutOrCall.CALL)) {
            contextByUnderlying.getCallSpotObservers().add(warrantSignalGenerator);
            contextByUnderlying.getCallWrtStratEventBuses().subscribeStateMachine(securitySid, strategySignalHandler.getStateMachine());
        }
        else {
            contextByUnderlying.getPutSpotObservers().add(warrantSignalGenerator);
            contextByUnderlying.getPutWrtStratEventBuses().subscribeStateMachine(securitySid, strategySignalHandler.getStateMachine());
        }
        contextByWarrant.getWrtStratEventBus().subscribeStateMachine(securitySid, strategySignalHandler.getStateMachine());
        
        m_warrantContexts.put(securitySid, contextByWarrant);
    }



}
