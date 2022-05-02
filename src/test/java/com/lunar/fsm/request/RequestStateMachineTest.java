package com.lunar.fsm.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.GrowthFunction;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.core.TimerService;
import com.lunar.fsm.request.RequestStateMachine.FinalStateReachedHandler;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.RequestTypeSetting;
import com.lunar.message.Response;
import com.lunar.message.ResponseHandler;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.agrona.DirectBuffer;

@RunWith(MockitoJUnitRunner.class)
public class RequestStateMachineTest {
	private RequestStateMachine fsm;
	private Request request;
	private int selfSinkId = 42;
	private int requestSeq = 12345;
	private long timeoutNs = 5000000000l;
	private long initRetryDelayNs = 10000000000l;
	private int maxRetryAttempts = 1;
	private RequestContext context;
	
	@Mock
	private MessageSink self; // mock sink, to check publications
	private MessageSinkRef selfRef = MessageSinkRef.of(self, "test-self"); 
	
	@Mock
	private MessageSink sink; // mock sink, to check publications
	private MessageSinkRef sinkRef; 

	@Mock
	private TimeoutHandlerTimerTask timerTask;
	
	@Mock
	private TimerService timerService; // mock timerService, to avoid indeterministic result 
	
	@Mock
	private Timeout timeout; // mock timeout, to avoid indeterministic result
	
	@Mock
	private Timeout retryDelayTimeout; // mock timeout, to avoid indeterministic result

	@Mock
	private ResponseHandler responseHandler; // mock responseHandler, to control when response is completed/failed/pending
	
	@Mock
	private RequestTypeSetting setting; // mock setting, to supply different settings
	
	@Mock
	private RequestSender sender;
	
	@Before
	public void setup(){
		when(sink.sinkId()).thenReturn(2);
		when(sink.publish((DirectBuffer)notNull(), anyInt(), anyInt())).thenReturn(MessageSink.OK);
		when(self.publish((DirectBuffer)notNull(), anyInt(), anyInt())).thenReturn(MessageSink.OK);
		
		when(timerService.newTimeout((TimerTask)notNull(), eq(timeoutNs), anyObject())).thenReturn(timeout);
		when(timerService.newTimeout((TimerTask)notNull(), not(eq(timeoutNs)), anyObject())).thenReturn(retryDelayTimeout);
		when(timerService.createTimerTask(isA(com.lunar.core.TimeoutHandler.class), any(String.class))).thenReturn(timerTask);
		
		when(setting.maxRetryAttempts()).thenReturn(maxRetryAttempts);
		when(setting.initDelayNs()).thenReturn(initRetryDelayNs);
		when(setting.timeoutNs()).thenReturn(timeoutNs);
		when(setting.growth()).thenReturn(GrowthFunction.CONSTANT);
		
		fsm = new RequestStateMachine(FinalStateReachedHandler.NULL_INSTANCE);
		request = Request.of(selfSinkId, RequestType.SUBSCRIBE, Parameter.NULL_LIST);
		
		RequestExecutionContext helper = RequestExecutionContext.of(selfRef, timerService, setting, sender);
		
		sinkRef = MessageSinkRef.of(sink, "test-sink");
		context = RequestContext.of(helper, sinkRef, request, responseHandler);
		
		when(sender.sendRequest(anyObject(), anyObject())).thenReturn(MessageSink.OK);
	}

	@Test
	public void testFsmInit(){
		fsm.init(context);
		assertEquals(States.IDLE, context.state());
		assertEquals(-1, context.reqState().startTimeNs());
	}
	
	// INIT to SEND_AND_WAIT
	@Test
	public void givenAtInitWhenReceivesSendThenGoToSendAndWait(){
		// given
		fsm.init(context);
		assertEquals(States.IDLE, context.state()); // verify original state
		
		// change
		fsm.process(context, StateTransitionEvent.SEND);

		// verify
		assertEquals(States.SEND_AND_WAIT, context.state()); // state changed to SEND_AND_WAIT
		assertNotEquals(-1, context.reqState().startTimeNs()); // recorded start time
		verify(sender, times(1)).sendRequest(sinkRef, request); // message published
		verify(timerService, times(1)).newTimeout(any(TimerTask.class), eq(timeoutNs), eq(TimeUnit.NANOSECONDS)); // timer created
	}
	
