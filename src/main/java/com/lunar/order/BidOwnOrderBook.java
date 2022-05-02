package com.lunar.order;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.util.BitUtil;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

/**
 * Provide a structure to hold all outstanding orders of a particular side.
 * We are not going to do anything special similar to [{@link MarketOrderBook}
 * for now.  However, if there is a need for large amount of searches of orders at a
 * specific price, we should do similar optimization as in [{@link MarketOrderBook}
 * @author wongca
 *
 */
public class BidOwnOrderBook {
	private static final int EXPECTED_OUTSTANDING_ORDERS_PER_ORDER_BOOK_LEVEL = 4;
	static final Logger LOG = LogManager.getLogger(BidOwnOrderBook.class);

	private final Int2ObjectRBTreeMap<OrderBookLevel> ordersByPrice;
	private int best;
	private final SpreadTable spreadTable;
	private final int nullPrice;
	
	private BidOwnOrderBook(SpreadTable spreadTable, int capacity, int expectedNumOrders, int nullPrice){
		this.spreadTable = spreadTable;
		ordersByPrice = new Int2ObjectRBTreeMap<>();
		this.nullPrice = nullPrice;
		best = this.nullPrice;
	}
	
	public boolean add(int price, Order order){
		OrderBookLevel level = ordersByPrice.get(price);
		if (level == null){
			level = OrderBookLevel.of(EXPECTED_OUTSTANDING_ORDERS_PER_ORDER_BOOK_LEVEL, spreadTable.priceToTick(price), price);
			ordersByPrice.put(price, level);
		}
		// Need to test this out in terms of performance
		if (level.addOrder(order)){
			best = BitUtil.max(price, best);
			return true;
		}
		return false;
	}
	
	public boolean cancel(Order order){
		OrderBookLevel level = ordersByPrice.get(order.limitPrice());
		if (level == null){
			LOG.error("Order {} not found in order book, cannot cancel", order.sid());
			return false;
		}
		if (level.removeOrder(order.sid())){
			if (level.numOrders() == 0){
				ordersByPrice.remove(order.limitPrice());
				if (ordersByPrice.isEmpty()){
					best = this.nullPrice;
				}
				else{
					best = ordersByPrice.lastIntKey();
				}
			}
			return true;
		}
		return false;
	}
}
