package com.lunar.order;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.SpreadTable;
import com.lunar.util.BitUtil;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

/**
 * Maintain own order book.
 * 
 * NOTE: Use this if you really want to maintain a view of your own book.
 * Otherwise, do something simpler to save processing power.
 * 
 * @author Calvin
 *
 */
public class SingleSidedOwnOrderBook implements OrderStateHandler {
	static final Logger LOG = LogManager.getLogger(SingleSidedOwnOrderBook.class);

	private final int capacity;
	private final OrderBookLevel[] levels; // a ring buffer of fixed size
										   // when exceeded, use tree map
	private int topOfBookTickLevel;
	private int bottomOfBookTickLevel;
	
	// AVL (faster lookup, slower insert/delete)
	// vs RB (slower lookup, faster insert/delete)
	// given that the expected number of items in outliers should
	// be very small, i don't think it really matter much.
	// on a related note, this class is not suitable for use case
	// where outstanding orders are scattered everywhere (e.g. retail investors)
	// TODO replace with a sorted array list and benchmark
	private final Int2ObjectRBTreeMap<OrderBookLevel> outliers;
	private final SpreadTable spreadTable;
	private final int indexMask;
	
	public static SingleSidedOwnOrderBook of(int capacity, SpreadTable spreadTable, int expNumOrdersPerLevel, int nullTickLevel, int nullPrice){
		return new SingleSidedOwnOrderBook(spreadTable, capacity, expNumOrdersPerLevel, nullTickLevel, nullPrice);
	}
	
	private SingleSidedOwnOrderBook(SpreadTable spreadTable, int capacity, int expNumOrdersPerLevel, int nullTickLevel, int nullPrice){
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.indexMask = this.capacity - 1;
		this.levels = new OrderBookLevel[this.capacity];
		for (int i = 0; i < this.capacity; i++){
			this.levels[i] = OrderBookLevel.of(expNumOrdersPerLevel, nullTickLevel, nullPrice);
		}
		this.outliers = new Int2ObjectRBTreeMap<OrderBookLevel>();
		this.spreadTable = spreadTable;
		this.topOfBookTickLevel = nullTickLevel;
		this.bottomOfBookTickLevel = nullTickLevel; // think think
	}
	
//	public void sampleFlow(){
//		// create
//		1. receive create from client
//		2. handleOrder
//		3. create order object
//		4. add order in order book of instrument
//		5. add order in single sided order book
//		6. add order in order level
//		7. insert order into hash map
//		8. send to line handler
//		
//		// cancel
//		1. handleOrderCancel
//		2. send to line handler
//		
//		// modify
//		1. handleOrderModify
//		2. send to line handler
//		
//		// created status
//
//		// cancelled status
//		
//		// rejected status
//		
//		
//	}

	/**
	 * 
	 * @param tickLevel
	 * @param order
	 */
	public void add(int tickLevel, Order order){
		int possibleBottomTickLevel = tickLevel + this.capacity - 1;
		
		// move any level outside 
		for (int i = levels[bottomOfBookTickLevel & indexMask].tickLevel();
			 i < possibleBottomTickLevel;
			 i++){
			OrderBookLevel level = this.levels[i & indexMask];
			outliers.put(level.tickLevel(), level.clone());
			level.recycleAs(i, spreadTable.tickToPrice(i));
		}
		
		// merge into element
		OrderBookLevel level = levels[tickLevel & indexMask];
		level.addOrder(order);
		
		// set new top of book
		topOfBookTickLevel = BitUtil.max(topOfBookTickLevel, BitUtil.returnOneIfNonZero(level.numOrders()) * tickLevel);
		bottomOfBookTickLevel = topOfBookTickLevel + this.capacity - 1;
	}
	
	/**
	 * Delete an order
	 * @param tickLevel
	 * @param ordSid
	 */
	@SuppressWarnings("unused")
	private void delete(int tickLevel, int ordSid){
		OrderBookLevel level = levels[tickLevel & indexMask];
		if (level.tickLevel() == tickLevel){
			if (level.removeOrder(ordSid)){
				// set new top of book
				topOfBookTickLevel = BitUtil.max(topOfBookTickLevel, BitUtil.returnOneIfNonZero(level.numOrders()) * tickLevel);
				bottomOfBookTickLevel = topOfBookTickLevel + this.capacity - 1;				
			}
			return;
		}
		level = outliers.get(tickLevel);
		if (level != null){
			level.removeOrder(ordSid);
		}
	}

	@SuppressWarnings("unused")
	private OrderBookLevel getOrderBookLevel(int tickLevel){
		OrderBookLevel level = levels[tickLevel & indexMask];
		if (level.tickLevel() == tickLevel){
			return level;
		}
		return outliers.get(tickLevel);
	}
	
	@Override
	public void onNew(Order order) {
		// noop
//		1. handleOrderStatus
//		2. get order from hashmap
//		3. mark order as NEW
//		4. send status back to sender sink
	}	

	@Override
	public void onUpdated(Order order) {
		
		// update outstanding information
//		getOrderBookLevel(order.limitTickLevel()).modifiedOrder(order, 
//														   origQty,
//														   origOutstanding);
	}

	@Override
	public void onRejected(Order order) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCancelled(Order order) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFailed(Order order) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPendingNew(Order order) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFilled(Order order) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onExpired(Order order) {
		// TODO Auto-generated method stub
		
	}	
	
/*
	@Override
	public void onDeleted(Order order) {
		delete(order.limitTickLevel(), order.sid());
//		1. handleOrderStatus
//		2. get order from hashmap, remove from hashmap
//		3. onCancelled of single sided order book
//	    4. send status back to sender sink
		// find the order in order book level
		// remove it
	}
*/	
}
