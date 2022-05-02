package com.lunar.order;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SequencingOnlyChannel;
import com.lunar.core.SubscriberList;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;
import com.lunar.util.BitUtil;
import com.lunar.util.ConcurrentInt2ObjMap;

/**
 * <b>Caution</b>This object is designed to be used by two threads:
 * 1) OMES
 * 2) Order Update Receiver
 * 
 * More specifically, most child data structures will be used by both threads.
 * Some will only be used by one thread.
 * 
 * Thread safety: Yes
 * 
 * Readability: For data structures that will be used by both threads, please declare
 * them as volatile. 
 * 
 * 
 * @author wongca
 *
 */
public class OrderManagementContext {
	private static final Logger LOG = LogManager.getLogger(OrderManagementContext.class);
	private final OrderChannelIdFinder channelIdFinder;
	private volatile MessageSinkRef[] orderUpdateSubscribers;
	private final SubscriberList performanceSubscribers;
	
	/**
	 * This is used by both OMES and OrderContextManager.  However OMES uses
	 * it after recovery and OrderContextManager uses it before recovery.
	 * There is no need to make this volatile at all.
	 */
    private Exposure exposure;
    private int latestOrderSid;
    private volatile long exposureAfterRecoveryUpdatedTime;

	/**
	 * This object is going to be shared between OMES and Order Update Receiver threads.
	 * This will impact performance of the two threads if there are large amount of updates.
	 * One possible optimization is to send OrderRequest from OMES to Order Update Receiver as message.
	 * A separate thread will be needed to process these messages.
	 * 
	 * On a similar note, the reason that I've decided not to share the map of all orders between
	 * OMES and Order Update Receiver threads is because of the same reason.  I think there will be lots
	 * order updates (i.e. N trade updates per order) and that will definitely impact the performance
	 * of the OMES thread.
	 *  
	 */
	private final ConcurrentInt2ObjMap<OrderRequest> orderRequestBySid;
	
	/**
	 * TODO Enhance ConcurrentLong2ObjMap to use array-base structure to avoid
	 * thread-issue.  Or replace this with a pre-populated data structure
	 */
	private volatile SecurityLevelInfo[] securityInfo;
	
	private final ConcurrentHashMap<Integer, Integer> origOrdSidToCancelOrderSid;
	
	/**
	 * Channels
	 */
	private final SequencingOnlyChannel[] channels;
	
	public static OrderManagementContext of(int numOutstandingRequests, int numSecurities, int numOutstandingOrdersPerSecurity, int numChannels){
		return new OrderManagementContext(numOutstandingRequests, numSecurities, numOutstandingOrdersPerSecurity, numChannels);
	}
	
	OrderManagementContext(int numOutstandingRequests, int numSecurities, int numOutstandingOrdersPerSecurity, int numChannels){
		if (!BitUtil.isPowerOfTwo(numChannels)){
			throw new IllegalArgumentException("Number of channels must be a power of two [now:" + numChannels + "]");
		}
		this.origOrdSidToCancelOrderSid = new ConcurrentHashMap<>();
		this.orderUpdateSubscribers = new MessageSinkRef[0];
		this.orderRequestBySid = new ConcurrentInt2ObjMap<>(numOutstandingRequests);
		this.channels = new SequencingOnlyChannel[numChannels];
		for (int i = 0; i < this.channels.length; i++){
			this.channels[i] = SequencingOnlyChannel.of(i);
		}
		this.channelIdFinder = new OrderChannelIdFinder(numChannels);

		this.securityInfo = new SecurityLevelInfo[numSecurities + 1];
		for (int secSid = 0; secSid < numSecurities + 1; secSid++){
			securityInfo[secSid] = SecurityLevelInfo.of(secSid,
					channels[channelIdFinder.fromSecSid(secSid)], 
					ValidationOrderBook.of(numOutstandingOrdersPerSecurity, secSid));
		}
		this.exposure = Exposure.of();
		this.performanceSubscribers = SubscriberList.of(ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY);
	}
	
