package com.lunar.core;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/**
 * 
 * Thread safety: No
 * 
 * @author Calvin
 *
 */
public class ServiceStatusTracker {
	private static final Logger LOG = LogManager.getLogger(ServiceStatusTracker.class);

	private int selfSinkId;
	private String name;
	private final Int2ObjectOpenHashMap<ServiceStatus> statuses;
	private final Int2ObjectOpenHashMap<ServiceStatusChangeHandlerList> handlerBySinkId;
	private final Reference2ObjectOpenHashMap<ServiceType, ServiceStatusChangeHandlerList> handlerByServiceType;
	private AggregatedServiceStatusChangeHandlerList handlerForAllServiceUp;
	private ServiceStatusChangeHandlerList handlerForAnyServiceStatusChange;
	private boolean allTrackedServicesUp;
	
	public static ServiceStatusTracker of(MessageSinkRef selfSink){
		return new ServiceStatusTracker(selfSink.sinkId(), selfSink.name());
	}
	
	private static ServiceStatusChangeHandler NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_ANY_SERVICE = new ServiceStatusChangeHandler() {
		@Override
		public void handle(ServiceStatus status) {
			LOG.warn("Receive {} in null handler for any service", status);
		}
	};
	
	private static ServiceStatusChangeHandler NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_SINK_ID = new ServiceStatusChangeHandler() {
		@Override
		public void handle(ServiceStatus status) {
			LOG.warn("Receive {} in null handler for sink ID", status);
		}
	};
	
	private static ServiceStatusChangeHandler VALID_NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_SERVICE_TYPE = new ServiceStatusChangeHandler() {
		@Override
		public void handle(ServiceStatus status) {
			LOG.debug("Receive {} in valid null handler for service type", status);
		}
	};
	
	ServiceStatusTracker(int selfSinkId, String name){
		this.selfSinkId = selfSinkId;
		this.name = name;
		statuses = new Int2ObjectOpenHashMap<ServiceStatus>();
		handlerBySinkId = new Int2ObjectOpenHashMap<ServiceStatusChangeHandlerList>();
		handlerByServiceType = new Reference2ObjectOpenHashMap<ServiceType, ServiceStatusChangeHandlerList>();
		allTrackedServicesUp = false;
		handlerForAllServiceUp = AggregatedServiceStatusChangeHandlerList.of(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY);
		handlerForAnyServiceStatusChange = ServiceStatusChangeHandlerList.of(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_ANY_SERVICE);
	}
	
	public boolean isTrackingService(ServiceType serviceType){
		return handlerByServiceType.containsKey(serviceType);
	}
	
	public boolean isTrackingSink(int sinkId){
		return handlerBySinkId.containsKey(sinkId);
	}
	
	/**
	 * This tracker is currently tracking a particular sink id or service type
	 * @return
	 */
	public boolean isTracking(){
		return (!handlerBySinkId.isEmpty() ||
				!handlerByServiceType.isEmpty());
	}
	
	public boolean hasHandlerForAnyServiceStatusChange(){
		return handlerForAnyServiceStatusChange.hasHandler();
	}
	
	public boolean allTrackedServicesUp(){
		return allTrackedServicesUp;
	}

