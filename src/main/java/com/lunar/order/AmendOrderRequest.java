package com.lunar.order;

import com.lunar.core.TriggerInfo;
import com.lunar.message.io.sbe.AmendOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.sink.MessageSinkRef;

public class AmendOrderRequest extends OrderRequest {
	private final int price;
	private final Side side;
	private final int quantity;
	private final int ordSidToBeAmended;
	
	public static AmendOrderRequest of(MessageSinkRef owner, AmendOrderRequestSbeDecoder request, int ordSid){
		return new AmendOrderRequest(request.clientKey(), owner, request.orderSidToBeAmended(), request.quantity(), request.limitPrice(), request.side());
	}
	
	public static AmendOrderRequest of(int clientKey, MessageSinkRef owner, int ordSidToBeAmended, int quantity, int price, Side side){
		return new AmendOrderRequest(clientKey, owner, ordSidToBeAmended, quantity, price, side);
	}
	
	private AmendOrderRequest(int clientKey, MessageSinkRef owner, int ordSidToBeAmended, int quantity, int price, Side side){
		super(clientKey, owner, true, TriggerInfo.of());
		this.ordSidToBeAmended = ordSidToBeAmended;
		this.quantity = quantity;
		this.price = price;
		this.side = side;
	}
	
	public int ordSidToBeAmended(){
		return ordSidToBeAmended;
	}
	
	public int quantity(){
		return quantity;
	}
	
	public int price(){
		return price;
	}
	
	public Side side(){
		return side;
	}
	@Override
	public OrderRequestType type(){
		return OrderRequestType.AMEND;
	}
}
