package com.lunar.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.lunar.entity.Entity;
import com.lunar.entity.Note;
import com.lunar.message.binary.NoteDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.service.ServiceConstant;

public class RequestTestUtil {
	public static Request createRequest(int senderSinkId, int numParameters){
		RequestType requestType = RequestType.GET;
		Builder<Parameter> builder = new ImmutableList.Builder<Parameter>();
		for (int i = 99999; i < 99999 + numParameters; i++){
			builder.add(Parameter.of(ParameterType.SECURITY_SID, i));
		}
		int clientKey = 99999;
		Request request = Request.of(senderSinkId, requestType, builder.build()).clientKey(clientKey);
		return request;
	}
	
	public static void assertRequest(Request request, RequestSbeDecoder requestSbe){
		assertEquals(request.clientKey(), requestSbe.clientKey());
		assertEquals(request.requestType(), requestSbe.requestType());
		assertParameters(request.parameters(), requestSbe.parameters());
	}

	public static void assertRequest(Request request, DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder requestSbe, Entity entity){
		assertEquals(request.clientKey(), requestSbe.clientKey());
		assertEquals(request.requestType(), requestSbe.requestType());
		
		// Note: parameterBinaryBlockLength and parameterBinaryTemplateId can be parsed in any order
		short binaryBlockLength = requestSbe.parameterBinaryBlockLength();
		byte binaryTemplateId = requestSbe.parameterBinaryTemplateId();
		assertEquals(entity.blockLength(), binaryBlockLength);
		assertEquals(entity.templateType().value(), binaryTemplateId);
//		System.out.println("Embedded entity is " + TemplateType.get(binaryTemplateId));

		ParametersDecoder parameters = requestSbe.parameters();
		int parametersCount = parameters.count();
		assertParameters(request.parameters(), parameters);

		// Note: parameterBinaryDataLength must be parsed after parameters
//		int binaryDataLength = requestSbe.parameterBinaryDataLength();


		// Find out where data begins
		int offsetToEntity = offset + MessageHeaderDecoder.ENCODED_LENGTH + RequestSbeDecoder.BLOCK_LENGTH + 
				RequestSbeDecoder.ParametersDecoder.sbeHeaderSize() + 
				parametersCount * RequestSbeDecoder.ParametersDecoder.sbeBlockLength() + 
				RequestSbeDecoder.parameterBinaryDataHeaderLength();
//		System.out.println("Expected note: " + entity);
//		System.out.println("binaryBlockLength(expect:38)" + binaryBlockLength + ", binaryDataLength(expect:49):" + binaryDataLength);
//		System.out.println("offset: " + offset + ", MessageHeaderDecoder.ENCODED_LENGTH: " + MessageHeaderDecoder.ENCODED_LENGTH);
//		System.out.println("RequestSbeDecoder.BLOCK_LENGTH: " + RequestSbeDecoder.BLOCK_LENGTH + ", RequestSbeDecoder.ParametersDecoder.sbeHeaderSize(): " + RequestSbeDecoder.ParametersDecoder.sbeHeaderSize());
//		System.out.println("requestSbe.parameters().count(): " + parametersCount + ", RequestSbeDecoder.ParametersDecoder.sbeBlockLength()(expect:13): " + RequestSbeDecoder.ParametersDecoder.sbeBlockLength());
//		System.out.println("OffsetToEntity: " + offsetToEntity);
//		System.out.println("Decode note: noteSid: " + noteDecoder.sbe().noteSid() + ", entitySid: " + noteDecoder.sbe().entitySid() + ", offset: " + offsetToEntity);
		
		TemplateType templateType = TemplateType.get(binaryTemplateId);
		switch (templateType){
		case NOTE:
			final NoteDecoder noteDecoder = NoteDecoder.of();
			noteDecoder.sbe().wrap(buffer, 
					offsetToEntity, 
					binaryBlockLength,
					NoteSbeDecoder.SCHEMA_VERSION);
			assertNote(entity, noteDecoder.sbe());
			break;
		default:
			assertTrue("Unexpected template type: " + templateType.name(), false);
			break;
		}
	}
	
	public static void assertNote(Entity entity, NoteSbeDecoder noteSbe){
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		assertTrue("Entity is not a Note", entity instanceof Note);
		Note note = (Note)entity;
		assertEquals(note.entitySid(), noteSbe.entitySid());
		assertEquals(note.sid(), noteSbe.noteSid());
		assertEquals(note.isArchived(), noteSbe.isArchived());
		assertEquals(note.isDeleted(), noteSbe.isDeleted());
		
		int length = noteSbe.descriptionLength();
		noteSbe.getDescription(stringBuffer, 0, stringBuffer.capacity());
		String desc = new String(stringBuffer.byteArray(), 0, length);
//		String desc = stringBuffer.getStringUtf8(0, length);
//		String desc = stringBuffer.getStringWithoutLengthUtf8(0, length);
//		String desc = noteSbe.description();
//		String desc = new String(stringBuffer.byteArray(), 0, length);
//		System.out.println("orig: " + note.description() + ", extracted desc: " + desc + ", length: " + length);
		
		assertEquals("Description is different", 0, desc.compareTo(note.description()));
	}

	public static void assertParameters(List<Parameter> parameters, ParametersDecoder parameterSbes){
		assertEquals(parameters.size(), parameterSbes.count());
		int i = 0;
		for (ParametersDecoder parameterSbe : parameterSbes){
			Parameter parameter = parameters.get(i);
			assertParameter(parameter.type(), parameter.value(), parameter.valueLong(), parameterSbe);
			i++;
		}
	}

	public static void assertParameter(ParameterType expectedType, String expectedValue, Long expectedValueLong, ParametersDecoder actual){
		assertEquals(expectedType, actual.parameterType());
		if (expectedValue == null){
			assertEquals(ParametersDecoder.parameterValueNullValue(), actual.parameterValue(0));
		}
		else {
			ByteBuffer buffer = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
			actual.getParameterValue(buffer.array(), 0);
			String string = new String(buffer.array(), 0, ParametersDecoder.parameterValueLength());
			assertEquals(expectedValue, string);
		}
		if (expectedValueLong == null){
			assertEquals(ParametersDecoder.parameterValueLongNullValue(), actual.parameterValueLong()); 
		}
		else{
			assertEquals(expectedValueLong.longValue(), actual.parameterValueLong());
		}
	}

}
