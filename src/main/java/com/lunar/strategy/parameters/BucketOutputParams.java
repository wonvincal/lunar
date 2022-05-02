package com.lunar.strategy.parameters;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;
import com.lunar.util.LongInterval;

public class BucketOutputParams extends ParamsSbeEncodable {
	public static final int NUM_PARAMS = 6;
    private long strategyId;
    private long secSid;
    private long activeBucketData;
    private long activeBucketBegin;
    private long activeBucketEndExcl;
    private long nextBucketData;
    private long nextBucketBegin;
    private long nextBucketEndExcl;

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return StrategySender.encodeBucketOutputParamsWithoutHeader(buffer, offset, encoder.strategyWrtParamsSbeEncoder(), this);
	}

    public long strategyId() {
        return strategyId;
    }

    public void strategyId(long strategyId) {
        this.strategyId = strategyId;
    }

    public long secSid() {
        return secSid;
    }

    public void secSid(long secSid) {
        this.secSid = secSid;
    }
    
    public long activeBucketData() {
        return activeBucketData;
    }

    public long activeBucketBegin() {
        return activeBucketBegin;
    }

    public long activeBucketEndExcl() {
        return activeBucketEndExcl;
    }
    
    public long nextBucketData() {
        return nextBucketData;
    }

    public long nextBucketBegin() {
        return nextBucketBegin;
    }

    public long nextBucketEndExcl() {
        return nextBucketEndExcl;
    }
    
    public void activeBucketInfo(LongInterval bucketInfo){
    	this.activeBucketBegin = bucketInfo.begin();
    	this.activeBucketEndExcl = bucketInfo.endExclusive();
    	this.activeBucketData = bucketInfo.data();
    }

    public void nextBucketInfo(LongInterval bucketInfo){
    	this.nextBucketBegin = bucketInfo.begin();
    	this.nextBucketEndExcl = bucketInfo.endExclusive();
    	this.nextBucketData = bucketInfo.data();
    }

	@Override
	public TemplateType templateType() {
        return TemplateType.STRATWRTPARAMUPDATE;
	}

	@Override
	public short blockLength() {
        return StrategyWrtParamsSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
        return StrategySender.expectedEncodedLength(this);
	}

	@Override
	public int schemaId() {
        return StrategyWrtParamsSbeEncoder.SCHEMA_ID;
	}

	@Override
	public int schemaVersion() {
        return StrategyWrtParamsSbeEncoder.SCHEMA_VERSION;
	}

	@Override
	public ParamsSbeEncodable clone() {
        final BucketOutputParams clone = new BucketOutputParams();
        copyTo((ParamsSbeEncodable)clone);
        return clone;
	}

	@Override
	public void copyTo(ParamsSbeEncodable other) {
        if (other instanceof BucketOutputParams) {
            final BucketOutputParams o = (BucketOutputParams)other;
            o.strategyId = this.strategyId;
            o.secSid = this.secSid;
            o.activeBucketBegin = this.activeBucketBegin;
            o.activeBucketEndExcl = this.activeBucketEndExcl;
            o.activeBucketData = this.activeBucketData;
            o.nextBucketBegin = this.nextBucketBegin;
            o.nextBucketEndExcl = this.nextBucketEndExcl;
            o.nextBucketData = this.nextBucketData;
        }		
	}

	public void reset() {
    	this.activeBucketBegin = LongInterval.NULL_INTERVAL_BEGIN_VALUE;
    	this.activeBucketEndExcl = LongInterval.NULL_INTERVAL_END_VALUE;
    	this.activeBucketData = LongInterval.NULL_DATA_VALUE;
    	this.nextBucketBegin = LongInterval.NULL_INTERVAL_BEGIN_VALUE;
    	this.nextBucketEndExcl = LongInterval.NULL_INTERVAL_END_VALUE;
    	this.nextBucketData = LongInterval.NULL_DATA_VALUE;	} 

}
