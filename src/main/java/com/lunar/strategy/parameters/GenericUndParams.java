package com.lunar.strategy.parameters;

import java.util.ArrayList;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

public class GenericUndParams extends ParamsSbeEncodable implements UndInputParams, UndOutputParams {
    static private Validator NULL_VALIDATOR = new UndInputParams.Validator() {
        @Override
        public boolean validateSizeThreshold(UndInputParams params, int sizeThreshold) {
            return true;
        }

        @Override
        public boolean validateVelocityThreshold(UndInputParams params, long velocityThreshold) {
            return true;
        }        
    };
    
    static private PostUpdateHandler NULL_POST_UPDATE_HANDLER = new UndInputParams.PostUpdateHandler() {
        @Override
        public void onUpdatedSizeThreshold(UndInputParams params) {
        }

        @Override
        public void onUpdatedVelocityThreshold(UndInputParams params) {
        }
    };
    
    static private class CompositePostUpdateHandler implements UndInputParams.PostUpdateHandler {
        private ArrayList<PostUpdateHandler> childHandlers = new ArrayList<PostUpdateHandler>();
        
        public void registerHandler(final PostUpdateHandler handler) {
            childHandlers.add(handler);
        }
        
        @Override
        public void onUpdatedVelocityThreshold(UndInputParams params) {
            for (final PostUpdateHandler handler : childHandlers) {
                handler.onUpdatedVelocityThreshold(params);
            }            
        }
        
        @Override
        public void onUpdatedSizeThreshold(UndInputParams params) {
            for (final PostUpdateHandler handler : childHandlers) {
                handler.onUpdatedSizeThreshold(params);
            }            
        }
    };

    private Validator validator = NULL_VALIDATOR;
    private CompositePostUpdateHandler postUpdateHandler = new CompositePostUpdateHandler();

    private long strategyId;
    private long underlyingSid;
    
    private int sizeThreshold;
    private long velocityThreshold;
    private long velocityThreshold2;
    private long velocityThreshold3;

    // Stats
    private int numActiveWarrants;
    private int numTotalWarrants;
    
    public void setValidator(final Validator validator) {
        this.validator = validator;
    }
    public void setPostUpdateHandler(final PostUpdateHandler handler) {
        this.postUpdateHandler.registerHandler(handler);
    }

    public long strategyId() {
        return strategyId;
    }
    public GenericUndParams strategyId(final long strategyId) {
        this.strategyId = strategyId;
        return this;
    }
    
    public long underlyingSid() {
        return underlyingSid;
    }
    public GenericUndParams underlyingSid(final long underlyingSid) {
        this.underlyingSid = underlyingSid;
        return this;
    }    
    
    @Override
    public int numActiveWarrants() {
        return numActiveWarrants;
    }
    @Override
    public GenericUndParams numActiveWarrants(final int numActiveWarrants) {
        this.numActiveWarrants = numActiveWarrants;
        return this;
    }
    @Override
    public void incActiveWarrants() {
        numActiveWarrants++;
    }
    @Override
    public void decActiveWarrants() {
        numActiveWarrants--;
    }    

    @Override
    public int numTotalWarrants() {
        return numTotalWarrants;
    }
    @Override
    public GenericUndParams numTotalWarrants(final int numTotalWarrants) {
        this.numTotalWarrants = numTotalWarrants;
        return this;
    }    
    @Override
    public void incTotalWarrants() {
        numTotalWarrants++;
    }

    @Override
    public int sizeThreshold() {
        return sizeThreshold;
    }
    @Override
    public GenericUndParams sizeThreshold(final int sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
        return this;
    }    
    @Override
    public GenericUndParams userSizeThreshold(final int sizeThreshold) {
        if (validator.validateSizeThreshold(this, sizeThreshold)) {
            sizeThreshold(sizeThreshold);
            postUpdateHandler.onUpdatedSizeThreshold(this);
        }
        return this;
    }
    
    @Override
    public long velocityThreshold() {
        return velocityThreshold;
    }
    @Override
    public GenericUndParams velocityThreshold(final long velocityThreshold) {
        this.velocityThreshold = velocityThreshold;
        this.velocityThreshold2 = this.velocityThreshold() * 2;
        this.velocityThreshold3 = this.velocityThreshold() * 3;
        return this;
    }
    @Override
    public GenericUndParams userVelocityThreshold(final long velocityThreshold) {
        if (validator.validateVelocityThreshold(this, velocityThreshold)) {
            velocityThreshold(velocityThreshold);
            postUpdateHandler.onUpdatedVelocityThreshold(this);
        }
        return this;
    }
    
    public long velocityThreshold2() {
        return velocityThreshold2;
    }

    public long velocityThreshold3() {
        return velocityThreshold3;
    }

    @Override
    public GenericUndParams clone() {
        final GenericUndParams clone = new GenericUndParams();
        copyTo((ParamsSbeEncodable)clone);
        return clone;
    }
    
    @Override
    public void copyTo(final ParamsSbeEncodable other) {
        if (other instanceof GenericUndParams) {
            final GenericUndParams o = (GenericUndParams)other;
            o.strategyId = this.strategyId;
            o.underlyingSid = this.underlyingSid;
            copyTo((UndInputParams)o);
            copyTo((UndOutputParams)o);
        }        
    }
    
    @Override
    public TemplateType templateType() {
        return TemplateType.STRATUNDPARAMUPDATE;
    }
    @Override
    public short blockLength() {
        return StrategyUndParamsSbeEncoder.BLOCK_LENGTH;
    }  

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeUndParamsWithoutHeader(buffer, offset, encoder.strategyUndParamsSbeEncoder(), this);
    }
    
    @Override
    public int expectedEncodedLength() {
        return StrategySender.expectedEncodedLength(this);
    }
    
    @Override
    public int schemaId() {
        return StrategyUndParamsSbeEncoder.SCHEMA_ID;
    }
    
    @Override
    public int schemaVersion() {
        return StrategyUndParamsSbeEncoder.SCHEMA_VERSION;
    }   
}
