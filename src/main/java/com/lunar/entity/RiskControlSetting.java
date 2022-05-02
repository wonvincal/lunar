package com.lunar.entity;

import java.util.Optional;

import com.google.common.math.DoubleMath;
import com.lunar.config.PortfolioAndRiskServiceConfig.RiskControlConfig;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.RiskControlSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.RiskControlSbeDecoder.ControlsDecoder;
import com.lunar.message.sender.RiskControlSender;

import org.agrona.MutableDirectBuffer;

public class RiskControlSetting extends Entity {
	public static final int NUM_CONTROLS = 4;
	private static final int SID_NA = -1;
	private final EntityType entityType;
	private final boolean allEntity;
	private final long specificEntitySid;
	
	private Optional<Long> maxOpenPosition;
	private Optional<Double> maxProfit;
	private Optional<Double> maxLoss;
	private Optional<Double> maxCapLimit;

	public RiskControlSetting clone(EntityType entityType, long specificEntitySid){
		return new RiskControlSetting(SID_NA, entityType, false, specificEntitySid, maxOpenPosition, maxProfit, maxLoss, maxCapLimit);
	}
	
	public static RiskControlSetting of(EntityType entityType, Optional<Long> maxOpenPosition, Optional<Double> maxProfit, Optional<Double> maxLoss, Optional<Double> maxCapLimit){
		return new RiskControlSetting(SID_NA, 
				entityType, 
				true, 
				SID_NA,
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
	}

	public static RiskControlSetting of(EntityType entityType, RiskControlConfig config){
		return new RiskControlSetting(SID_NA, 
				entityType, 
				true, 
				SID_NA,
				config.maxOpenPosition(), 
				config.maxProfit(), 
				config.maxLoss(),
				config.maxCapLimit());
	}
	
	public static RiskControlSetting basedOn(RiskControlSetting setting, long entitySid){
		return new RiskControlSetting(SID_NA, 
				setting.entityType, 
				false, 
				entitySid,
				setting.maxOpenPosition, 
				setting.maxProfit, 
				setting.maxLoss,
				setting.maxCapLimit);
	}

	RiskControlSetting(long sid, 
			EntityType entityType, 
			boolean allEntity,
			long specificEntitySid,
			Optional<Long> maxOpenPosition, 
			Optional<Double> maxProfit, 
			Optional<Double> maxLoss,
			Optional<Double> maxCapLimit){
		super(sid);
		this.entityType = entityType;
		this.allEntity = allEntity;
		this.specificEntitySid = specificEntitySid;
		this.maxLoss = maxLoss;
		this.maxOpenPosition = maxOpenPosition;
		this.maxProfit = maxProfit;
		this.maxCapLimit = maxCapLimit;
		
		if (maxOpenPosition.isPresent() && maxOpenPosition.get().longValue() <= 0){
			throw new IllegalArgumentException("Max open position must be > 0");
		}
		if (maxProfit.isPresent() && DoubleMath.fuzzyCompare(maxProfit.get(), 0.0, 0.000001d) <= 0){
			throw new IllegalArgumentException("Max profit must be > 0");
		}
		if (maxLoss.isPresent() && DoubleMath.fuzzyCompare(maxLoss.get(), 0.0, 0.000001d) >= 0){
			throw new IllegalArgumentException("Max loss must be < 0");
		}		
		if (maxCapLimit.isPresent() && DoubleMath.fuzzyCompare(this.maxCapLimit.get(), 0.0, 0.000001d) <= 0){
			throw new IllegalArgumentException("Max cap limit must be > 0 [value:" + this.maxCapLimit.get() + "]");
		}

	}
	
	public EntityType entityType(){
		return entityType;
	}
	
	public boolean allEntity(){
		return allEntity;
	}
	
	public long specificEntitySid(){
		return specificEntitySid;
	}
	
	public Optional<Long> maxOpenPosition(){
		return maxOpenPosition;
	}
	
	public RiskControlSetting maxOpenPosition(Optional<Long> value){
		this.maxOpenPosition = value;
		return this;
	}

	public Optional<Double> maxProfit(){
		return maxProfit;
	}
	
	public RiskControlSetting maxProfit(Optional<Double> value){
		this.maxProfit = value;
		return this;
	}

	public Optional<Double> maxLoss(){
		return maxLoss;
	}
	
	public RiskControlSetting maxLoss(Optional<Double> value){
		this.maxLoss = value;
		return this;
	}

	public Optional<Double> maxCapLimit(){
		return maxCapLimit;
	}
	
	public RiskControlSetting maxCapLimit(Optional<Double> value){
		this.maxCapLimit = value;
		return this;
	}

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return RiskControlSender.encodeRiskControlOnly(buffer, 
				offset, 
				encoder.riskControlSbeEncoder(), 
				this);
	}
	
	@Override
	public TemplateType templateType(){
		return TemplateType.RISK_CONTROL;
	}

	@Override
	public short blockLength() {
		return RiskControlSbeEncoder.BLOCK_LENGTH;
	}
	
	@Override
	public int expectedEncodedLength() {
		return RiskControlSbeEncoder.BLOCK_LENGTH + 
				RiskControlSbeEncoder.ControlsEncoder.sbeHeaderSize() + 
				RiskControlSbeEncoder.ControlsEncoder.sbeBlockLength() * NUM_CONTROLS;
	}

    @Override
    public int schemaId() {
        return RiskControlSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return RiskControlSbeEncoder.SCHEMA_VERSION;
    }    

	@Override
	public String toString() {
		return "openPositionLimit:" + ((maxOpenPosition.isPresent()) ? Long.toString(maxOpenPosition.get()) : "n/a") +
				", maxProfit:" + ((maxProfit.isPresent()) ? maxProfit.get().toString() : "n/a") + 
				", maxLoss:" + ((maxLoss.isPresent()) ? maxLoss.get().toString() : "n/a") +
				", maxCapLimit:" + ((maxCapLimit.isPresent()) ? maxCapLimit.get().toString() : "n/a");
	}

	public RiskControlSetting merge(ControlsDecoder controls){
		for (ControlsDecoder control : controls){
			switch (control.type()){
			case MAX_LOSS:
				maxLoss(Optional.of(control.value()));
				break;
			case MAX_OPEN_POSITION:
				maxOpenPosition(Optional.of((long)control.value()));
				break;
			case MAX_PROFIT:
				maxProfit(Optional.of(control.value()));
				break;
			case MAX_CAP_LIMIT:
				maxCapLimit(Optional.of(control.value()));
				break;
			default:
				throw new IllegalArgumentException("Unexpected risk control type [riskControlType:" + control.type().name() + "]");
			}
		}
		return this;
	}

}