	private final Handler<ServiceStatusSbeDecoder> eventHandler = new Handler<ServiceStatusSbeDecoder>() {
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
			int sinkId = codec.sinkId();
			ServiceStatusType statusType = codec.statusType();
			ServiceStatus prev = statuses.get(sinkId);
			boolean statusChanged = false;
			if ((prev == null) ||
				(statusType.equals(ServiceStatusType.HEARTBEAT)) ||
				(prev.statusType() != statusType)) {
				// TODO compare the original last update time
				// this won't work if a hot-hot actor got swapped in on a machine with
				// slower clock
				statusChanged = true;		
			}
			if (!statusChanged){
				return;
			}
			// Creating a new object here is not too bad because we are not going to 
			// get a lot of these.
			ServiceStatus newStatus = ServiceStatus.of(codec.systemId(), header.senderSinkId(), sinkId, codec.serviceType(), statusType, codec.modifyTimeAtOrigin(), codec.sentTime(), codec.healthCheckTime());
			updateStatus(sinkId, newStatus);
		}
	};
	
	public Handler<ServiceStatusSbeDecoder> eventHandler(){
		return eventHandler;
	}
	
	public void onMessage(ServiceStatus status){
		int sinkId = status.sinkId();
		ServiceStatus prev = statuses.get(sinkId);
		boolean statusChanged = false;
		if ((prev == null) || 
			(status.statusType() == ServiceStatusType.HEARTBEAT) ||
			(prev.statusType() != status.statusType())) {
			// TODO compare the original last update time
			// this won't work if a hot-hot actor got swapped in on a machine with
			// slower clock
			statusChanged = true;
		}
		if (!statusChanged){
			return;
		}
		updateStatus(sinkId, status);
	}
	
	private void updateStatus(int sinkId, ServiceStatus status){
		LOG.trace("Update status [name:{}, {}]", name, status);
		statuses.put(sinkId, status);
		ServiceStatusChangeHandler handler = handlerBySinkId.get(sinkId);
		if (handler != null){
			handler.handle(status);
		}
		
		handler = handlerByServiceType.get(status.serviceType());
		if (handler != null){
			handler.handle(status);
		}
		handlerForAnyServiceStatusChange.handle(status);
		
		evalIsAllUp();		
	}
	
	private void evalIsAllUp(){
		boolean latestStatus = isAllUp();
		if (latestStatus != allTrackedServicesUp){
			if (latestStatus){
				LOG.debug("All tracked services are up [selfSinkId:{}, name:{}]", selfSinkId, name);
			}
			allTrackedServicesUp = latestStatus;
			handlerForAllServiceUp.handle(allTrackedServicesUp);
		}
	}

	/**
	 * TODO need a faster way
	 * @return
	 */
	boolean isAllUp(){
		if (!isTracking()){
			return false;
		}
		for (int sinkId : handlerBySinkId.keySet()){
			ServiceStatus status = statuses.get(sinkId);
			if (status == null || (status.statusType() != ServiceStatusType.UP && status.statusType() != ServiceStatusType.HEARTBEAT)){
				LOG.debug("Not all tracked services are up [selfSinkId:{}, name:{}, missingSinkId:{}]", selfSinkId, name, sinkId);
				return false;
			}
		}
		// For each service type, traverse thru all statuses.  if one service
		// of that specific service type is up, we treat it as service type up
		for (ServiceType serviceType : handlerByServiceType.keySet()){
			boolean found = false;
			for (ServiceStatus status : statuses.values()){
				if (status != null && status.serviceType() == serviceType && (status.statusType() == ServiceStatusType.UP || status.statusType() == ServiceStatusType.HEARTBEAT)){
					found = true;
					break;
				}
			}
			if (!found){
				LOG.debug("Not all tracked service type(s) are up [selfSinkId:{}, name:{}, missingServiceType:{}]", selfSinkId, name, serviceType.name());
				return false;
			}
		}
		return true;		
	}
	
	/**
	 * Start tracking a particular sinkId
	 * 
	 * @param sinkId
	 * @param handler
	 * @return
	 */
	public ServiceStatusTracker trackSinkId(int sinkId, ServiceStatusChangeHandler handler){
		ServiceStatusChangeHandlerList existing = handlerBySinkId.get(sinkId);
		if (existing == handlerBySinkId.defaultReturnValue()){
			existing = ServiceStatusChangeHandlerList.of(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_SINK_ID);
			handlerBySinkId.put(sinkId, existing);
		}
		LOG.info("Start tracking [selfSinkId:{}, name:{}, sink:{}]", selfSinkId, name, sinkId);
		existing.add(handler);
		return this;
	}
	
	public ServiceStatusTracker untrackSinkId(int sinkId, ServiceStatusChangeHandler handler){
		ServiceStatusChangeHandlerList existing = handlerBySinkId.get(sinkId);
		if (existing == null){
			return this;
		}
		existing.remove(handler);
		return this;
	}
	
	/**
	 * Start tracking service type.  Any associated handler for this service type will
	 * be removed.
	 * 
	 * @param serviceType
	 * @return
	 */
	public ServiceStatusTracker trackServiceType(ServiceType serviceType){
		ServiceStatusChangeHandlerList existing = handlerByServiceType.get(serviceType);
		if (existing == handlerByServiceType.defaultReturnValue()){
			existing = ServiceStatusChangeHandlerList.of(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, VALID_NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_SERVICE_TYPE);
			handlerByServiceType.put(serviceType, existing);
		}
		return this;
	}
	
	/**
	 * Start tracking service type.  Handler will be replaced.
	 * @param serviceType
	 * @param handler
	 * @return
	 */
	public ServiceStatusTracker trackServiceType(ServiceType serviceType, ServiceStatusChangeHandler handler){
		ServiceStatusChangeHandlerList existing = handlerByServiceType.get(serviceType);
		if (existing == handlerByServiceType.defaultReturnValue()){
			existing = ServiceStatusChangeHandlerList.of(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, VALID_NULL_SERVICE_STATUS_CHANGE_HANDLER_FOR_SERVICE_TYPE);
			handlerByServiceType.put(serviceType, existing);
		}
		existing.add(handler);
		return this;
	}
	
	public ServiceStatusTracker untrackServiceType(ServiceType serviceType, ServiceStatusChangeHandler handler){
		ServiceStatusChangeHandlerList existing = handlerByServiceType.get(serviceType);
		if (existing == null){
			return this;
		}
		existing.remove(handler);
		return this;
	}

	public ServiceStatusTracker untrackServiceType(ServiceType serviceType){
	    handlerByServiceType.remove(serviceType);
	    return this;
	}

	
	/**
	 * Start tracking aggregated service status change.  Handler will be called on next change.
	 * Make you get the aggregated status
	 * 
	 * @param handler
	 * @return
	 */
	public ServiceStatusTracker trackAggregatedServiceStatus(AggregatedServiceStatusChangeHandler handler){
		if (!isTracking()){
			throw new IllegalStateException("nothing is tracked at the moment; therefore tracking aggregated service status does not make sense");
		}
		handlerForAllServiceUp.add(handler);
		return this;
	}
	
	public ServiceStatusTracker untrackAggregatedServiceStatus(AggregatedServiceStatusChangeHandler handler){
		handlerForAllServiceUp.remove(handler);
		return this;
	}

	public ServiceStatusTracker trackAnyServiceStatusChange(ServiceStatusChangeHandler handler){
		LOG.debug("Start tracking any service status change");
		handlerForAnyServiceStatusChange.add(handler);
		return this;
	}
	
	public ServiceStatusTracker untrackAnyServiceStatusChange(ServiceStatusChangeHandler handler){
		handlerForAnyServiceStatusChange.remove(handler);
		return this;
	}

	public ObjectCollection<ServiceStatus> statuses(){
		return this.statuses.values();
	}
	
	public ServiceStatus[] toStatusArray(){
		ServiceStatus[] result = new ServiceStatus[this.statuses.size()];
		return this.statuses.values().toArray(result);
	}
		
	public static interface ServiceStatusChangeHandler {
		void handle(ServiceStatus status);
	}
	
	public static interface AggregatedServiceStatusChangeHandler {
		void handle(boolean status);
	}

}
