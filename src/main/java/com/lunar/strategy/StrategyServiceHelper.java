package com.lunar.strategy;

import static org.apache.logging.log4j.util.Unbox.box;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SbeEncodable;
import com.lunar.entity.Issuer;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.entity.SidManager;
import com.lunar.entity.StrategyType;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

public class StrategyServiceHelper {
    private static final Logger LOG = LogManager.getLogger(StrategyServiceHelper.class);

    public static interface UpdateHandler {
        void onStrategyTypeParamsUpdated(final byte senderId, final StrategyType strategyType, final GenericStrategyTypeParams params);
        void onStrategyUndParamsUpdated(final byte senderId, final StrategyType strategyType, final Security underlying, final GenericUndParams params);
        void onStrategyIssuerParamsUpdated(final byte senderId, final StrategyType strategyType, final Issuer issuer, final GenericIssuerParams params);
        void onStrategyWrtParamsUpdated(final byte senderId, final StrategyType strategyType, final Security security, final GenericWrtParams params);
        void onStrategyIssuerUndParamsUpdated(final byte senderId, final StrategyType strategyType, final Issuer issuer, final Security underlying, final GenericIssuerUndParams params);
        void onStrategySwitchUpdated(final byte senderId, final StrategyType strategyType, final StrategySwitch strategySwitch);
        void onIssuerSwitchUpdated(final byte senderId, final Issuer issuer, final StrategySwitch strategySwitch);
        void onUnderlyingSwitchUpdated(final byte senderId, final Security underlying, final StrategySwitch strategySwitch);
        void onWarrantSwitchUpdated(final byte senderId, final Security warrant, final StrategySwitch strategySwitch);
        void onDayOnlyWarrantSwitchUpdated(final byte senderId, final Security warrant, final StrategySwitch strategySwitch);
    }
    
    private class StrategyTypeWrapper extends StrategyType {
        private final StrategySchema strategySchema;
        private StrategyBooleanSwitch strategySwitch;
        private GenericStrategyTypeParams strategyTypeParams;
        private Long2ObjectLinkedOpenHashMap<GenericUndParams> underlyingParams;
        private Long2ObjectLinkedOpenHashMap<GenericWrtParams> warrantParams;
        private Long2ObjectLinkedOpenHashMap<GenericIssuerParams> issuerParams;
        private Long2ObjectLinkedOpenHashMap<GenericIssuerUndParams> issuerUndParams;
        private final int NUM_UNDERLYINGS = 128;
        
        protected StrategyTypeWrapper(final long sid, final String name) {
            super(sid, name);
            strategySchema = new GenericStrategySchema();
            underlyingParams = new Long2ObjectLinkedOpenHashMap<GenericUndParams>(numSecurities);
            warrantParams = new Long2ObjectLinkedOpenHashMap<GenericWrtParams>(numSecurities);
            issuerParams = new Long2ObjectLinkedOpenHashMap<GenericIssuerParams>(numIssuers);
            issuerUndParams = new Long2ObjectLinkedOpenHashMap<GenericIssuerUndParams>(numIssuers * NUM_UNDERLYINGS);
        }
        
        public StrategySchema strategySchema() {
            return strategySchema;
        }
        
        public StrategyBooleanSwitch strategySwitch() {
            return strategySwitch;
        }        
        public StrategyTypeWrapper strategySwitch(final StrategyBooleanSwitch strategySwitch) {
            this.strategySwitch = strategySwitch;
            return this;
        }
        
        public GenericStrategyTypeParams strategyParams() {
            return strategyTypeParams;
        }
        public StrategyTypeWrapper strategyParams(final GenericStrategyTypeParams strategyParams) {
            this.strategyTypeParams = strategyParams;
            return this;
        }
        
        public Long2ObjectLinkedOpenHashMap<GenericUndParams> underlyingParams() {
            return this.underlyingParams;
        }
        public GenericUndParams underlyingParams(final long underlyingSid) {
            return underlyingParams.get(underlyingSid);
        }
        public StrategyTypeWrapper underlyingParams(final long underlyingSid, final GenericUndParams undParams) {
            underlyingParams.put(underlyingSid, undParams);
            return this;
        }

        public Long2ObjectLinkedOpenHashMap<GenericWrtParams> warrantParams() {
            return this.warrantParams;
        }        
        public GenericWrtParams warrantParams(final long secSid) {
            return warrantParams.get(secSid);
        }
        public StrategyTypeWrapper warrantParams(final long secSid, final GenericWrtParams wrtParams) {
            warrantParams.put(secSid, wrtParams);
            return this;
        }
        
