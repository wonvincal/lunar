package com.lunar.core;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.message.Command;
import com.lunar.message.CommandAckHandler;
import com.lunar.message.Parameter;
import com.lunar.message.binary.MessageHeaderUtil;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeEncoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.CommandAckSender;
import com.lunar.message.sender.CommandSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.TestHelper;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

@RunWith(MockitoJUnitRunner.class)
public class CommandTrackerTest {
	private CommandTracker tracker;
	private int selfSinkId = 42;
	private int sinkSinkId = 43;
//	private int seq = 1001;
//	private int key = 2002;
	private long commandTimeoutNs = 5000000000l;
	
	@Mock
	private MessageSink self;
	private MessageSinkRef selfRef;

	@Mock
	private MessageSink sink;
	private MessageSinkRef sinkRef;
	
	@Mock
	private TimerService timerService;
	
	@Mock
	private CommandAckHandler handler;
	
	@Mock
	private Timeout timeout;
	
	@Mock
	private CommandSender sender;
	
	private MutableDirectBuffer buffer;
	
	@Before
	public void setup(){
		tracker = CommandTracker.of(selfRef, timerService, commandTimeoutNs, sender);
		//.registerHandler(handler);
		when(self.publish(any(DirectBuffer.class), anyInt(), anyInt())).thenReturn(MessageSink.OK);
		when(timerService.newTimeout(any(TimerTask.class), anyLong(), any())).thenReturn(timeout);
		TestHelper.mock(self, selfSinkId, ServiceType.RefDataService);
		selfRef = MessageSinkRef.of(self);
		TestHelper.mock(sink, sinkSinkId, ServiceType.RefDataService);
		sinkRef = MessageSinkRef.of(sink);
		TestHelper helper = TestHelper.of();
		buffer = helper.createDirectBuffer();
		when(sender.sendCommand(anyObject(), anyObject())).thenReturn(MessageSink.OK);
	}
	
	@Test
	public void givenEmptyWhenSendAndTrackThenSendOut(){
		// given - empty tracker
		
		// action
		Command command = Command.of(selfSinkId, 2, CommandType.GATHER_PERF_STAT, Parameter.NULL_LIST);
		tracker.sendAndTrack(sinkRef, command);
		
		// verify
		verify(sender, times(1)).sendCommand(sinkRef, command);
//		verify(sink, times(1)).publish(any(DirectBuffer.class), anyInt(), anyInt()); // check send
		assertEquals(1, tracker.numOutstandings()); // check outstanding
		verify(timerService, times(1)).newTimeout(any(TimerTask.class), eq(commandTimeoutNs), eq(TimeUnit.NANOSECONDS)); // started timer
	}
	
	@Test
	public void givenCommandSentWhenCommandTimeoutThenHandlerIsCalled(){
		// given
		Command command = Command.of(selfSinkId, 2, CommandType.GATHER_PERF_STAT, Parameter.NULL_LIST);
		tracker.sendAndTrack(sinkRef, command);
		
		// action
//		tracker.onEvent(TimerEvent.of(command, -1, System.nanoTime()));
		
		// verify
//		verify(sink, times(1)).publish(any(DirectBuffer.class), anyInt(), anyInt()); // check send
//		assertEquals(0, tracker.numOutstandings()); // check outstanding
//		verify(handler, times(1)).handleCommandFailure(eq(command), eq(CommandAckType.TIMEOUT));
	}
	
	@Test
	public void givenCommandSentWhenCommandAckIsReceivedThenCallHandlerAndCancelTimeout(){
		// given
		Command command = Command.of(selfSinkId, 2, CommandType.GATHER_PERF_STAT, Parameter.NULL_LIST);
		CompletableFuture<Command> future = tracker.sendAndTrack(sinkRef, command);

		// action
		CommandAckSbeEncoder encoder = new CommandAckSbeEncoder();
		int payloadLength = CommandAckSender.encodeCommandAckOnly(buffer, 
				MessageHeaderEncoder.ENCODED_LENGTH, 
				encoder, 
				command.clientKey(), 
				CommandAckType.OK, 
				command.commandType());
		
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0, 
				CommandAckSbeDecoder.BLOCK_LENGTH, 
				CommandAckSbeDecoder.SCHEMA_VERSION, 
				CommandAckSbeDecoder.TEMPLATE_ID, 
				1, 
				payloadLength, 
				(byte)selfSinkId, 
				(byte)sinkSinkId);
		
