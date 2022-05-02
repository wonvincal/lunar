package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import com.lunar.message.Echo;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EchoSbeDecoder;
import com.lunar.message.io.sbe.EchoSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class EchoSenderTest {
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final EchoSbeEncoder sbeEncoder = new EchoSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeMessageAndDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refKey = 101;
		final BooleanType refIsResponse = BooleanType.FALSE;
		final long refStartTimeNs = System.nanoTime();
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		Echo refEcho = new Echo(refSenderSink.sinkId(), refKey, refIsResponse, refStartTimeNs);
		
		EchoSender.encodeEcho(sender,
				refDstSinkId,
				buffer, 
				0, 
				sbeEncoder,
				refEcho);
				
		MessageReceiver receiver = MessageReceiver.of();
		receiver.echoHandlerList().add(new Handler<EchoSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset,
							   MessageHeaderDecoder header,
							   EchoSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refEcho.seq(), header.seq());
				assertEquals(refKey, codec.key());
				assertEquals(refIsResponse, codec.isResponse());
				assertEquals(refStartTimeNs, codec.startTime());
			}
		});
		receiver.receive(buffer, 0);
	}

	@Test
	public void testEncodeDecode(){
		final int refKey = 101;
		final BooleanType refIsResponse = BooleanType.FALSE;
		final long refStartTimeNs = System.nanoTime();
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
												refSenderSink);
		final int refSeq = sender.peekNextSeq();
		
		EchoSender.encodeEcho(sender,
				refDstSinkId,
				buffer, 
				0, 
				sbeEncoder,
				refKey, 
				refStartTimeNs, 
				refIsResponse);
				
		MessageReceiver receiver = MessageReceiver.of();
		receiver.echoHandlerList().add(new Handler<EchoSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset,
							   MessageHeaderDecoder header,
							   EchoSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refKey, codec.key());
				assertEquals(refIsResponse, codec.isResponse());
				assertEquals(refStartTimeNs, codec.startTime());
			}
		});
		receiver.receive(buffer, 0);
	}
}