        public Long2ObjectLinkedOpenHashMap<GenericIssuerParams> issuerParams() {
            return this.issuerParams;
        }
        public GenericIssuerParams issuerParams(final long issuerSid) {
            return issuerParams.get(issuerSid);
        }
        public StrategyTypeWrapper issuerParams(final long issuerSid, final GenericIssuerParams issParams) {
            issuerParams.put(issuerSid, issParams);
            return this;
        }
        
        public Long2ObjectLinkedOpenHashMap<GenericIssuerUndParams> issuerUndParams() {
            return this.issuerUndParams;
        }
        public GenericIssuerUndParams issuerUndParams(final long issuerUndSid) {
            return issuerUndParams.get(issuerUndSid);
        }
        public StrategyTypeWrapper issuerUndParams(final long issuerUndSid, final GenericIssuerUndParams issUndParams) {
            issuerUndParams.put(issuerUndSid, issUndParams);
            return this;
        }        
        
    }

    private final UpdateHandler updateHandler;
    
    private final LongEntityManager<Security> securities;
    private final LongEntityManager<Issuer> issuers;

    private final LongEntityManager<StrategyTypeWrapper> strategyTypes;
    private final Long2ObjectLinkedOpenHashMap<StrategyBooleanSwitch> issuerSwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyBooleanSwitch> securitySwitches;
    private final Long2ObjectLinkedOpenHashMap<StrategyBooleanSwitch> dayOnlySecuritySwitches;
    
    final int numUnderlyings;
    final int numWarrants;
    final int numSecurities;
    final int numIssuers;

    public StrategyServiceHelper(final UpdateHandler updateHandler, final LongEntityManager<Security> securities, final LongEntityManager<Issuer> issuers, final int numStrategyTypes, final int numUnderlyings, final int numWarrants, final int numIssuers) {
        this.updateHandler = updateHandler;
        this.numUnderlyings = numUnderlyings;
        this.numWarrants = numWarrants;
        this.numSecurities = numUnderlyings + numWarrants;
        this.numIssuers = numIssuers;
        
        this.securities = securities;
        this.issuers = issuers;
        this.strategyTypes = new LongEntityManager<>(numStrategyTypes);
        this.securitySwitches = new Long2ObjectLinkedOpenHashMap<StrategyBooleanSwitch>(numSecurities);
        this.issuerSwitches = new Long2ObjectLinkedOpenHashMap<StrategyBooleanSwitch>(numIssuers);
        this.dayOnlySecuritySwitches = new Long2ObjectLinkedOpenHashMap<StrategyBooleanSwitch>(numSecurities);
    }
    
