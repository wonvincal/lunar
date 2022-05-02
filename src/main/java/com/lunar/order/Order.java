package com.lunar.order;

import com.lunar.core.TriggerInfo;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class Order {
	private long secSid;
	private int sid;
	private int clientKey;
	/*
	 * Original submitted quantity
	 */
	private int quantity;
	private BooleanType isAlgo;
	private OrderType orderType;
	private Side side;
	private int limitPrice;
	private int stopPrice;
	private int outstanding;
	private MessageSinkRef ownerSinkRef;
	private OrderStatus status;
	private TimeInForce tif;
	private int channelId;
	private long channelSeq;
    private long createTime;
    private long updateTime;
    private OrderRejectType orderRejectType;
    private String reason = "";
    private final TriggerInfo triggerInfo;

	/**
	 * A temporary field for orderId, in exchange's spec, this is an alphanumeric field of length 21. 
	 */
	private int orderId;
	
	/**
	 * This is a FIX term.  Previously known as outstanding quantity.
	 */
	private int leavesQty;
	
	/**
	 * This is a FIX term. Previously known as traded quantity.
	 */
	private int cumulativeExecQty;
	
    public static Order of(NewOrderRequest request, OrderStatus status, int leavesQty, int cumulativeQty){
        return of(request, status, leavesQty, cumulativeQty, 0, 0);
    }

	public static Order of(NewOrderRequest request, OrderStatus status, int leavesQty, int cumulativeQty, long createTime, long updateTime){
	    final TriggerInfo triggerInfo = TriggerInfo.of(request.triggerInfo()); 
		return new Order(request.secSid(), 
				request.owner(),
				request.orderSid(),
				request.clientKey(),
				request.quantity(),
				request.isAlgoOrder(),
				request.orderType(),
				request.side(),
				request.limitPrice(),
				request.stopPrice(),
				request.tif(),
				status,
				leavesQty,
				cumulativeQty,
				createTime,
				updateTime,
				OrderRejectType.NULL_VAL,
				triggerInfo);
	}
	
    public static Order of(long secSid, MessageSinkRef ownerSinkRef, int sid, int quantity, BooleanType isAlgo, OrderType orderType, Side side, int limitPrice, int stopPrice, TimeInForce tif, OrderStatus status){        
        return of(secSid, ownerSinkRef, sid, quantity, isAlgo, orderType, side, limitPrice, stopPrice, tif, status, 0, 0);
    }

    public static Order of(long secSid, MessageSinkRef ownerSinkRef, int sid, int quantity, BooleanType isAlgo, OrderType orderType, Side side, int limitPrice, int stopPrice, TimeInForce tif, OrderStatus status, long createTime, long updateTime){
        return new Order(secSid, ownerSinkRef, sid, ServiceConstant.NULL_CLIENT_ORDER_REQUEST_ID, quantity, isAlgo, orderType, side, limitPrice, stopPrice, tif, status, quantity, 0, createTime, updateTime, OrderRejectType.NULL_VAL, TriggerInfo.of());
    }

    public static Order of(long secSid, MessageSinkRef ownerSinkRef, int sid, int quantity, BooleanType isAlgo, OrderType orderType, Side side, int limitPrice, int stopPrice, TimeInForce tif, OrderStatus status, long createTime, long updateTime, TriggerInfo triggerInfo){
        return new Order(secSid, ownerSinkRef, sid, ServiceConstant.NULL_CLIENT_ORDER_REQUEST_ID, quantity, isAlgo, orderType, side, limitPrice, stopPrice, tif, status, quantity, 0, createTime, updateTime, OrderRejectType.NULL_VAL, triggerInfo);
    }
    
    public static Order of(long secSid, MessageSinkRef ownerSinkRef, int sid, int quantity, BooleanType isAlgo, OrderType orderType, Side side, int limitPrice, int stopPrice, TimeInForce tif, OrderStatus status, long createTime, long updateTime, TriggerInfoDecoder triggerInfo){
        return new Order(secSid, ownerSinkRef, sid, ServiceConstant.NULL_CLIENT_ORDER_REQUEST_ID, quantity, isAlgo, orderType, side, limitPrice, stopPrice, tif, status, quantity, 0, createTime, updateTime, OrderRejectType.NULL_VAL, TriggerInfo.of(triggerInfo));
    }
    
    
	private Order(long secSid, MessageSinkRef ownerSinkRef, int sid, int clientKey, int quantity, BooleanType isAlgo, OrderType orderType, Side side, int limitPrice, int stopPrice, TimeInForce tif, OrderStatus status, int leavesQty, int cumulativeQty, long createTime, long updateTime, OrderRejectType rejectType, TriggerInfo triggerInfo){
		this.secSid = secSid;
		this.ownerSinkRef = ownerSinkRef;
		this.sid = sid;
		this.clientKey = clientKey;
		this.quantity = quantity;
		this.isAlgo = isAlgo;
		this.orderType = orderType;
		this.side = side;
		this.limitPrice = limitPrice;
		this.stopPrice = stopPrice;
		this.tif = tif;
		this.status = status;
		this.leavesQty = leavesQty;
		this.cumulativeExecQty = cumulativeQty;
		this.createTime = createTime;
		this.updateTime = updateTime;
		this.orderRejectType = rejectType;
		this.triggerInfo = triggerInfo;
	}
	
	public OrderRejectType orderRejectType(){
		return this.orderRejectType;
	}

	public Order orderRejectType(OrderRejectType rejectType){
		this.orderRejectType = rejectType;
		return this;
	}

	public TimeInForce timeInForce(){
		return this.tif;
	}
	
	public Order timeInForce(TimeInForce tif){
		this.tif = tif;
		return this;
	}

	public long secSid(){
		return this.secSid;
	}
	
	public int leavesQty(){
		return leavesQty;
	}
	
	public Order leavesQty(int value){
		this.leavesQty = value;
		return this;
	}
	
	public int cumulativeExecQty(){
		return cumulativeExecQty;
	}	

	public Order cumulativeExecQty(int value){
		this.cumulativeExecQty = value;
		return this;
	}	

	public int limitPrice(){
		return limitPrice;
	}
	
	public Order limitPrice(int limitPrice) {
		this.limitPrice = limitPrice;
		return this;
	}
	
	public int stopPrice(){
		return stopPrice;
	}
	
	public int sid(){
		return sid;
	}
	
	public int clientKey(){
		return clientKey;
	}

	public OrderType orderType(){
		return orderType;
	}
	
	public BooleanType isAlgo(){
		return isAlgo;
	}
	
	public Side side(){
		return side;
	}
	
	public int quantity(){
		return quantity;
	}
	
	public Order quantity(int quantity) {
		this.quantity = quantity;
		return this;
	}
	
	public int outstanding(){
		return outstanding;
	}
	
	public Order outstanding(int outstanding){
		this.outstanding = outstanding;
		return this;
	}
	
	public int ownerSinkId(){
		return ownerSinkRef.sinkId();
	}

	public OrderStatus status(){
		return status;
	}

	public Order status(OrderStatus status){
		this.status = status;
		return this;
	}

	public MessageSinkRef ownerSinkRef(){
		return ownerSinkRef;
	}
	
	public Order orderId(int value){
		this.orderId = value;
		return this;
	}
	
	public int orderId(){
		return orderId;
	}
	
	Order sid(int value){
		this.sid = value;
		return this;
	}
	
	Order clientKey(int value){
		this.clientKey = value;
		return this;
	}
	
	public int channelId(){
		return this.channelId;
	}
	
	public long channelSeq(){
		return this.channelSeq;
	}
	
	public Order channelId(int channelId){
		this.channelId = channelId;
		return this;
	}
	
	public Order channelSeq(long channelSeq){
		this.channelSeq = channelSeq;
		return this;
	}

	public long createTime() {
	    return this.createTime;
	}
	
	public long updateTime() {
	    return this.updateTime;
	}
	
	public Order updateTime(final long updateTime) {
	    this.updateTime = updateTime;
	    return this;
	}

	public String reason() {
	    return this.reason;
	}
	
	public Order reason(String reason) {
	    this.reason = reason;
	    return this;
	}
	
	public TriggerInfo triggerInfo() {
	    return this.triggerInfo;
	}

}
