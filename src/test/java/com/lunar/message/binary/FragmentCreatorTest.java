package com.lunar.message.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.FragmentInitSbeDecoder;
import com.lunar.message.io.sbe.FragmentInitSbeEncoder;
import com.lunar.message.io.sbe.FragmentSbeDecoder;
import com.lunar.message.io.sbe.FragmentSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.NewOrderRequest;
import com.lunar.service.ServiceConstant;
import com.lunar.util.BitUtil;
import com.lunar.util.LogUtil;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class FragmentCreatorTest {	
	static final Logger LOG = LogManager.getLogger(FragmentCreatorTest.class);
	private FragmentCreator creator;
	private MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private FragmentInitSbeDecoder fragmentInitSbeDecoder = new FragmentInitSbeDecoder();
	private FragmentSbeDecoder fragmentSbeDecoder = new FragmentSbeDecoder();
	private MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
	private EntityEncoder entityEncoder = new EntityEncoder();
	
	@Test
	public void testCreate(){
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		creator = FragmentCreator.of(fragmentSize);
		int numBuffers = creator.buffers().length;
		assertEquals((int)Math.ceil((double)ServiceConstant.MAX_MESSAGE_SIZE / creator.fragmentPayloadSize()), numBuffers);
		assertEquals(0, creator.peekSeq());
		assertEquals(0, creator.limit());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testZeroFrameSize(){
		creator = FragmentCreator.of(0);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testWrapAroundZeroLength(){
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		creator = FragmentCreator.of(fragmentSize);
		int expectedSeq = creator.peekSeq();
		creator.wrap(buffer, 0, 0);
		
		fragmentInitSbeDecoder.wrap(creator.buffers()[0], 0, FragmentInitSbeDecoder.BLOCK_LENGTH, FragmentInitSbeDecoder.SCHEMA_VERSION);
		assertEquals(expectedSeq, fragmentInitSbeDecoder.fragmentSeq());
		assertEquals(0, fragmentInitSbeDecoder.payloadLength());
		assertEquals(0, creator.limit());
	}

	@Test
	public void testSizeLessThanFragmentSize(){
		final int systemId = 1;
		final int selfSinkId = 11;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE + 36;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);
		creator = FragmentCreator.of(fragmentSize);
		
		// Given - create a message
		int clientKey = 1234567;
		MessageSinkRef owner = MessageSinkRef.createValidNullSinkRef(1, ServiceType.AdminService, "test-admin"); 
		Security security = Security.of(55555l, SecurityType.CBBC, "65565", 77777, 44444l, Optional.of(LocalDate.now()), ServiceConstant.NULL_LISTED_DATE, PutOrCall.CALL, OptionStyle.AMERICAN, 100000, 1000, 1, 1000, true, SpreadTableBuilder.get(SecurityType.CBBC));
		NewOrderRequest request = NewOrderRequest.of(clientKey, owner, security, OrderType.LIMIT_ORDER, 1000, Side.BUY, TimeInForce.DAY, BooleanType.FALSE, 100000, 10000, 111);
		int dstSinkId = 3;
		int encodedLength = OrderSender.encodeNewOrder(sender, dstSinkId, buffer, 0, new NewOrderRequestSbeEncoder(), request);
		int expectedSeq = creator.peekSeq();

		// When
		assertTrue("encodedLength is unexpected to be <= fragmentInitPayloadSize [encodedLength:" + encodedLength + ", fragmentInitPayloadSize:" + creator.fragmentInitPayloadSize() + "]", encodedLength <= creator.fragmentInitPayloadSize());
		assertEquals(0, creator.wrap(buffer, 0, encodedLength));

		// Then
		// Verify header
		headerDecoder.wrap(creator.buffers()[0], 0);
		assertEquals(FragmentInitSbeEncoder.TEMPLATE_ID, headerDecoder.templateId());
		assertEquals(selfSinkId, headerDecoder.senderSinkId());
		
		// Verify fragment
		fragmentInitSbeDecoder.wrap(creator.buffers()[0], MessageHeaderDecoder.ENCODED_LENGTH, FragmentInitSbeDecoder.BLOCK_LENGTH, FragmentInitSbeDecoder.SCHEMA_VERSION);
		assertTrue("encodedLength must be smaller than fragment size for this test case [encodedLength:" + encodedLength + ", fragmentSize:" + fragmentSize + "]",
				encodedLength < fragmentSize);
		assertEquals(expectedSeq, fragmentInitSbeDecoder.fragmentSeq());
		assertEquals(encodedLength, fragmentInitSbeDecoder.payloadLength());

		MutableDirectBuffer payload = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		fragmentInitSbeDecoder.getPayload(payload, 0, ServiceConstant.MAX_MESSAGE_SIZE);
		assertTrue("Encoded payload is different frm decoded payload", BitUtil.compare(buffer, 0, payload, 0, encodedLength));
	}
	
	@Test
	public void testSizeSameAsFragmentInitPayloadSize(){
		final int systemId = 1;
		final int selfSinkId = 11;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(1024, self);

		// Given - create a message
		int clientKey = 1234567;
		MessageSinkRef owner = MessageSinkRef.createValidNullSinkRef(1, ServiceType.AdminService, "test-admin"); 
		Security security = Security.of(55555l, SecurityType.CBBC, "65565", 77777, 44444l, Optional.of(LocalDate.now()), ServiceConstant.NULL_LISTED_DATE, PutOrCall.CALL, OptionStyle.AMERICAN, 100000, 1000, 1, 1000, true, SpreadTableBuilder.get(SecurityType.CBBC));
		NewOrderRequest request = NewOrderRequest.of(clientKey, owner, security, OrderType.LIMIT_ORDER, 1000, Side.BUY, TimeInForce.DAY, BooleanType.FALSE, 100000, 10000, 111);
		int encodedLength = OrderSender.encodeNewOrder(sender, 12, buffer, 0, new NewOrderRequestSbeEncoder(), request);
		final int fragmentSize = encodedLength + FragmentInitSbeEncoder.BLOCK_LENGTH + 1 + MessageHeaderDecoder.ENCODED_LENGTH;
		creator = FragmentCreator.of(fragmentSize);
		int expectedSeq = creator.peekSeq();
		
		// When - break into fragments
		creator.wrap(buffer, 0, encodedLength);
		fragmentInitSbeDecoder.wrap(creator.buffers()[0], MessageHeaderDecoder.ENCODED_LENGTH, FragmentInitSbeDecoder.BLOCK_LENGTH, FragmentInitSbeDecoder.SCHEMA_VERSION);

		// Then
		assertTrue("encodedLength must be same as fragment payload size for this test case [encodedLength:" + encodedLength + 
				", fragmentSize:" + fragmentSize + 
				", fragmentInitPayloadSize:" + creator.fragmentInitPayloadSize() + "]",
				encodedLength == creator.fragmentInitPayloadSize());
		assertEquals(expectedSeq, fragmentInitSbeDecoder.fragmentSeq());
		assertEquals(0, creator.limit());
		assertEquals(encodedLength, fragmentInitSbeDecoder.payloadLength());

		MutableDirectBuffer payload = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		fragmentInitSbeDecoder.getPayload(payload, 0, ServiceConstant.MAX_MESSAGE_SIZE);
		assertTrue("Encoded payload is different from decoded payload", BitUtil.compare(buffer, 0, payload, 0, encodedLength));
	}

	@Test
	public void testSizeGreaterThanFragmentSize(){
		// Given - create a big message
		final int systemId = 1;
		final int selfSinkId = 11;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);

		int senderSinkId = 1;
		RequestType requestType = RequestType.GET;
		Builder<Parameter> builder = new ImmutableList.Builder<Parameter>();
		final int numParameters = 9;
		for (int i = 99999; i < 99999 + numParameters; i++){
			builder.add(Parameter.of(ParameterType.SECURITY_SID, i));
		}
		Request request = Request.of(senderSinkId, requestType, builder.build());
		int dstSinkId = 2;
		int encodedLength = RequestSender.encodeRequest(sender, dstSinkId, buffer, 0, new RequestSbeEncoder(), request, entityEncoder);

		LogUtil.dumpBinary(buffer.byteBuffer(), 64, 8);
		
		creator = FragmentCreator.of(fragmentSize);

		// When - break into fragments
		int limit = creator.wrap(buffer, 0, encodedLength);
		
		// Then
		assertEquals((int)Math.ceil((double)encodedLength / creator.fragmentPayloadSize()) - 1, limit);
		// Assemble all buffers into a big buffer
		MutableDirectBuffer[] fragmentBuffers = creator.buffers();
		MutableDirectBuffer payload = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int payloadOffset = 0;
		
		// Copy out the initial buffer
		headerDecoder.wrap(creator.buffers()[0], 0);
		assertEquals(FragmentInitSbeEncoder.TEMPLATE_ID, headerDecoder.templateId());
		assertEquals(selfSinkId, headerDecoder.senderSinkId());

		fragmentInitSbeDecoder.wrap(fragmentBuffers[0], MessageHeaderDecoder.ENCODED_LENGTH, FragmentInitSbeDecoder.BLOCK_LENGTH, FragmentInitSbeDecoder.SCHEMA_VERSION);
		fragmentInitSbeDecoder.getPayload(payload, payloadOffset, fragmentInitSbeDecoder.payloadLength());
		payloadOffset += creator.fragmentInitPayloadSize();
		// Copy out the remaining buffer
		for (int i = 1; i <= limit; i++){
			headerDecoder.wrap(creator.buffers()[i], 0);
			assertEquals(FragmentSbeEncoder.TEMPLATE_ID, headerDecoder.templateId());
			assertEquals(selfSinkId, headerDecoder.senderSinkId());

			fragmentSbeDecoder.wrap(fragmentBuffers[i], MessageHeaderDecoder.ENCODED_LENGTH, FragmentSbeDecoder.BLOCK_LENGTH, FragmentSbeDecoder.SCHEMA_VERSION);
			fragmentSbeDecoder.getPayload(payload, payloadOffset, fragmentSbeDecoder.payloadLength());
			payloadOffset += creator.fragmentPayloadSize();
		}
		assertTrue("Encoded payload is different from decoded payload", BitUtil.compare(buffer, 0, payload, 0, encodedLength));
	}

	@Test
	public void testSizeSameAsMessageSize(){
	}

	@Test
	public void testSizeGreaterThanMessageSize(){
	}
}