		CommandAckSbeDecoder decoder = new CommandAckSbeDecoder();
		decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, CommandAckSbeDecoder.BLOCK_LENGTH, CommandAckSbeDecoder.SCHEMA_VERSION);
		tracker.commandHandler().handle(buffer, 0, header, decoder);

		final AtomicInteger expectedCount = new AtomicInteger(1);
		future.whenComplete(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command t, Throwable u) {
				if (t.ackType() == CommandAckType.OK && t.clientKey() == command.clientKey()){
					expectedCount.decrementAndGet();
				}
				else {
					expectedCount.set(Integer.MIN_VALUE);
				}
			}
		});
		
		// verify
		assertEquals(0, tracker.numOutstandings());
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenCommandSentWhenCommandNackIsReceivedThenCallHandlerAndCancelTimeout(){
		// given
		final int clientKey = 2222;
		Command command = Command.of(selfSinkId, clientKey, CommandType.GATHER_PERF_STAT, Parameter.NULL_LIST);
		CompletableFuture<Command> future = tracker.sendAndTrack(sinkRef, command);

		// action
		CommandAckSbeEncoder encoder = new CommandAckSbeEncoder();
		int payloadLength = CommandAckSender.encodeCommandAckOnly(buffer, 
				MessageHeaderEncoder.ENCODED_LENGTH, 
				encoder, 
				command.clientKey(), 
				CommandAckType.NOT_SUPPORTED, 
				command.commandType());
		
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0, 
				CommandAckSbeDecoder.BLOCK_LENGTH, 
				CommandAckSbeDecoder.SCHEMA_VERSION, 
				CommandAckSbeDecoder.TEMPLATE_ID, 
				1, 
				payloadLength, 
				(byte)selfSinkId, 
				(byte)sinkSinkId);
		
		CommandAckSbeDecoder decoder = new CommandAckSbeDecoder();
		decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH, CommandAckSbeDecoder.BLOCK_LENGTH, CommandAckSbeDecoder.SCHEMA_VERSION);
		tracker.commandHandler().handle(buffer, 0, header, decoder);

		final AtomicInteger expectedCount = new AtomicInteger(1);
		future.whenComplete(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command t, Throwable u) {
				if (t.ackType() == CommandAckType.NOT_SUPPORTED && t.clientKey() == command.clientKey()){
					expectedCount.decrementAndGet();
				}
				else {
					expectedCount.set(Integer.MIN_VALUE);					
				}
			}
		});		
		// verify
		assertEquals(0, tracker.numOutstandings());
		assertEquals(0, expectedCount.get());
	}

	@Test
	public void testTimerTaskForBinarySink(){
		// given
//		final int clientKey = 2222;
//		Command command = Command.of(selfSinkId, clientKey, CommandType.GATHER_PERF_STAT, Parameter.NULL_LIST);

		// action
//		TimerTask task = tracker.timerTaskBuilder.build(command, System.nanoTime());
//		try {
//			task.run(timeout);
//		}
//		catch (Exception e) {
//   		}
		
		// verify
//		verify(self, times(1)).publish(any(DirectBuffer.class), anyInt(), anyInt());
	}

	@Test
	public void testTimerTaskForObjectSink(){    
		// given
//		final int clientKey = 2222;
//		tracker = CommandTracker.of(selfRef, timerService, commandTimeoutNs, sender);
		//.registerHandler(handler);
//		Command command = Command.of(selfSinkId, clientKey, CommandType.GATHER_PERF_STAT, Parameter.NULL_LIST);

		// action
/*		TimerTask task = tracker.timerTaskBuilder.build(command, System.nanoTime());
		try {
			task.run(timeout);
		}
		catch (Exception e) {
   		}
*/		
		// verify
//		verify(self, times(1)).tell(any(TimerEvent.class));
	}
}