	// INIT | not SEND | DONE
	@Test
	public void givenAtInitWhenReceivesNotSendThenGoToDone(){
		// given
		fsm.init(context);
		
		assertEquals(States.IDLE, context.state()); // verify original state
		
		// change
		fsm.process(context, StateTransitionEvent.COMPLETE);

		// verify
		assertEquals(States.DONE, context.state()); // state changed to DONE
//		verify(responseHandler, times(1)).handleRequestFailure(eq(request), any()); // failure handler was called
	}

	// SEND_WAIT | Response -> COMPLETE | DONE
	@Test
	public void givenAtSendAndWaitWhenReceiveCompleteResponseThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenReturn(ResponseResultType.COMPLETE);
		
		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.TRUE, 1, ResultType.OK);
		fsm.process(context, response);
		
		// verify state change, response is handled
		assertEquals(States.DONE, context.state()); // state changed to DONE
//		verify(responseHandler, times(1)).handleRequestCompletion(request); // completion handler was called
		verify(timeout, times(1)).cancel(); // request timeout has been cancelled
	}
	
	// SEND_WAIT | Response -> FAIL | DONE
	@Test
	public void givenAtSendAndWaitWhenReceiveResponseAndFailThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenReturn(ResponseResultType.FAILED);
		
		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.TRUE, 1, ResultType.OK);
		fsm.process(context, response);
		
		// verify state change, response is handled
		assertEquals(States.DONE, context.state());
//		verify(responseHandler, times(1)).handleRequestFailure(eq(request), any());
		verify(timeout, times(1)).cancel(); // request timeout has been cancelled
	}
	
	// SEND_WAIT | FAIL (unexpected) | DONE
	@Test
	public void givenAtSendAndWaitWhenReceiveUnexpectedAndFailThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenReturn(ResponseResultType.FAILED);
		
		// send response
		fsm.process(context, StateTransitionEvent.SEND);
		
		// verify state change, response is handled
		assertEquals(States.DONE, context.state()); // state changed
//		verify(responseHandler, times(1)).handleRequestFailure(eq(request), any()); // failure handler is called
		verify(timeout, times(1)).cancel();
	}

	// SEND_WAIT | Response -> PENDING | SEND_WAIT
	@Test
	public void givenAtSendAndWaitWhenReceiveResponseAndPendingThenNoTransition(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenReturn(ResponseResultType.PENDING);

		assertEquals(States.SEND_AND_WAIT, context.state()); // no change

		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.FALSE, 1, ResultType.OK);
		fsm.process(context, response);
		
		// verify state change, response is handled
		assertEquals(States.SEND_AND_WAIT, context.state()); // no change
//		verify(responseHandler, times(0)).handleRequestFailure(eq(request), any());
		verify(timeout, times(0)).cancel();
	}

	// SEND_WAIT | Request Timeout -> RETRY | RETRY
	@Test
	public void givenAtSendAndWaitWhenReceiveRequestTimeoutThenGoToRetry(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
		when(setting.maxRetryAttempts()).thenReturn(1);
		when(timerService.newTimeout((TimerTask)notNull(), anyInt(), anyObject())).thenReturn(timeout);
		when(setting.computeDelayNs(0)).thenReturn(initRetryDelayNs);

		// send timeout
		fsm.process(context, TimerEventType.REQUEST_TIMEOUT);
		
		// verify state change, response is handled
		assertEquals(States.RETRY, context.state()); // state change
//		verify(responseHandler, times(0)).handleRequestFailure(eq(request), any()); // no handler has been called yet
		verify(timerService).newTimeout((TimerTask)notNull(), eq(timeoutNs), eq(TimeUnit.NANOSECONDS)); // request timeout started
		verify(timerService).newTimeout((TimerTask)notNull(), eq(initRetryDelayNs), eq(TimeUnit.NANOSECONDS)); // retry timer started
		verify(timeout, times(1)).cancel(); // request timeout is cancelled
	}

	// SEND_WAIT | Request Timeout -> FAIL | DONE
	@Test
	public void givenAtSendAndWaitWhenReceiveRequestTimeoutAndFailThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
		when(setting.maxRetryAttempts()).thenReturn(0);

		// send timeout
		fsm.process(context, TimerEventType.REQUEST_TIMEOUT);
		
		// verify state change, response is handled
		assertEquals(States.DONE, context.state()); // state change
