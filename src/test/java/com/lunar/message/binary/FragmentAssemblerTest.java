package com.lunar.message.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Request;
import com.lunar.message.RequestTestUtil;
import com.lunar.message.binary.FragmentAssembler.FragmentBuffer;
import com.lunar.message.binary.FragmentAssembler.FragmentExceptionHandler;
import com.lunar.message.io.sbe.FragmentSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.OrderChannelBuffer;
import com.lunar.service.ServiceConstant;

/**
 * Instead of testing FragmentAssembler only, I chose to test it via MessageReceiver.
 * That is, testing the instance inside MessageReceiver.  The reason behind is that
 * 1) MessageReceiver is a rather straight forward class
 * 2) MessageReceiver has all different handlers that I need to test out the correctness
 *    of FragmentAssembler 
 * @author wongca
 *
 */
public class FragmentAssemblerTest {
	private static final Logger LOG = LogManager.getLogger(FragmentAssemblerTest.class);
	private MutableDirectBuffer buffer;
	private EntityEncoder entityEncoder = new EntityEncoder();
	
	@Before
	public void setup(){
		buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	}
	
	@Test
	public void testCreate(){
		MessageReceiver.of(16, OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
	}
	
	@Test
	public void testReceiveOneFragmentInit(){
		// Given
		final int systemId = 1;
		final int selfSinkId = 1;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE * 2;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);

		// Create a message that is smaller than one fragment
		MessageReceiver messageReceiver = MessageReceiver.of(1, OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
		int refSenderSinkId = 1;
		int refDstSinkId = 2;
		
		int clientKey = 99999;
		Request request = RequestTestUtil.createRequest(refSenderSinkId, 1).clientKey(clientKey);
		int encodedLength = RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, new RequestSbeEncoder(), request, entityEncoder);
//		System.out.println("Encoded length for request: " + encodedLength);
//		System.out.println(LogUtil.dumpBinary(buffer.byteBuffer(), 128, 8));

		FragmentCreator creator = FragmentCreator.of(fragmentSize);
		assertTrue("Message size (" + encodedLength + ") must be <= allowable payload size (" + creator.fragmentInitPayloadSize() + ")",
				encodedLength <= creator.fragmentInitPayloadSize());
		int limit = creator.wrap(buffer, 0, encodedLength);
		assertEquals(0, limit);
//		System.out.println("Encoded length for fragment: " + encodedLength);
//		System.out.println(LogUtil.dumpBinary(creator.buffers()[limit].byteBuffer(), 128, 8));
		
		// When and then
		// Pass into onEvent
		final AtomicInteger numRequestReceived = new AtomicInteger();
		messageReceiver.requestHandlerList().add(new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
				numRequestReceived.getAndIncrement();
				assertEquals(refSenderSinkId, header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				System.out.println("Received at test");
				RequestTestUtil.assertRequest(request, codec);
			}
		});
		messageReceiver.receive(creator.buffers()[limit], 0);
		assertEquals(1, numRequestReceived.get());
	}
	
	@Test
	public void testReceiveTwoFragments(){
		// Given
		final int systemId = 1;
		final int selfSinkId = 1;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);

		// Create a message that is smaller than one fragment
		MessageReceiver messageReceiver = MessageReceiver.of(1, OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
		int refSenderSinkId = 1;
		int refDstSinkId = 2;
		
		int clientKey = 99999;
		Request request = RequestTestUtil.createRequest(refSenderSinkId, 2).clientKey(clientKey);
		int encodedLength = RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, new RequestSbeEncoder(), request, entityEncoder);
//		System.out.println("Encoded length for request: " + encodedLength);
//		System.out.println(LogUtil.dumpBinary(buffer.byteBuffer(), 128, 8));

		FragmentCreator creator = FragmentCreator.of(fragmentSize);
		assertTrue("Message size (" + encodedLength + ") must be > allowable payload size (" + creator.fragmentInitPayloadSize() + ")",
				encodedLength > creator.fragmentInitPayloadSize());
		int limit = creator.wrap(buffer, 0, encodedLength);
		assertEquals(1, limit);

		// When and then
		// Pass into onEvent
		final AtomicInteger numRequestReceived = new AtomicInteger();
		messageReceiver.requestHandlerList().add(new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
				numRequestReceived.getAndIncrement();
				assertEquals(refSenderSinkId, header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				System.out.println("Received at test");
				RequestTestUtil.assertRequest(request, codec);
			}
		});
		MutableDirectBuffer[] buffers = creator.buffers();
		for (int i = 0; i <= limit; i++){
			messageReceiver.receive(buffers[i], 0);
		}
		assertEquals(1, numRequestReceived.get());
	}
	
	@Test
	public void testReceiveMultipleFragments(){
		// Given
		final int systemId = 1;
		final int selfSinkId = 1;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);

		// Create a message that is smaller than one fragment
		MessageReceiver messageReceiver = MessageReceiver.of(1, OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
		int refSenderSinkId = 1;
		int refDstSinkId = 2;
		
		int clientKey = 99999;
		Request request = RequestTestUtil.createRequest(refSenderSinkId, 100).clientKey(clientKey);
		int encodedLength = RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, new RequestSbeEncoder(), request, entityEncoder);
		System.out.println("Encoded length for request: " + encodedLength);

		FragmentCreator creator = FragmentCreator.of(fragmentSize);
		assertTrue("Message size (" + encodedLength + ") must be > allowable payload size (" + creator.fragmentInitPayloadSize() + ")",
				encodedLength > creator.fragmentInitPayloadSize());
		int limit = creator.wrap(buffer, 0, encodedLength);
		assertTrue("Limit should be > 1", limit > 1);

		// When and then
		// Pass into onEvent
		final AtomicInteger numRequestReceived = new AtomicInteger();
		messageReceiver.requestHandlerList().add(new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
				numRequestReceived.getAndIncrement();
				assertEquals(refSenderSinkId, header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				System.out.println("Received at test");
				RequestTestUtil.assertRequest(request, codec);
			}
		});
		MutableDirectBuffer[] buffers = creator.buffers();
		for (int i = 0; i <= limit; i++){
			messageReceiver.receive(buffers[i], 0);
		}
		assertEquals(1, numRequestReceived.get());
	}

	@Test
	public void testReceiveOutOfSyncFragments(){
		// Given
		final int systemId = 1;
		final int selfSinkId = 1;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);

		// Create a message that is smaller than one fragment
		MessageReceiver messageReceiver = MessageReceiver.of(1, 
				OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
		int refSenderSinkId = 1;
		int refDstSinkId = 2;
		
		int clientKey = 99999;
		Request request = RequestTestUtil.createRequest(refSenderSinkId, 2).clientKey(clientKey);
		int encodedLength = RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, new RequestSbeEncoder(), request, entityEncoder);
