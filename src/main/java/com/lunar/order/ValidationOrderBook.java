package com.lunar.order;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.Side;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

/**
 * This is an order book that is built specifically for validation purpose.
 * It won't maintain references to individual order.  The goal is to do 
 * as minimal as possible while to sufficiently perform validation.
 * 
 * Implementation:
 * 1) Hash map by price
 *    Advantage: easy to implement
 *    Disadvantage: more use of memory, cannot enjoy cache hit as much as tick level since
 *                  the difference between the price of two consecutive tick level may be
 *                  huge 
 * 2) Convert price to tickLevel, access OrderBookLevel by tick level using RingBuffer
 *    Advantage: better use of memory
 *    Disadvantage: more complicated to implement, not sure about the performance
 *    
 * So, do 1) now, do 2) later
 *
 * How to validate market order?
 * 
 * 		// According to HKEX_OCG_and_OG_FIX_and_Binary_Field_Mapping_v1_4.pdf
		// Market Order Type is only applicable to Auction Period
		// 
		// In general market order is an order which you don't know the price
		// For imaginary exposure, we want to be more restrictive.

 * Crossing
 * - Market order means it can be at any price.  Unless we know the market order book, there 
 *   is no way to tell if this market order will cross with any of my orders.  Also,
 *   even if the price of my order is at best, there is no way to tell if the order is the first
 *   one in queue.  Therefore, there is no way to tell if the market order will cross with 
 *   any of my existing order.
 * 
 * Exposure
 * - Market order has no price, pre-trade exposure would need to be estimated.  This class won't
 *   be sufficient.  We need a class with access to market data, or we can make sure of yesterday's
 *   close price (or previous traded price)
 *   
 * Position
 * - Market order has quantity
 * 
 * @author wongca
 *
 */
public class ValidationOrderBook {
	static final Logger LOG = LogManager.getLogger(ValidationOrderBook.class);
	
