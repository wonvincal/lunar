package com.lunar.fsm.request;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimerService;
import com.lunar.message.Request;
import com.lunar.message.RequestTypeSetting;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;

import io.netty.util.TimerTask;

/**
 * Static references and helper methods for a type of request for a specific service
 * @author Calvin
 *
 */
public final class RequestExecutionContext {
	private static final Logger LOG = LogManager.getLogger(RequestExecutionContext.class);
	private final RequestTypeSetting setting;
	private final TimerService timerService;
	private final RequestSender sender;
	private final MessageSinkRef self;
	
	public static RequestExecutionContext of(
			MessageSinkRef self,
			TimerService timerService,
			RequestTypeSetting setting, 
			RequestSender sender){
		return new RequestExecutionContext(
				timerService, 
				setting, 
				sender,
				self);
	}
	
	RequestExecutionContext(TimerService timerService, 
						  RequestTypeSetting setting, 
						  RequestSender sender,
						  MessageSinkRef self){
		this.setting = setting;
		this.timerService = timerService;
		this.sender = sender;
		this.self = self;
	}

	public RequestTypeSetting setting(){ return setting;}
	
	void startRequestTimeoutTimer(Request request, RequestState reqState){
		TimerTask task = reqState.requestTimeoutTimerTask(); 
		if (task == null){
			task = timerService.createTimerTask(new TimeoutHandler() {
				@Override
				public void handleTimeoutThrowable(Throwable ex) {
					LOG.error("Caught throwable when procesing request timeout for [clientKey:" + request.clientKey() + "]", ex);
				}
				
				@Override
				public void handleTimeout(TimerEventSender timerEventSender) {
					long result = timerEventSender.sendTimerEvent(self, request.clientKey(), TimerEventType.REQUEST_TIMEOUT, reqState.startTimeNs(), reqState.startTimeNs() + setting.timeoutNs());
					if (result != MessageSink.OK){
						reqState.activeRequestTimeout(timerService.newTimeout(reqState.requestTimeoutTimerTask(), setting.timeoutNs(), TimeUnit.NANOSECONDS));
					}
				}
			}, "request-exec-timeout-" + request.clientKey());
			reqState.requestTimeoutTimerTask(task);
		}
		reqState.activeRequestTimeout(timerService.newTimeout(task, setting.timeoutNs(), TimeUnit.NANOSECONDS));
	}
	
	void startRetryDelayTimer(Request request, RequestState reqState){
		TimerTask task = reqState.retryDelayTimeoutTimerTask();
		if (task == null){
			task = timerService.createTimerTask(new TimeoutHandler() {
				@Override
				public void handleTimeoutThrowable(Throwable ex) {
					LOG.error("Caught throwable when procesing request timeout for [clientKey:" + request.clientKey() + "]", ex);
				}
				
				@Override
				public void handleTimeout(TimerEventSender timerEventSender) {
					long result = timerEventSender.sendTimerEvent(self, request.clientKey(), TimerEventType.REQUEST_RETRY_DELAY_TIMEOUT, reqState.startTimeNs(), reqState.startTimeNs() + setting.computeDelayNs(reqState.retryCount()));
					if (result != MessageSink.OK){
						reqState.activeRetryDelayTimeout(timerService.newTimeout(reqState.retryDelayTimeoutTimerTask(), setting.computeDelayNs(reqState.retryCount()), TimeUnit.NANOSECONDS));
					}
				}
			}, "request-retry-timeout-" + request.clientKey());
			reqState.retryDelayTimeoutTimerTask(task);
		}
		reqState.activeRetryDelayTimeout(timerService.newTimeout(task, setting.computeDelayNs(reqState.retryCount()), TimeUnit.NANOSECONDS));
	}

	/**
	 * Send request out, then start timer. 
	 * @param sink
	 * @param request
	 * @param reqState
	 * @return
	 */
	public long sendAndStartRequestTimeoutTimer(MessageSinkRef sink, Request request, RequestState reqState){
		reqState.startTimeNs(timerService.toNanoOfDay());
		long result = sender.sendRequest(sink, request);
		if (result != MessageSink.OK){
			LOG.error("Couldn't send request to sink [sinkId:{}, clientKey:{}, result:{}]", sink.sinkId(), request.clientKey(), MessageSink.getSendResultMessage(result));
			return result;
		}
		startRequestTimeoutTimer(request, reqState);			
		return result;
	}	
}