//		System.out.println("Encoded length for request: " + encodedLength);
//		System.out.println(LogUtil.dumpBinary(buffer.byteBuffer(), 128, 8));

		FragmentCreator creator = FragmentCreator.of(fragmentSize);
		assertTrue("Message size (" + encodedLength + ") must be > allowable payload size (" + creator.fragmentInitPayloadSize() + ")",
				encodedLength > creator.fragmentInitPayloadSize());
		int limit = creator.wrap(buffer, 0, encodedLength);
		assertEquals(1, limit);
		
		final AtomicInteger numDroppedFragment = new AtomicInteger(0);
		messageReceiver.fragmentAssemblerExceptionHandler(new FragmentExceptionHandler() {
			
			@Override
			public void handleDroppedFragments(FragmentBuffer fragmentBuffer) {
			}
			
			@Override
			public void handleDroppedFragment(FragmentSbeDecoder fragment) {
				numDroppedFragment.getAndIncrement();
				LOG.info("Drop fragment [receivedSeq:{}]", fragment.fragmentSeq());
			}
		});
		
		messageReceiver.receive(creator.buffers()[limit], 0);
		assertEquals(1, numDroppedFragment.get());
	}
	
	@Test
	public void testMissingOneInASeriesOfFragments(){
		// Given
		final int systemId = 1;
		final int selfSinkId = 1;
		final int fragmentSize = ServiceConstant.DEFAULT_FRAGMENT_SIZE;
		MessageSinkRef self = DummyMessageSink.refOf(systemId, selfSinkId, 
				"self-sink", ServiceType.AdminService);
		MessageSender sender = MessageSender.of(fragmentSize, self);

		// Create a message that is smaller than one fragment
		MessageReceiver messageReceiver = MessageReceiver.of(1, OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
		int refSenderSinkId = 1;
		int refDstSinkId = 2;
		
		int clientKey = 99999;
		Request request = RequestTestUtil.createRequest(refSenderSinkId, 100).clientKey(clientKey);
		int encodedLength = RequestSender.encodeRequest(sender, refDstSinkId, buffer, 0, new RequestSbeEncoder(), request, entityEncoder);
		System.out.println("Encoded length for request: " + encodedLength);

		FragmentCreator creator = FragmentCreator.of(fragmentSize);
		assertTrue("Message size (" + encodedLength + ") must be > allowable payload size (" + creator.fragmentInitPayloadSize() + ")",
				encodedLength > creator.fragmentInitPayloadSize());
		int limit = creator.wrap(buffer, 0, encodedLength);
		assertTrue("Limit should be >= 4", limit >= 4);

		// When and then
		final int skipIndex = 5;
		final AtomicInteger numOccurrenceOfDroppedFragments = new AtomicInteger(0);
		final AtomicInteger numOccurrenceOfDroppedFragment = new AtomicInteger(0);
		messageReceiver.fragmentAssemblerExceptionHandler(new FragmentExceptionHandler() {
			
			@Override
			public void handleDroppedFragments(FragmentBuffer fragmentBuffer) {
				numOccurrenceOfDroppedFragments.getAndIncrement();
				assertEquals(0, fragmentBuffer.fromSeq);
				assertEquals(skipIndex - 1, fragmentBuffer.toSeq);
			}
			
			@Override
			public void handleDroppedFragment(FragmentSbeDecoder fragment) {
				numOccurrenceOfDroppedFragment.getAndIncrement();
				LOG.info("Drop fragment [receivedSeq:{}]", fragment.fragmentSeq());
			}
		});
		// limit = 4, limit - skipIndex = 2
		// total fragments = 5
		// skipped fragment buffer from 0 to (skipIndex - 1)
		// skipped individual fragment of skipIndex + 1 to limit
		MutableDirectBuffer[] buffers = creator.buffers();
		for (int i = 0; i <= limit; i++){
			if (i != skipIndex){
				messageReceiver.receive(buffers[i], 0);
			}
		}
		assertEquals(limit - skipIndex, numOccurrenceOfDroppedFragment.get());		
		assertEquals(1, numOccurrenceOfDroppedFragments.get());		
	}
}
