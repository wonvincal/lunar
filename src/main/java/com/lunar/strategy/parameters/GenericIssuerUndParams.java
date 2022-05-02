package com.lunar.strategy.parameters;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

public class GenericIssuerUndParams extends ParamsSbeEncodable implements IssuerUndInputParams, IssuerUndOutputParams
{
	private static Validator NULL_VALIDATOR = new IssuerUndInputParams.Validator() {
		
		@Override
		public boolean validateUndTradeVolThreshold(IssuerUndInputParams params, long value) {
			return true;
		}
	}; 
	
	private static PostUpdateHandler NULL_POST_UPDATE_HANDLER = new IssuerUndInputParams.PostUpdateHandler() {
		
		@Override
		public void onUpdatedUndTradeVolThreshold(IssuerUndInputParams params) {
		}
	};
	
    private Validator validator = NULL_VALIDATOR;
    private PostUpdateHandler postUpdateHandler = NULL_POST_UPDATE_HANDLER;    

    private long strategyId;
    private long issuerUndSid;
    private long issuerSid;
    private long undSid;
    private long undTradeVolThreshold;
    private long undTradeVol;
    private long undDeltaShares;
    private long maxUndDeltaShares;
    private long pendingUndDeltaShares;
    
    public void setValidator(final Validator validator) {
        this.validator = validator;
    }
    public void setPostUpdateHandler(final PostUpdateHandler handler) {
        this.postUpdateHandler = handler;
    }

    
    public long strategyId() {
        return strategyId;
    }
    public GenericIssuerUndParams strategyId(final long strategyId) {
        this.strategyId = strategyId;
        return this;
    }
    
    public long issuerSid() {
        return issuerSid;
    }
    public GenericIssuerUndParams issuerSid(final long issuerSid) {
        this.issuerSid = issuerSid;
        return this;
    }    

    public long undSid() {
        return undSid;
    }
    public GenericIssuerUndParams undSid(final long undSid) {
        this.undSid = undSid;
        return this;
    }    
    
    public long issuerUndSid() {
        return issuerUndSid;
    }
    
    public GenericIssuerUndParams issuerUndSid(final long issuerUndSid) {
        this.issuerUndSid = issuerUndSid;
        return this;
    }
    
    @Override
    public long undTradeVolThreshold() {
    	return undTradeVolThreshold;
    }
    
    @Override
    public GenericIssuerUndParams undTradeVolThreshold(long tradeVolThreshold) {
    	this.undTradeVolThreshold = tradeVolThreshold;
    	return this;
    }
	@Override
	public IssuerUndInputParams userUndTradeVolThreshold(long tradeVolThreshold) {
		if (validator.validateUndTradeVolThreshold(this, tradeVolThreshold)){
			undTradeVolThreshold(tradeVolThreshold);
			postUpdateHandler.onUpdatedUndTradeVolThreshold(this);
		}
		return null;
	}

    @Override
    public long undTradeVol() {
        return undTradeVol;
    }
    @Override
    public GenericIssuerUndParams undTradeVol(final long tradeVol) {
        this.undTradeVol = tradeVol;
        return this;
    }
    
    @Override
	public long undDeltaShares() {
		return undDeltaShares;
	}
    @Override
	public IssuerUndOutputParams undDeltaShares(final long undDeltaShares) {
    	this.undDeltaShares = undDeltaShares;
    	return this;
    }
    
    @Override
	public long pendingUndDeltaShares() {
		return pendingUndDeltaShares;
	}
    @Override
	public IssuerUndOutputParams pendingUndDeltaShares(final long pendingUndDeltaShares) {
    	this.pendingUndDeltaShares = pendingUndDeltaShares;
    	return this;
    }
    
	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeIssuerUndParamsWithoutHeader(buffer, offset, encoder.strategyIssuerUndParamsSbeEncoder(), this);
	}
	
	@Override
	public TemplateType templateType() {
		return TemplateType.STRAT_ISSUER_UND_PARAM_UPDATE;
	}
	@Override
	public short blockLength() {
		return StrategyIssuerUndParamsSbeEncoder.BLOCK_LENGTH;
	}
	@Override
	public int expectedEncodedLength() {
		return StrategySender.expectedEncodedLength(this);
	}
	@Override
	public int schemaId() {
		return StrategyIssuerUndParamsSbeEncoder.SCHEMA_ID;
	}
	@Override
	public int schemaVersion() {
		return StrategyIssuerUndParamsSbeEncoder.SCHEMA_VERSION;
	}
	@Override
	public ParamsSbeEncodable clone() {
        final GenericIssuerUndParams clone = new GenericIssuerUndParams();
        copyTo((ParamsSbeEncodable)clone);
        return clone;
	}
	@Override
	public void copyTo(ParamsSbeEncodable other) {
        if (other instanceof GenericIssuerUndParams) {
            final GenericIssuerUndParams o = (GenericIssuerUndParams)other;
            o.strategyId = this.strategyId;
            o.issuerSid = this.issuerSid;
            o.undSid = this.undSid;
            o.issuerUndSid = this.issuerUndSid;
            copyTo((IssuerUndInputParams)o);
            copyTo((IssuerUndOutputParams)o);
        }
	}
	
    static final long LOWER_ORDER_MASK = 0x00000000FFFFFFFFl;
    public static long convertToIssuerUndSid(long issuerSid, long undSid){
    	return (issuerSid << 32) | (undSid & LOWER_ORDER_MASK);
    }
}
