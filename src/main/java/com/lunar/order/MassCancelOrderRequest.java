package com.lunar.order;

import com.lunar.core.TriggerInfo;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class MassCancelOrderRequest extends OrderRequest {
	public static enum MassCancelType {
		ALL,
		SECURITY,
		SIDE
	}
	
	private final MassCancelType massCancelType;
	private final long secSid;
	private final Side side;
	
	public static MassCancelOrderRequest ofAll(int clientKey, MessageSinkRef owner){
		return new MassCancelOrderRequest(clientKey, owner, MassCancelType.ALL, ServiceConstant.NULL_SEC_SID, Side.NULL_VAL);
	}
	
	public static MassCancelOrderRequest ofSecurity(int clientKey, MessageSinkRef owner, long secSid){
		return new MassCancelOrderRequest(clientKey, owner, MassCancelType.SECURITY, secSid, Side.NULL_VAL);
	}
	
	public static MassCancelOrderRequest ofSide(int clientKey, MessageSinkRef owner, Side side){
		return new MassCancelOrderRequest(clientKey, owner, MassCancelType.SIDE, ServiceConstant.NULL_SEC_SID, side);
	}
	
	private MassCancelOrderRequest(int clientKey, MessageSinkRef owner, MassCancelType massCancelType, long secSid, Side side){
		super(clientKey, owner, true, TriggerInfo.of());
		this.massCancelType = massCancelType;
		this.secSid = secSid;
		this.side = side;
	}
	
	public MassCancelType massCancelType(){
		return massCancelType;
	}
	
	public long secSid(){
		return secSid;
	}
	
	public Side side(){
		return side;
	}

	@Override
	public OrderRequestType type() {
		return OrderRequestType.MASS_CANCEL;
	}
}
