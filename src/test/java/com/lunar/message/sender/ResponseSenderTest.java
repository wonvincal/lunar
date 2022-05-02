package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeEncoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class ResponseSenderTest {
	static final Logger LOG = LogManager.getLogger(ResponseSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final ResponseSbeEncoder sbeEncoder = new ResponseSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeDecodeSecurity(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;

		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(123);
		final int refClientKey = 12121;
		final int refResponseMsgSeq = 1;
		final BooleanType refIsLast = BooleanType.FALSE;
		final ResultType refResultType = ResultType.OK;

		Security refSec = Security.of(9999,
				SecurityType.CBBC, 
				"9999", 
				2, 
				99998,
				Optional.of(LocalDate.of(2016, 3, 31)),
				ServiceConstant.NULL_LISTED_DATE, 
				PutOrCall.CALL,
				OptionStyle.ASIAN,
				654321,
				123456,
				-1,
				12345,
				false,
				SpreadTableBuilder.get(SecurityType.CBBC))
				.mdsSink(MessageSinkRef.createValidNullSinkRef(1, ServiceType.MarketDataService, "test-mds"))
				.omesSink(MessageSinkRef.createValidNullSinkRef(1, ServiceType.OrderManagementAndExecutionService, "test-omes"))
				.mdsssSink(MessageSinkRef.createValidNullSinkRef(1, ServiceType.MarketDataSnapshotService, "test-mdsss"));
		
		EntityEncoder entityEncoder = EntityEncoder.of();
		
		ResponseSender.encodeResponse(sender,
				refDstSinkId, 
				buffer,
				0, 
				sbeEncoder, 
				refClientKey, 
				refIsLast, 
				refResponseMsgSeq, 
				refResultType,
				refSec,
				entityEncoder);
		
		MessageReceiver receiver = MessageReceiver.of();
		receiver.responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   ResponseSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refResponseMsgSeq, codec.responseMsgSeq());
				assertEquals(refClientKey, codec.clientKey());
				assertEquals(refIsLast, codec.isLast());
				assertEquals(refResultType, codec.resultType());
			}
		});
		receiver.securityHandlerList().add(new Handler<SecuritySbeDecoder>() {
			private void handleSecurity(SecuritySbeDecoder actual){
				SecuritySenderTest.assertSecurity(refSec, actual);				
			}
			
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   SecuritySbeDecoder actual) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				handleSecurity(actual);
			}
			@Override
			public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, SecuritySbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refClientKey, clientKey);
				handleSecurity(codec);
			}
		});
		receiver.receive(buffer, 0);
	}
	
	@Test
	public void testEncodeDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(123);
		final int refKey = 12121;
		final int refResponseMsgSeq = 2;
		final BooleanType refIsLast = BooleanType.TRUE;
		final ResultType refResultType = ResultType.FAILED;
		
		ResponseSender.encodeResponse(sender,
				refDstSinkId,
				buffer,
				0,
				sbeEncoder,
				refKey,
				refIsLast,
				refResponseMsgSeq,
				refResultType);
		
		MessageReceiver receiver = MessageReceiver.of();
		receiver.responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   ResponseSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refResponseMsgSeq, codec.responseMsgSeq());
				assertEquals(refKey, codec.clientKey());
				assertEquals(refIsLast, codec.isLast());
				assertEquals(refResultType, codec.resultType());
			}
		});
		receiver.receive(buffer, 0);
	}
}