    public void load(final StrategyLoader strategyLoader) throws Exception {
        strategyLoader.loadStrategyTypes((strategyType)->{
            this.strategyTypes.add(new StrategyTypeWrapper(strategyType.sid(), strategyType.name()));
        });
        strategyLoader.loadStrategySwitchesForStrategies((sid, onOff)->{
           if (sid != SidManager.INVALID_SID) {
               this.strategyTypes.get(sid).strategySwitch(new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.STRATEGY_ID, sid, onOff));
           }
        });
        strategyLoader.loadStrategySwitchesForActiveIssuers((sid, onOff)->{
            this.issuerSwitches.put(sid, new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.ISSUER_SID, sid, onOff));
        });
        strategyLoader.loadStrategySwitchesForActiveUnderlyings((sid, onOff)->{
            if (sid != SidManager.INVALID_SID && securities.get(sid).isAlgo()) {
                this.securitySwitches.put(sid, new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.UNDERLYING_SECURITY_SID, sid, onOff));
            }
        });
        strategyLoader.loadStrategySwitchesForActiveInstruments((sid, onOff)->{
            if (sid != SidManager.INVALID_SID && securities.get(sid).isAlgo()) {
                this.securitySwitches.put(sid, new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.SECURITY_SID, sid, onOff));
            }
        });
        for (StrategyTypeWrapper strategyType : strategyTypes.entities()) {
            strategyLoader.loadStrategyTypeParams(strategyType.name(), (params)-> {
                params.strategyId(strategyType.sid());
                strategyType.strategyParams(params);
            });
            strategyLoader.loadStrategyUndParams(strategyType.name(), (sid, params)-> {
                if (sid != SidManager.INVALID_SID && securities.get(sid).isAlgo()) {
                    params.strategyId(strategyType.sid());
                    params.underlyingSid(sid);
                    strategyType.underlyingParams(sid, params);
                }
            });
            strategyLoader.loadStrategyIssuerParams(strategyType.name(), (sid, params)-> {
                if (sid != SidManager.INVALID_SID) {
                    params.strategyId(strategyType.sid());
                    params.issuerSid(sid);                      
                    strategyType.issuerParams(sid, params);
                }
            });
            strategyLoader.loadStrategyWrtParams(strategyType.name(), (sid, params)-> {
                if (sid != SidManager.INVALID_SID && securities.get(sid).isAlgo()) {
                    params.strategyId(strategyType.sid());
                    params.secSid(sid);                        
                    strategyType.warrantParams(sid, params);
                }
            });    
            strategyLoader.loadStrategyIssuerUndParams(strategyType.name(), (issuerSid, undSid, params) -> {
                if (undSid != SidManager.INVALID_SID && securities.get(undSid).isAlgo() && issuerSid != SidManager.INVALID_SID){
                    long issuerUndSid = GenericIssuerUndParams.convertToIssuerUndSid(issuerSid, undSid);
                    params.strategyId(strategyType.sid());
                    params.issuerUndSid(issuerUndSid);
                    params.issuerSid(issuerSid);
                    params.undSid(undSid);
                    strategyType.issuerUndParams(issuerUndSid, params);
                }
            });
        }
    }

    public int replyStrategyTypeGetRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final Messenger messenger, int count) {
        for (final StrategyTypeWrapper strategyType : strategyTypes.entities()) {
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, strategyType);
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, strategyType.strategyParams());
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, (SbeEncodable)strategyType.strategyParams().defaultUndInputParams());
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, (SbeEncodable)strategyType.strategyParams().defaultWrtInputParams());
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, (SbeEncodable)strategyType.strategyParams().defaultIssuerInputParams());
            messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, (SbeEncodable)strategyType.strategyParams().defaultIssuerUndInputParams());
            for (final GenericUndParams undParams : strategyType.underlyingParams().values()) {
                messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, undParams);
            }
            for (final GenericWrtParams wrtParams : strategyType.warrantParams().values()) {
                messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, wrtParams);
            }
            for (final GenericIssuerParams issuerParams : strategyType.issuerParams().values()) {
                messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, issuerParams);
            }
            for (final GenericIssuerUndParams issuerUndParams: strategyType.issuerUndParams().values()){
                messenger.responseSender().sendSbeEncodable(sender, request.clientKey(), BooleanType.FALSE, count++, ResultType.OK, issuerUndParams);
            }
            messenger.responseSender().sendSbeEncodable(sender,  request.clientKey(),  BooleanType.FALSE,  count++, ResultType.OK, strategyType.strategySwitch());
        }
        for (final StrategySwitch strategySwitch : securitySwitches.values()) {
            messenger.responseSender().sendSbeEncodable(sender,  request.clientKey(),  BooleanType.FALSE,  count++, ResultType.OK, strategySwitch);
        }
        for (final StrategySwitch strategySwitch : issuerSwitches.values()) {
            messenger.responseSender().sendSbeEncodable(sender,  request.clientKey(),  BooleanType.FALSE,  count++, ResultType.OK, strategySwitch);
        }
        for (final StrategySwitch strategySwitch : dayOnlySecuritySwitches.values()) {
            messenger.responseSender().sendSbeEncodable(sender,  request.clientKey(),  BooleanType.FALSE,  count++, ResultType.OK, strategySwitch);
        }
        return count;
    }

    public void registerHandlers(final Messenger messenger) {
        messenger.receiver().strategyParamsHandlerList().add(strategyParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().add(strategyUndParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().add(strategyWrtParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().add(strategyIssuerParamsHandler);
        messenger.receiver().strategyIssuerUndParamsHandlerList().add(strategyIssuerUndParamsHandler);
        messenger.receiver().strategySwitchHandlerList().add(strategySwitchHandler);
    }
    
    public void unregisterHandlers(final Messenger messenger) {
        messenger.receiver().strategyParamsHandlerList().remove(strategyParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().remove(strategyUndParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().remove(strategyWrtParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().remove(strategyIssuerParamsHandler);
        messenger.receiver().strategyIssuerUndParamsHandlerList().remove(strategyIssuerUndParamsHandler);
        messenger.receiver().strategySwitchHandlerList().remove(strategySwitchHandler);
    }
    
    private Handler<StrategyParamsSbeDecoder> strategyParamsHandler = new Handler<StrategyParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder params) {
            try {
                LOG.debug("Received target strategy params for strategy {}", box(params.strategyId()));
                final StrategyTypeWrapper strategyType = strategyTypes.get(params.strategyId());
                if (strategyType != null) {
                    final GenericStrategyTypeParams strategyParams = strategyType.strategyParams();
                    strategyType.strategySchema().handleStrategyTypeParamsSbe(params, strategyParams);
                    updateHandler.onStrategyTypeParamsUpdated(header.senderSinkId(), strategyType, strategyParams);
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist StrategyParamsSbeDecoder", e);
            }
        }
    };
   
    private Handler<StrategyUndParamsSbeDecoder> strategyUndParamsHandler = new Handler<StrategyUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder params) {
            try {
                LOG.debug("Received target strategy underlying params for strategy {} and underlying {}", box(params.strategyId()), box(params.undSid()));
                final StrategyTypeWrapper strategyType = strategyTypes.get(params.strategyId());
                if (strategyType != null) {
                    final Security security = securities.get(params.undSid());
                    if (security != null) {
                        GenericUndParams undParams = strategyType.underlyingParams(params.undSid());
                        if (undParams == null) {
                            undParams = new GenericUndParams();
                            undParams.strategyId(strategyType.sid());
                            undParams.underlyingSid(params.undSid());
                            strategyType.strategyTypeParams.defaultUndInputParams().copyTo(undParams);
                            strategyType.underlyingParams(params.undSid(), undParams);
                        }
                        strategyType.strategySchema().handleStrategyUndParamsSbe(params, undParams);
                        updateHandler.onStrategyUndParamsUpdated(header.senderSinkId(), strategyType, security, undParams);
                    } 
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist StrategyUndParamsSbeDecoder", e);
            }   
        }        
    };

    private Handler<StrategyWrtParamsSbeDecoder> strategyWrtParamsHandler = new Handler<StrategyWrtParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder params) {
            try {
                LOG.debug("Received target strategy warrant params for strategy {} and warrant {}", box(params.strategyId()), box(params.secSid()));
                final StrategyTypeWrapper strategyType = strategyTypes.get(params.strategyId());
                if (strategyType != null) {
                    final Security security = securities.get(params.secSid());
                    if (security != null) {
                        GenericWrtParams wrtParams = strategyType.warrantParams(params.secSid());
                        if (wrtParams == null) {
                            wrtParams = new GenericWrtParams();
                            wrtParams.strategyId(strategyType.sid());
                            wrtParams.secSid(params.secSid());
                            strategyType.strategyTypeParams.defaultWrtInputParams().copyTo(wrtParams);
                            strategyType.warrantParams(params.secSid(), wrtParams);
                        }
                        strategyType.strategySchema().handleStrategyWrtParamsSbe(params, wrtParams);
                        updateHandler.onStrategyWrtParamsUpdated(header.senderSinkId(), strategyType, security, wrtParams);
                    }                
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist StrategyWrtParamsSbeDecoder", e);
            }   
        }        
    };
    
    private Handler<StrategyIssuerParamsSbeDecoder> strategyIssuerParamsHandler = new Handler<StrategyIssuerParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder params) {
            try {
                LOG.debug("Received target strategy issuer params for strategy {} and issuer {}", box(params.strategyId()), box(params.issuerSid()));
                final StrategyTypeWrapper strategyType = strategyTypes.get(params.strategyId());
                if (strategyType != null) {
                    final Issuer issuer = issuers.get(params.issuerSid());
                    if (issuer != null) {
                        GenericIssuerParams issuerParams = strategyType.issuerParams(params.issuerSid());
                        if (issuerParams == null) {
                            issuerParams = new GenericIssuerParams();
                            issuerParams.strategyId(strategyType.sid());
                            issuerParams.issuerSid(params.issuerSid());
                            strategyType.strategyTypeParams.defaultIssuerInputParams().copyTo(issuerParams);
                            strategyType.issuerParams(params.issuerSid(), issuerParams);
                        }
                        strategyType.strategySchema().handleStrategyIssuerParamsSbe(params, issuerParams);
                        updateHandler.onStrategyIssuerParamsUpdated(header.senderSinkId(), strategyType, issuer, issuerParams);
                    }                
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist StrategyIssuerParamsSbeDecoder", e);
            }   
        }
        
    };
    
    private Handler<StrategyIssuerUndParamsSbeDecoder> strategyIssuerUndParamsHandler = new Handler<StrategyIssuerUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder params) {
            try {
                LOG.debug("Received target strategy issuer und params for strategy {}, issuer {}, und {}", box(params.strategyId()), box(params.issuerSid()), box(params.undSid()));
                final StrategyTypeWrapper strategyType = strategyTypes.get(params.strategyId());
                if (strategyType != null) {
                    final Issuer issuer = issuers.get(params.issuerSid());
                    final Security security = securities.get(params.undSid());
                    if (issuer != null && security != null) {
                        long issuerUndSid = GenericIssuerUndParams.convertToIssuerUndSid(params.issuerSid(), params.undSid());
                        GenericIssuerUndParams issuerUndParams = strategyType.issuerUndParams(issuerUndSid);
                        if (issuerUndParams == null) {
                            issuerUndParams = new GenericIssuerUndParams();
                            issuerUndParams.strategyId(strategyType.sid());
                            issuerUndParams.issuerUndSid(issuerUndSid);
                            issuerUndParams.issuerSid(params.issuerSid());
                            issuerUndParams.undSid(params.undSid());
                            strategyType.strategyTypeParams.defaultIssuerUndInputParams().copyTo(issuerUndParams);
                            strategyType.issuerUndParams(issuerUndSid, issuerUndParams);
                        }
                        strategyType.strategySchema().handleStrategyIssuerUndParamsSbe(params, issuerUndParams);
                        updateHandler.onStrategyIssuerUndParamsUpdated(header.senderSinkId(), strategyType, issuer, security, issuerUndParams);
                    }                
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist StrategyIssuerParamsSbeDecoder", e);
            }   
        }
        
    };
    
    private Handler<StrategySwitchSbeDecoder> strategySwitchHandler = new Handler<StrategySwitchSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder params) {
            try {
                LOG.debug("Received target strategy switch params for source {} sid {} on/off {}", params.switchSource(), box(params.sourceSid()), params.onOff());
                if (params.switchType() == StrategySwitchType.STRATEGY_PERSIST) {
                    if (params.switchSource() == StrategyParamSource.STRATEGY_ID) {
                        final StrategyTypeWrapper strategyType = strategyTypes.get(params.sourceSid());
                        strategyType.strategySwitch().onOff(params.onOff());
                        updateHandler.onStrategySwitchUpdated(header.senderSinkId(), strategyType, strategyType.strategySwitch());
                    }
                    else if (params.switchSource() == StrategyParamSource.UNDERLYING_SECURITY_SID) {
                        StrategyBooleanSwitch strategySwitch = securitySwitches.get(params.sourceSid());
                        if (strategySwitch == null) {
                            strategySwitch = new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.UNDERLYING_SECURITY_SID, params.sourceSid(), params.onOff());
                            securitySwitches.put(params.sourceSid(), strategySwitch);
                        }
                        else {
                            strategySwitch.onOff(params.onOff());
                        }
                        updateHandler.onUnderlyingSwitchUpdated(header.senderSinkId(), securities.get(params.sourceSid()), strategySwitch);
                    }
                    else if (params.switchSource() == StrategyParamSource.SECURITY_SID) {
                        StrategyBooleanSwitch strategySwitch = securitySwitches.get(params.sourceSid());
                        if (strategySwitch == null) {
                            strategySwitch = new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.SECURITY_SID, params.sourceSid(), params.onOff());
                            securitySwitches.put(params.sourceSid(), strategySwitch);
                        }
                        else {
                            strategySwitch.onOff(params.onOff());
                        }
                        updateHandler.onWarrantSwitchUpdated(header.senderSinkId(), securities.get(params.sourceSid()), strategySwitch);
                    }
                    else if (params.switchSource() == StrategyParamSource.ISSUER_SID) {
                        StrategyBooleanSwitch strategySwitch = issuerSwitches.get(params.sourceSid());
                        if (strategySwitch == null) {
                            strategySwitch = new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.ISSUER_SID, params.sourceSid(), params.onOff());
                            issuerSwitches.put(params.sourceSid(), strategySwitch);
                        }
                        else {
                            strategySwitch.onOff(params.onOff());
                        }
                        updateHandler.onIssuerSwitchUpdated(header.senderSinkId(), issuers.get(params.sourceSid()), strategySwitch);
                    }
                }
                else {
                    if (params.switchSource() == StrategyParamSource.SECURITY_SID) {
                        StrategyBooleanSwitch strategySwitch = dayOnlySecuritySwitches.get(params.sourceSid());
                        if (strategySwitch == null) {
                            strategySwitch = new StrategyBooleanSwitch(StrategySwitchType.STRATEGY_DAY_ONLY, StrategyParamSource.SECURITY_SID, params.sourceSid(), params.onOff());
                            dayOnlySecuritySwitches.put(params.sourceSid(), strategySwitch);
                        }
                        else {
                            strategySwitch.onOff(params.onOff());
                        }
                        updateHandler.onDayOnlyWarrantSwitchUpdated(header.senderSinkId(), securities.get(params.sourceSid()), strategySwitch);
                    }
                }
            } 
            catch (final Exception e) {
                LOG.error("Failed to persist StrategySwitchSbeDecoder", e);
            }
        }
    };

}
