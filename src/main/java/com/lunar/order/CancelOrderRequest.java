package com.lunar.order;

import com.lunar.core.TriggerInfo;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.sink.MessageSinkRef;

/**
 * 	Whether a field is optional or not is modeled base on the OCG specification.
 * 	We may want to have another OrderCancel class for a different exchange
 * 
 * 	When we want to cancel an order, we need:  
 *  1) client order request id: that's the client-created order request id
 *  2) order sid: that's the internal order sid to be cancelled
 *  3) orig client order request id: the original client order request id, useful when order sid is not yet
 *     available
 *  
 *  We will need a reference to the original order.
 *  Answer: We don't always have a reference to the order, coz the order may not have come back.
 *  
 * @author Calvin
 *
 */
public class CancelOrderRequest extends OrderRequest {
	private final int ordSidToBeCancelled;
	private final long secSid; 
	private final Side side;
	
	public static CancelOrderRequest of(MessageSinkRef owner, CancelOrderRequestSbeDecoder request){
		CancelOrderRequest item = new CancelOrderRequest(request.clientKey(), owner, request.orderSidToBeCancelled(), request.secSid(), Side.NULL_VAL);
		return item;
	}

	public static CancelOrderRequest of(MessageSinkRef owner, CancelOrderRequestSbeDecoder request, int ordSid, long secSid, Side side){
		CancelOrderRequest item = new CancelOrderRequest(request.clientKey(), owner, request.orderSidToBeCancelled(), secSid, side);
		item.orderSid(ordSid);
		return item;
	}
	
	public static CancelOrderRequest of(int clientKey, MessageSinkRef owner, int ordSidToBeCancelled, int ordSid, long secSid, Side side){
		CancelOrderRequest item = new CancelOrderRequest(clientKey, owner, ordSidToBeCancelled, secSid, side);
		item.orderSid(ordSid);
		return item;
	}
	
	public static CancelOrderRequest of(int clientKey, MessageSinkRef owner, int ordSidToBeCancelled, long secSid, Side side){
		return new CancelOrderRequest(clientKey, owner, ordSidToBeCancelled, secSid, side);
	}
	
	private CancelOrderRequest(int clientKey, MessageSinkRef owner, int ordSidToBeCancelled, long secSid, Side side){
		super(clientKey, owner, true, TriggerInfo.of());
		this.ordSidToBeCancelled = ordSidToBeCancelled;
		this.secSid = secSid;
		this.side = side;
	}

	public int ordSidToBeCancelled(){ return ordSidToBeCancelled;}

	@Override
	public long secSid(){ return secSid;}
	
	public Side side(){ return side;}

	@Override
	public OrderRequestType type(){
		return OrderRequestType.CANCEL;
	}
	
	@Override
	public String toString() {
		return super.toString() + ", ordSidToBeCancelled: " + ordSidToBeCancelled + ", secSid: " + secSid + ", side: " + side;
	}
}
