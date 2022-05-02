package com.lunar.position;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.EntityType;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public class AggregatedSecurityPosition implements PositionChangeHandler {
	private static final Logger LOG = LogManager.getLogger(AggregatedSecurityPosition.class);
	private final PositionChangeTracker changeTracker;
	private final ReferenceArrayList<PositionChangeHandler> handlers;

	public static AggregatedSecurityPosition of(long entitySid, EntityType entityType){
		return new AggregatedSecurityPosition(entitySid, entityType);
	}
	
	AggregatedSecurityPosition(long entitySid, EntityType entityType) {
		this.changeTracker = PositionChangeTracker.of(entitySid, entityType);
		this.handlers = new ReferenceArrayList<>();
	}

	public long entitySid(){
		return changeTracker.entitySid();
	}
	
	public EntityType entityType(){
		return changeTracker.entityType();
	}
	
	public PositionDetails details(){
		return changeTracker.current();
	}
	
	public boolean addHandler(PositionChangeHandler handler){
		if (!this.handlers.contains(handler)){
			return this.handlers.add(handler);
		}
		return false;
	}
	
	public boolean removeHandler(PositionChangeHandler handler){
		return this.handlers.rem(handler);
	}
	
	@Override
	public void handleChange(SecurityPositionDetails current, SecurityPositionDetails previous) {
		handleChangeImpl(current, previous);
	};
		
	@Override
	public void handleChange(PositionDetails current, PositionDetails previous) {
		handleChangeImpl(current, previous);
	}

	/**
	 * Get the delta of each field between current and previous and add the delta
	 * @param current E.g. Current security position details
	 * @param previous E.g. Previous security position details
	 */
	private void handleChangeImpl(PositionDetails current, PositionDetails previous) {
		changeTracker.clearChanges();
		changeTracker.addCommission(current.commission() - previous.commission());
		changeTracker.addFees(current.fees() - previous.fees());
		changeTracker.addUnrealizedPnl(current.unrealizedPnl() - previous.unrealizedPnl());
		changeTracker.addNetRealizedPnl(current.netRealizedPnl() - previous.netRealizedPnl());
		changeTracker.addBuyQty(current.buyQty() - previous.buyQty());
		changeTracker.addSellQty(current.sellQty() - previous.sellQty());
		changeTracker.addOpenPosition(current.openPosition() - previous.openPosition());		
		changeTracker.addOpenCallPosition(current.openCallPosition() - previous.openCallPosition());
		changeTracker.addOpenPutPosition(current.openPutPosition() - previous.openPutPosition());
		changeTracker.addBuyNotional(current.buyNotional() - previous.buyNotional());
		changeTracker.addSellNotional(current.sellNotional() - previous.sellNotional());
		changeTracker.addExperimentalUnrealizedPnl(current.experimentalUnrealizedPnl() - previous.experimentalUnrealizedPnl());
		changeTracker.addExperimentalNetRealizedPnl(current.experimentalNetRealizedPnl() - previous.experimentalNetRealizedPnl());
		changeTracker.addCapUsed(current.capUsed() - previous.capUsed());
		
		int origCurrentTradeCount = current.tradeCount();
		int origPrevTradeCount = previous.tradeCount();
		changeTracker.addTradeCount(current.tradeCount() - previous.tradeCount());
		if (this.details().entityType() == EntityType.UNDERLYING && this.details().tradeCount() == 0 && !(origCurrentTradeCount == 0 && origPrevTradeCount == 0)){
			LOG.error("POSITION - trade count before [entitySid:{}, entityType:{}, tradeCount:{}, triggeredBy entity: {}, triggeredBy type:{}, current security tradeCount:{}, prev security tradeCount:{}]",
			        box(this.details().entitySid()),
					this.details().entityType().name(),
					box(this.details().tradeCount()),
					box(current.entitySid()),
					current.entityType().name(),
					box(origCurrentTradeCount),
					box(origPrevTradeCount)
					);
		}
		if (changeTracker.hasChange()){
			for (PositionChangeHandler handler : this.handlers){
				handler.handleChange(changeTracker.current(), changeTracker.previous());
			}
		}		
	}	
}
