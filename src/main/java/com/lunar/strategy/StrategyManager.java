package com.lunar.strategy;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.LongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.config.StrategyServiceConfig;
import com.lunar.core.SystemClock;
import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.entity.SidManager;
import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.StrategyCircuitSwitch.SwitchHandler;
import com.lunar.strategy.parameters.BucketOutputParams;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.parameters.ParamsSbeEncodable;
import com.lunar.strategy.parameters.WrtInputParams;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridFactory;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class StrategyManager {
    private class StrategyTypeInfo extends StrategyType {
        private StrategyFactory factory;
        private StrategyContext context;
        
        protected StrategyTypeInfo(final long sid, final String name, final StrategyFactory factory) {
            super(sid, name);
            this.factory = factory;
            this.context = factory.createStrategyContext(this, m_underlyings, m_warrants, m_issuers, m_orderService, m_strategyInfoSender, m_scheduler);
        }
        
        public StrategyFactory factory() {
            return this.factory;
        }
        
        public StrategyContext context() {
            return this.context;
        }
        
    }
    
    private class GenericSwitchHandler implements SwitchHandler {
        @Override
        public void onPerformTask(final long securitySid) {
            final StrategySecurity security = securities().get(securitySid);
            try {
                switchOnStrategy(security.activeStrategy());
            }
            catch (final Exception e) {
                LOG.error("Failed to switch off strategy for {}...", security.code());
            }
        }

        @Override
        public void onStopTask(final long securitySid) {
            final StrategySecurity security = securities().get(securitySid);
            try {
                switchOffStrategy(security.activeStrategy());
            }
            catch (final Exception e) {
                LOG.error("Failed to switch off strategy for {}...", security.code());
            }          
        }
    }
    
    private static final Logger LOG = LogManager.getLogger(StrategyManager.class);

    private static final int INITIAL_NUMBER_OF_STRATEGIES = 10;
    private static final int DEFAULT_UNDERLYING_SIZE = 200;
    private static final int DEFAULT_WARRANT_SIZE = 5000;
    private static final int DEFAULT_ISSUER_SIZE = 100;
    private static final int DEFAULT_WARRANT_PER_UNDERLYING_SIZE = 1000;
    private static final int DEFAULT_WARRANT_PER_ISSUER_SIZE = 1000;
    
    private final LongEntityManager<StrategyTypeInfo> m_strategyTypes;
    private final LongEntityManager<? extends StrategySecurity> m_securities;
    private final LongEntityManager<StrategyIssuer> m_issuers;    
    
    private final Long2ObjectOpenHashMap<Strategy> m_strategiesByWarrants;
    
    private final Object2ObjectOpenHashMap<String, StrategyFactory> m_strategyFactoriesByName;
    private final Object2LongOpenHashMap<String> m_strategyIdByName;

    private final StrategyCircuitSwitch m_mainSwitch;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> m_strategySwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> m_underlyingSwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> m_issuerSwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> m_warrantSwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch> m_dayOnlyWarrantSwitches;

    private final Long2ObjectOpenHashMap<StrategySecurity> m_underlyings;
    private final Long2ObjectOpenHashMap<StrategySecurity> m_warrants;
    private final Long2ObjectOpenHashMap<ArrayList<StrategySecurity>> m_warrantsByUnderlying;
    private final Long2ObjectOpenHashMap<ArrayList<StrategySecurity>> m_warrantsByIssuer;
    
    private final StrategyOrderService m_orderService;
    private final StrategyInfoSender m_strategyInfoSender;
    private final SwitchHandler m_switchHandler;
    private final StrategyScheduler m_scheduler;
    
    private final StrategySchema m_strategySchema;
    
    private final ObjectArrayList<StrategyWarmupEngine> m_warmupEngines;
    
    private final int expectedNumStrategies;
    private final int expectedNumWarrants;
    private final int expectedNumUnderlyings;
    private final int expectedNumSecurities;
    private final int expectedNumIssuers;
    private final int expectedNumWrtsPerUnd;
    private final int expectedNumWrtsPerIssuer;
    
    private StrategyExitMode m_currentExitMode = StrategyExitMode.NULL_VAL;
    
    public StrategyManager(final StrategyServiceConfig specificConfig, final LongEntityManager<? extends StrategySecurity> securities, LongEntityManager<StrategyIssuer> issuers, final StrategyOrderService orderService, final StrategyInfoSender paramsUpdateHandler, final StrategyScheduler scheduler) {
        this(specificConfig, securities, issuers, orderService, paramsUpdateHandler, null, scheduler);
    }

    public StrategyManager(final StrategyServiceConfig specificConfig, final LongEntityManager<? extends StrategySecurity> securities, LongEntityManager<StrategyIssuer> issuers, final StrategyOrderService orderService, final StrategyInfoSender paramsUpdateHandler, final SwitchHandler switchHandler, final StrategyScheduler scheduler) {
        m_orderService = orderService;
        m_strategyInfoSender = paramsUpdateHandler;
        m_switchHandler = switchHandler == null ? new GenericSwitchHandler() : switchHandler;
        m_scheduler = scheduler;
        m_strategySchema = new GenericStrategySchema();
        
        expectedNumStrategies = INITIAL_NUMBER_OF_STRATEGIES;
        expectedNumWarrants = specificConfig.numWarrants();
        expectedNumUnderlyings = specificConfig.numUnderlyings();
        expectedNumSecurities = expectedNumWarrants + expectedNumUnderlyings;
        expectedNumIssuers = DEFAULT_ISSUER_SIZE;
        expectedNumWrtsPerUnd = specificConfig.numWrtsPerUnd();
        expectedNumWrtsPerIssuer = DEFAULT_WARRANT_PER_ISSUER_SIZE;

        m_strategyTypes = new LongEntityManager<StrategyTypeInfo>(expectedNumStrategies);
        m_securities = securities;
        m_issuers = issuers;
        
        m_underlyings = new Long2ObjectOpenHashMap<StrategySecurity>(expectedNumUnderlyings);
        m_warrants = new Long2ObjectOpenHashMap<StrategySecurity>(expectedNumWarrants);
        m_warrantsByUnderlying = new Long2ObjectOpenHashMap<ArrayList<StrategySecurity>>(expectedNumUnderlyings);
        m_warrantsByIssuer = new Long2ObjectOpenHashMap<ArrayList<StrategySecurity>>(expectedNumIssuers);

        m_strategyFactoriesByName = new Object2ObjectOpenHashMap<String, StrategyFactory>(expectedNumStrategies);
        m_strategyIdByName = new Object2LongOpenHashMap<String>(expectedNumStrategies);
        m_strategyIdByName.defaultReturnValue(SidManager.INVALID_SID);
        m_mainSwitch = new StrategyCircuitSwitch(expectedNumWarrants, StrategySwitchType.STRATEGY_DAY_ONLY, StrategyParamSource.NULL_VAL, 0);
        m_strategySwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(expectedNumStrategies);
        m_underlyingSwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(expectedNumUnderlyings);
        m_warrantSwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(expectedNumWarrants);
        m_dayOnlyWarrantSwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(expectedNumWarrants);
        m_issuerSwitches = new Long2ObjectLinkedOpenHashMap<StrategyCircuitSwitch>(expectedNumIssuers);

        m_strategiesByWarrants = new Long2ObjectOpenHashMap<Strategy>(expectedNumWarrants);

        m_warmupEngines = ObjectArrayList.wrap(new StrategyWarmupEngine[INITIAL_NUMBER_OF_STRATEGIES]);
        m_warmupEngines.size(0);
    }
    
	public void onEntitiesLoaded() {
	    for (final Issuer issuer : m_issuers.entities()) {
            m_issuerSwitches.put(issuer.sid(), new StrategyCircuitSwitch(expectedNumWrtsPerIssuer, StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.ISSUER_SID, issuer.sid()));
        }
	    final Long2LongOpenHashMap undToPrcMap = new Long2LongOpenHashMap(2);
	    for (final StrategySecurity security : m_securities.entities()) {
	        if (!security.isAlgo()) {
	            continue;
	        }
	        if (security.securityType().equals(SecurityType.WARRANT) && security.underlyingSid() != Security.INVALID_SECURITY_SID) {
	            m_warrants.put(security.sid(), security);
	            m_dayOnlyWarrantSwitches.put(security.sid(), new StrategyCircuitSwitch(1, StrategySwitchType.STRATEGY_DAY_ONLY, StrategyParamSource.SECURITY_SID, security.sid()));
	            m_warrantSwitches.put(security.sid(), new StrategyCircuitSwitch(0, StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.SECURITY_SID, security.sid(), m_switchHandler));
	            if (!m_warrantsByUnderlying.containsKey(security.underlyingSid())) {
	                m_warrantsByUnderlying.put(security.underlyingSid(), new ArrayList<StrategySecurity>(expectedNumWrtsPerUnd));
	            }
	            m_warrantsByUnderlying.get(security.underlyingSid()).add(security);
	            
	            if (!m_warrantsByIssuer.containsKey(security.issuerSid())) {
	                m_warrantsByIssuer.put(security.issuerSid(), new ArrayList<StrategySecurity>(expectedNumWrtsPerIssuer));
	            }
	            m_warrantsByIssuer.get(security.issuerSid()).add(security);
	        }
	        else if (security.securityType().equals(SecurityType.STOCK)) {
	            m_underlyings.put(security.sid(), security);
	            m_underlyingSwitches.put(security.sid(), new StrategyCircuitSwitch(expectedNumWrtsPerUnd, StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.UNDERLYING_SECURITY_SID, security.sid()));
	        }
            else if (security.securityType().equals(SecurityType.INDEX)) {
                m_underlyings.put(security.sid(), security);
                m_underlyingSwitches.put(security.sid(), new StrategyCircuitSwitch(expectedNumWrtsPerUnd, StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.UNDERLYING_SECURITY_SID, security.sid()));
            }
	        else if (security.securityType().equals(SecurityType.FUTURES)) {
	            undToPrcMap.put(security.underlyingSid(), security.sid());
	        }
	        else {
	            //LOG.warn("Unknown security: secCode {}, securityType {}, underlyingId {}", security.code(), security.securityType(), box(security.underlyingSid()));
	        }
	    }
	    for (final StrategySecurity security : m_warrants.values()) {
	        if (undToPrcMap.containsKey(security.underlyingSid())) {
	            security.pricingSecSid(undToPrcMap.get(security.underlyingSid()));
	        }
	        else {
	            security.pricingSecSid(security.underlyingSid());
	        }
            security.underlying(m_underlyings.get(security.underlyingSid()));
            security.pricingInstrument(m_securities.get(security.pricingSecSid()));
            security.issuer(m_issuers.get(security.issuerSid()));
	        final StrategyCircuitSwitch underlyingSwitch = m_underlyingSwitches.get(security.underlyingSid());
	        final StrategyCircuitSwitch issuerSwitch = m_issuerSwitches.get(security.issuerSid());
	        final StrategyCircuitSwitch dayOnlySwitch = m_dayOnlyWarrantSwitches.get(security.sid());
	        dayOnlySwitch.switchOn();
	        if (underlyingSwitch == null) {
	            LOG.error("Underying not found for warrant: secCode {}, undSid {}", security.code(), security.underlyingSid());
	            continue;
	        }
            if (issuerSwitch == null) {
                LOG.error("Issuer not found for warrant: secCode {}, issuerSid {}", security.code(), security.issuerSid());
                continue;
            }
	        final StrategyCircuitSwitch circuitSwitch = m_warrantSwitches.get(security.sid());
	        dayOnlySwitch.hookChildSwitch(circuitSwitch);
            underlyingSwitch.hookChildSwitch(circuitSwitch);
            issuerSwitch.hookChildSwitch(circuitSwitch);
            m_mainSwitch.hookChildSwitch(circuitSwitch);            
	    }
    }
	
	public void setupStrategies() {
	    final ArrayList<StrategySecurity> sortedWarrants = new ArrayList<StrategySecurity>(m_warrants.values());
	    sortedWarrants.sort(new Comparator<StrategySecurity>() {
            @Override
            public int compare(StrategySecurity o1, StrategySecurity o2) {
                return Integer.compare(o1.sortOrder(), o2.sortOrder());
            }
	    });
	    for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
	        for (final StrategySecurity security : sortedWarrants) {
	            //TODO should dynamic find out if the security uses this strategy by:
	            //final StrategyContext context = strategyType.context();
	            //final GenericWrtParams wrtParams = context.getStrategyWrtParams(security.sid(), true);
	            //then figure out from wrtParams if strategy is supported for the strategy, etc...
	            //right now just hard-code...	            
	            if (strategyType.name().equals(SpeedArbHybridFactory.STRATEGY_NAME)) {
	                setupStrategyForSecurity(strategyType, security);
	            }
	        }
	    }
	}
	
	private void setupStrategyForSecurity(final StrategyTypeInfo strategyType, final StrategySecurity security) {
        final StrategyContext context = strategyType.context();
        final Strategy strategy = strategyType.factory().createStrategy(context, security.sid(), strategyType.sid());
        security.activeStrategy(strategy);
        try {
            strategy.start();
        }
        catch (final Exception e) {
            LOG.error("Failed to start strategy: secCode {}, strategyType {}", security.code(), strategyType.name());
        }
        this.m_strategiesByWarrants.put(security.sid(), strategy);
	}

    public void registerStrategyFactory(final StrategyFactory factory) {
        this.m_strategyFactoriesByName.put(factory.getStrategyName(), factory);
        final StrategyWarmupEngine warmupEngine = factory.createWarmupEngine();
        m_warmupEngines.add(warmupEngine);        
    }
    
    public void addStrategyType(final long strategyId, final String strategyName) throws Exception {
        final StrategyFactory factory = m_strategyFactoriesByName.get(strategyName);
        if (factory == null) {
            throw new Exception("Factory is not registered for strategy " + strategyName);
        }
        final StrategyTypeInfo strategyType = new StrategyTypeInfo(strategyId, strategyName, factory); 
        m_strategySwitches.put(strategyType.sid(), new StrategyCircuitSwitch(expectedNumWarrants, StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.STRATEGY_ID, strategyType.sid()));                
        m_strategyTypes.add(strategyType);
        m_strategyIdByName.put(strategyName, strategyId);
        m_strategySchema.populateSchema(strategyType);        
    }

    final byte[] strategyNameBytes = new byte[StrategyTypeSbeDecoder.nameLength()];
    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategyTypeSbeDecoder sbe) throws Exception {      
        final long strategyId = sbe.strategyId();
        sbe.getName(strategyNameBytes, 0);
        final String strategyName = new String(strategyNameBytes, StrategyTypeSbeDecoder.nameCharacterEncoding()).trim();
        addStrategyType(strategyId, strategyName);
    }
    
    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategyParamsSbeDecoder sbe) {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(sbe.strategyId());
        if (strategyType == null) {
            return;
        }
        final GenericStrategyTypeParams strategyTypeParams = strategyType.context().getStrategyTypeParams();
        m_strategySchema.handleStrategyTypeParamsSbe(sbe, strategyTypeParams);
    }
    
    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategyUndParamsSbeDecoder sbe) {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(sbe.strategyId());
        if (strategyType == null)
            return;
        if (sbe.undSid() == SidManager.INVALID_SID) {
            m_strategySchema.handleStrategyUndParamsSbe(sbe, (ParamsSbeEncodable)strategyType.context().getStrategyTypeParams().defaultUndInputParams());
        }
        else {
            final Security underlying = m_underlyings.get(sbe.undSid());
            if (underlying == null)
                return;
            final GenericUndParams undParams = strategyType.context.getStrategyUndParams(underlying.sid(), true);
            m_strategySchema.handleStrategyUndParamsSbe(sbe, undParams);
        }
    }    

    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategyIssuerParamsSbeDecoder sbe) {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(sbe.strategyId());
        if (strategyType == null)
            return;
        if (sbe.issuerSid() == SidManager.INVALID_SID) {
            m_strategySchema.handleStrategyIssuerParamsSbe(sbe, (ParamsSbeEncodable)strategyType.context().getStrategyTypeParams().defaultIssuerInputParams());
        }
        else {
            final Issuer issuer = m_issuers.get(sbe.issuerSid());
            if (issuer == null)
                return;
            final GenericIssuerParams issuerParams = strategyType.context.getStrategyIssuerParams(issuer.sid(), true);
            m_strategySchema.handleStrategyIssuerParamsSbe(sbe, issuerParams);
        }
    }
    
    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategyIssuerUndParamsSbeDecoder sbe) {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(sbe.strategyId());
        if (strategyType == null)
            return;
//    	LOG.info("strategy issuer und, got sid [issuerSid:{}, undSid:{}]", sbe.issuerSid(), sbe.undSid());
        if (sbe.issuerSid() == SidManager.INVALID_SID || sbe.undSid() == SidManager.INVALID_SID) {
            m_strategySchema.handleStrategyIssuerUndParamsSbe(sbe, (ParamsSbeEncodable)strategyType.context().getStrategyTypeParams().defaultIssuerUndInputParams());
        }
        else {
            final Issuer issuer = m_issuers.get(sbe.issuerSid());
            if (issuer == null){
//            	LOG.info("strategy issuer und, cannot find issuer [issuerSid:{}]", sbe.issuerSid());
                return;
            }
            final Security underlying = m_underlyings.get(sbe.undSid());
            if (underlying == null){
//            	LOG.info("strategy issuer und, cannot find underlying [undSid:{}]", sbe.undSid());
                return;
            }

            final GenericIssuerUndParams issuerUndParams = strategyType.context.getStrategyIssuerUndParams(issuer.sid(), underlying.sid(), true);
//            LOG.info("Handle strategy issuer und param by strategy schema ");
            m_strategySchema.handleStrategyIssuerUndParamsSbe(sbe, issuerUndParams);
        }
    }

    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategyWrtParamsSbeDecoder sbe) {  
        final StrategyTypeInfo strategyType = m_strategyTypes.get(sbe.strategyId());
        if (strategyType == null)
            return;
        if (sbe.secSid() == SidManager.INVALID_SID) {
            m_strategySchema.handleStrategyWrtParamsSbe(sbe, (ParamsSbeEncodable)strategyType.context().getStrategyTypeParams().defaultWrtInputParams());
        }
        else {
            final Security warrant = m_warrants.get(sbe.secSid());
            if (warrant == null)
                return;
            final GenericWrtParams wrtParams = strategyType.context.getStrategyWrtParams(warrant.sid(), true);
            m_strategySchema.handleStrategyWrtParamsSbe(sbe, wrtParams);
        }
    }
    
    public void handle(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final StrategySwitchSbeDecoder strategySwitch) throws Exception {
        final StrategySwitchType switchType = strategySwitch.switchType();
        final StrategyParamSource source = strategySwitch.switchSource();
        if (source == StrategyParamSource.STRATEGY_ID) {
            flipSwitchForStrategy(strategySwitch.sourceSid(), strategySwitch.onOff(), StrategyExitMode.NULL_VAL, false);
        }
        else if (source == StrategyParamSource.ISSUER_SID) {
            flipSwitchForIssuer(strategySwitch.sourceSid(), strategySwitch.onOff(), StrategyExitMode.NULL_VAL, false);
        }
        else if (source == StrategyParamSource.UNDERLYING_SECURITY_SID) {
            flipSwitchForUnderlying(strategySwitch.sourceSid(), strategySwitch.onOff(), StrategyExitMode.NULL_VAL, false);
        }
        else if (source == StrategyParamSource.SECURITY_SID) {
            if (switchType == StrategySwitchType.STRATEGY_DAY_ONLY) {
                flipDayOnlySwitchForSecurity(strategySwitch.sourceSid(), strategySwitch.onOff(), StrategyExitMode.NULL_VAL, false);
            }
            else {
                flipSwitchForSecurity(strategySwitch.sourceSid(), strategySwitch.onOff(), StrategyExitMode.NULL_VAL, false);
            }
        }
    }
    
    public void handleStrategyTypeParamUpdateRequest(final long strategyId, final ParametersDecoder parameters) throws Exception {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        final GenericStrategyTypeParams params = strategyType.context.getStrategyTypeParams();
        m_strategySchema.handleStratTypeParamUpdateRequest(parameters, params);
        m_strategyInfoSender.broadcastStrategyParams(params);
    }
    
    public void handleUnderlyingParamUpdateRequest(final long strategyId, final long undSid, final ParametersDecoder parameters) throws Exception {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        final GenericUndParams params = strategyType.context().getStrategyUndParams(undSid, false);
        if (params == null) {
            throw new IllegalArgumentException("No strategy parameters found for underlying with sid " + undSid);
        }
        m_strategySchema.handleStratUndParamUpdateRequest(parameters, params);
        m_strategyInfoSender.broadcastStrategyParams(params);        
    }
    
    public void handleIssuerUndParamUpdateRequest(final long strategyId, final long issuerSid, final long undSid, final ParametersDecoder parameters) throws Exception {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        final GenericIssuerUndParams params = strategyType.context().getStrategyIssuerUndParams(issuerSid, undSid, false);
        if (params == null) {
            throw new IllegalArgumentException("No strategy parameters found for issuer sid " + issuerSid + " underlying sid " + undSid);
        }
//        LOG.info("Handle strategy issuer und param by StrategyManager");
        m_strategySchema.handleStratIssuerUndParamUpdateRequest(parameters, params);
        m_strategyInfoSender.broadcastStrategyParams(params);        
    }

    public void handleUnderlyingParamUpdateRequest(final long strategyId, final long issuerSid, final long undSid, final ParametersDecoder parameters) throws Exception {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        final GenericIssuerUndParams params = strategyType.context().getStrategyIssuerUndParams(issuerSid, undSid, false);
        if (params == null) {
            throw new IllegalArgumentException("No strategy parameters found for issuer " +  issuerSid + ", underlying " + undSid);
        }
        m_strategySchema.handleStratIssuerUndParamUpdateRequest(parameters, params);
        m_strategyInfoSender.broadcastStrategyParams(params);
    }

    public void handleIssuerParamUpdateRequest(final long strategyId, final long issuerSid, final ParametersDecoder parameters) throws Exception {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        final GenericIssuerParams params = strategyType.context().getStrategyIssuerParams(issuerSid, false);
        if (params == null) {
            throw new IllegalArgumentException("No strategy parameters found for issuer with sid " + issuerSid);
        }
        m_strategySchema.handleStratIssuerParamUpdateRequest(parameters, params);
        m_strategyInfoSender.broadcastStrategyParams(params);  
    }    

    public void handleWrtParamUpdateRequest(final long strategyId, final long secSid, final ParametersDecoder parameters) throws Exception {
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        final GenericWrtParams params = strategyType.context().getStrategyWrtParams(secSid, false);
        if (params == null) {
            throw new IllegalArgumentException("No strategy parameters found for warrant with sid " + secSid);
        }
        m_strategySchema.handleStratWrtParamUpdateRequest(parameters, params);
        m_strategyInfoSender.broadcastStrategyParams(params); 
    }

    private final ObjectOpenHashSet<WrtInputParams> m_requestedParams = new ObjectOpenHashSet<>(128);
    public void handleWrtParamUpdateRequest(final long strategyId, final LongHashSet receivedUndSecSids, final IntHashSet receivedIssuerSids, final LongHashSet receivedWrtSecSids, final ParametersDecoder parameters) throws Exception {
        if (receivedWrtSecSids.size() == 1) {
            for (final long secSid : receivedWrtSecSids) {
                handleWrtParamUpdateRequest(strategyId, secSid, parameters);
            }
            return;
        }
        final StrategyTypeInfo strategyType = m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            throw new Exception("StrategyType not found for strategy with id " + strategyId);
        }
        m_requestedParams.clear();
        if (receivedWrtSecSids.isEmpty()) {
            for (final StrategySecurity security : m_warrants.values()) {
                if ((receivedIssuerSids.isEmpty() || receivedIssuerSids.contains(security.issuerSid())) && (receivedUndSecSids.isEmpty() || receivedUndSecSids.contains(security.underlyingSid()))) {
                    final GenericWrtParams params = strategyType.context().getStrategyWrtParams(security.sid(), false);
                    if (params != null) {
                        m_requestedParams.add(params);
                    }
                }
            }
        }
        else {
            for (final long secSid : receivedWrtSecSids) {
                final GenericWrtParams params = strategyType.context().getStrategyWrtParams(secSid, false);
                if (params != null) {
                    m_requestedParams.add(params);
                }
            }            
        }
        m_strategySchema.handleStratWrtParamUpdateRequest(parameters, m_requestedParams);
        for (final WrtInputParams params : m_requestedParams) {
            m_strategyInfoSender.broadcastStrategyParams((GenericWrtParams)params);
        }
    }
    
    public void handleMainSwitchUpdateRequest(final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        final byte mode = (byte)((switchValue >> Byte.SIZE) & 0xFF);
        final StrategyExitMode exitMode = mode == 0 ? StrategyExitMode.NULL_VAL : StrategyExitMode.get(mode);
        flipMainSwitch(BooleanType.get(onOffInt), exitMode, true);
    }
    
    public void handleStrategySwitchUpdateRequest(final long strategyId, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        final byte mode = (byte)((switchValue >> Byte.SIZE) & 0xFF);
        final StrategyExitMode exitMode = mode == 0 ? StrategyExitMode.NULL_VAL : StrategyExitMode.get(mode);
        flipSwitchForStrategy(strategyId, BooleanType.get(onOffInt), exitMode, true);
    }
    
    public void handleUnderlyingSwitchUpdateRequest(final long underlyingSid, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        final byte mode = (byte)((switchValue >> Byte.SIZE) & 0xFF);
        final StrategyExitMode exitMode = mode == 0 ? StrategyExitMode.NULL_VAL : StrategyExitMode.get(mode);
        flipSwitchForUnderlying(underlyingSid, BooleanType.get(onOffInt), exitMode, true);
    }
    
    public void handleIssuerSwitchUpdateRequest(final long issuerSid, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        final byte mode = (byte)((switchValue >> Byte.SIZE) & 0xFF);
        final StrategyExitMode exitMode = mode == 0 ? StrategyExitMode.NULL_VAL : StrategyExitMode.get(mode);
        flipSwitchForIssuer(issuerSid, BooleanType.get(onOffInt), exitMode, true);
    }

    public void handleWarrantSwitchUpdateRequest(final long secSid, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        final byte mode = (byte)((switchValue >> Byte.SIZE) & 0xFF);
        final StrategyExitMode exitMode = mode == 0 ? StrategyExitMode.NULL_VAL : StrategyExitMode.get(mode);
        flipSwitchForSecurity(secSid, BooleanType.get(onOffInt), exitMode, true);
    }

    public void handleTempWarrantSwitchUpdateRequest(final long secSid, final long switchValue) throws Exception {
        final byte onOffInt = (byte)(switchValue & 0xFF);
        final byte mode = (byte)((switchValue >> Byte.SIZE) & 0xFF);
        final StrategyExitMode exitMode = mode == 0 ? StrategyExitMode.NULL_VAL : StrategyExitMode.get(mode);
        flipDayOnlySwitchForSecurity(secSid, BooleanType.get(onOffInt), exitMode, true);
    }

    public boolean isMainSwitchOn() {
    	return m_mainSwitch.isOn();    	
    }
    
    public void flipMainSwitch(final BooleanType isOn, final StrategyExitMode exitMode, final boolean broadcast) throws Exception {
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on main switch...");
            m_mainSwitch.switchOn();
        }
        else {
            LOG.info("Flipping off main switch...");
            flipOffSwitchWithExitMode(m_mainSwitch, exitMode);
        }
        if (broadcast) {
            m_strategyInfoSender.broadcastSwitch(m_mainSwitch);
        }
    }
    
    public void flipSwitchForStrategy(final long strategyId, final BooleanType isOn, final StrategyExitMode exitMode, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = m_strategySwitches.get(strategyId);
        if (strategySwitch == null) {
            return;
            //throw new Exception("Cannot find switch for strategy with Id " + strategyId);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on strategy {}", box(strategyId));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off strategy {}", box(strategyId));
            flipOffSwitchWithExitMode(strategySwitch, exitMode);
        }
        if (broadcast) {
            m_strategyInfoSender.broadcastSwitch(m_mainSwitch);
        }
    }

    public void flipSwitchForUnderlying(final long underlyingSid, final BooleanType isOn, final StrategyExitMode exitMode, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = m_underlyingSwitches.get(underlyingSid);
        if (strategySwitch == null) {
        	return;
            //throw new Exception("Cannot find switch for underlying with sid " + underlyingSid);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on underlying {}", box(underlyingSid));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off underlying {}", box(underlyingSid));
            flipOffSwitchWithExitMode(strategySwitch, exitMode);
        }
        if (broadcast) {
            m_strategyInfoSender.broadcastSwitch(strategySwitch);
        }
    }
    
    public void flipSwitchForIssuer(final long issuerSid, final BooleanType isOn, final StrategyExitMode exitMode, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = m_issuerSwitches.get(issuerSid);
        if (strategySwitch == null) {
            return;
            //throw new Exception("Cannot find switch for issuer with sid " + issuerSid);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on issuer {}", box(issuerSid));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off issuer {}", box(issuerSid));
            flipOffSwitchWithExitMode(strategySwitch, exitMode);
        }
        if (broadcast) {
            m_strategyInfoSender.broadcastSwitch(strategySwitch);
        }
    }

    public void flipSwitchForSecurity(final long securitySid, final BooleanType isOn, final StrategyExitMode exitMode, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = m_warrantSwitches.get(securitySid);
        if (strategySwitch == null) {
            return;
            //throw new Exception("Cannot find switch for security with sid " + securitySid);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on security {}", box(securitySid));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off security {}", box(securitySid));
            flipOffSwitchWithExitMode(strategySwitch, exitMode);
        }
        if (broadcast) {
            m_strategyInfoSender.broadcastSwitch(strategySwitch);
        }
    }
    
    public void flipDayOnlySwitchForSecurity(final long securitySid, final BooleanType isOn, final StrategyExitMode exitMode, final boolean broadcast) throws Exception {
        final StrategyCircuitSwitch strategySwitch = m_dayOnlyWarrantSwitches.get(securitySid);
        if (strategySwitch == null) {
            return;
            //throw new Exception("Cannot find temporary switch for security with sid " + securitySid);
        }
        if (isOn == BooleanType.TRUE) {
            LOG.info("Flipping on temporary switch for security {}", box(securitySid));
            strategySwitch.switchOn();
        }
        else {
            LOG.info("Flipping off temporary switch for security {}", box(securitySid));
            flipOffSwitchWithExitMode(strategySwitch, exitMode);
        }
        if (broadcast) {
            m_strategyInfoSender.broadcastSwitch(strategySwitch);
        }
    }

    private void flipOffSwitchWithExitMode(final StrategyCircuitSwitch strategySwitch, final StrategyExitMode exitMode) {
        final StrategyExitMode prevMode = m_currentExitMode;
        m_currentExitMode = exitMode;
        strategySwitch.switchOff();
        m_currentExitMode = prevMode;        
    }

    public void pendingSwitchOnStrategy(final Strategy strategy) {
        strategy.pendingSwitchOn();        
    }

    public void cancelSwitchOnStrategy(final Strategy strategy) {
        strategy.cancelSwitchOn();        
    }

    public void proceedSwitchOnStrategy(final Strategy strategy) throws Exception {
        strategy.proceedSwitchOn();        
    }

    public void switchOnStrategy(final Strategy strategy) throws Exception {
        strategy.switchOn();        
    }

    public void switchOffStrategy(final Strategy strategy) throws Exception {
        if (m_currentExitMode == StrategyExitMode.NULL_VAL) {
            strategy.switchOff();
        }
        else {
            strategy.switchOff(m_currentExitMode);
        }
    }
    
    public int sendStrategyTypesSnapshot(final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            m_strategyInfoSender.sendStrategyType(strategyType, sender, request, seqNum++);
        }
        return seqNum;
    }

    public int sendMainSwitchSnapshot(final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        m_strategyInfoSender.sendSwitch(m_mainSwitch, sender, request, seqNum++);
        return seqNum;
    }
    
    public int sendStrategyTypeParamsSnapshot(final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            final GenericStrategyTypeParams params = strategyType.context().getStrategyTypeParams();
            m_strategyInfoSender.sendStrategyParams(params, sender, request, seqNum++);
            m_strategyInfoSender.sendSwitch(m_strategySwitches.get(strategyType.sid()), sender, request, seqNum++);
        }
        return seqNum;
    }
   
    public int sendUndParamsSnapshot(final long undSid, final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            final GenericUndParams params = strategyType.context().getStrategyUndParams(undSid, false);
            if (params != null) {
                m_strategyInfoSender.sendStrategyParams(params, sender, request, seqNum++);
            }            
        }
        m_strategyInfoSender.sendSwitch(m_underlyingSwitches.get(undSid), sender, request, seqNum++);
        return seqNum;
    }   
    
    public int sendAllIssuerUndParamsSnapshot(final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            Collection<GenericIssuerUndParams> allStrategyIssuerUndParams = strategyType.context().getAllStrategyIssuerUndParams();
            for (GenericIssuerUndParams params : allStrategyIssuerUndParams){
            	LOG.info("Sending strategy issuer und param back to sender [issuerSid:{}, undSid:{}, issuerUndSid:{}]", params.issuerSid(), params.undSid(), params.issuerUndSid());
            	m_strategyInfoSender.sendStrategyParams(params, sender, request, seqNum++);
            }
        }
        return seqNum;
    }   

    public int sendIssuerUndParamsSnapshot(final long issuerSid, final long undSid, final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            final GenericIssuerUndParams params = strategyType.context().getStrategyIssuerUndParams(issuerSid, undSid, false);
            if (params != null) {
                m_strategyInfoSender.sendStrategyParams(params, sender, request, seqNum++);
            }
        }
        return seqNum;
    }   

    public int sendIssuerParamsSnapshot(final long issuerSid, final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            final GenericIssuerParams params = strategyType.context().getStrategyIssuerParams(issuerSid, false);
            if (params != null) {
                m_strategyInfoSender.sendStrategyParams(params, sender, request, seqNum++);
            }            
        }
        m_strategyInfoSender.sendSwitch(m_issuerSwitches.get(issuerSid), sender, request, seqNum++);
        return seqNum;
    }

    public int sendWrtParamsSnapshot(final long secSid, final MessageSinkRef sender, final RequestSbeDecoder request, int seqNum) throws Exception {
        for (final StrategyTypeInfo strategyType : m_strategyTypes.entities()) {
            final GenericWrtParams params = strategyType.context().getStrategyWrtParams(secSid, false);
            if (params != null) {
                m_strategyInfoSender.sendStrategyParams(params, sender, request, seqNum++);
            }
            BucketOutputParams bucketUpdateParams = strategyType.context().getBucketUpdateParams(secSid, false);
            if (bucketUpdateParams != null){
            	m_strategyInfoSender.sendStrategyParams(bucketUpdateParams, sender, request, seqNum++);
            }
        }
        final StrategyCircuitSwitch persistSwitch = m_warrantSwitches.get(secSid);
        if (persistSwitch != null) {
            m_strategyInfoSender.sendSwitch(persistSwitch, sender, request, seqNum++);
        }
        final StrategyCircuitSwitch dayOnlySwitch = m_dayOnlyWarrantSwitches.get(secSid);
        if (dayOnlySwitch != null) {
            m_strategyInfoSender.sendSwitch(dayOnlySwitch, sender, request, seqNum++);
        }
        return seqNum;
    }
    
    public StrategyContext getStrategyContext(final long strategyId) {
        final StrategyTypeInfo strategyType = this.m_strategyTypes.get(strategyId);
        if (strategyType == null) {
            return null;
        }
        return strategyType.context();
    }
    
    public long getStrategyId(final String strategyName) {
        return m_strategyIdByName.getLong(strategyName);
    }
    
    public LongEntityManager<StrategyTypeInfo> strategyTypes() {
        return m_strategyTypes;
    }
    
    public LongEntityManager<? extends StrategySecurity> securities() {
        return m_securities;
    }
    
    public LongEntityManager<StrategyIssuer> issuers() {
        return m_issuers;
    }

    public Long2ObjectOpenHashMap<StrategySecurity> underlyings() {
        return m_underlyings;
    }    

    public Long2ObjectOpenHashMap<StrategySecurity> warrants() {
        return m_warrants;
    }    
    
    public ArrayList<StrategySecurity> warrantsForUnderlying(final long undSecSid) {
        return m_warrantsByUnderlying.get(undSecSid);
    }
    
    public ArrayList<StrategySecurity> warrantsForIssuer(final int issuerSid) {
        return m_warrantsByIssuer.get(issuerSid);
    }

    public void initializeWarmups(final SystemClock systemClock, final StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender, final LongEntityManager<StrategyType> strategyTypes, final LongEntityManager<StrategyIssuer> issuers, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final Long2ObjectOpenHashMap<OrderStatusReceivedHandler> osReceivedHandlers) {
    	for (final StrategyWarmupEngine warmupEngine : m_warmupEngines) {
    		warmupEngine.initialize(systemClock, orderService, strategyInfoSender, strategyTypes, issuers, underlyings, warrants, osReceivedHandlers);
    	}
    }

    public void doWarmups() {
    	for (final StrategyWarmupEngine warmupEngine : m_warmupEngines) {
    		warmupEngine.doWarmup();
    	}
    }
    
    public void releaseWarmups() {
    	for (final StrategyWarmupEngine warmupEngine : m_warmupEngines) {
    		warmupEngine.release();
    	}
    }

}