	public void addOrderUpdateSubscriber(MessageSinkRef subscriber){
		MessageSinkRef[] refs = orderUpdateSubscribers;
		for (int i = 0; i < refs.length; i++){
			if (refs[i].equals(subscriber)){
				LOG.info("Subscriber {} is already added", subscriber);
				return;
			}
		}
		MessageSinkRef[] newArray = new MessageSinkRef[refs.length + 1];
		for (int i = 0; i < refs.length; i++){
			newArray[i] = refs[i];
		}
		newArray[refs.length] = subscriber;
		this.orderUpdateSubscribers = newArray;
	}
	
	ConcurrentHashMap<Integer, Integer> origOrdSidToCancelOrderSid(){
	    return origOrdSidToCancelOrderSid;
	}
	
	public MessageSinkRef[] orderUpdateSubscribers(){
		return orderUpdateSubscribers;
	}

	public OrderRequest getOrderRequest(int ordSid){
		return orderRequestBySid.get(ordSid);
	}
	
	public void putOrderRequest(int ordSid, OrderRequest request){
		OrderRequest orderRequest = orderRequestBySid.put(ordSid, request);
		if (orderRequest != null){
			throw new IllegalStateException("Add aborted.  Order request already exists | sid:" + request.orderSid() + ", existing order request:" + orderRequest.toString());
		}
	}
	
	public OrderRequest removeOrderRequest(int sid){
		return orderRequestBySid.remove(sid);
	}
	
	/**
	 * 
	 * @param secSid
	 * @return
	 */
	public SecurityLevelInfo securiytLevelInfo(long secSid){
		return this.securityInfo[(int)secSid];
	}

	/**
	 * Note: we are not going to throw away this channel
	 * @param secSid
	 * @return
	 */
	public SequencingOnlyChannel channel(long secSid){
		return this.securityInfo[(int)secSid].channel();
	}

	public void logSecurityPosition(){
		LOG.debug("Current security position(s) - only non empty book");
		for (int i = 0; i < this.securityInfo.length; i++){
			if (!this.securityInfo[i].isClearBook()){
				LOG.debug(this.securityInfo[i].toString());
			}
		}
	}
	
	public boolean reset() {
		LOG.info("Reset state");
		orderUpdateSubscribers = new MessageSinkRef[0];
		orderRequestBySid.clear();
		performanceSubscribers.clear();
		
		for (int i = 0; i < this.channels.length; i++){
			this.channels[i].reset();
		}
		
		for (int i = 0; i < securityInfo.length; i++){
			securityInfo[i].clearBook();
		}

		return true;
	}
	
	public boolean isClear(){
	    for (int i = 0; i < this.channels.length; i++){
	        if (!this.channels[i].isClear()){
	            return false;
	        }
	    }
	    for (int i = 0; i < this.securityInfo.length; i++){
	    	if (!this.securityInfo[i].isClearBook()){
	    		return false;
	    	}
	    }
	    return orderUpdateSubscribers.length == 0 &&
	            orderRequestBySid.isEmpty();
	}
	
	ConcurrentInt2ObjMap<OrderRequest> orderRequestBySid(){
	    return orderRequestBySid;
	}
	
	public void exposureAfterRecoveryUpdatedTime(long value){
	    this.exposureAfterRecoveryUpdatedTime = value;
	}
	
	public long volatileExposureAfterRecoveryUpdatedTime(){
	    return exposureAfterRecoveryUpdatedTime;
	}
	
	public Exposure exposure(){
	    return exposure;
	}
	
	public int latestOrderSid(){
	    return latestOrderSid;
	}
	
	public void latestOrderSid(int value){
	    this.latestOrderSid = value;
	}
	
	public long updateLatestOrderSid(int value){
	    latestOrderSid = Math.max(latestOrderSid, value);
	    return latestOrderSid;
	}

	public SubscriberList performanceSubscribers(){
		return performanceSubscribers;
	}
}
