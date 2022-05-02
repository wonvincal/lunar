package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.entity.Note;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.RequestTestUtil;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeEncoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class RequestSenderTest {
	static final Logger LOG = LogManager.getLogger(RequestSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final RequestSbeEncoder sbeEncoder = new RequestSbeEncoder();
	private final EntityEncoder entityEncoder = new EntityEncoder();
	
	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testCreateNoteRequest(){
		ImmutableListMultimap.Builder<ParameterType, Parameter> builder = ImmutableListMultimap.<ParameterType, Parameter>builder();
		builder.put(ParameterType.DESCRIPTION, Parameter.of(ParameterType.DESCRIPTION, "Test"));
		builder.put(ParameterType.ENTITY_SID, Parameter.of(ParameterType.ENTITY_SID, 12345L));
		builder.put(ParameterType.IS_ARCHIVED, Parameter.of(ParameterType.IS_ARCHIVED, BooleanType.TRUE.value()));
		builder.put(ParameterType.IS_DELETED, Parameter.of(ParameterType.IS_DELETED, BooleanType.FALSE.value()));
		builder.put(ParameterType.SERVICE_TYPE, Parameter.of(ServiceType.AdminService));
		builder.put(ParameterType.TEMPLATE_TYPE, Parameter.of(TemplateType.NOTE));
		
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final RequestType refRequestType = RequestType.CREATE;
		ImmutableListMultimap<ParameterType, Parameter> build = builder.build();
		Request refRequest = Request.createAndExtractEntityFromParameters(refSenderSink.sinkId(),
				 refRequestType,
				 build,
				 BooleanType.FALSE);
		Map<ParameterType, Parameter> items = refRequest.parameters().stream().collect(Collectors.toMap(Parameter::type, Function.identity()));
		assertFalse(items.containsKey(ParameterType.DESCRIPTION));
		assertFalse(items.containsKey(ParameterType.IS_DELETED));
		assertFalse(items.containsKey(ParameterType.ENTITY_SID));
		assertFalse(items.containsKey(ParameterType.IS_ARCHIVED));
		assertTrue(items.containsKey(ParameterType.SERVICE_TYPE));
		assertTrue(items.containsKey(ParameterType.TEMPLATE_TYPE));
	}
	
	@Test
	public void testEncodeEntityAndDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(123);
		final RequestType refRequestType = RequestType.GET;
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		Request refRequest = Request.of(refSenderSink.sinkId(),
									 refRequestType,
									 refParameters);

		long noteSid = 123;
		long entitySid = 456;
		int createDate = 20170109;
		long createTime = 8181712;
		int updateDate = createDate;
		long updateTime = createTime;
		BooleanType isDeleted = BooleanType.NULL_VAL;
		BooleanType isArchived = BooleanType.TRUE;
		String description = "test stuff";
		Note note = Note.of(noteSid, entitySid, createDate, createTime, updateDate, updateTime, isDeleted, isArchived, description);
		refRequest.entity(note);
		
		RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, sbeEncoder, refRequest, refRequest.entity().get(), entityEncoder);
		MessageReceiver receiver = MessageReceiver.of();
		receiver.requestHandlerList().add(new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   RequestSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refRequest.seq(), header.seq());
				RequestTestUtil.assertRequest(refRequest, buffer, offset, header, codec, note);
			}
		});
		receiver.receive(buffer, 0);
	}
	
	@Test
	public void testEncodeEntityAndDecodeWithBinaryCopy(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
//		final int refSeq = sender.overrideNextSeq(123);
//		final RequestType refRequestType = RequestType.GET;
//		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
//				Parameter.of(ParameterType.SECURITY_SID, 123456),
//				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
//		Request refRequest = Request.of(refSenderSink.sinkId(),
//									 refRequestType,
//									 refParameters);

		long noteSid = 123;
		long entitySid = 456;
		int createDate = 20170109;
		long createTime = 8181712;
		int updateDate = createDate;
		long updateTime = createTime;
		BooleanType isDeleted = BooleanType.NULL_VAL;
		BooleanType isArchived = BooleanType.TRUE;
		String description = "test stuff";
		Note note = Note.of(noteSid, entitySid, createDate, createTime, updateDate, updateTime, isDeleted, isArchived, description);
//		refRequest.entity(note);

		NoteSbeEncoder sbeEncoder = new NoteSbeEncoder();
		NoteSender.encodeNote(sender, refDstSinkId, buffer, 0, sbeEncoder, (int)noteSid, entitySid, createDate, createTime, updateDate, updateTime, isDeleted, isArchived, description);
		
//		RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, sbeEncoder, refRequest, refRequest.entity().get(), entityEncoder);
		MessageReceiver receiver = MessageReceiver.of();
//		receiver.requestHandlerList().add(new Handler<RequestSbeDecoder>() {
//			@Override
//			public void handle(DirectBuffer buffer, 
//							   int offset, 
//							   MessageHeaderDecoder header,
//							   RequestSbeDecoder codec) {
//				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
//				assertEquals(refDstSinkId, header.dstSinkId());
//				assertEquals(refSeq, header.seq());
//				assertEquals(refRequest.seq(), header.seq());
//				RequestTestUtil.assertRequest(refRequest, buffer, offset, header, codec, note);
//			}
//		});
		receiver.noteHandlerList().add(new Handler<NoteSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder payload) {
//				LOG.info("{} {}", payload.entitySid(), payload.description());
				assertEquals(noteSid, payload.noteSid());
				assertEquals(entitySid, payload.entitySid());
				assertEquals(createDate, payload.createDate());
				assertEquals(createTime, payload.createTime());
			}
		});
		
		receiver.receive(buffer, 0);
		
		// Encode into another buffer
		MutableDirectBuffer newBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		int payloadLength = NoteSbeDecoder.BLOCK_LENGTH + NoteSbeDecoder.descriptionHeaderLength() + note.description().length();
		MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
		headerEncoder.wrap(newBuffer, 0)
		.blockLength(NoteSbeEncoder.BLOCK_LENGTH)
		.templateId(NoteSbeEncoder.TEMPLATE_ID)
		.schemaId(NoteSbeEncoder.SCHEMA_ID)
		.version(NoteSbeEncoder.SCHEMA_VERSION)
		.senderSinkId((byte)sender.self().sinkId())
		.dstSinkId((byte)ServiceConstant.SERVICE_ID_NOT_APPLICABLE)
		.seq(1)
		.payloadLength(payloadLength);
		
//		LOG.info("payloadLength:{}", payloadLength);
		newBuffer.putBytes(MessageHeaderEncoder.ENCODED_LENGTH, buffer, MessageHeaderDecoder.ENCODED_LENGTH, payloadLength);
		receiver.receive(newBuffer, 0);
	}
	
	@Test
	public void testEncodeMessageAndDecode(){
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.overrideNextSeq(123);
		final RequestType refRequestType = RequestType.GET;
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		Request refRequest = Request.of(refSenderSink.sinkId(),
									 refRequestType,
									 refParameters);
		RequestSender.encodeRequest(sender, 
				refDstSinkId,
				buffer,
				0,
				sbeEncoder,
				refRequest,
				entityEncoder);
		MessageReceiver receiver = MessageReceiver.of();
		receiver.requestHandlerList().add(new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   RequestSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refRequest.seq(), header.seq());
				
				RequestTestUtil.assertRequest(refRequest, codec);
			}
		});
		receiver.receive(buffer, 0);
	}


}
