package com.lunar.fsm.request;

import java.util.concurrent.CompletableFuture;

import com.lunar.message.Request;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;

import org.agrona.DirectBuffer;

/**
 * Since the life cycle of each request can be represented by a fsm, this class
 * is created as a wrapper to hold all the relevant objects. 
 * @author Calvin
 *
 */
public final class RequestContext {
	private final RequestExecutionContext execContext; // execution context for the request
	private State state;
	private final RequestState reqState; // request state	
	private final Request request;
	private final MessageSinkRef sink; // pre-computed sink ref
	private final CompletableFuture<Request> result;
	private final ResponseHandler responseHandler;
	
	public static RequestContext of(RequestExecutionContext context, MessageSinkRef sink, Request request, ResponseHandler responseHandler){
		return new RequestContext(context, sink, new RequestState(), request, responseHandler);
	}
	
	private RequestContext(RequestExecutionContext context, MessageSinkRef sink, RequestState requestState, Request request, ResponseHandler responseHandler){
		this.execContext = context;
		this.sink = sink;
		this.reqState = requestState;
		this.request = request;
		this.result = new CompletableFuture<Request>();
		this.responseHandler = responseHandler;
	}
	
	public RequestExecutionContext execContext(){
		return this.execContext;
	}
	
	public RequestState reqState(){
		return this.reqState;
	}
	
	public Request request(){
		return this.request;
	}
	
	public ResponseHandler responseHandler(){
		return this.responseHandler;
	}
	
	public long sendAndStartRequestTimeoutTimer(){
		return execContext.sendAndStartRequestTimeoutTimer(sink, request, reqState);		
	}

	public void restartRequestTimeoutTimer(){
		execContext.startRequestTimeoutTimer(request, reqState);		
	}

	public void startRetryDelayTimer(){
		execContext.startRetryDelayTimer(request, reqState);		
	}
	
	public void cancelActiveRequestTimeout(){
		this.reqState.cancelActiveRequestTimeout();
	}
	
	public void cancelActiveRetryDelayTimeout(){
		this.reqState.cancelActiveRetryDelayTimeout();
	}
	
	public boolean canRetry(){
		return this.reqState.retryCount() < execContext.setting().maxRetryAttempts();
	}
	
	public MessageSinkRef sink(){
		return this.sink;
	}
	
	State state(){ return state; }
	
	State state(State state) {
		return (this.state = state);
	}

	public CompletableFuture<Request> result(){
		return this.result;
	}
	
	public void handleResponse(Response response){
		this.responseHandler.handleResponse(response);
	}
	
	public void handleResponse(DirectBuffer buffer, int offset, ResponseSbeDecoder response){
		this.responseHandler.handleResponse(buffer,
											offset, 
											response);
	}
}