	static {
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			LOG.info("Order validation has been disabled");
		}
	}
	private final long secSid;
	
	// TODO: Not happy with two lookups per order completion, we should 
	// create a new Tree map that removes an entry when number of orders in 
	// OrderBookLevel is zero!
	private final Int2ObjectRBTreeMap<OrderBookLevel> bidOrderBookLevelByPrice;
	private final Int2ObjectRBTreeMap<OrderBookLevel> askOrderBookLevelByPrice;

	/**
	 * This is a position object that is written by Order Update Receiver
	 * and read by OMES. 
	 */
	private final Position position;
	
	public static ValidationOrderBook of(int expectedOutstandingOrders, long secSid){
		return new ValidationOrderBook(expectedOutstandingOrders, secSid);
	}
	
	ValidationOrderBook(int expectedOutstandingOrders, long secSid){
		this.position = Position.of();
		this.secSid = secSid;
		
		this.bidOrderBookLevelByPrice = new Int2ObjectRBTreeMap<>();
		this.askOrderBookLevelByPrice = new Int2ObjectRBTreeMap<>();
	}
	
	Int2ObjectRBTreeMap<OrderBookLevel> bidOrderBookLevelByPrice(){
		return this.bidOrderBookLevelByPrice;
	}
	
	Int2ObjectRBTreeMap<OrderBookLevel> askOrderBookLevelByPrice(){
		return this.askOrderBookLevelByPrice;
	}
	
	int bestBid(){
		return (!bidOrderBookLevelByPrice.isEmpty()) ? bidOrderBookLevelByPrice.lastIntKey() : ServiceConstant.INVALID_BID;
	}
	
	int minBid(){
		return (!bidOrderBookLevelByPrice.isEmpty()) ? bidOrderBookLevelByPrice.firstIntKey() : ServiceConstant.INVALID_BID;
	}

	int bestAsk(){
		return (!askOrderBookLevelByPrice.isEmpty()) ? askOrderBookLevelByPrice.firstIntKey() : ServiceConstant.INVALID_ASK;
	}
	
	int maxAsk(){
		return (!askOrderBookLevelByPrice.isEmpty()) ? askOrderBookLevelByPrice.lastIntKey() : ServiceConstant.INVALID_ASK;		
	}
	
	public long secSid(){
		return secSid;
	}
	
	public Position position(){
		return this.position;
	}
	
	static class OrderEntry {
		private final Side side;
		private final int price;
		private final int quantity;
		
		OrderEntry(Side side, int price, int quantity){
			this.side = side;
			this.price = price;
			this.quantity = quantity;
		}
		
		public Side side() { return side;}
		public int price() { return price;}
		public int quantity() { return quantity;}
	}
	static class OrderBookLevel {
		private static final int DEFAULT_NUM_ORDER = 0;
		private final int price;
		private Side side;
		private int numOrders;

		@Override
		public String toString() {
			return side.name() + ":" + numOrders + "@" + price;
		}
		
		public static OrderBookLevel of(int price, Side side){
			return new OrderBookLevel(price, side, DEFAULT_NUM_ORDER);
		}
		
		OrderBookLevel(int price, Side side, int numOrders){
			this.price = price;
			this.side = side;
			this.numOrders = numOrders;
		}
		
		public int price(){
			return price;
		}
		
		public int numOrders(){
			return numOrders;
		}

		/**
		 * 
		 * @return Number of orders
		 */
		public int incNumOrders(){
			return ++this.numOrders;
		}

		/**
		 * 
		 * @return Number of orders
		 */
		public int decNumOrders(){
			return --this.numOrders;
		}

		public Side side(){
			return side;
		}
	}

	public OrderRejectType isNewBuyOrderOk(int price, int quantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return OrderRejectType.VALID_AND_NOT_REJECT;
		}

		if (!this.askOrderBookLevelByPrice.isEmpty() && (price >= this.askOrderBookLevelByPrice.firstIntKey())){
		    LOG.error("Detected crossed [secSid:{}, buyPrice:{}, bestOutstandingSell:{}]", secSid, price, this.askOrderBookLevelByPrice.firstIntKey());
			return OrderRejectType.CROSSED;
		}
		return OrderRejectType.VALID_AND_NOT_REJECT;
	}
	
	public OrderRejectType isNewSellOrderOk(int price, int quantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return OrderRejectType.VALID_AND_NOT_REJECT;
		}

		if (!this.bidOrderBookLevelByPrice.isEmpty() && (price <= this.bidOrderBookLevelByPrice.lastIntKey())){
		    LOG.error("Detected crossed [secSid:{}, sellPrice:{}, bestOutstandingBuy:{}]", secSid, price, this.bidOrderBookLevelByPrice.lastIntKey());
		    for (OrderBookLevel orderBook : this.bidOrderBookLevelByPrice.values()){
		    	LOG.debug("price:{}, side:{}", orderBook.price(), orderBook.side());
		    }
			return OrderRejectType.CROSSED;
		}
		if (!position.okToSell(quantity)){
			return OrderRejectType.INSUFFICIENT_LONG_POSITION;
		}
		return OrderRejectType.VALID_AND_NOT_REJECT;
	}

	public void newBuyOrder(int price){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}
		OrderBookLevel orderBookLevel = this.bidOrderBookLevelByPrice.get(price);
		if (orderBookLevel == this.bidOrderBookLevelByPrice.defaultReturnValue()){
			orderBookLevel = OrderBookLevel.of(price, Side.BUY);
//            LOG.debug("Added price level to bidOrderBookLevelByPrice [secSid:{}, addedPrice:{}]", secSid, price);
			this.bidOrderBookLevelByPrice.put(price, orderBookLevel);
		}
		orderBookLevel.incNumOrders();
	}
	
	public void newSellOrder(int price, int quantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

		position.decPosition(quantity);
		
		OrderBookLevel orderBookLevel = this.askOrderBookLevelByPrice.get(price);
		if (orderBookLevel == this.askOrderBookLevelByPrice.defaultReturnValue()){
			orderBookLevel = OrderBookLevel.of(price, Side.SELL);
//            LOG.debug("Added price level to askOrderBookLevelByPrice [secSid:{}, addedPrice:{}]", secSid, price);
			this.askOrderBookLevelByPrice.put(price, orderBookLevel);
		}
		orderBookLevel.incNumOrders();
	}
	
	/**
	 * New Order - Done
	 * Cancel Order - Done
	 * Amend Order - ?
	 * Order Accepted - Noop
	 * Order Cancelled - Done
	 * Order Expired - Done
	 * Order Rejected - Done
	 * Order Amended - ?
	 * Order Filled - Done
	 * Order Cancel Rejected - Noop
	 * Order Amend Rejected - Noop
	 * Trade - Done
	 * Trade Cancel - Done
	 */
	private void buyOrderCompletedExceptFilled(int price){
//        LOG.debug("Buy order completed except filled: [secSid:{}, price:{}, numberOfOrdersAtPrice:{}]", secSid, price, bidOrderBookLevelByPrice.get(price).numOrders());
		if (bidOrderBookLevelByPrice.get(price).decNumOrders() == 0){
			bidOrderBookLevelByPrice.remove(price);
//            LOG.debug("Removed price level from bidOrderBookLevelByPrice [secSid:{}, removePrice:{}]", secSid, price);
		}
	}

	/**
	 * Throws null pointer exception if price cannot be found
	 * @param price
	 * @param leavesQuantity
	 */
	private void sellOrderCompletedExceptFilled(int price, int resetQuantity){
		this.position.incPosition(resetQuantity);
//        LOG.debug("Sell order completed except filled: [secSid:{}, price:{}, resetQuantity:{}, numberOfOrdersAtPrice:{}]", secSid, price, resetQuantity, askOrderBookLevelByPrice.get(price).numOrders());
		if (askOrderBookLevelByPrice.get(price).decNumOrders() == 0){
			askOrderBookLevelByPrice.remove(price);
//			LOG.debug("Removed price level from askOrderBookLevelByPrice [secSid:{}, removePrice:{}]", secSid, price);
		}
	}

	/**
	 * Throws null pointer exception if price cannot be found
	 * @param price
	 * @param leavesQuantity
	 */
	public void buyOrderFilled(int price){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//        LOG.debug("Buy order filled: [secSid:{}, price:{}, numberOfOrdersAtPrice:{} ]", secSid, price, bidOrderBookLevelByPrice.get(price).numOrders());
		if (bidOrderBookLevelByPrice.get(price).decNumOrders() == 0){
			bidOrderBookLevelByPrice.remove(price);
//            LOG.debug("Removed price level from bidOrderBookLevelByPrice [secSid:{}, removePrice:{}]", secSid, price);
		}
	}
	
	/**
	 * Throws null pointer exception if price cannot be found
	 * @param price
	 * @param leavesQuantity
	 */
	public void sellOrderFilled(int price){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//        LOG.debug("Sell order filled: [secSid:{}, price:{}, resetQuantity:{}, numberOfOrdersAtPrice:{}]", secSid, price, askOrderBookLevelByPrice.get(price).numOrders());
		if (askOrderBookLevelByPrice.get(price).decNumOrders() == 0){
			askOrderBookLevelByPrice.remove(price);
//            LOG.debug("Removed price level from askOrderBookLevelByPrice [secSid:{}, removePrice:{}]", secSid, price);
		}			
	}

	public void buyOrderCancelled(int price){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//        LOG.debug("Buy order cancelled at [secSid:{}, price:{}]", secSid, price);
		buyOrderCompletedExceptFilled(price);
	}

	public void sellOrderCancelled(int price, int resetQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//	    LOG.debug("Sell order cancelled at [secSid:{}, price:{}, resetQuantity:{}]", secSid, price, resetQuantity);
		sellOrderCompletedExceptFilled(price, resetQuantity);
	}

	public void buyOrderExpired(int price){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//	    LOG.debug("Buy order expired at [secSid:{}, price:{}]", secSid, price);
		buyOrderCompletedExceptFilled(price);
	}

	public void sellOrderExpired(int price, int resetQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//        LOG.debug("Sell order expired at [secSid:{}, price:{}, resetQuantity:{}]", secSid, price, resetQuantity);
		sellOrderCompletedExceptFilled(price, resetQuantity);
	}

	public void buyOrderRejected(int price){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//        LOG.debug("Buy order rejected at [secSid:{}, price:{}]", secSid, price);
		buyOrderCompletedExceptFilled(price);
	}

	public void sellOrderRejected(int price, int resetQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

//        LOG.debug("Sell order rejected at [secSid:{}, price:{}, resetQuantity:{}]", secSid, price, resetQuantity);
		sellOrderCompletedExceptFilled(price, resetQuantity);
	}

	public void buyTrade(int price, int executedQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

		position.incPosition(executedQuantity);
//		LOG.debug("Buy trade, increased position [secSid:{}, execQuantity:{}, currentPosition:{}]", secSid, executedQuantity, position.volatileLongPosition());
	}
	
	public void sellTrade(int price, int executedQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

		// noop
	}

	public void addExistingPosition(int quantity){
		position.incPosition(quantity);
	}
	
	public void buyTradeCancelled(int price, int executedQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

		position.decPosition(executedQuantity);
//		LOG.debug("Buy trade cancelled, decreased position [secSid:{}, execQuantity:{}, currentPosition:{}]", secSid, executedQuantity, position.volatileLongPosition());
	}

	public void sellTradeCancelled(int price, int executedQuantity){
		if (!ServiceConstant.SHOULD_ORDER_VALIDATION){
			return;
		}

		position.incPosition(executedQuantity);
//		LOG.debug("Sell trade cancelled, increased position [secSid:{}, execQuantity:{}, currentPosition:{}]", secSid, executedQuantity, position.volatileLongPosition());
	}

	public void clear(){
		this.position.clear();
		this.bidOrderBookLevelByPrice.clear();
		this.askOrderBookLevelByPrice.clear();
	}
	
	public boolean isClear(){
		return this.position.isClear() && this.bidOrderBookLevelByPrice.isEmpty() && this.askOrderBookLevelByPrice.isEmpty();
	}
	
	@Override
	public String toString() {
		String message = "position:" + position;
		for (OrderBookLevel bidPrice : this.bidOrderBookLevelByPrice.values()){
			message += ", bid: " + bidPrice.toString();
		}
		for (OrderBookLevel askPrice : this.askOrderBookLevelByPrice.values()){
			message += ", ask: " + askPrice;
		}
		return message;
	}
}
