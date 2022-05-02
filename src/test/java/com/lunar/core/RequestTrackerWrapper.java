package com.lunar.core;

import com.lunar.fsm.request.RequestContext;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class RequestTrackerWrapper {
	private final RequestTracker requestTracker;
	public RequestTrackerWrapper(RequestTracker requestTracker){
		this.requestTracker = requestTracker;
	}
	public Int2ObjectOpenHashMap<RequestContext> requests(){
		return requestTracker.requests();
	}
	@Override
	public String toString() {
		return requestTracker.toString();
	}
}
