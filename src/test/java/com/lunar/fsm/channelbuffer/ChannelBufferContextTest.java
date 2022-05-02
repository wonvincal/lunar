package com.lunar.fsm.channelbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.EventHandler;
import com.lunar.fsm.channelbuffer.MessageAction.MessageActionType;
import com.lunar.message.binary.MessageHeaderUtil;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.service.ServiceConstant;

@RunWith(MockitoJUnitRunner.class)
public class ChannelBufferContextTest {
	@Mock
	private ChannelMissingMessageRequester messageRequester;
	
	private final int messageBufferCapacity = 1024;
	private final int snapshotBufferCapacity = 2048;
	private ChannelBufferContext context;
	
	@Before
	public void setup(){
		context = ChannelBufferContext.of("order", 
				1, 
				messageBufferCapacity, 
				snapshotBufferCapacity, 
				new EventHandler<ChannelEvent>() {
					@Override
					public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
					}
				});		
	}
	
	@Test
	public void testAccessAfterInit() throws Exception{
		context.init(false);
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSeq());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSnapshotSeq());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.channelSeqOfCompletedSnapshot());
		assertTrue(context.flush());
		assertEquals(States.INITIALIZATION, context.state());
	}

	@Test(expected=NullPointerException.class)
	public void testAccessBeforeInit() throws Exception{
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSeq());
		
		MessageAction action = MessageAction.of();
		int channelId = 1;
		long channelSeq = 201012;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		int templateId = OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID;
		
		int payloadLength = OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, 
				OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onMessage(channelId, 
				channelSeq, 
				buffer, 
				0, 
				header,
				templateId, 
				action);
		
		try {
		context.onMessage(channelId, 
				channelSeq + 1, 
				buffer, 
				0, 
				header,
				templateId, 
				action);
		}
		catch (Exception e){
			assertTrue(false);
		}
		
		// When
		context.onMessage(channelId, 
				channelSeq + 3, 
				buffer, 
				0, 
				header,
				templateId, 
				action);
	}
	
	@Test
	public void givenInitWhenMessageArrivesAdvanceToPassThru(){
		// Given
		int channelId = 1;
		long channelSeq = 201;
		int templateId = OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID;
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		
		// When and then
		advanceToPassThru(context, channelId, channelSeq, templateId, senderSinkId, dstSinkId, seq);
	}

	@Test(expected=IllegalStateException.class)
	public void givenInitWhenMessageArrivesFromTwoDifferntSendersThenException(){
		// Given
		context.init(false);
		assertTrue(context.isReady());
		assertEquals(States.INITIALIZATION, context.state());
		
		MessageAction action = MessageAction.of();
		int channelId = 1;
		long channelSeq = 201;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		int templateId = OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID;
		
		int payloadLength = OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, 
				OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onMessage(channelId, 
				channelSeq, 
				buffer, 
				0, 
				header,
				templateId, 
				action);

		MessageHeaderDecoder header2 = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, 
				OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq + 1, 
				payloadLength, 
				(byte)(senderSinkId + 1), 
				(byte)dstSinkId);
		
		// When
		context.onMessage(channelId, 
				channelSeq + 1, 
				buffer, 
				0, 
				header2,
				templateId , 
				action);

		// Then
		assertEquals(channelSeq + 1, context.expectedNextSeq());
		assertEquals(MessageActionType.SEND_NOW, action.actionType());
		assertEquals(States.PASS_THRU, context.state());
	}
	
	@Test
	public void givenInitWhenIndividualSnapshotArrivesThenDropTheSnapshot(){
		// Given
		context.init(false);
		assertTrue(context.isReady());
		assertEquals(States.INITIALIZATION, context.state());

		MessageAction action = MessageAction.of();
		int channelId = 1;
		long channelSeq = 201;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		int templateId = OrderSbeDecoder.TEMPLATE_ID;
		
		int payloadLength = OrderSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderSbeDecoder.BLOCK_LENGTH, 
				OrderSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onIndividualSnapshot(channelId, 
				channelSeq, 
				buffer, 
				0, 
				header,
				templateId, 
				action);

		// Then
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSeq());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSnapshotSeq());
		assertEquals(MessageActionType.DROPPED_NO_ACTION, action.actionType());
		assertEquals(States.INITIALIZATION, context.state());
	}
	
	@Test
	public void givenInitWhenSequentialSnapshotArrivesThenDropTheSnapshot(){
		// Given
		context.init(false);
		assertTrue(context.isReady());
		assertEquals(States.INITIALIZATION, context.state());

		MessageAction action = MessageAction.of();
		int channelId = 1;
		long channelSeq = 201;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		int templateId = OrderSbeDecoder.TEMPLATE_ID;
		int snapshotSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
		BooleanType isLast = BooleanType.TRUE;
		
		int payloadLength = OrderSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderSbeDecoder.BLOCK_LENGTH, 
				OrderSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onSequentialSnapshot(channelId, 
				channelSeq, 
				snapshotSeq, 
				isLast, 
				buffer, 
				0,
				header,
				templateId, 
				action);

		// Then
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSeq());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSnapshotSeq());
		assertEquals(MessageActionType.DROPPED_NO_ACTION, action.actionType());
		assertEquals(States.INITIALIZATION, context.state());
	}
	
	private static void advanceToPassThru(ChannelBufferContext context, 
			int channelId,
			long channelSeq,
			int templateId,
			int senderSinkId,
			int dstSinkId,
			int seq){
		context.init(false);
		assertTrue(context.isReady());
		assertEquals(States.INITIALIZATION, context.state());
		
		MessageAction action = MessageAction.of();
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		
		int payloadLength = OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, 
				OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onMessage(channelId, 
				channelSeq, 
				buffer, 
				0, 
				header,
				templateId, 
				action);

		// Then
		assertEquals(channelSeq + 1, context.expectedNextSeq());
		assertEquals(MessageActionType.SEND_NOW, action.actionType());
		assertEquals(States.PASS_THRU, context.state());
	}
	
	@Test
	public void givenPassThruWhenNextMessageArrivesThenSendImmediately(){
		// Given
		int channelId = 1;
		long channelSeq = 201;
		int templateId = OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID;
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		advanceToPassThru(context, channelId, channelSeq, templateId, senderSinkId, dstSinkId, seq);

		MessageAction action = MessageAction.of();
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));

		int count = 100;
		for (int i = 1; i <= count; i++){
			int payloadLength = OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
			MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
					0,
					OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, 
					OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION, 
					templateId, 
					seq + 1, 
					payloadLength, 
					(byte)senderSinkId, 
					(byte)dstSinkId);
			
			context.onMessage(channelId, 
					channelSeq + i, 
					buffer, 
					0, 
					header,
					templateId, 
					action);

			assertEquals(channelSeq + i + 1, context.expectedNextSeq());
			assertEquals(MessageActionType.SEND_NOW, action.actionType());
			assertEquals(States.PASS_THRU, context.state());
		}
	}
	
	@Test
	public void givenPassThruWhenNextIndividualSnapshotArrivesThenDrop(){
		// Given
		int channelId = 1;
		long channelSeq = 201;
		int templateId = OrderSbeDecoder.TEMPLATE_ID;
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		advanceToPassThru(context, channelId, channelSeq, templateId, senderSinkId, dstSinkId, seq);

		MessageAction action = MessageAction.of();
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		
		int payloadLength = OrderSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderSbeDecoder.BLOCK_LENGTH, 
				OrderSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onIndividualSnapshot(channelId, 
				channelSeq, 
				buffer, 
				0, 
				header,
				templateId, 
				action);
		
		assertEquals(channelSeq + 1, context.expectedNextSeq());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSnapshotSeq());
		assertEquals(MessageActionType.DROPPED_NO_ACTION, action.actionType());
		assertEquals(States.PASS_THRU, context.state());
	}
	
	@Test
	public void givenPassThruWhenNextSequentialSnapshotArrivesThenDrop(){
		// Given
		int channelId = 1;
		long channelSeq = 201;
		int templateId = OrderSbeDecoder.TEMPLATE_ID;
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		advanceToPassThru(context, channelId, channelSeq, templateId, senderSinkId, dstSinkId, seq);

		MessageAction action = MessageAction.of();
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));

		int snapshotSeq = 1;
		BooleanType isLast = BooleanType.TRUE;

		int payloadLength = OrderSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderSbeDecoder.BLOCK_LENGTH, 
				OrderSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		// When
		context.onSequentialSnapshot(channelId, 
				channelSeq, 
				snapshotSeq, 
				isLast, 
				buffer, 
				0, 
				header, 
				templateId, 
				action);

		assertEquals(channelSeq + 1, context.expectedNextSeq());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.expectedNextSnapshotSeq());
		assertEquals(MessageActionType.DROPPED_NO_ACTION, action.actionType());
		assertEquals(States.PASS_THRU, context.state());
	}

	@Test
	public void givenPassThruWhenMessageArrivesWithGapDetectedThenStoreMessageAndMoveToBufferingState(){
		// Given
		context.messageRequester(messageRequester);
		int channelId = 1;
		long channelSeq = 201;
		int templateId = OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID;
		int senderSinkId = 2;
		int dstSinkId = 3;
		int seq = 12345;
		advanceToPassThru(context, channelId, channelSeq, templateId, senderSinkId, dstSinkId, seq);
		
		MessageAction action = MessageAction.of();
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));

		long arrivedChannelSeq = channelSeq + 2;
		
		int payloadLength = OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0,
				OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, 
				OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION, 
				templateId, 
				seq, 
				payloadLength, 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		context.onMessage(channelId, 
				arrivedChannelSeq, 
				buffer, 
				0, 
				header,
				templateId, 
				action);

		assertEquals(channelSeq + 1, context.expectedNextSeq());
		assertEquals(MessageActionType.STORED_NO_ACTION, action.actionType());
		assertEquals(States.BUFFERING, context.state());
		assertEquals(ServiceConstant.NULL_SEQUENCE, context.channelSeqOfCompletedSnapshot());
		assertFalse(context.messageBuffer().isEmpty());
		assertEquals(channelSeq + 1, context.messageBuffer().sequence());
		verify(messageRequester, times(1)).request(any(), anyInt(), anyLong(), anyLong(), anyLong(), anyInt());
	}
}
