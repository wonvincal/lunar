package com.lunar.order;

import com.lunar.core.TriggerInfo;
import com.lunar.entity.Security;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.NewCompositeOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class NewOrderRequest extends OrderRequest {
	private final OrderType orderType;
	private final int quantity;
	private final Side side;
	private final TimeInForce tif;
	private final BooleanType isAlgoOrder;
	private final int limitPrice;
	private final int stopPrice;
	private final int portSid;
	private final long secSid;

	/**
	 * Channel that is used to generate channel sequence.  Only being used by {@link OrderContextManager}.
	 */
	private SecurityLevelInfo securityLevelInfo;
	
  public static NewOrderRequest of(MessageSinkRef owner, NewOrderRequestSbeDecoder request){
      final TriggerInfo triggerInfo = TriggerInfo.of(request.triggerInfo());
      NewOrderRequest item = new NewOrderRequest(
              request.clientKey(),
              owner, 
              request.secSid(),
              request.orderType(), 
              request.quantity(), 
              request.side(), 
              request.tif(), 
              request.isAlgoOrder(), 
              request.limitPrice(), 
              request.stopPrice(), 
              request.timeoutAt(), 
              request.retry() == BooleanType.TRUE ? true : false,
              request.assignedThrottleTrackerIndex(),
              request.portSid(),
              request.throttleCheckRequired() == BooleanType.TRUE ? true : false,
              triggerInfo);
        return item;
    }

	
    public static NewOrderRequest of(MessageSinkRef owner, NewOrderRequestSbeDecoder request, int ordSid, SecurityLevelInfo securityLevelInfo){
        final TriggerInfo triggerInfo = TriggerInfo.of(request.triggerInfo());
        NewOrderRequest item = new NewOrderRequest(
                request.clientKey(),
                owner, 
                request.secSid(),
                request.orderType(), 
                request.quantity(), 
                request.side(), 
                request.tif(), 
                request.isAlgoOrder(), 
                request.limitPrice(), 
                request.stopPrice(), 
                request.timeoutAt(),
                request.retry() == BooleanType.TRUE ? true : false,
                request.assignedThrottleTrackerIndex(),
                request.portSid(),
                request.throttleCheckRequired() == BooleanType.TRUE ? true : false,
                triggerInfo);
        item.orderSid(ordSid);
        item.securityLevelInfo = securityLevelInfo;
        return item;
    }
    
    public static NewOrderRequest of(MessageSinkRef owner, NewCompositeOrderRequestSbeDecoder request, int ordSid, SecurityLevelInfo securityLevelInfo){
    	if (request.orderType() != OrderType.LIMIT_THEN_CANCEL_ORDER){
    		throw new IllegalArgumentException("Composite order allows only LIMIT_THEN_CANCEL_ORDER");
    	}
        final TriggerInfo triggerInfo = TriggerInfo.of(request.triggerInfo());
        NewOrderRequest item = new NewOrderRequest(
                request.clientKey(),
                owner, 
                request.secSid(),
                OrderType.LIMIT_ORDER, 
                request.quantity(), 
                request.side(), 
                TimeInForce.DAY,
                request.isAlgoOrder(), 
                request.limitPrice(), 
                request.stopPrice(), 
                request.timeoutAt(),
                request.retry() == BooleanType.TRUE ? true : false,
                request.assignedThrottleTrackerIndex(),
                request.portSid(),
                true,
                triggerInfo);
        item.orderSid(ordSid);
        item.securityLevelInfo = securityLevelInfo;
        return item;
    }

    static NewOrderRequest of(int clientKey, MessageSinkRef owner, Security security, OrderType orderType, int quantity, Side side, TimeInForce tif,
			   BooleanType isAlgoOrder, int limitPrice, int stopPrice, int portSid, SecurityLevelInfo securityLevelInfo){
		NewOrderRequest request = new NewOrderRequest(clientKey, owner, security.sid(), orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, portSid, true, TriggerInfo.of());
		request.securityLevelInfo = securityLevelInfo;
		return request;
	}
	
    public static NewOrderRequest of(int clientKey, MessageSinkRef owner, Security security, OrderType orderType, int quantity, Side side, TimeInForce tif,
            BooleanType isAlgoOrder, int limitPrice, int stopPrice, long timeoutAtNanoOfDay, boolean retry, int assignedThrottleTrackerIndex, int portSid, boolean throttleCheckRequired, TriggerInfo triggerInfo){
        return new NewOrderRequest(clientKey, owner, security.sid(), orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, timeoutAtNanoOfDay, retry, assignedThrottleTrackerIndex, portSid, throttleCheckRequired, triggerInfo);
    }

	public static NewOrderRequest of(int clientKey, MessageSinkRef owner, Security security, OrderType orderType, int quantity, Side side, TimeInForce tif,
			   BooleanType isAlgoOrder, int limitPrice, int stopPrice, int portSid){
		return new NewOrderRequest(clientKey, owner, security.sid(), orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, portSid, true, TriggerInfo.of());
	}

	static NewOrderRequest of(int clientKey, MessageSinkRef owner, Security security, OrderType orderType, int quantity, Side side, TimeInForce tif,
			   BooleanType isAlgoOrder, int limitPrice, int stopPrice, long timeoutAtNanoOfDay, boolean retry, int throttleTrackerIndex, int portSid){
		return new NewOrderRequest(clientKey, owner, security.sid(), orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, 
				timeoutAtNanoOfDay, retry, throttleTrackerIndex, portSid, true, TriggerInfo.of());
	}
	
	public static NewOrderRequest ofWithDefaults(int clientKey, MessageSinkRef owner, long secSid, OrderType orderType, int quantity, Side side, 
			TimeInForce tif, BooleanType isAlgoOrder, int limitPrice, int stopPrice, long timeoutAtNanoOfDay, boolean retry, int portSid){
		return new NewOrderRequest(clientKey, owner, secSid, orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, 
				timeoutAtNanoOfDay, retry, ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX, portSid, true, TriggerInfo.of());
	}

	public static NewOrderRequest of(int clientKey, MessageSinkRef owner, long secSid, OrderType orderType, int quantity, Side side, 
			TimeInForce tif, BooleanType isAlgoOrder, int limitPrice, int stopPrice, long timeoutAtNanoOfDay, boolean retry, int assignedThrottleTrackerIndex, int portSid, TriggerInfo triggerInfo){
		return new NewOrderRequest(clientKey, owner, secSid, orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, timeoutAtNanoOfDay, retry, assignedThrottleTrackerIndex, portSid, true, triggerInfo);
	}

	NewOrderRequest(int clientKey, MessageSinkRef owner, long secSid, OrderType orderType, int quantity, Side side, TimeInForce tif,
			BooleanType isAlgoOrder, int limitPrice, int stopPrice, int portSid, boolean throttleCheckRequired, TriggerInfo triggerInfo){
		super(clientKey, owner, throttleCheckRequired, triggerInfo);
		this.secSid = secSid;
		this.orderType = orderType;
		this.quantity = quantity;
		this.side = side;
		this.tif = tif;
		this.limitPrice = limitPrice;
		this.stopPrice = stopPrice;
		this.portSid = portSid;
		this.isAlgoOrder = isAlgoOrder;
	}
	NewOrderRequest(int clientKey, MessageSinkRef owner, long secSid, OrderType orderType, int quantity, Side side, TimeInForce tif,
			BooleanType isAlgoOrder, int limitPrice, int stopPrice, long timeoutAtNanoOfDay, boolean retry, int assignedThrottleTrackerIndex, int portSid, boolean throttleCheckRequired, TriggerInfo triggerInfo){
		super(clientKey, owner, timeoutAtNanoOfDay, retry, 1, false, assignedThrottleTrackerIndex, throttleCheckRequired, triggerInfo);
		this.secSid = secSid;
		this.orderType = orderType;
		this.quantity = quantity;
		this.side = side;
		this.tif = tif;
		this.limitPrice = limitPrice;
		this.stopPrice = stopPrice;
		this.portSid = portSid;
		this.isAlgoOrder = isAlgoOrder;
	}
	@Override
	public long secSid(){ return secSid;}
	public OrderType orderType(){ return orderType;}
	public int quantity(){ return quantity;}
	public Side side(){ return side;}
	public TimeInForce tif(){ return tif;}
	public BooleanType isAlgoOrder(){ return isAlgoOrder;}
	public int limitPrice(){ return limitPrice;}
	public int stopPrice(){ return stopPrice;}
	public int portSid(){ return portSid;}
	
	/**
	 * To be used by {@link OrderContextManager} only
	 * @return
	 */
	SecurityLevelInfo securityLevelInfo(){ return securityLevelInfo;}
	
	NewOrderRequest securityLevelInfo(SecurityLevelInfo securityLevelInfo){
		this.securityLevelInfo = securityLevelInfo;
		return this;
	}

	@Override
	public OrderRequestType type(){
		return OrderRequestType.NEW;
	}
}
