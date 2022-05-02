package com.lunar.strategy.parameters;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyParamsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

public class GenericStrategyTypeParams extends ParamsSbeEncodable implements StrategyTypeInputParams {
    static private Validator NULL_VALIDATOR = new StrategyTypeInputParams.Validator() {
        @Override
        public boolean validateStrategyExitMode(StrategyTypeInputParams params, StrategyExitMode exitMode) {
            return true;
        }
    };
    
    static private PostUpdateHandler NULL_POST_UPDATE_HANDLER = new StrategyTypeInputParams.PostUpdateHandler() {
        @Override
        public void onUpdatedStrategyExitMode(StrategyTypeInputParams params) {
        }
    };
    
    private Validator validator = NULL_VALIDATOR;
    private PostUpdateHandler postUpdateHandler = NULL_POST_UPDATE_HANDLER;
    private StrategyExitMode exitMode = StrategyExitMode.STRATEGY_EXIT;

    final private GenericUndParams defaultUndInputParams;
    final private GenericIssuerParams defaultIssuerInputParams;
    final private GenericWrtParams defaultWrtInputParams;
    final private GenericIssuerUndParams defaultIssuerUndInputParams;

    private long strategyId;

    public GenericStrategyTypeParams() {
        this.defaultUndInputParams = new GenericUndParams();
        this.defaultUndInputParams.underlyingSid(-1);
        this.defaultIssuerInputParams = new GenericIssuerParams();
        this.defaultIssuerInputParams.issuerSid(-1);
        this.defaultWrtInputParams = new GenericWrtParams();
        this.defaultWrtInputParams.secSid(-1);
        this.defaultIssuerUndInputParams = new GenericIssuerUndParams();
        this.defaultIssuerUndInputParams.undSid(-1).issuerSid(-1);
    }
    
    public void setValidator(final Validator validator) {
        this.validator = validator;
    }
    public void setPostUpdateHandler(final PostUpdateHandler handler) {
        this.postUpdateHandler = handler;
    }
    
    public long strategyId() {
        return strategyId;
    }
    public GenericStrategyTypeParams strategyId(final long strategyId) {
        this.strategyId = strategyId;
        this.defaultUndInputParams.strategyId(strategyId);
        this.defaultIssuerInputParams.strategyId(strategyId);
        this.defaultWrtInputParams.strategyId(strategyId);
        this.defaultIssuerUndInputParams.strategyId(strategyId);
        return this;
    }
    
    @Override
    public StrategyExitMode exitMode() {
        return exitMode;
    }
    @Override
    public GenericStrategyTypeParams exitMode(final StrategyExitMode exitMode) {
        this.exitMode = exitMode;
        return this;  
    }
    @Override
    public GenericStrategyTypeParams userExitMode(final StrategyExitMode exitMode) {
        if (validator.validateStrategyExitMode(this, exitMode)) {
            this.exitMode(exitMode);
            this.postUpdateHandler.onUpdatedStrategyExitMode(this);
        }
        return this;  
    }
    
    @Override
    public UndInputParams defaultUndInputParams() {
        return defaultUndInputParams;
    }
    
    @Override
    public IssuerInputParams defaultIssuerInputParams() {
        return defaultIssuerInputParams;
    }

    @Override
    public IssuerUndInputParams defaultIssuerUndInputParams() {
        return defaultIssuerUndInputParams;
    }

    @Override
    public WrtInputParams defaultWrtInputParams() {
        return defaultWrtInputParams;
    }

    @Override
    public GenericStrategyTypeParams clone() {
        final GenericStrategyTypeParams clone = new GenericStrategyTypeParams();
        copyTo((ParamsSbeEncodable)clone);
        return clone;
    }

    @Override
    public void copyTo(final ParamsSbeEncodable other) {
        if (other instanceof GenericStrategyTypeParams) {
            final GenericStrategyTypeParams o = (GenericStrategyTypeParams)other;
            copyTo((StrategyTypeInputParams)o);
        }
    }
    
    @Override
    public TemplateType templateType() {
        return TemplateType.STRATPARAMUPDATE;
    }
    @Override
    public short blockLength() {
        return StrategyParamsSbeEncoder.BLOCK_LENGTH;
    }    
    
    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeStrategyTypeParamsWithoutHeader(buffer, offset, encoder.strategyParamsSbeEncoder(), this);
    }
    
    @Override
    public int expectedEncodedLength() {
        return StrategySender.expectedEncodedLength(this);
    }
    
    @Override
    public int schemaId() {
        return StrategyParamsSbeEncoder.SCHEMA_ID;
    }
    
    @Override
    public int schemaVersion() {
        return StrategyParamsSbeEncoder.SCHEMA_VERSION;
    }
    
}
