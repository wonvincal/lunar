package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.service.ServiceConstant;
import com.lunar.util.DateUtil;
import com.lunar.util.ServiceTestHelper;

public class NoteSenderTest {
	static final Logger LOG = LogManager.getLogger(NoteSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	
	private MessageSender sender;
	private NoteSender noteSender;
	private final int testSinkId = 1;
	private RingBufferMessageSinkPoller testSinkPoller;
	private MessageSinkRef testSinkRef;
	private Messenger testMessenger;

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.ClientService, "test-client");
		sender = MessageSender.of(ServiceConstant.MAX_MESSAGE_SIZE, refSenderSink);
		noteSender = NoteSender.of(sender);
		
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		testSinkPoller = testHelper.createRingBufferMessageSinkPoller(testSinkId, 
				ServiceType.DashboardService, 256, "testDashboard");
		RingBufferMessageSink sink = testSinkPoller.sink();
		testSinkRef = MessageSinkRef.of(sink, "testSink");
		testMessenger = testHelper.createMessenger(sink, "testSinkMessenger");
		testMessenger.registerEvents();
		testSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				testMessenger.receive(event, 0);
				return false;
			}
		});
	}
	
	@Test
	public void testEncodeMessageAndDecode(){
		long entitySid = 123456789l;
		LocalDate date = LocalDateTime.now().toLocalDate();
		int createDate = DateUtil.toIntDate(date);
		int updateDate = createDate;
		LocalTime time = LocalDateTime.now().toLocalTime();
		long createTime = time.toNanoOfDay();
		long updateTime = createTime; 
		BooleanType isDeleted = BooleanType.FALSE;
		BooleanType isArchived = BooleanType.FALSE;
		String description = "long text, long text, long text, long text, long text, " +
				"long text, long text, long text, long text, long text, " +
				"long text, long text, long text, long text, long text, " +
				"long text, long text, long text, long text, long text, ";

		int noteSid = 11111;
		long result = noteSender.sendNote(testSinkRef,
				noteSid,
				entitySid,
				createDate,
				createTime,
				updateDate,
				updateTime,
				isDeleted,
				isArchived,
				description);
		testMessenger.receiver().noteHandlerList().add(new Handler<NoteSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder codec) {
				assertEquals(noteSid, codec.noteSid());
				assertEquals(entitySid, codec.entitySid());
				assertEquals(createDate, codec.createDate());
				assertEquals(createTime, codec.createTime());
				assertEquals(updateDate, codec.updateDate());
				assertEquals(updateTime, codec.updateTime());
				assertEquals(isDeleted, codec.isDeleted());
				assertEquals(isArchived, codec.isArchived());
				assertEquals(updateTime, codec.updateTime());
				String receivedDescription = codec.description();
				assertEquals(0, description.compareTo(receivedDescription));
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
	

	@Test
	public void testEncodeMessageAndDecodeExceedMax(){
		long entitySid = 123456789l;
		LocalDate date = LocalDateTime.now().toLocalDate();
		int createDate = DateUtil.toIntDate(date);
		int updateDate = createDate;
		LocalTime time = LocalDateTime.now().toLocalTime();
		long createTime = time.toNanoOfDay();
		long updateTime = createTime; 
		BooleanType isDeleted = BooleanType.FALSE;
		BooleanType isArchived = BooleanType.FALSE;
		
		String description = "";
		for (int i = 0; i <= NoteSender.MAX_DESC_LENGTH; i++){
			description += "0";
		}
		final String expectedDesc = description;
		
		System.out.println("desc length: " + description.length());

		int noteSid = 11111;
		long result = noteSender.sendNote(testSinkRef,
				noteSid,
				entitySid,
				createDate,
				createTime,
				updateDate,
				updateTime,
				isDeleted,
				isArchived,
				description);
		testMessenger.receiver().noteHandlerList().add(new Handler<NoteSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder codec) {
				assertEquals(noteSid, codec.noteSid());
				assertEquals(entitySid, codec.entitySid());
				assertEquals(createDate, codec.createDate());
				assertEquals(createTime, codec.createTime());
				assertEquals(updateDate, codec.updateDate());
				assertEquals(updateTime, codec.updateTime());
				assertEquals(isDeleted, codec.isDeleted());
				assertEquals(isArchived, codec.isArchived());
				assertEquals(updateTime, codec.updateTime());
				String receivedDescription = codec.description();

				// description will be truncated
				String expectedDescTrunc = expectedDesc.substring(0, NoteSender.MAX_DESC_LENGTH);
				assertEquals(0, expectedDescTrunc.compareTo(receivedDescription));
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
}
