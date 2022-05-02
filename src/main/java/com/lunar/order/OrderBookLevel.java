package com.lunar.order;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.FastEntrySet;

/**
 * Equivalent to a {@link Tick}.
 * @author Calvin
 *
 */
public class OrderBookLevel {
	static final Logger LOG = LogManager.getLogger(OrderBookLevel.class);

	private int tickLevel;
	private int price;
	private int totalQty;
	private int totalOutstanding;
	private Int2ObjectArrayMap<Order> orders;

	public static OrderBookLevel MIN_ORDER_BOOK_LEVEL = OrderBookLevel.of(0, Integer.MIN_VALUE, Integer.MIN_VALUE);
	public static OrderBookLevel MAX_ORDER_BOOK_LEVEL = OrderBookLevel.of(0, Integer.MAX_VALUE, Integer.MAX_VALUE);

	public static OrderBookLevel of(int capacity, int tickLevel, int price){
		return new OrderBookLevel(capacity, tickLevel, price);
	}

	public OrderBookLevel clone(){
		OrderBookLevel level = new OrderBookLevel();
		level.tickLevel = this.tickLevel;
		level.price = this.price;
		level.orders = this.orders;
		level.totalOutstanding = this.totalOutstanding;
		level.totalQty = this.totalQty;
		return level;
	}
	
	private OrderBookLevel(){}
	
	public OrderBookLevel(int capacity, int tickLevel, int price){
		this.tickLevel = tickLevel;
		this.price = price;
		this.orders = new Int2ObjectArrayMap<Order>(capacity);
	}

	public void recycleAs(int tickLevel, int price){
		this.tickLevel = tickLevel;
		this.price = price;
		this.orders.clear();
	}

	public int tickLevel(){
		return tickLevel;
	}
	
	public OrderBookLevel tickLevel(int value){
		this.tickLevel = value;
		return this;
	}

	public OrderBookLevel price(int value){
		this.price = value;
		return this;
	}
	
	/**
	 * Remove order from order book level
	 * @param sid
	 * @return
	 */
	public boolean removeOrder(int sid){
		Order removed = this.orders.remove(sid);
		if (removed != null){
			totalQty -= removed.quantity();
			totalOutstanding -= removed.outstanding();
			return true;
		}
		return false;
	}

	/**
	 * Add order to order book level
	 * @param order
	 * @return
	 */
	public boolean addOrder(Order order){
		if (this.orders.put(order.sid(), order) != this.orders.defaultReturnValue()){
			totalQty += order.quantity();
			totalOutstanding += order.outstanding();
			return true;
		}
		LOG.error("order already exists in this order book: ordSid:{}, secSid:{}", order.sid(), order.secSid());
		return false;
	}

	public Order getOrder(int ordSid){
		return this.orders.get(ordSid);
	}
	
	public FastEntrySet<Order> orders(){
		return this.orders.int2ObjectEntrySet();
	}

	public void modifiedOrder(Order modifiedOrder, 
							  int origQty, 
							  int origOutstanding){
		totalQty += (modifiedOrder.quantity() - origQty);
		totalOutstanding += (modifiedOrder.outstanding() - origOutstanding);
	}
	
	public int totalQty(){
		return totalQty;
	}
	
	public int totalOutstanding(){
		return totalOutstanding;
	}
	
	public int numOrders(){
		return this.orders.size();
	}
}