package com.lunar.position;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.sender.PositionSender;
import static org.apache.logging.log4j.util.Unbox.box;

public class SecurityPositionDetails extends PositionDetails {
	private static final Logger LOG = LogManager.getLogger(SecurityPositionDetails.class);
	private static final long NA_SID = -1; 
	private static final EntityType NA_ENTRY_TYPE = EntityType.NULL_VAL; 
	private static final PutOrCall NA_PUT_OR_CALL = PutOrCall.NULL_VAL;
	private PutOrCall putOrCall;
	private FeeAndCommissionSchedule schedule;
	protected PnlCalculator pnlCalculator;
	
	protected double avgBuyPrice;
	protected double avgSellPrice;

	protected double mtmBuyPrice;
	protected double mtmSellPrice;
	protected boolean hasMtmBuyPrice;
	protected boolean hasMtmSellPrice;
	
	public static SecurityPositionDetails of(){
		return new SecurityPositionDetails(null, NA_SID, NA_ENTRY_TYPE, NA_PUT_OR_CALL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static SecurityPositionDetails of(FeeAndCommissionSchedule schedule, long entitySid, EntityType entityType, PutOrCall putOrCall){
		return new SecurityPositionDetails(schedule, entitySid, entityType, putOrCall, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
	
	public static SecurityPositionDetails cloneOf(SecurityPositionDetails details){
		return new SecurityPositionDetails(details.schedule, 
				details.entitySid(), 
				details.entityType(), 
				details.putOrCall(),
				details.openPosition, 
				details.openCallPosition,
				details.openPutPosition,
				details.buyQty, 
				details.buyNotional, 
				details.sellQty, 
				details.sellNotional, 
				details.osBuyQty, 
				details.osBuyNotional, 
				details.osSellQty, 
				details.osSellNotional,
				details.capUsed,
				details.maxCapUsed,
				details.fees, 
				details.commission, 
				details.totalPnl(), 
				details.avgBuyPrice, 
				details.avgSellPrice, 
				details.mtmBuyPrice, 
				details.mtmSellPrice,
				details.tradeCount);
	}

	public static SecurityPositionDetails of(FeeAndCommissionSchedule schedule,
			long entitySid,
			EntityType entityType,
			PutOrCall putOrCall,
			long openPosition,
			long openCallPosition,
			long openPutPosition,
			long buyQty,
			double buyNotional,
			long sellQty,
			double sellNotional,
			long osBuyQty,
			double osBuyNotional,
			long osSellQty,
			double osSellNotional,
			double capUsed,
			double maxCapUsed,
			double fees,
			double commission,
			double totalPnl,
			double avgBuyPrice,
			double avgSellPrice,
			double mtmBuyPrice,
			double mtmSellPrice,
			int tradeCount){
		return new SecurityPositionDetails(schedule, 
				entitySid, entityType, putOrCall, openPosition, openCallPosition, openPutPosition, 
				buyQty, 
				buyNotional, 
				sellQty, 
				sellNotional,
				osBuyQty, 
				osBuyNotional, 
				osSellQty, 
				osSellNotional,
				capUsed,
				maxCapUsed,
				fees,
				commission,
				totalPnl, 
				avgBuyPrice, avgSellPrice, mtmBuyPrice, mtmSellPrice, tradeCount);
	}

	SecurityPositionDetails(FeeAndCommissionSchedule schedule,
			long entitySid,
			EntityType entityType,
			PutOrCall putOrCall,
			long openPosition,
			long openCallPosition,
			long openPutPosition,
			long buyQty,
			double buyNotional,
			long sellQty,
			double sellNotional,
			long osBuyQty,
			double osBuyNotional,
			long osSellQty,
			double osSellNotional,
			double capUsed,
			double maxCapUsed,
			double fees,
			double commission,
			double totalPnl,
			double avgBuyPrice,
			double avgSellPrice,
			double mtmBuyPrice,
			double mtmSellPrice,
			int tradeCount){
		super(entitySid, entityType, fees, commission, 0, 0, tradeCount, openPosition, openCallPosition, openPutPosition, 
				buyQty, 
				buyNotional, 
				sellQty, 
				sellNotional, 
				osBuyQty, 
				osBuyNotional, 
				osSellQty, 
				osSellNotional, 
				capUsed, 
				maxCapUsed,
				0, 0);
		this.putOrCall = putOrCall;
		this.schedule = schedule;
		this.avgBuyPrice = avgBuyPrice;
		this.avgSellPrice = avgSellPrice;
		this.mtmSellPrice = mtmSellPrice;
		this.mtmBuyPrice = mtmBuyPrice;
		this.tradeCount = tradeCount;
		this.fees = fees;
		this.commission = commission;
		this.pnlCalculator = new PnlCalculator();
	}

	public PutOrCall putOrCall() {
		return putOrCall;
	}
	
	public SecurityPositionDetails putOrCall(PutOrCall putOrCall) {
		this.putOrCall = putOrCall;
		return this;
	}
	
	public SecurityPositionDetails feesAndCommssionSchedule(FeeAndCommissionSchedule schedule){		
		this.schedule = schedule;
		return this;
	}
	
	public FeeAndCommissionSchedule feesAndCommssionSchedule(){		
		return schedule;
	}
	
	public double avgBuyPrice() {
		return avgBuyPrice;
	}

	public SecurityPositionDetails avgBuyPrice(double avgBuyPrice) {
		this.avgBuyPrice = avgBuyPrice;
		return this;
	}

	public double avgSellPrice() {
		return avgSellPrice;
	}

	public SecurityPositionDetails avgSellPrice(double avgSellPrice) {
		this.avgSellPrice = avgSellPrice;
		return this;
	}

	/**
	 * Sum of pnl of each matched pair of buy and sell trades
	 */
	void updateNetRealizedPnl() {
		if (buyQty == 0 && sellQty == 0){
			netRealizedPnl(0);
			fees(0);
			commission(0);
			return;
		}
		long qty = Math.min(buyQty, sellQty);
		double notionalUsingAvgPrice = qty * (avgSellPrice + avgBuyPrice);
		LOG.debug("[realized:{}, qty:{}, avgSellPrice:{}, avgBuyPrice:{}, notionalUsingAvgPrice:{}, fees:{}, commission:{}]",
				box(((qty * (avgSellPrice - avgBuyPrice)) - fees - commission)),
				box(qty), 
				box(avgSellPrice), 
				box(avgBuyPrice), 
				box(notionalUsingAvgPrice), 
				box(fees), 
				box(commission));

		netRealizedPnl((qty * (avgSellPrice - avgBuyPrice)) - fees - commission);
		
		experimentalNetRealizedPnl(pnlCalculator.realizedPnl());
		LOG.debug("[expNetRealizedPnl:{}]", box(this.experimentalNetRealizedPnl));
	}
	
	/**
	 * Pnl of net quantity 
	 */
	void updateUnrealizedPnl(){
		if (buyQty == 0 && sellQty == 0){
			unrealizedPnl(0);
			return;
		}
		
		long netQty = buyQty - sellQty;
		if (netQty > 0){
			LOG.debug("[unrealized:{}, hasMtmBuyPrice:{}, netQty:{}, mtmBuyPrice:{}, avgBuyPrice:{}]",
					box(((hasMtmBuyPrice) ? netQty * (mtmBuyPrice - avgBuyPrice) : 0)),
					box(hasMtmBuyPrice), 
					box(netQty), 
					box(mtmBuyPrice), 
					box(avgBuyPrice));
			unrealizedPnl((hasMtmBuyPrice) ? netQty * (mtmBuyPrice - avgBuyPrice) : 0);
		}
		else{
			LOG.debug("[unrealized:{}, hasMtmSellPrice:{}, netQty:{}, avgSellPrice:{}, mtmSellPrice:{}]",
					box(((hasMtmSellPrice) ? -netQty * (avgSellPrice - mtmSellPrice) : 0)),
					box(hasMtmSellPrice), 
					box(netQty), 
					box(avgSellPrice), 
					box(mtmSellPrice));
			unrealizedPnl((hasMtmSellPrice) ? -netQty * (avgSellPrice - mtmSellPrice) : 0);
		}
		experimentalUnrealizedPnl(pnlCalculator.unrealizedPnl());
		LOG.debug("[expUnrealized:{}]", box(experimentalUnrealizedPnl));
	}

	public double mtmBuyPrice() {
		return mtmBuyPrice;
	}

	public SecurityPositionDetails mtmBuyPrice(double mtmBuyPrice) {
		this.mtmBuyPrice = mtmBuyPrice;
		this.hasMtmBuyPrice = true;
		return this;
	}

	public double mtmSellPrice() {
		return mtmSellPrice;
	}

	public SecurityPositionDetails mtmSellPrice(double mtmSellPrice) {
		this.mtmSellPrice = mtmSellPrice;
		this.hasMtmSellPrice = true;
		return this;
	}

	public SecurityPositionDetails addBuyTrade(int tradeSid, double price, long quantity, double fees, double commission){
		pnlCalculator.addBuyTrade(tradeSid, price, quantity, fees, commission);
		return this;
	}
	
	public SecurityPositionDetails addSellTrade(int tradeSid, double price, long quantity, double fees, double commission){
		pnlCalculator.addSellTrade(tradeSid, price, quantity, fees, commission);
		return this;
	}

	public SecurityPositionDetails removeBuyTrade(int tradeSid){
		pnlCalculator.removeBuyTrade(tradeSid);
		return this;
	}
	
	public SecurityPositionDetails removeSellTrade(int tradeSid){
		pnlCalculator.removeSellTrade(tradeSid);
		return this;
	}

	public SecurityPositionDetails bidAsk(double bid, double ask){
		pnlCalculator.bidAsk(bid, ask);
		return this;
	}
	
	public void clearMtmSellPrice(){
		hasMtmSellPrice = false;
	}

	public void clearMtmBuyPrice(){
		hasMtmBuyPrice = false;
	}

	public boolean hasMtmSellPrice(){
		return hasMtmSellPrice;
	}

	public boolean hasMtmBuyPrice(){
		return hasMtmBuyPrice;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder
		.append("avgBuyPrice:").append(avgBuyPrice).append(", avgSellPrice:").append(avgSellPrice)
		.append(", mtmBuyPrice:").append(mtmBuyPrice).append(", mtmSellPrice:").append(mtmSellPrice)
		.append(", hasMtmBuyPrice:").append(hasMtmBuyPrice).append(", hasMtmSellPrice:").append(hasMtmSellPrice)
		.append(", ").append(super.toString());
		return builder.toString();
	}
	
	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return PositionSender.encodeSecurityPositionOnly(buffer, 
				offset, 
				stringBuffer, 
				encoder.positionSbeEncoder(),
				this);
	}
}