//		verify(responseHandler, times(1)).handleRequestFailure(eq(request), any()); // no handler has been called yet
		verify(timerService, times(1)).newTimeout((TimerTask)notNull(), eq(timeoutNs), eq(TimeUnit.NANOSECONDS)); // request timeout started
		verify(timeout, times(1)).cancel(); // request timeout is cancelled
	}
	
	// RETRY | Retry Delay Timeout -> SEND | SEND_AND_WAIT
	@Test
	public void givenAtRetryWhenReceiveRetryDelayTimeoutThenGoToSendAndWait(){
		// given
		fsm.enterSpecifcState(context, States.RETRY, StateTransitionEvent.RETRY);
		
		// send timeout
		fsm.process(context, TimerEventType.REQUEST_RETRY_DELAY_TIMEOUT);

		// verify
		assertEquals(States.SEND_AND_WAIT, context.state()); // state change
		verify(retryDelayTimeout, times(1)).cancel(); // retry delay timeout was cancelled
	}

	// RETRY | Response -> COMPLETE | DONE
	@Test
	public void givenAtRetryWhenReceiveResponseCompleteThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.RETRY, StateTransitionEvent.RETRY);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenReturn(ResponseResultType.COMPLETE);
		
		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.TRUE, 1, ResultType.OK);
		fsm.process(context, response);

		// verify
		assertEquals(States.DONE, context.state()); // state change
		verify(retryDelayTimeout, times(1)).cancel(); // retry delay timeout was cancelled
//		verify(responseHandler, times(1)).handleRequestCompletion(any()); // completion handler was called
	}

	// RETRY | Response -> FAIL | DONE
	@Test
	public void givenAtRetryWhenReceiveResponseFailedThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.RETRY, StateTransitionEvent.RETRY);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenReturn(ResponseResultType.FAILED);
		
		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.TRUE, 1, ResultType.OK);
		fsm.process(context, response);

		// verify
		assertEquals(States.DONE, context.state()); // state change
		verify(retryDelayTimeout, times(1)).cancel(); // retry delay timeout was cancelled
//		verify(responseHandler, times(1)).handleRequestFailure(any(), any()); // completion handler was called
	}

	// RETRY | Response -> PENDING | DONE
	@Test
	public void givenAtRetryWhenReceiveResponsePendingThenGoToSendAndWait(){
		// given
		fsm.enterSpecifcState(context, States.RETRY, StateTransitionEvent.RETRY);
		
		// send response - isLast == FALSE
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.FALSE, 1, ResultType.OK);
		fsm.process(context, response);

		// verify
		assertEquals(States.SEND_AND_WAIT, context.state()); // state change
		verify(retryDelayTimeout, times(1)).cancel(); // retry delay timeout was cancelled
		verify(timerService, times(1)).newTimeout(any(TimerTask.class), eq(timeoutNs), eq(TimeUnit.NANOSECONDS));
		verify(sink, times(0)).publish(any(DirectBuffer.class), anyInt(), anyInt()); // message not being published
	}

	@Test
	public void givenAtDoneWhenReceiveResponseThenNoTransition(){
		// given
		fsm.enterSpecifcState(context, States.DONE, StateTransitionEvent.COMPLETE);

		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.TRUE, 1, ResultType.OK);
		fsm.process(context, response);

		// verify
		assertEquals(States.DONE, context.state()); // no state change
//		verify(responseHandler, times(1)).handleRequestCompletion(any()); // completion handler was called
	}
	
	@Test
	public void givenAtSendAndWaitWhenReceiveResponseAndCaughtExceptionThenGoToDone(){
		// given
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
//		when(responseHandler.handleResponse(any(Response.class), any(), any())).thenThrow(IllegalArgumentException.class);

		// send response
		Response response = Response.of(selfSinkId, requestSeq, BooleanType.TRUE, 1, ResultType.OK);
		fsm.process(context, response);

		// verify
		assertEquals(States.DONE, context.state()); // no state change
//		verify(responseHandler, times(1)).handleRequestFailure(eq(request), any(IllegalArgumentException.class));
	}
	
	@Test
	public void givenAtSendAndWaitWhenReceiveSendErrorThenGoToDone(){
		// given
		when(sender.sendRequest(anyObject(), anyObject())).thenReturn(MessageSink.INSUFFICIENT_SPACE);
		fsm.enterSpecifcState(context, States.SEND_AND_WAIT, StateTransitionEvent.SEND);
		
		assertEquals(States.DONE, context.state());
		assertTrue(context.result().isCompletedExceptionally());
	}
}
