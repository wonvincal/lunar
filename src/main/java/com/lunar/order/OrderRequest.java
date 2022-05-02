package com.lunar.order;

import org.apache.logging.log4j.util.Strings;

import com.lunar.core.TriggerInfo;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public abstract class OrderRequest {
	public static final int NULL_ORD_SID = OrderAcceptedSbeDecoder.orderSidNullValue();
	public static final int NUM_THROTTLE_TO_BE_ACQUIRED = 1;
	public static final int NUM_THROTTLE_REQUIRED_TO_PROCEED_FOR_LIMIT_THEN_CANCEL = 1;
	public static final int NUM_THROTTLE_REQUIRED_TO_PROCEED = 1;
			
	/**
	 * An identifier created by the originator of the request
	 * It can be used by the client to link up with the corresponding reply message.e
	 * 
	 */
	private int clientKey;
	
	private MessageSinkRef owner;

	/**
	 * An internal system ID assigned to this Order Request by our system
	 * Borrowing from order management from HKEx, the term "Order ID" is used for both
	 * order request and order interchangeably
	 * 
	 * Default it to a SBE null value of client order ID
	 */
	private int orderSid = NULL_ORD_SID;
	
	/**
	 * Number of throttles that this order needs in order to proceed
	 */
	private int numThrottleRequiredToProceed = 1;
	
	private boolean throttleCheckRequired = true;
	
	/**
	 * Consider this request as TimedOut if it cannot be successfully sent before this value.
	 * This is the number of millisecond after Epoch
	 * 
	 */
	private final int assignedThrottleTrackerIndex;
	private boolean retry;
	private long timeoutAtNanoOfDay;
	private OrderRequestCompletionType completionType = OrderRequestCompletionType.NULL_VAL;
	private boolean isPartOfCompositOrder;
	private OrderRequestRejectType rejectType = OrderRequestRejectType.NULL_VAL;
	private String reason = Strings.EMPTY;
	private final TriggerInfo triggerInfo;

	OrderRequest(int clientKey, MessageSinkRef owner, boolean throttleCheckRequired, TriggerInfo triggerInfo){
		this(clientKey, owner, Long.MAX_VALUE, false, 1, false, 0, throttleCheckRequired, triggerInfo);
	}
	
	OrderRequest(int clientKey, MessageSinkRef owner, long timeoutAtNanoOfDay, boolean retry, int numThrottlesCheck, boolean isPartOfCompositOrder, int assignedThrottleTrackerIndex, boolean throttleCheckRequired, TriggerInfo triggerInfo){
		this.clientKey = clientKey;
		this.owner = owner;
		this.timeoutAtNanoOfDay = timeoutAtNanoOfDay;
		this.retry = retry;
		this.numThrottleRequiredToProceed = numThrottlesCheck;
		this.isPartOfCompositOrder = isPartOfCompositOrder;
		this.triggerInfo = triggerInfo;
		this.assignedThrottleTrackerIndex = assignedThrottleTrackerIndex;
		this.throttleCheckRequired = throttleCheckRequired;
	}
	
	public int clientKey(){
		return clientKey;
	}

	public MessageSinkRef owner(){
		return owner;
	}

	public OrderRequest clientKey(int value){
		this.clientKey = value;
		return this;
	}

	public OrderRequest owner(MessageSinkRef value){
		this.owner = value;
		return this;
	}
	
	/**
	 * Nano of day
	 * @return
	 */
	public long timeoutAtNanoOfDay(){
		return timeoutAtNanoOfDay;
	}

	public OrderRequest timeoutAtNanoOfDay(long timeoutAtNanoOfDay){
		this.timeoutAtNanoOfDay = timeoutAtNanoOfDay;
		return this;
	}

	public OrderRequest orderSid(int ordSid){
		this.orderSid = ordSid;
		return this;
	}

	public OrderRequest isPartOfCompositOrder(boolean value){
		this.isPartOfCompositOrder = value;
		return this;
	}
	
	public OrderRequest numThrottleRequiredToProceed(int value){
		numThrottleRequiredToProceed = value;
		return this;
	}

	public boolean throttleCheckRequired(){
		return throttleCheckRequired;
	}
	
	public OrderRequest throttleCheckRequired(boolean value){
		throttleCheckRequired =  value;
		return this;
	}
	
	public int assignedThrottleTrackerIndex(){
		return this.assignedThrottleTrackerIndex;
	}
	
	/**
	 * Our own system id of this order request
	 * @return
	 */
	public int orderSid(){
		return orderSid;
	}
	
	public OrderRequestCompletionType completionType(){
		return this.completionType;
	}

	public OrderRequest completionType(OrderRequestCompletionType completionType){
		this.completionType = completionType;
		return this;
	}

	public OrderRequestRejectType rejectType(){
		return this.rejectType;
	}

	public OrderRequest rejectType(OrderRequestRejectType rejectType){
		this.rejectType = rejectType;
		return this;
	}
	
	public OrderRequest reason(byte[] byteBuffer){
		reason = new String(byteBuffer);
		return this;
	}
	
	public String reason(){
		return reason;
	}
	
	public boolean retry(){
		return retry; 
	}

	public abstract OrderRequestType type();
	
	public TriggerInfo triggerInfo() { return triggerInfo; }

	public NewOrderRequest asNewOrderRequest(){
		return (NewOrderRequest)this;
	}

	AmendOrderRequest asAmendOrderRequest(){
		return (AmendOrderRequest)this;
	}

	public CancelOrderRequest asCancelOrderRequest(){
		return (CancelOrderRequest)this;
	}
	
	MassCancelOrderRequest asMassCancelOrderRequest(){
		return (MassCancelOrderRequest)this;
	}

	public long secSid(){
		return ServiceConstant.NULL_SEC_SID;
	}
	
	public int numThrottleRequiredToProceed(){
		return numThrottleRequiredToProceed;
	}
	
	@Override
	public String toString() {
		return "clientKey: " + clientKey + ", orderSid: " + orderSid + ", isComposite: " + isPartOfCompositOrder;
	}
	
	public boolean isPartOfCompositOrder(){
		return isPartOfCompositOrder;
	}
}
