package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageHeaderUtil;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.ServiceStatusDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeEncoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class ServiceStatusSenderTest {
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final ServiceStatusSbeEncoder sbeEncoder = new ServiceStatusSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		final int refSinkId = 4;
		final int refSystemId = 1;
		final ServiceType refServiceType = ServiceType.AdminService;
		final ServiceStatusType refStatusType = ServiceStatusType.INITIALIZING;
		final long refModifyTime = System .nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.peekNextSeq();
		
		ServiceStatusSender.encodeServiceStatus(
				sender,
				refDstSinkId,
				buffer, 
				0, 
				sbeEncoder, 
				refSystemId,
				refSinkId, 
				refServiceType, 
				refStatusType, 
				refModifyTime,
				refSentTime,
				refHealthCheckTime);
		
		MessageReceiver receiver = MessageReceiver.of(); 
		receiver.serviceStatusHandlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(refSystemId, codec.systemId());
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
			}
		});
		receiver.receive(buffer, 0);
	}

	@Test
	public void testEncodeDecodeWithoutHeader(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		final int refSystemId = 1;
		final int refSinkId = 4;
		final ServiceType refServiceType = ServiceType.AdminService;
		final ServiceStatusType refStatusType = ServiceStatusType.INITIALIZING;
		final long refModifyTime = System .nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.peekNextSeq();
		
		int payloadLength = ServiceStatusSender.encodeServiceStatusWithoutHeader(
				buffer, 
				MessageHeaderDecoder.ENCODED_LENGTH, 
				sbeEncoder, 
				refSystemId,
				refSinkId, 
				refServiceType, 
				refStatusType, 
				refModifyTime,
				refSentTime,
				refHealthCheckTime);
		
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0, 
				ServiceStatusSbeDecoder.BLOCK_LENGTH, 
				ServiceStatusSbeDecoder.SCHEMA_VERSION, 
				ServiceStatusSbeDecoder.TEMPLATE_ID, 
				refSeq, 
				payloadLength, 
				(byte)refSenderSink.sinkId(), 
				(byte)refDstSinkId);

		ServiceStatusDecoder decoder = ServiceStatusDecoder.of();
		
		decoder.handlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(refSystemId, codec.systemId());
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
			}
		});
		decoder.decode(buffer, 
					   0, 
					   header);
	}
}
