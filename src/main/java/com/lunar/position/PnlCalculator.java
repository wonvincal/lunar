package com.lunar.position;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.math.DoubleMath;
import com.lunar.message.io.sbe.Side;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class PnlCalculator {
	private static final Logger LOG = LogManager.getLogger(PnlCalculator.class);
	private static final double TOLERANCE = 0.0000001;
	private double mtmBuyPrice;
	private boolean isMtmBuyPriceValid;
	private double mtmSellPrice;
	private boolean isMtmSellPriceValid;

	private double unrealizedPnl;
	private double realizedPnl;

	private final Int2ObjectLinkedOpenHashMap<TradeInfo> realizedBuyTrades;
	private final Int2ObjectLinkedOpenHashMap<TradeInfo> realizedSellTrades;
	
	private final Int2ObjectLinkedOpenHashMap<TradeInfo> unrealizedTrades;
	
	private PartialMatchedTradeInfo partialMatchedTrade;
	
	private final ObjectArrayList<TradeInfo> reversedRealizedTrades = new ObjectArrayList<>();
	
	public PnlCalculator(){
		realizedBuyTrades = new Int2ObjectLinkedOpenHashMap<>();
		realizedSellTrades = new Int2ObjectLinkedOpenHashMap<>();
		unrealizedTrades = new Int2ObjectLinkedOpenHashMap<>();
		isMtmBuyPriceValid = false;
		isMtmSellPriceValid = false;
		mtmBuyPrice = 0;
		mtmSellPrice = 0;
		partialMatchedTrade = PartialMatchedTradeInfo.NULL_INSTANCE;
	}
	
	Int2ObjectLinkedOpenHashMap<TradeInfo> realizedBuyTrades(){
		return realizedBuyTrades;
	}
	
	Int2ObjectLinkedOpenHashMap<TradeInfo> realizedSellTrades(){
		return realizedSellTrades;
	}
	
	Int2ObjectLinkedOpenHashMap<TradeInfo> unrealizedTrades(){
		return unrealizedTrades;
	}
	
	public void bidAsk(double bid, double ask){
		boolean update = false;
		if (!DoubleMath.fuzzyEquals(mtmBuyPrice, bid, PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			if (!DoubleMath.fuzzyEquals(0, bid, TOLERANCE)){
				mtmBuyPrice = bid;
				isMtmBuyPriceValid = true;
			}
			else {
				isMtmBuyPriceValid = false;
			}
			update = true;
		}
		
		if (!DoubleMath.fuzzyEquals(mtmSellPrice, ask, PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			if (!DoubleMath.fuzzyEquals(0, ask, TOLERANCE)){
				mtmSellPrice = ask;
				isMtmSellPriceValid = true;
			}
			else {
				isMtmSellPriceValid = false;
			}
			update = true;
		}
		
		if (update){
			updateUnrealizedPnl();
		}
	}
	
	public void addBuyTrade(int tradeSid, double price, long quantity){
		addBuyTrade(tradeSid, price, quantity, 0, 0);
	}
	
	public void addBuyTrade(int tradeSid, double price, long quantity, double fees, double commission){
		if (realizedBuyTrades.containsKey(tradeSid)){
			LOG.error("Received duplicate buy trade, ignore [tradeSid:{}, price:{}, quantity:{}]", tradeSid, price, quantity);
			return;
		}
		
		TradeInfo trade = TradeInfo.of(tradeSid, price, quantity, Side.BUY, fees, commission);
		applyFeesAndCommission(trade);
		matchWithUnrealizedTrades(trade, trade.quantity());
		updateUnrealizedPnl();
	}
	
	public void addSellTrade(int tradeSid, double price, long quantity){
		addSellTrade(tradeSid, price, quantity, 0, 0);
	}
	
	public void addSellTrade(int tradeSid, double price, long quantity, double fees, double commission){
		if (realizedSellTrades.containsKey(tradeSid)){
			LOG.error("Received duplicate sell trade, ignore [tradeSid:{}, price:{}, quantity:{}]", tradeSid, price, quantity);
			return;
		}
		
		TradeInfo trade = TradeInfo.of(tradeSid, price, quantity, Side.SELL, fees, commission);
		applyFeesAndCommission(trade);
		matchWithUnrealizedTrades(trade, trade.quantity());
		updateUnrealizedPnl();
	}
	
	private void applyFeesAndCommission(TradeInfo trade) {
		realizedPnl -= (trade.fees() + trade.commission());
	}
	
	private void moveToRealizedList(TradeInfo trade){
		if (trade.side() == Side.BUY){
			realizedBuyTrades.put(trade.tradeSid(), trade);
		}
		else if (trade.side() == Side.SELL){
			realizedSellTrades.put(trade.tradeSid(), trade);
		}
		else {
			throw new IllegalArgumentException("Invalid trade side [side:" + trade.side().name() + "]");
		}
	}
	
	private void matchWithUnrealizedTrades(TradeInfo trade, long outstandingQty){
		if (partialMatchedTrade == PartialMatchedTradeInfo.NULL_INSTANCE){
			setPartialMatchedTrade(trade, trade.quantity());
			updateUnrealizedPnl();
			return;
		}
		
		// Move to unrealized 
		if (trade.side().equals(partialMatchedTrade.side())){
			unrealizedTrades.put(trade.tradeSid(), trade);
			updateUnrealizedPnl();
			return;
		}
		
		int pnlMultipler = 1;
		if (trade.side() == Side.SELL){
			pnlMultipler = -1;
		}

		// Match trade with existing partial matched trade and unrealized trades 
		if (outstandingQty < partialMatchedTrade.outstandingQty()){
			// Fully match trade away, while partialMatchedTrade remains the same
			// Add trade to realized list
			realizedPnl += outstandingQty * (partialMatchedTrade.price() - trade.price()) * pnlMultipler;
			long quantity = partialMatchedTrade.quantity();
			partialMatchedTrade.decOutstandingQty(outstandingQty);
			outstandingQty = 0;
			if (LOG.isTraceEnabled()){
				LOG.trace("MatchWithUnrealized#1: Matched with partialMatchedTrade [matching trade:{}, outstanding before:{}, outstanding now:{}, partial before: {}, partial now:{}]",
						trade.toString(),
						outstandingQty, 0, quantity, partialMatchedTrade.quantity());
				LOG.trace("MatchWithUnrealized#1: Changed realized to {}", realizedPnl);
			}
			moveToRealizedList(trade);
			return;
		}
		else {
			// Remaining of partial matched trade will be matched
			// Move partial matched trade to realized list
			long outstandingBefore = outstandingQty; 
			long quantity = partialMatchedTrade.outstandingQty();
			outstandingQty -= partialMatchedTrade.outstandingQty();//partialMatchedOutstandingQty;
			realizedPnl += partialMatchedTrade.outstandingQty() * (partialMatchedTrade.price() - trade.price()) * pnlMultipler;
			moveToRealizedList(partialMatchedTrade.trade());
			clearPartialMatchedTrade();
			
			if (LOG.isTraceEnabled()){
				LOG.trace("MatchWithUnrealized#2: Matched with partialMatchedTrade [matching trade:{}, outstanding before:{}, outstanding now:{}, partial before: {}, partial now:{}",
						trade.toString(),
						outstandingBefore, outstandingQty, quantity, partialMatchedTrade.quantity());
				LOG.trace("MatchWithUnrealized#2: Changed realized to {}", realizedPnl);
			}
			
			ArrayList<TradeInfo> removeUnrealizedTrades = new ArrayList<>();
			if (outstandingQty != 0){				
				for (TradeInfo unrealizedTrade : unrealizedTrades.values()){
					if (unrealizedTrade.side() == trade.side()){
						continue;
					}
					
					removeUnrealizedTrades.add(unrealizedTrade);
					if (unrealizedTrade.quantity() < outstandingQty){
						// Consume whole unrealized trade
						realizedPnl += unrealizedTrade.quantity() * (unrealizedTrade.price() - trade.price()) * pnlMultipler;
						outstandingBefore = outstandingQty;
						outstandingQty -= unrealizedTrade.quantity();
						LOG.info("MatchWithUnrealized#3: Matched with whole unrealized trade [matching trade:{}, unrealized trade: {}, outstanding before:{}, outstanding now:{}, unrealized:{}@{}]",
								trade.toString(),
								unrealizedTrade.toString(),
								outstandingBefore, outstandingQty, unrealizedTrade.quantity(), unrealizedTrade.price());
						LOG.info("MatchWithUnrealized#3: Changed realized to {}", realizedPnl);
					}
					else {
						// Consume partially unrealized trade
						realizedPnl += outstandingQty * (unrealizedTrade.price() - trade.price()) * pnlMultipler;
						setPartialMatchedTrade(unrealizedTrade, unrealizedTrade.quantity() - outstandingQty);
						LOG.info("MatchWithUnrealized#4: Matched with unrealized trade, set remaining to partial [matching trade:{}, unrealized trade: {}, outstanding before: {}, outstanding now: {}, partial matched:{}",
								trade.toString(),
								unrealizedTrade.toString(),
								outstandingQty, 0, partialMatchedTrade.matchedQty());
						LOG.info("MatchWithUnrealized#4: Changed realized to {}", realizedPnl);
						outstandingQty = 0;
						moveToRealizedList(trade);
						break;
					}
				}
			}
			else {
				moveToRealizedList(trade);
			}

			// Move these trades from unrealized to realized list
			for (TradeInfo remove: removeUnrealizedTrades){
				if (remove.tradeSid() != partialMatchedTrade.tradeSid()){
					moveToRealizedList(remove);
				}
				unrealizedTrades.remove(remove.tradeSid());
			}

			if (partialMatchedTrade == PartialMatchedTradeInfo.NULL_INSTANCE){
			    // At this point, partialMatchTrade is NULL.
			    // We need to find the next candidate for partialMatchTrade
			    // This trade has become the partialMatchTrade
			    if (outstandingQty > 0){
			        setPartialMatchedTrade(trade, outstandingQty);
			    }
			    else if (unrealizedTrades.size() > 0){
			        TradeInfo removed = unrealizedTrades.removeFirst();
			        setPartialMatchedTrade(removed, removed.quantity());
			    }
			}
		}
	}
	
	private void removeTrade(int tradeSid, 
			Int2ObjectLinkedOpenHashMap<TradeInfo> realizedTradesSameSide, 
			Int2ObjectLinkedOpenHashMap<TradeInfo> realizedTradsOppSide){
		
		// If unrealized, remove it and return.
		if (unrealizedTrades.remove(tradeSid) != unrealizedTrades.defaultReturnValue()){
			updateUnrealizedPnl();
			return;
		}
		
		// If partial
		if (partialMatchedTrade.tradeSid() == tradeSid){
			reversePartial();
			updateUnrealizedPnl();
			return;
		}
		
		TradeInfo removed = realizedTradesSameSide.remove(tradeSid);
		if (removed == null){
			LOG.error("Cannot remove trade, not found [tradeSid:{}]", tradeSid );
			return;
		}
		
		reverseRealizedTrades(removed, removed.quantity());
		updateUnrealizedPnl();
	}
	
	public void removeBuyTrade(int tradeSid){
		removeTrade(tradeSid, realizedBuyTrades, realizedSellTrades);
	}

	public void removeSellTrade(int tradeSid){
		removeTrade(tradeSid, realizedSellTrades, realizedBuyTrades);
	}
	
	private double calculatePnl(PartialMatchedTradeInfo trade1, PartialMatchedTradeInfo trade2, long quantity){
		if (trade1.side() == trade2.side()){
			throw new IllegalArgumentException();
		}
		if (trade1.side() == Side.BUY){
			return (trade2.price() - trade1.price()) * quantity;
		}
		return (trade1.price() - trade2.price()) * quantity;
	}
	
	private void reversePartial(){
		ObjectArrayList<TradeInfo> reversedTrades = new ObjectArrayList<>();
		
		// If nothing has been matched, remove it
		if (partialMatchedTrade.nothingHasBeenMatched()){
			clearPartialMatchedTrade();
			if (!unrealizedTrades.isEmpty()){
				TradeInfo removed = unrealizedTrades.removeFirst();
				setPartialMatchedTrade(removed, removed.quantity());
			}
			return;
		}
		
		// Match partialTrade with realizedTrades
		Int2ObjectLinkedOpenHashMap<TradeInfo> realizedTrades;
		int pnlMultiplier = 1;
		if (partialMatchedTrade.side() == Side.SELL){
			realizedTrades = realizedBuyTrades;
			pnlMultiplier = -1;
		}
		else{
			realizedTrades = realizedSellTrades;
		}
		
		PartialMatchedTradeInfo newPartial = PartialMatchedTradeInfo.NULL_INSTANCE;
		long remainingReverseQuantity = partialMatchedTrade.matchedQty();
		TradeInfo reverseTrade = partialMatchedTrade.trade();
		for (TradeInfo realizedTrade : realizedTrades.values()){
			reversedTrades.add(realizedTrade);
			
			if (realizedTrade.quantity() < remainingReverseQuantity){
				// Reverse against this whole realized trade 
				realizedPnl -= realizedTrade.quantity() * (realizedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				long remaining = remainingReverseQuantity;
				remainingReverseQuantity -= realizedTrade.quantity();
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#X: Back out realized trade [reverse realized trade: {}, remaining before: {}, remaining now: {}, realized now: {}]",
							realizedTrade.toString(),
							remaining, 
							remainingReverseQuantity,
							0);
					LOG.trace("Reverse matchWithUnrealized#X: Changed realized to {}", realizedPnl);
				}
			}
			else if (realizedTrade.quantity() == remainingReverseQuantity){
				// Reverse against this whole realized trade
				// And all remaining quantity has been reversed
				realizedPnl -= realizedTrade.quantity() * (realizedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#Y: Back out whole realized trade [reverse realized trade: {}, remaining before: {}, remaining now: {}, realized now: {}]",
							realizedTrade.toString(),
							remainingReverseQuantity, 
							0,
							0);
					LOG.trace("Reverse matchWithUnrealized#Y: Changed realized to {}", realizedPnl);
				}
				remainingReverseQuantity = 0;
				break;
			}
			else {
				// We now have a partial matched trade
				// original realizedTrade.quantity(), but we are going to reverse remainingReverseQuantity
				newPartial = PartialMatchedTradeInfo.of(realizedTrade, remainingReverseQuantity);
				realizedPnl -= remainingReverseQuantity * (realizedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#Z: Back out realized trade [reverse realized trade: {}, remaining before: {}, remaining now: {}, realized (new partial) outstanding: {}, matched: {}]",
							realizedTrade.toString(),
							remainingReverseQuantity, 
							0,
							newPartial.outstandingQty(),
							newPartial.matchedQty());
					LOG.trace("Reverse matchWithUnrealized#Z: Changed realized to {}", realizedPnl);
				}
				
				remainingReverseQuantity = 0;
				break;
			}
		}
		
		// Reverse fees and commission
		realizedPnl -= reverseTrade.fees();
		realizedPnl -= reverseTrade.commission();
		
		// If we cannot reverse partialTrade (something is wrong...actually)
		if (remainingReverseQuantity != 0){
			LOG.error("Cannot reverse partialTrade");
			return;
		}
		
		clearPartialMatchedTrade();
		setPartialMatchedTrade(newPartial);
		
		// Remove from realized trades
		for (TradeInfo reversedTrade : reversedTrades){
			realizedTrades.remove(reversedTrade.tradeSid());
			
			// Refeed reversed trade into the system
			if (newPartial.tradeSid() != reversedTrade.tradeSid()){
				matchWithUnrealizedTrades(reversedTrade, reversedTrade.quantity());				
			}
		}
		
		if (partialMatchedTrade == PartialMatchedTradeInfo.NULL_INSTANCE){
			if (!unrealizedTrades.isEmpty()){
				TradeInfo removed = unrealizedTrades.removeFirst();
				setPartialMatchedTrade(removed, removed.quantity());
			}
		}
		
		updateUnrealizedPnl();
	}
	
	/**
	 * Reverse this trade with 'realized portion of partial trade' and 'realizedTrade'
	 * The 'wholly' reversed trades should be placed in unrealizedTrades list
	 * 
	 * @param reverseTrade
	 * @param reverseQuantity
	 * @param reversedTrades
	 * @return
	 */
	private void reverseRealizedTrades(TradeInfo reverseTrade, 
			final long reverseQuantity){
		reversedRealizedTrades.clear();
		
		Int2ObjectLinkedOpenHashMap<TradeInfo> realizedTrades;
		int pnlMultiplier;
		long remainingReverseQuantity = reverseQuantity;
		
		PartialMatchedTradeInfo realizedPartialTrade = PartialMatchedTradeInfo.NULL_INSTANCE;
		if (reverseTrade.side() == Side.SELL){
			pnlMultiplier = -1;
			if (partialMatchedTrade != PartialMatchedTradeInfo.NULL_INSTANCE && partialMatchedTrade.side() == Side.BUY){
				realizedPartialTrade = partialMatchedTrade;
			}			
			realizedTrades = realizedBuyTrades;
		}
		else{
			pnlMultiplier = 1;
			if (partialMatchedTrade != PartialMatchedTradeInfo.NULL_INSTANCE && partialMatchedTrade.side() == Side.SELL){
				realizedPartialTrade = partialMatchedTrade;
			}
			realizedTrades = realizedSellTrades;
		}

		

		// Try to see if we can reverse partial matched trade first
		// It is possible that the input realizedTrades are empty.
		if (realizedPartialTrade != PartialMatchedTradeInfo.NULL_INSTANCE){
			if (partialMatchedTrade.matchedQty() < remainingReverseQuantity){
				// Reverse 
				realizedPnl -= partialMatchedTrade.matchedQty() * (partialMatchedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				long remaining = remainingReverseQuantity;
				remainingReverseQuantity -= partialMatchedTrade.matchedQty();
				long matchedQtyBefore = partialMatchedTrade.matchedQty();
				partialMatchedTrade.incOutstandingQty(partialMatchedTrade.matchedQty());
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#1: Back out all partial matched quantity [partial tradeSid:{}, price: {}, remaining before: {}, remaining now: {}, partial matched before:{}, partial matched now:{}",
							partialMatchedTrade.tradeSid(),
							partialMatchedTrade.price(),
							remaining, 
							remainingReverseQuantity,
							matchedQtyBefore,
							partialMatchedTrade.matchedQty());
					LOG.trace("Reverse matchWithUnrealized#1: Changed realized to {}", realizedPnl);
				}
				unrealizedTrades.put(partialMatchedTrade.tradeSid(), partialMatchedTrade.trade());
				clearPartialMatchedTrade();				
			}
			else if (partialMatchedTrade.matchedQty() == remainingReverseQuantity){
				realizedPnl -= partialMatchedTrade.matchedQty() * (partialMatchedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				partialMatchedTrade.incOutstandingQty(partialMatchedTrade.matchedQty());
				long remaining = remainingReverseQuantity;
				remainingReverseQuantity = 0;
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#2: Back out all partial matched quantity [price: {}, remaining before: {}, remaining now: {}, partial matched:{}",
							partialMatchedTrade.price(), 
							remaining, remainingReverseQuantity, partialMatchedTrade.matchedQty());
					LOG.trace("Reverse matchWithUnrealized#2: Changed realized to {}", realizedPnl);
				}
				unrealizedTrades.put(partialMatchedTrade.tradeSid(), partialMatchedTrade.trade());
				clearPartialMatchedTrade();
				return;
			}
			else {
				// partialMatchedTrade.outstandingQty() > remainingReverseQuantity
				realizedPnl -= remainingReverseQuantity * (partialMatchedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				partialMatchedTrade.incOutstandingQty(remainingReverseQuantity);
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#3: Back out part of partial matched quantity [price:{}, remaining before: {}, remaining now: {}, partial matched:{}",
							partialMatchedTrade.price(),
							remainingReverseQuantity, 0, partialMatchedTrade.matchedQty());
					LOG.trace("Reverse matchWithUnrealized#3: Changed realized to {}", realizedPnl);
				}
				remainingReverseQuantity = 0;
				return;
			}			
		}
		
		PartialMatchedTradeInfo newPartial = PartialMatchedTradeInfo.NULL_INSTANCE;
		for (TradeInfo realizedTrade : realizedTrades.values()){
			reversedRealizedTrades.add(realizedTrade);
			
			if (realizedTrade.quantity() < remainingReverseQuantity){
				// Reverse 
				realizedPnl -= realizedTrade.quantity() * (realizedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				long remaining = remainingReverseQuantity;
				remainingReverseQuantity -= realizedTrade.quantity();
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#4: Back out realized trade [reverse realized trade: {}, remaining before: {}, remaining now: {}, realized now: {}]",
							realizedTrade.toString(),
							remaining, 
							remainingReverseQuantity,
							0);
					LOG.trace("Reverse matchWithUnrealized#4: Changed realized to {}", realizedPnl);
				}
			}
			else if (realizedTrade.quantity() == remainingReverseQuantity){
				// Reverse 
				realizedPnl -= realizedTrade.quantity() * (realizedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#5: Back out whole realized trade [reverse realized trade: {}, remaining before: {}, remaining now: {}, realized now: {}]",
							realizedTrade.toString(),
							remainingReverseQuantity, 
							0,
							0);
					LOG.trace("Reverse matchWithUnrealized#5: Changed realized to {}", realizedPnl);
				}
				remainingReverseQuantity = 0;
				break;
			}
			else {
				// We now have a partial matched trade
				// original realizedTrade.quantity(), but we are going to reverse remainingReverseQuantity
				newPartial = PartialMatchedTradeInfo.of(realizedTrade, remainingReverseQuantity);
				realizedPnl -= remainingReverseQuantity * (realizedTrade.price() - reverseTrade.price()) * pnlMultiplier;
				if (LOG.isTraceEnabled()){
					LOG.trace("Reverse matchWithUnrealized#6: Back out realized trade [reverse realized trade: {}, remaining before: {}, remaining now: {}, realized (new partial) outstanding: {}, matched: {}]",
							realizedTrade.toString(),
							remainingReverseQuantity, 
							0,
							newPartial.outstandingQty(),
							newPartial.matchedQty());
					LOG.trace("Reverse matchWithUnrealized#6: Changed realized to {}", realizedPnl);
				}
				remainingReverseQuantity = 0;
				break;
			}
		}
		
		// Reverse fees and commission
		realizedPnl -= reverseTrade.fees();
		realizedPnl -= reverseTrade.commission();
		
		// Set newPartial
		if (newPartial != PartialMatchedTradeInfo.NULL_INSTANCE){
			// This may conflict with existing partial
			if (partialMatchedTrade != PartialMatchedTradeInfo.NULL_INSTANCE){
				if (partialMatchedTrade.matchedQty() > newPartial.matchedQty()){
					realizedPnl -= calculatePnl(partialMatchedTrade, newPartial, newPartial.matchedQty());
					if (LOG.isTraceEnabled()){
						LOG.trace("Reverse matchWithUnrealized#8: Changed realized to {} [partial trade sid:{}, newPartial sid:{}]", 
								realizedPnl,
								partialMatchedTrade.tradeSid(),
								newPartial.tradeSid());
					}
					partialMatchedTrade.incOutstandingQty(newPartial.matchedQty());
					// newPartial is now unrealized again
				}
				else if (partialMatchedTrade.matchedQty() < newPartial.matchedQty()){
					realizedPnl -= calculatePnl(partialMatchedTrade, newPartial, partialMatchedTrade.matchedQty());
					if (LOG.isTraceEnabled()){
						LOG.trace("Reverse matchWithUnrealized#9: Changed realized to {} [partial trade sid:{}, newPartial sid:{}]", 
								realizedPnl,
								partialMatchedTrade.tradeSid(),
								newPartial.tradeSid());
					}
					// partial is now unrealized
					reversedRealizedTrades.add(partialMatchedTrade.trade());
					newPartial.incOutstandingQty(partialMatchedTrade.matchedQty());
					clearPartialMatchedTrade();
					setPartialMatchedTrade(newPartial);
				}
				else {
					realizedPnl -= calculatePnl(partialMatchedTrade, newPartial, newPartial.matchedQty());
					if (LOG.isTraceEnabled()){
						LOG.trace("Reverse matchWithUnrealized#10: Changed realized to {} [partial trade sid:{}, newPartial sid:{}]", 
								realizedPnl,
								partialMatchedTrade.tradeSid(),
								newPartial.tradeSid());
					}
					// both partials are now unrealized again
					reversedRealizedTrades.add(partialMatchedTrade.trade());
					clearPartialMatchedTrade();
				}
			}
			else {
				setPartialMatchedTrade(newPartial);
			}
		}
		
		// Remove from realized trades
		for (TradeInfo reversedTrade : reversedRealizedTrades){
			realizedTrades.remove(reversedTrade.tradeSid());
			
			// Refeed on reversed trades
			if (partialMatchedTrade.tradeSid() != reversedTrade.tradeSid()){
				matchWithUnrealizedTrades(reversedTrade, reversedTrade.quantity());
			}
		}
	}
	
	private void clearPartialMatchedTrade(){
		partialMatchedTrade = PartialMatchedTradeInfo.NULL_INSTANCE;
	}
	
	private void setPartialMatchedTrade(TradeInfo trade, long outstandingQty){
		partialMatchedTrade = PartialMatchedTradeInfo.of(trade, outstandingQty);
	}

	private void setPartialMatchedTrade(PartialMatchedTradeInfo partial){
		partialMatchedTrade = partial;
	}

	void updateUnrealizedPnl(){
		double pnl = 0;
		if (partialMatchedTrade == PartialMatchedTradeInfo.NULL_INSTANCE){
			unrealizedPnl = 0;
			return;
		}
		
		if (partialMatchedTrade.side() == Side.BUY){
			// Do not update
			if (!isMtmBuyPriceValid){
				return;
			}
			
			LOG.debug("Unrealized Pnl Calculation");
			LOG.debug("==========================");
			LOG.debug("Individual unrealized pnl (partial): tradeSid:{}, side:{}, quantity:{}, outstanding:{}, price:{}, mtm:{}, pnl:{}",
					partialMatchedTrade.tradeSid(),
					partialMatchedTrade.side(),
					partialMatchedTrade.quantity(),
					partialMatchedTrade.outstandingQty(),
					partialMatchedTrade.price(),
					mtmBuyPrice,
					partialMatchedTrade.outstandingQty() * (mtmBuyPrice - partialMatchedTrade.price()));
			pnl += partialMatchedTrade.outstandingQty() * (mtmBuyPrice - partialMatchedTrade.price());
			for (TradeInfo trade : unrealizedTrades.values()){
				LOG.debug("individual unrealized pnl (unrealized): tradeSid:{}, side:{}, quantity:{}, price:{}, mtm:{}, pnl:{}",
						trade.tradeSid(),
						trade.side(),
						trade.quantity(),
						trade.price(),
						this.mtmBuyPrice,
						trade.quantity() * (mtmBuyPrice - trade.price()));
				pnl += trade.quantity() * (mtmBuyPrice - trade.price());
			}
		}
		else {
			// Do not update
			if (!isMtmSellPriceValid){
				return;
			}
	
			LOG.debug("Unrealized Pnl Calculation");
			LOG.debug("==========================");
			LOG.debug("Individual unrealized pnl (partial): side:{}, quantity:{}, outstanding:{}, price:{}, mtm:{}, pnl:{}",
					partialMatchedTrade.side(),
					partialMatchedTrade.quantity(),
					partialMatchedTrade.outstandingQty(),
					partialMatchedTrade.price(),
					mtmSellPrice,
					partialMatchedTrade.outstandingQty() * (mtmSellPrice - partialMatchedTrade.price()));
			pnl += partialMatchedTrade.outstandingQty() * (partialMatchedTrade.price() - mtmSellPrice);
			for (TradeInfo trade : unrealizedTrades.values()){
				LOG.debug("individual unrealized pnl (unrealized): side:{}, quantity:{}, price:{}, mtm:{}, pnl:{}",
						trade.side(),
						trade.quantity(),
						trade.price(),
						this.mtmSellPrice,
						trade.quantity() * (mtmSellPrice - trade.price()));
				pnl += trade.quantity() * (trade.price() - mtmSellPrice);
			}
		}
		unrealizedPnl = pnl;
	}
	
	public double unrealizedPnl(){
		return unrealizedPnl;
	}
	
	public double realizedPnl(){
		return realizedPnl;
	}
	
	double mtmSellPrice(){
		return mtmSellPrice;
	}
	
	double mtmBuyPrice(){
		return mtmBuyPrice;
	}
	
	boolean isMtmBuyPriceValid(){
		return isMtmBuyPriceValid;
	}

	boolean isMtmSellPriceValid(){
		return isMtmSellPriceValid;
	}

	boolean hasPartialMatchedTrade(){
		return partialMatchedTrade != PartialMatchedTradeInfo.NULL_INSTANCE;
	}
	
	TradeInfo partialMatchedTrade(){
		return partialMatchedTrade.trade();
	}
	
	long partialMatchedOutstandingQty(){
		return partialMatchedTrade.outstandingQty();
	}
	
	public void clear(){
		realizedBuyTrades.clear();
		realizedSellTrades.clear();
		unrealizedTrades.clear();
		isMtmBuyPriceValid = false;
		isMtmSellPriceValid = false;
		mtmBuyPrice = 0;
		mtmSellPrice = 0;
		partialMatchedTrade = null;
	}
}
