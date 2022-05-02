package com.lunar.core;

import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.fsm.request.RequestContext;
import com.lunar.fsm.request.RequestExecutionContext;
import com.lunar.fsm.request.RequestStateMachine;
import com.lunar.message.MessageFactory;
import com.lunar.message.Request;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.TimerEvent;
import com.lunar.message.binary.Handler;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Track all outstanding requests.  Request originator should be the one who creates the object.  This tracker holds
 * reference to these objects.  
 * 
 * Notes on request object creation:
 * 1. Local actors - No new object will be created, because we will only be passing references around.  
 * 2. Remote actors - Requests will be serialized and de-serialize when crossing the akka system boundary.  Extra memory
 * 	  and cpu will be consumed.
 * 3. Local and remote services - No new object will be created because these services can use SBE decoders to examine 
 *    the content of the requests without object creation.
 * 
 * Notes on response object creation:
 * 1. Local actors - New object will be created.  Response objects should be small if the response is to actors.  Large
 *    response objects should be created on system that is not latency sensitive, because such objects will have impact
 *    on GC of that system.
 * 2. Remote actors - Response will be serialized and de-serialized when crossing the akka system boundary.  Extra
 *    memory and cpu will be consumed.
 * 3. Local and remote services - similarly to requests, no new object will be created.
 * 
 * Notes on trackable object creation:
 * A new object is created each time we call {@link sendAndTrack}.  This is not desired, but this can decouple request
 * with retrying and timeout logic.  We can create an object pool if this is going to be used in large volume.  We should 
 * create a separate class for {@link Order} handling. 
 * 
 * @author Calvin
 *
 */
public class RequestTracker {
	
	private static final Logger LOG = LogManager.getLogger(RequestTracker.class);
	private final int selfSinkId;
	// number of request is not huge, so there is no need to limit creation of request objects
	private final Int2ObjectOpenHashMap<RequestContext> requests;
	private final RequestStateMachine machine;
	private final EnumMap<RequestType, RequestExecutionContext> execContextByReqType;
	
	public static RequestTracker of(MessageSinkRef self,
			TimerService timerService,
			MessageFactory messageFactory,
			RequestSender sender){
		RequestType[] reqTypes = RequestType.values();
		EnumMap<RequestType, RequestExecutionContext> execContextByReqType = new EnumMap<RequestType, RequestExecutionContext>(RequestType.class);
		for (RequestType reqType : reqTypes){
			execContextByReqType.put(reqType,
					RequestExecutionContext.of(
							self,
							timerService,
							messageFactory.getSettings(reqType), 
							sender));
		}
		return new RequestTracker(self.sinkId(), execContextByReqType);
	}
	
	public RequestTracker(int selfSinkId, EnumMap<RequestType, RequestExecutionContext> execContextByReqType){
		this(selfSinkId, 100, 0.6f, execContextByReqType);
	}
	
	public RequestTracker(int selfSinkId, 
			int expected, 
			float load,
			EnumMap<RequestType, RequestExecutionContext> execContextByReqType){
		requests = new Int2ObjectOpenHashMap<RequestContext>(expected, load);
		machine = new RequestStateMachine(this::onRequestDone);
		this.execContextByReqType = execContextByReqType;
		this.selfSinkId = selfSinkId;
	}
	
	private void onRequestDone(RequestContext context){
		RequestContext result = requests.remove(context.request().clientKey());
		if (result == requests.defaultReturnValue()){
			LOG.error("request is done but cannot be found in tracker - {}.  this may be a concurrent bug, are you running this with a single-writer only?", context.request());
		}
	}
	
	/**
	 * 
	 * @param sink
	 * @param request
	 * @param handler To process each response
	 * @return To track when a request has been completed / failed
	 */
	public CompletableFuture<Request> sendAndTrack(MessageSinkRef sink, Request request, ResponseHandler handler){
		RequestContext context =  RequestContext.of(execContextByReqType.get(request.requestType()), sink, request, handler);
		RequestContext previous = requests.put(request.clientKey(), context);
		if (previous == requests.defaultReturnValue()){
			machine.init(context);
			machine.start(context); // publish message during start of fsm
			return context.result();
		}
		else {
			requests.put(request.clientKey(), previous);
			context.result().completeExceptionally(new IllegalArgumentException("Duplicate requests of same clientKey [" + request.toString() + "]"));
			return context.result();
		}
	}
	
	public CompletableFuture<Request> sendAndTrack(MessageSinkRef sink, Request request){
		return sendAndTrack(sink,request, ResponseHandler.NULL_HANDLER);
	} 

	/**
	 * Handle object based response
	 * 
	 * @param response
	 */
	public void onMessage(Response response){
		int key = response.clientKey();
		RequestContext reqContext = requests.get(key);
		if (reqContext == null){
			return;
		}
		machine.process(reqContext, response);
	}
	
	public void onMessage(TimerEvent timerEvent){
		int key = timerEvent.key();
		RequestContext reqContext = requests.get(key);
		if (reqContext == null){
			return;
		}
		if (timerEvent.timerEventType() != TimerEventType.REQUEST_RETRY_DELAY_TIMEOUT && 
			timerEvent.timerEventType() != TimerEventType.REQUEST_TIMEOUT){
			LOG.error("received unexpected timer event {} for request {} | sinkId:{}", timerEvent, reqContext.request(), this.selfSinkId);
			return;
		}
		machine.process(reqContext, timerEvent.timerEventType());
	}
	
	private final Handler<ResponseSbeDecoder> responseHandler = new Handler<ResponseSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder response) {
			RequestContext requestContext = requests.get(response.clientKey());
			if (requestContext != null){
				machine.process(requestContext, buffer, offset, response);
				return;
			}
			LOG.error("received response for a request that we have no reference to | selfSinkId:{}, clientKey:{}", selfSinkId, response.clientKey());
		}
	};
	
	public Handler<ResponseSbeDecoder> responseHandler(){
		return responseHandler;
	}
	
	private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder timerEvent) {
			int key = timerEvent.clientKey();
			RequestContext reqContext = requests.get(key);
			if (timerEvent.timerEventType() != TimerEventType.REQUEST_RETRY_DELAY_TIMEOUT && 
				timerEvent.timerEventType() != TimerEventType.REQUEST_TIMEOUT){
				LOG.error("received unexpected timer event {} for request {}", timerEvent, reqContext.request());
				return;
			}
			machine.process(reqContext, timerEvent.timerEventType());
		}
	};

	public Handler<TimerEventSbeDecoder> timerEventHandler(){
		return timerEventHandler;
	}
	
	public int numOutstandings(){
		return requests.size();
	}

	Int2ObjectOpenHashMap<RequestContext> requests(){
		return requests;
	}
}
