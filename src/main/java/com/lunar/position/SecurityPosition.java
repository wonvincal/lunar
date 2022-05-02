package com.lunar.position;

import static org.apache.logging.log4j.util.Unbox.box;

import java.lang.reflect.Array;
import java.util.EnumMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
/**
 * Anything that needs to be aggregated at security level
 * @author wongca
 *
 */
public class SecurityPosition {
	private static final int EXPECTED_NUM_SUBSCRIBERS = 4;
	private static final Logger LOG = LogManager.getLogger(SecurityPosition.class);
	private SecurityType securityType;
	private long undSecSid;
	private int issuerSid; 
	@SuppressWarnings("unused")
	private int channelId;
	private long orderChannelSnapshotSeq;
	private long tradeChannelSnapshotSeq;
	private final SecurityPositionDetails current;
	private final ReferenceArrayList<PositionChangeHandler> handlers;
	private final Int2ObjectOpenHashMap<PositionChangeRecord> appliedPositionChangeByOrderSid;
	private final Int2ObjectRBTreeMap<PositionChangeRecord> appliedPositionChangeByTradeSid;
	
	public static SecurityPosition of(long entitySid, SecurityType securityType, PutOrCall putOrCall, long undSecSid, int issuerSid, FeeAndCommissionSchedule schedule){
		return new SecurityPosition(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
	}
	
	public static SecurityPosition of(long entitySid){
		return new SecurityPosition(entitySid, SecurityType.NULL_VAL, PutOrCall.NULL_VAL, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION);
	}

	SecurityPosition(long entitySid, SecurityType securityType, PutOrCall putOrCall, long undSecSid, int issuerSid, FeeAndCommissionSchedule schedule){
		this.current = SecurityPositionDetails.of(schedule, entitySid, EntityType.SECURITY, putOrCall);
		this.securityType = securityType;
		this.undSecSid = undSecSid;
		this.issuerSid = issuerSid;
		this.appliedPositionChangeByOrderSid = new Int2ObjectOpenHashMap<>();
		this.appliedPositionChangeByTradeSid = new Int2ObjectRBTreeMap<>();
		PositionChangeHandler[] item = (PositionChangeHandler[])(Array.newInstance(PositionChangeHandler.class, EXPECTED_NUM_SUBSCRIBERS));
		this.handlers = ReferenceArrayList.wrap(item);
		this.handlers.size(0);
	}
	
	public boolean addHandler(PositionChangeHandler handler){
		if (!handlers.contains(handler)){
			return handlers.add(handler);
		}
		return false;
	}
	
	public boolean removeHandler(PositionChangeHandler handler){
		return this.handlers.rem(handler);
	}

	public SecurityPositionDetails details(){
		return current;
	}

	public SecurityType securityType(){
		return this.securityType;
	}
	
	public long undSecSid(){
		return undSecSid;
	}
	
	public int issuerSid(){
		return issuerSid;
	}
	
	public void handleSecurity(SecuritySbeDecoder security, EnumMap<SecurityType, FeeAndCommissionSchedule> schedules, SecurityPositionChangeTracker changeTracker){
		changeTracker.init(current);		
		this.issuerSid = security.issuerSid();
		this.undSecSid = security.undSid();
		
		if (!this.securityType.equals(security.securityType())){
			this.securityType = security.securityType();
			FeeAndCommissionSchedule newSchedule = schedules.get(security.securityType());
			if (newSchedule != null){
				for (Entry<PositionChangeRecord> entrySet : appliedPositionChangeByTradeSid.int2ObjectEntrySet()){
					PositionChangeRecord appliedChange = entrySet.getValue();
					if (appliedChange.side() == Side.BUY)
					{
						LOG.trace("Remove buy trade [tradeSid:{}, price:{}, quantity:{}", entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());
						changeTracker.removeBuyTrade(entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());	
					}
					else {
						LOG.trace("Remove sell trade [tradeSid:{}, price:{}, quantity:{}", entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());
						changeTracker.removeSellTrade(entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());
					}					
				}
								
				changeTracker.putOrCall(security.putOrCall()).feesAndCommssionSchedule(newSchedule);				
				for (Entry<PositionChangeRecord> entrySet : appliedPositionChangeByTradeSid.int2ObjectEntrySet()){
					PositionChangeRecord appliedChange = entrySet.getValue();
					if (appliedChange.side() == Side.BUY)
					{
						LOG.trace("Add buy trade [tradeSid:{}, price:{}, quantity:{}", entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());
						changeTracker.addBuyTrade(entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());	
					}
					else {
						LOG.trace("Add sell trade [tradeSid:{}, price:{}, quantity:{}", entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());
						changeTracker.addSellTrade(entrySet.getIntKey(), appliedChange.price(), appliedChange.quantity());
					}
				}
			}
			else {
				LOG.warn("There is no fees and commission schedule for this security [secSid:{}, secType:{}]", security.sid(), security.securityType().name());
			}
		}
		updateAndReport(changeTracker);
	}
	
	public void handleInitPosition(PositionSbeDecoder position){
		current.openPosition(position.openPosition());
	}
	
	public void handleOrder(OrderSbeDecoder order, SecurityPositionChangeTracker changeTracker){
		changeTracker.init(current);
		
		channelId = order.channelId();
		
		if (order.channelSnapshotSeq() > orderChannelSnapshotSeq){
			orderChannelSnapshotSeq = order.channelSnapshotSeq();			
			int orderSid = order.orderSid();
			PositionChangeRecord appliedChange = appliedPositionChangeByOrderSid.get(orderSid);
			if (appliedChange == null){
				appliedChange = PositionChangeRecord.of();
				appliedPositionChangeByOrderSid.put(orderSid, appliedChange);
			}
			
			if (order.side() == Side.BUY){
				// Reverse previous change
				changeTracker.removeBuyOrder(appliedChange.price(), appliedChange.quantity());

				switch (order.status()){
				case NEW:
				case PARTIALLY_FILLED:
					appliedChange.set(order.limitPrice() * ServiceConstant.INT_PRICE_SCALING_FACTOR, order.leavesQty(), Side.BUY);
					changeTracker.addBuyOrder(appliedChange.price(), appliedChange.quantity());
					break;
				case FILLED:
				case FAILED:
				case REJECTED:
				case EXPIRED:
				case CANCELLED:
					appliedPositionChangeByOrderSid.remove(orderSid);
					break;
				default:
					/* Ignore all PENDING status */
					LOG.error("Unexpected order status [orderSid:{}, orderStatus:{}]", order.orderSid(), order.status().name());
					break;
				}
			}
			else {
				// Reverse previous change
				changeTracker.removeSellOrder(appliedChange.price(), appliedChange.quantity());

				switch (order.status()){
				case NEW:
				case FILLED:
				case PARTIALLY_FILLED:
					appliedChange.set(order.limitPrice() * ServiceConstant.INT_PRICE_SCALING_FACTOR, order.leavesQty(), Side.SELL);
					changeTracker.addSellOrder(appliedChange.price(), appliedChange.quantity());
					break;
				case FAILED:
				case REJECTED:
				case EXPIRED:
				case CANCELLED:
					break;
				default:
					LOG.error("Unexpected order status [orderSid:{}, orderStatus:{}]", order.orderSid(), order.status().name());
					break;
				}			
			}
			updateAndReport(changeTracker);
		}
		else {
			LOG.error("Received order with channel snapshot seq less than our current channel snapshot seql [current:{}, received:{}]", this.orderChannelSnapshotSeq, order.channelSnapshotSeq());
		}
	}
	
	public void handleTrade(TradeSbeDecoder trade, SecurityPositionChangeTracker changeTracker){
		changeTracker.init(current);
		
		channelId = trade.channelId();
		
		if (trade.channelSnapshotSeq() > tradeChannelSnapshotSeq){
			tradeChannelSnapshotSeq = trade.channelSnapshotSeq();			
		}
		else {
			LOG.error("Received trade with channel snapshot seq less than our current channel snapshot seq [current:{}, received:{}]", this.tradeChannelSnapshotSeq, trade.channelSnapshotSeq());
		}
		
		double price = trade.executionPrice() * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		long quantity = trade.executionQty();		
		PositionChangeRecord change = new PositionChangeRecord(price, quantity, trade.side());
		
		if (trade.tradeStatus() == TradeStatus.NEW) {
			appliedPositionChangeByTradeSid.put(trade.tradeSid(), change);
		}
		else {
			// CANCELLED
			appliedPositionChangeByTradeSid.remove(trade.tradeSid());
		}								

		if (trade.side() == Side.BUY){
			switch (trade.tradeStatus()){
			case NEW:
			    LOG.trace("POSITION add buy trade before [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
			    changeTracker.addBuyTrade(trade.tradeSid(), price, quantity);
			    LOG.trace("POSITION add buy trade after [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
			    break;
			case CANCELLED:
			    LOG.trace("POSITION cancel buy trade before [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
			    changeTracker.removeBuyTrade(trade.tradeSid(), price, quantity);
			    LOG.trace("POSITION cancel buy trade after [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
				break;
			default:
				LOG.error("Unexpected trade status [orderSid:{}, tradeSid:{}, tradeStatus:{}]", trade.orderSid(), trade.tradeSid(), trade.tradeStatus().name());
				break;
			}
		}
		else{
			switch (trade.tradeStatus()){
			case NEW:
			    LOG.trace("POSITION add sell trade before [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
			    changeTracker.addSellTrade(trade.tradeSid(), price, quantity);
			    LOG.trace("POSITION add sell trade after [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
				break;
			case CANCELLED:
			    LOG.trace("POSITION cancel sell trade before [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
			    changeTracker.removeSellTrade(trade.tradeSid(), price, quantity);
			    LOG.trace("POSITION cancel sell trade after [entitySid:{}, entityType:{}, undSecId:{}, tradeCount:{}, price:{}, quantity:{}]",
			            box(this.current.entitySid()),
			            this.current.entityType().name(),
			            box(this.undSecSid),
			            box(this.current.tradeCount()),
			            price,
			            quantity);
				break;
			default:
				LOG.error("Unexpected order status [orderSid:{}, tradeSid:{}, tradeStatus:{}]", trade.orderSid(), trade.tradeSid(), trade.tradeStatus().name());
				break;
			}			
		}
		updateAndReport(changeTracker);
	}
	
	public void handleBoobs(BoobsSbeDecoder boobs, SecurityPositionChangeTracker changeTracker){
		changeTracker.init(current);
		changeTracker.bidAsk(boobs.bid() * ServiceConstant.INT_PRICE_SCALING_FACTOR, boobs.ask() * ServiceConstant.INT_PRICE_SCALING_FACTOR);
		updateAndReport(changeTracker);
	}
	
	private void updateAndReport(SecurityPositionChangeTracker changeTracker){
		if (changeTracker.hasNetRealizedPnlImpact()){
			LOG.trace("Updated net realized pnl");
			current.updateNetRealizedPnl();
		}
		if (changeTracker.hasUnrealizedPnlImpact()){
			LOG.trace("Updated unrealized pnl");
			current.updateUnrealizedPnl();
		}
		if (changeTracker.hasReportableChange()){
			LOG.trace("Has reportable change");
			for (PositionChangeHandler handler : this.handlers){
				handler.handleChange(current, changeTracker.previous());
			}
		}		
	}
	
	public static class PositionChangeRecord {
		private long quantity;
		private double price;
		private Side side;
		
		static PositionChangeRecord of(){
			return new PositionChangeRecord(0, 0, Side.NULL_VAL);
		}
		
		PositionChangeRecord(double price, long quantity, Side side){
			this.price = price;
			this.quantity = quantity;
			this.side = side;
		}
		
		long quantity(){ return quantity;}
		
		double price(){ return price;}
		
		Side side(){ return side;}
		
		PositionChangeRecord set(double value, long quantity, Side side){
			this.price = value;
			this.quantity = quantity;
			this.side = side;
			return this;
		}
	}
}
