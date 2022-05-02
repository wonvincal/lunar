package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeEncoder;
import com.lunar.message.sender.NoteSender;
import com.lunar.service.ServiceConstant;
import com.lunar.util.DateUtil;

public class NoteFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(NoteFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final NoteSbeEncoder sbeEncoder = new NoteSbeEncoder();
	private final NoteSbeDecoder sbeDecoder = new NoteSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);

	@Test
	public void testSbeFbs(){
		int noteSid = 11111;
		long entitySid = 123456789L;
		int createDate = DateUtil.toFbsDate(LocalDate.now());
		long createTime = LocalTime.now().toNanoOfDay();
		int updateDate = createDate;
		long updateTime = createTime;
		String description = "testing testing testing";
		BooleanType isDeleted = BooleanType.FALSE; 
		BooleanType isArchived = BooleanType.TRUE;
		NoteSender.encodeNoteOnly(buffer, 0, sbeEncoder, noteSid, entitySid, createDate, createTime, updateDate, updateTime, isDeleted, isArchived, description);
		sbeDecoder.wrap(buffer, 0, NoteSbeDecoder.BLOCK_LENGTH, NoteSbeDecoder.SCHEMA_VERSION);
		
		int offset = NoteFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);

		NoteFbs noteFbs = NoteFbs.getRootAsNoteFbs(builder.dataBuffer());
		assertEquals(noteFbs.noteSid(), noteSid);
		assertEquals(noteFbs.entitySid(), entitySid);
		assertEquals(noteFbs.createDate(), createDate);
		assertEquals(noteFbs.createTime(), createTime);
		assertEquals(noteFbs.updateDate(), updateDate);
		assertEquals(noteFbs.updateTime(), updateTime);
		assertEquals(noteFbs.isDeleted(), isDeleted == BooleanType.TRUE);
		assertEquals(noteFbs.isArchived(), isArchived == BooleanType.TRUE);
		assertEquals(0, noteFbs.description().compareTo(description));
	}
}

