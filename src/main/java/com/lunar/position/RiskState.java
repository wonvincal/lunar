package com.lunar.position;

import java.lang.reflect.Array;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.math.DoubleMath;
import com.lunar.core.SbeEncodable;
import com.lunar.entity.RiskControlSetting;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.RiskStateSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.RiskStateSender;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public class RiskState implements PositionChangeHandler, SbeEncodable {
	private static final Logger LOG = LogManager.getLogger(RiskState.class);
	private static final int EXPECTED_NUM_SUBSCRIBERS = 4;
	private static final double PNL_EQUALITY_TOLERANCE = 0.1;
	public static final int RESULT_OK = 0;
	public static final int RESULT_EXCEEDED = -1;
	public static final int RESULT_NA = 1;
	private final long entitySid;
	private final EntityType entityType;
	private int maxOpenPositionState;
	private long openPosition;
	private int maxProfitState;
	private double totalPnl;
	private int maxLossState;
	private double maxCapUsed;
	private int maxCapLimitState;
	private boolean changed;
	private RiskControlSetting setting;
	private final ReferenceArrayList<RiskControlStateChangeHandler> handlers;
	
	public static RiskState cloneOf(RiskState value){
		RiskState item = new RiskState(value.entitySid, value.entityType, value.setting);
		item.maxLossState = value.maxLossState;
		item.maxOpenPositionState = value.maxOpenPositionState;
		item.maxProfitState = value.maxProfitState;
		item.maxCapLimitState = value.maxCapLimitState;
		return item;
	}

	public static RiskState of(long entitySid, EntityType entityType, RiskControlSetting setting){
		return new RiskState(entitySid, entityType, setting);
	}

	RiskState(long entitySid, EntityType entityType, RiskControlSetting setting){
		this.entitySid = entitySid;
		this.entityType = entityType;
		this.setting = setting;
		this.maxLossState = RESULT_NA;
		this.maxProfitState = RESULT_NA;
		this.maxOpenPositionState = RESULT_NA;
		this.maxCapLimitState = RESULT_NA;
		RiskControlStateChangeHandler[] item = (RiskControlStateChangeHandler[])(Array.newInstance(RiskControlStateChangeHandler.class, EXPECTED_NUM_SUBSCRIBERS));
		this.handlers = ReferenceArrayList.wrap(item);
		this.handlers.size(0);
	}

	public long entitySid(){
		return entitySid;
	}

	public EntityType entityType(){
		return entityType;
	}

	public RiskControlSetting setting(){
		return setting;
	}
	
	public double totalPnl(){
		return totalPnl;
	}
	
	public double openPosition(){
		return openPosition;
	}
	
	public double maxCapUsed(){
		return maxCapUsed;
	}
	
	public boolean changed(){
		return changed;
	}

	public boolean exceededMaxOpenPosition(){
		return maxOpenPositionState == RESULT_EXCEEDED;
	}
	
	public boolean exceededMaxProfit(){
		return maxProfitState == RESULT_EXCEEDED;
	}

	public boolean exceededMaxLoss(){
		return maxLossState == RESULT_EXCEEDED;
	}

	public boolean exceededMaxCapLimit(){
		return maxCapLimitState == RESULT_EXCEEDED;
	}

	public RiskState setting(RiskControlSetting value){
		this.setting = value;
		return this;
	}
	
	public boolean addHandler(RiskControlStateChangeHandler handler){
		return this.handlers.add(handler);
	}
	
	public boolean removeHandler(RiskControlStateChangeHandler handler){
		return this.handlers.rem(handler);
	}
	
	int checkOpenPosition(long position){
		if (!this.setting.maxOpenPosition().isPresent() || position <= this.setting.maxOpenPosition().get()){
			return RESULT_OK;
		}
		return RESULT_EXCEEDED;
	}

	/**
	 * 
	 * @param position
	 * @return true if maxLoss does not exist or position.totalPnl() >= maxLoss
	 */
	int checkMaxLoss(double totalPnl){
		if (!this.setting.maxLoss().isPresent() || totalPnl >= this.setting.maxLoss().get()){
			return RESULT_OK;
		}
		return RESULT_EXCEEDED;
	}
	
	/**
	 * 
	 * @param position
	 * @return true if maxProfit does not exist or position.totalPnl() <= maxProfit
	 */
	int checkMaxProfit(double totalPnl){
		if (!this.setting.maxProfit().isPresent() || totalPnl <= this.setting.maxProfit().get()){
			return RESULT_OK;
		}
		return RESULT_EXCEEDED;		
	}

	int checkMaxCapUsed(double capUsed){
		if (!this.setting.maxCapLimit().isPresent() || capUsed <= this.setting.maxCapLimit().get()){
			return RESULT_OK;
		}
		return RESULT_EXCEEDED;		
	}
	
	private static boolean stateToExceededFlag(int state){
		return (state == RESULT_EXCEEDED) ? true : false; 
	}

	@Override
	public void handleChange(SecurityPositionDetails current, SecurityPositionDetails previous) {
		LOG.debug("Handle change in SecurityPositionDetails [{},{}, current openPosition:{}, prev openPosition:{}, {}]", this.entityType.name(), this.entitySid, current.openPosition(), previous.openPosition());
		long openPosition = current.openPosition();
		if (openPosition != previous.openPosition()){
			int state = this.checkOpenPosition(openPosition);
 			if ((state != this.maxOpenPositionState && this.maxOpenPositionState != RESULT_NA) || (state == RESULT_EXCEEDED && this.maxOpenPositionState == RESULT_NA)){
				updateOpenPosition(state, openPosition);
				RiskControlStateChangeHandler[] handlerArray = handlers.elements();
				for (int i = 0; i < handlers.size(); i++){
					handlerArray[i].handleOpenPositionStateChange(
							current.entitySid(),
							current.entityType(),
							openPosition,
							previous.openPosition(),
							stateToExceededFlag(this.maxOpenPositionState));
				}
			}
		}
		handlePnlChange(current, previous);
		handleMaxCapUsedChange(current, previous);
	};
	
	void updateOpenPosition(int newState, long newValue){
		this.maxOpenPositionState = newState;
		this.openPosition = newValue;
		this.changed = true;
	}
	
	@Override
	public void handleChange(PositionDetails current, PositionDetails previous) {
		LOG.debug("Handle change in PositionDetails [{},{}, current openPosition:{}, prev openPosition:{}, {}]", this.entityType.name(), this.entitySid, current.openPosition(), previous.openPosition());
		long openPosition = current.openPosition();
		if (openPosition != previous.openPosition()){
			int state = this.checkOpenPosition(openPosition);
 			if ((state != this.maxOpenPositionState && this.maxOpenPositionState != RESULT_NA) || (state == RESULT_EXCEEDED && this.maxOpenPositionState == RESULT_NA)){
				updateOpenPosition(state, openPosition);
				RiskControlStateChangeHandler[] handlerArray = handlers.elements();
				for (int i = 0; i < handlers.size(); i++){
					handlerArray[i].handleOpenPositionStateChange(
							current.entitySid(),
							current.entityType(),
							openPosition,
							previous.openPosition(),
							stateToExceededFlag(this.maxOpenPositionState));
				}
			}
		}
		handlePnlChange(current, previous);
		handleMaxCapUsedChange(current, previous);
	}

	private void handlePnlChange(PositionDetails current, PositionDetails previous) {
		LOG.trace("Risk state handle pnl change for [{},{}]", this.entityType.name(), this.entitySid);
		double currentTotalPnl = current.totalPnl();
		double previousTotalPnl = previous.totalPnl();
		if (!DoubleMath.fuzzyEquals(currentTotalPnl, previousTotalPnl, PNL_EQUALITY_TOLERANCE)){
			int state = this.checkMaxProfit(currentTotalPnl);			
			if ((state != this.maxProfitState && this.maxProfitState != RESULT_NA) || (state == RESULT_EXCEEDED && this.maxProfitState == RESULT_NA)){
				// this.maxProfitState = 0
				updateMaxProfit(state, currentTotalPnl);
				RiskControlStateChangeHandler[] handlerArray = handlers.elements();
				for (int i = 0; i < handlers.size(); i++){
					handlerArray[i].handleMaxProfitStateChange(
							current.entitySid(),
							current.entityType(),
							currentTotalPnl,
							previous.totalPnl(), 
							stateToExceededFlag(this.maxProfitState));
				}
			}
			state = this.checkMaxLoss(currentTotalPnl);
			if ((state != this.maxLossState && this.maxLossState != RESULT_NA) || (state == RESULT_EXCEEDED && this.maxLossState == RESULT_NA)){
				updateMaxLoss(state, currentTotalPnl);
				RiskControlStateChangeHandler[] handlerArray = handlers.elements();
				for (int i = 0; i < handlers.size(); i++){
					handlerArray[i].handleMaxLossStateChange(
							current.entitySid(),
							current.entityType(),
							currentTotalPnl, 
							previous.totalPnl(), 
							stateToExceededFlag(this.maxLossState));
				}
			}
		}
	}
	
	private void handleMaxCapUsedChange(PositionDetails current, PositionDetails previous) {
		LOG.trace("Risk state handle cap used change for [{},{}]", this.entityType.name(), this.entitySid);
		double currentMaxCapUsed = current.maxCapUsed();
		double previousMaxCapUsed = previous.maxCapUsed();
		if (!DoubleMath.fuzzyEquals(currentMaxCapUsed, previousMaxCapUsed, PNL_EQUALITY_TOLERANCE)){
			int state = this.checkMaxCapUsed(currentMaxCapUsed);			
			if ((state != this.maxCapLimitState && this.maxCapLimitState != RESULT_NA) || (state == RESULT_EXCEEDED && this.maxCapLimitState == RESULT_NA)){
				updateMaxCapUsed(state, currentMaxCapUsed);
				RiskControlStateChangeHandler[] handlerArray = handlers.elements();
				for (int i = 0; i < handlers.size(); i++){
					handlerArray[i].handleMaxCapLimitStateChange(
							current.entitySid(),
							current.entityType(),
							currentMaxCapUsed,
							previousMaxCapUsed, 
							stateToExceededFlag(this.maxCapLimitState));
				}
			}
		}
	}
	
	void updateMaxCapUsed(int newMaxCapLimitState, double newMaxCapUsed){
		this.maxCapLimitState = newMaxCapLimitState;
		this.maxCapUsed = newMaxCapUsed;
		this.changed = true;
	}
	
	void updateMaxProfit(int newMaxProfitState, double newTotalPnl){
		this.maxProfitState = newMaxProfitState;
		this.totalPnl = newTotalPnl;
		this.changed = true;
	}
	
	void updateMaxLoss(int newMaxLossState, double newTotalPnl){
		this.maxLossState = newMaxLossState;
		this.totalPnl = newTotalPnl;
		this.changed = true;
	}

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return RiskStateSender.encodeRiskStateOnly(
				buffer, 
				offset, 
				encoder.riskStateSbeEncoder(),
				this);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.RISK_STATE;
	}

	@Override
	public short blockLength() {
		return RiskStateSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
		return RiskStateSbeEncoder.BLOCK_LENGTH + 
				RiskStateSbeEncoder.ControlsEncoder.sbeHeaderSize() + 
				RiskControlSetting.NUM_CONTROLS * RiskStateSbeEncoder.ControlsEncoder.sbeBlockLength();
	}
	
    @Override
    public int schemaId() {
        return RiskStateSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return RiskStateSbeEncoder.SCHEMA_VERSION;
    }
	
	@Override
	public String toString() {
		return "entityType:" + entityType.name() +
				", entitySid:" + entitySid +
				", maxOpenPositionState:" + maxOpenPositionState +
				", openPosition:" + openPosition + 
				", maxProfitState:" + maxProfitState +
				", maxLossState:" + maxLossState +
				", maxCapLimitState:" + maxCapLimitState +
				", totalPnl:" + totalPnl + "\n" + setting.toString();
	}

}
