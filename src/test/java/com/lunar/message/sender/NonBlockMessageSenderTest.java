package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.journal.io.sbe.MessageHeaderEncoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.MessageSinkEventFactory;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeEncoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.TestMessageSinkBuilder;
import com.lunar.service.ServiceConstant;
import com.lunar.util.ConcurrentUtil;

public class NonBlockMessageSenderTest {
	static final Logger LOG = LogManager.getLogger(NonBlockMessageSenderTest.class);
	private int queueSize = 128;
	private static MessageSinkEventFactory factory;
	private static MessageSinkRef nullSelf;
	private Disruptor<MutableDirectBuffer> disruptor;
	private static ExecutorService executor;
	private MessageSender sender;
	
	@BeforeClass
	public static void setupClass(){
		nullSelf = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-null");
		factory = new MessageSinkEventFactory(ServiceConstant.MAX_MESSAGE_SIZE);
		executor = Executors.newCachedThreadPool(new NamedThreadFactory("test","test-disruptor"));
	}
	
	@Before
	public void setup(){
		sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				nullSelf);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSendToRingBufferOverflow(){
		CyclicBarrier barrier = new CyclicBarrier(2);
		final int count = queueSize * 3;
		CountDownLatch latch = new CountDownLatch(1);
		MessageProcessor processor = new MessageProcessor(barrier, latch, count, factory, false);
		disruptor = new Disruptor<>(factory, 
									queueSize, 
									executor);
		disruptor.handleEventsWith(processor);
		disruptor.handleExceptionsWith(reportableExceptionHandler);
		disruptor.start();
		
		// wait to barrier
		try {
			barrier.await();
		} 
		catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(e);
		}
		
		final int refSystemId = 1;
		final int refSinkId = 1;
		final ServiceType refServiceType = ServiceType.EchoService;
		final ServiceStatusType refStatusType = ServiceStatusType.DOWN;
		final long refModifyTime = System.nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		// send event, let's encode service status manually for testing
		RingBufferMessageSink sink = TestMessageSinkBuilder.createRingBufferMessageSink(refSystemId, refSinkId, refServiceType, "test", disruptor.getRingBuffer());
		MessageSinkRef sinkRef = MessageSinkRef.of(sink); 
		
		// create a buffer and send message
		int rejected = 0;
		int success = 0;
		ServiceStatusSbeEncoder sbe = new ServiceStatusSbeEncoder();
		while (true){
			int size = ServiceStatusSender.encodeServiceStatus(
					sender,
					sink.sinkId(),
					sender.buffer(),
					0, 
					sbe,
					refSystemId,
					nullSelf.sinkId(), 
					refServiceType, 
					refStatusType, 
					refModifyTime,
					refSentTime,
					refHealthCheckTime);
			long result = sender.trySend(sinkRef, sender.buffer(), 0, size);
			if (result > 0L){
				success++;
			}
			else{
				rejected++;
			}
			if (processor.receivedEvents.size() >= count){
				break;
			}
		}
		assertTrue(rejected >  0);
		
		// wait barrier
		try {
			latch.await();
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		// verify
		MessageReceiver receiver = MessageReceiver.of(); 
		final AtomicInteger maxReceivedSeq = new AtomicInteger(0);
		receiver.serviceStatusHandlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(nullSelf.sinkId(), header.senderSinkId());
				assertEquals(sink.sinkId(), header.dstSinkId());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
				int seq = header.seq();
				if (seq > maxReceivedSeq.get()){
					maxReceivedSeq.set(seq);
				}
			}
		});
		while (disruptor.getRingBuffer().remainingCapacity() != queueSize){
			ConcurrentUtil.sleep(1);
		}
		for (MutableDirectBuffer buffer : processor.receivedEvents){
			receiver.receive(buffer, 0);			
		}
		LOG.info("Received {} messages", processor.receivedEvents.size());
		LOG.info("Max seq number: {}, Total attempts: {}, success: {}, rejected: {}, startSeq: {}", 
				maxReceivedSeq.get(),
				success + rejected,
				success,
				rejected);
		
		assertNotEquals(success, processor.receivedEvents.size());
		assertTrue("Number of rejected message should be greater than zero", rejected > 0);
		disruptor.shutdown();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSendToRingBuffer(){
		CyclicBarrier barrier = new CyclicBarrier(2);
		final int count = 2000;
		CountDownLatch latch = new CountDownLatch(1);
		MessageProcessor processor = new MessageProcessor(barrier, latch, count, factory);
		disruptor = new Disruptor<>(factory, 
									queueSize, 
									executor);
		disruptor.handleEventsWith(processor);
		disruptor.handleExceptionsWith(reportableExceptionHandler);
		disruptor.start();
		
		// wait to barrier
		try {
			barrier.await();
		} 
		catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(e);
		}
		
		int startSeq = sender.overrideNextSeq(101);
		final int refSystemId = 1;
		final int refSinkId = 1;
		final ServiceType refServiceType = ServiceType.EchoService;
		final ServiceStatusType refStatusType = ServiceStatusType.DOWN;
		final long refModifyTime = System.nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		// send event, let's encode service status manually for testing
		RingBufferMessageSink sink = TestMessageSinkBuilder.createRingBufferMessageSink(refSystemId, refSinkId, refServiceType, "test", disruptor.getRingBuffer());
		MessageSinkRef sinkRef = MessageSinkRef.of(sink); 
		
		// create a buffer
		int overflowCount = 0;

		Set<Integer> overflown = new HashSet<>();
		ServiceStatusSbeEncoder sbe = new ServiceStatusSbeEncoder();
		for (int i = 0; i < count; i++){
			int size = ServiceStatusSender.encodeServiceStatus(
				sender,
				sink.sinkId(),
				sender.buffer(),
				0, 
				sbe,
				refSystemId,
				nullSelf.sinkId(), 
				refServiceType, 
				refStatusType, 
				refModifyTime,
				refSentTime,
				refHealthCheckTime);
			if (!(sender.trySend(sinkRef, sender.buffer(), 0, size) == MessageSink.OK)){
				overflown.add(sender.peekNextSeq() - 1);
				overflowCount++;
			}

		}
		
		// wait barrier
		try {
			if (overflowCount > 0){
				processor.expectedCountAdjustment(overflowCount * -1);
			}
			latch.await();
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		// verify
		final AtomicInteger refSeq = new AtomicInteger(startSeq);
		MessageReceiver receiver = MessageReceiver.of(); 
		receiver.serviceStatusHandlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				if (refSeq.get() != header.seq()){
					for (int i = refSeq.get(); i < header.seq(); i++){
						assertTrue("expect " + i, overflown.remove(i));
					}
					refSeq.set(header.seq());
				}
					
				assertEquals(nullSelf.sinkId(), header.senderSinkId());
				assertEquals(sink.sinkId(), header.dstSinkId());
				assertEquals(refSeq.get(), header.seq());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
				refSeq.incrementAndGet();
			}
		});
		
		if (overflowCount > 0){
			long expireAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10);
			while (processor.receivedEvents.size() != count - overflowCount){
				LockSupport.parkNanos(50_000_000);
				if (System.nanoTime() > expireAt){
					break;
				}
			}
		}
		
		for (MutableDirectBuffer buffer : processor.receivedEvents){
			receiver.receive(buffer, 0);			
		}
		
		assertEquals(count, processor.receivedEvents.size() + overflowCount);
		LOG.debug("received:{}, overflow:{}", processor.receivedEvents.size(), overflowCount);
		disruptor.shutdown();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSendToRingBufferByTryClaim(){
		CyclicBarrier barrier = new CyclicBarrier(2);
		final int count = 200;
		CountDownLatch latch = new CountDownLatch(1);
		MessageProcessor processor = new MessageProcessor(barrier, latch, count, factory);
		disruptor = new Disruptor<>(factory, 
									queueSize, 
									executor);
		disruptor.handleEventsWith(processor);
		disruptor.handleExceptionsWith(reportableExceptionHandler);
		disruptor.start();
		
		// wait to barrier
		try {
			barrier.await();
		} 
		catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(e);
		}
		
		int startSeq = sender.overrideNextSeq(101);
		final int refSystemId = 1;
		final int refSinkId = 1;
		final ServiceType refServiceType = ServiceType.EchoService;
		final ServiceStatusType refStatusType = ServiceStatusType.DOWN;
		final long refModifyTime = System.nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		// send event, let's encode service status manually for testing
		RingBufferMessageSink sink = TestMessageSinkBuilder.createRingBufferMessageSink(refSystemId, refSinkId, refServiceType, "test", disruptor.getRingBuffer());
		MessageSinkRef sinkRef = MessageSinkRef.of(sink); 
		
		// create a buffer
		ServiceStatusSbeEncoder sbe = new ServiceStatusSbeEncoder();
		int overflowCount = 0;
		for (int i = 0; i < count; i++){
			MessageSinkBufferClaim bufferClaim = sender.bufferClaim();
			if (sender.tryClaim(ServiceStatusSbeEncoder.BLOCK_LENGTH + MessageHeaderEncoder.ENCODED_LENGTH, sinkRef, bufferClaim) == MessageSink.OK){
				ServiceStatusSender.encodeServiceStatus(
						sender,
						sink.sinkId(),
						bufferClaim.buffer(),
						bufferClaim.offset(), 
						sbe,
						refSystemId,
						nullSelf.sinkId(), 
						refServiceType, 
						refStatusType, 
						refModifyTime,
						refSentTime,
						refHealthCheckTime);
				bufferClaim.commit();
			}
			else {
				overflowCount++;
			}
		}
		
		// wait barrier
		try {
			if (overflowCount > 0){
				processor.expectedCountAdjustment(overflowCount * -1);
			}
			latch.await();
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		// verify
		final AtomicInteger refSeq = new AtomicInteger(startSeq);
		MessageReceiver receiver = MessageReceiver.of(); 
		receiver.serviceStatusHandlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(nullSelf.sinkId(), header.senderSinkId());
				assertEquals(sink.sinkId(), header.dstSinkId());
				assertEquals(refSeq.get(), header.seq());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
				refSeq.incrementAndGet();
			}
		});
		
		if (overflowCount > 0){
			long expireAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10);
			while (processor.receivedEvents.size() != count - overflowCount){
				LockSupport.parkNanos(50_000_000);
				if (System.nanoTime() > expireAt){
					break;
				}
			}
		}
		
		for (MutableDirectBuffer buffer : processor.receivedEvents){
			receiver.receive(buffer, 0);			
		}
		assertEquals(count, processor.receivedEvents.size() + overflowCount);
		LOG.debug("received:{}, overflow:{}", processor.receivedEvents.size(), overflowCount);
		disruptor.shutdown();		
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSendingToMultipleSinks(){
		final int count = 150;
		final int refSystemId = 1;
		// send event, let's encode service status manually for testing
		int numSinks = 4;
		CyclicBarrier barrier = new CyclicBarrier(numSinks + 1);
		final ServiceType refServiceType = ServiceType.EchoService;
		CountDownLatch latch = new CountDownLatch(numSinks);
		
		ArrayList<Disruptor<MutableDirectBuffer>> disruptors = new ArrayList<>(numSinks);
		ArrayList<MessageSinkRef> sinkRefs = new ArrayList<>(numSinks);
		ArrayList<MessageProcessor> processors = new ArrayList<>(numSinks);
		final int startSinkId = 32;
		for (int i = startSinkId; i < startSinkId + numSinks; i++){
			MessageProcessor processor = new MessageProcessor(barrier, latch, count, factory);
			processors.add(processor);
			Disruptor<MutableDirectBuffer> disruptor = new Disruptor<>(factory, 
										queueSize, 
										executor);
			disruptor.handleEventsWith(processor);
			disruptor.handleExceptionsWith(reportableExceptionHandler);
			disruptors.add(disruptor);
			
			RingBufferMessageSink sink = TestMessageSinkBuilder.createRingBufferMessageSink(refSystemId, i, refServiceType, "test", disruptor.getRingBuffer());
			sinkRefs.add(MessageSinkRef.of(sink));
		}

		for (Disruptor<MutableDirectBuffer> d : disruptors){
			d.start();
		}
		
		// wait for barrier
		try {
			barrier.await();
		} 
		catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(e);
		}	
		
		// send
		int startSeq = sender.overrideNextSeq(101);
		final ServiceStatusType refStatusType = ServiceStatusType.DOWN;
		final long refModifyTime = System.nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		// create a buffer
		long[] sinkSendResults = new long[sinkRefs.size()];
		ServiceStatusSbeEncoder sbe = new ServiceStatusSbeEncoder();
		for (int i = 0; i < count; i++){
			int size = ServiceStatusSender.encodeServiceStatus(
				sender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				sender.buffer(),
				0, 
				sbe,
				refSystemId,
				nullSelf.sinkId(), 
				refServiceType, 
				refStatusType, 
				refModifyTime,
				refSentTime,
				refHealthCheckTime);
			if (sender.send(sinkRefs.toArray(new MessageSinkRef[0]), sender.buffer(), 0, size, sinkSendResults) != MessageSink.OK){
				LOG.error("Could not send service status");
			}
		}
		
		// wait for latch
		try {
			latch.await();
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		// verify
		final AtomicInteger refSeq = new AtomicInteger();
		final AtomicInteger refSinkId = new AtomicInteger(); 
		MessageReceiver receiver = MessageReceiver.of(); 
		receiver.serviceStatusHandlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(nullSelf.sinkId(), header.senderSinkId());
				assertEquals(refSinkId.get(), header.dstSinkId());
				assertEquals(refSeq.get(), header.seq());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
				refSeq.incrementAndGet();
			}
		});
		int totalCount = 0;
		for (int i = 0; i < numSinks; i++){
			refSeq.set(startSeq);
			refSinkId.set(startSinkId + i);
			for (MutableDirectBuffer buffer : processors.get(i).receivedEvents){
				receiver.receive(buffer, 0);
				totalCount++;
			}						
		}
		
		assertEquals(count, refSeq.get() - startSeq /* 111 - 101 */);
		assertEquals(numSinks * count, totalCount);
		for (Disruptor<MutableDirectBuffer> d : disruptors){
			d.shutdown();
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSendingToMultipleSinksExceptOne(){
		final int count = 150;
		final int refSystemId = 1;
		// send event, let's encode service status manually for testing
		int numSinks = 4;
		CyclicBarrier barrier = new CyclicBarrier(numSinks + 1);
		final ServiceType refServiceType = ServiceType.EchoService;
		CountDownLatch latch = new CountDownLatch(numSinks);
		
		ArrayList<Disruptor<MutableDirectBuffer>> disruptors = new ArrayList<>(numSinks);
		ArrayList<MessageSinkRef> sinkRefs = new ArrayList<>(numSinks);
		ArrayList<MessageProcessor> processors = new ArrayList<>(numSinks);
		final int startSinkId = 32;
		final int sinkToSkip = startSinkId;
		for (int i = startSinkId; i < startSinkId + numSinks; i++){
			MessageProcessor processor = null;
			if (i != sinkToSkip){
				processor = new MessageProcessor(barrier, latch, count, factory);
			}
			else{
				processor = new MessageProcessor(barrier, latch, 0, factory);
			}
			
			processors.add(processor);
			Disruptor<MutableDirectBuffer> disruptor = new Disruptor<>(factory, 
										queueSize, 
										executor);
			disruptor.handleEventsWith(processor);
			disruptor.handleExceptionsWith(reportableExceptionHandler);
			disruptors.add(disruptor);
			
			RingBufferMessageSink sink = TestMessageSinkBuilder.createRingBufferMessageSink(refSystemId, i, refServiceType, "test", disruptor.getRingBuffer());
			sinkRefs.add(MessageSinkRef.of(sink)); 
		}

		for (Disruptor<MutableDirectBuffer> d : disruptors){
			d.start();
		}
		
		// wait for barrier
		try {
			barrier.await();
		} 
		catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(e);
		}	
		
		// send
		int startSeq = sender.overrideNextSeq(101);
		final ServiceStatusType refStatusType = ServiceStatusType.DOWN;
		final long refModifyTime = System.nanoTime();
		final long refSentTime = System.nanoTime();
		final long refHealthCheckTime = System.nanoTime();

		long[] results = new long[ServiceConstant.MAX_SUBSCRIBERS];
		// create a buffer
		ServiceStatusSbeEncoder sbe = new ServiceStatusSbeEncoder();
		for (int i = 0; i < count; i++){
			int size = ServiceStatusSender.encodeServiceStatus(
				sender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				sender.buffer(),
				0, 
				sbe,
				refSystemId,
				nullSelf.sinkId(), 
				refServiceType, 
				refStatusType, 
				refModifyTime,
				refSentTime,
				refHealthCheckTime);
			sender.send(sinkRefs.toArray(new MessageSinkRef[0]), sender.buffer(), 0, size, startSinkId, results);
		}
		
		// wait for latch
		try {
			latch.await();
		} 
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		// verify
		final AtomicInteger refSeq = new AtomicInteger();
		final AtomicInteger refSinkId = new AtomicInteger(); 
		MessageReceiver receiver = MessageReceiver.of(); 
		receiver.serviceStatusHandlerList().add(new Handler<ServiceStatusSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
				assertEquals(nullSelf.sinkId(), header.senderSinkId());
				assertEquals(refSinkId.get(), header.dstSinkId());
				assertEquals(refSeq.get(), header.seq());
				assertEquals(refServiceType, codec.serviceType());
				assertEquals(refStatusType, codec.statusType());
				assertEquals(refModifyTime, codec.modifyTimeAtOrigin());
				refSeq.incrementAndGet();
			}
		});
		int totalCount = 0;
		for (int i = 0; i < numSinks; i++){
			refSeq.set(startSeq);
			refSinkId.set(startSinkId + i);
			for (MutableDirectBuffer buffer : processors.get(i).receivedEvents){
				receiver.receive(buffer, 0);
				totalCount++;
			}						
		}
		
		assertEquals(count, refSeq.get() - startSeq /* 111 - 101 */);
		assertEquals((numSinks - 1) * count, totalCount);
		for (Disruptor<MutableDirectBuffer> d : disruptors){
			d.shutdown();
		}
	}

	public void testFragment(){}
	
	public static class MessageProcessor implements EventHandler<MutableDirectBuffer>, LifecycleAware {
		private final ConcurrentLinkedQueue<MutableDirectBuffer> receivedEvents = new ConcurrentLinkedQueue<>();
		private final CyclicBarrier barrier;
		private final CountDownLatch latch;
		private final MessageSinkEventFactory factory;
		private int remainingCount;
		private final boolean exceptionOnOverflow;
		private volatile int expectedCountAdjustment;
		
		public MessageProcessor(CyclicBarrier barrier, CountDownLatch latch, int expectedCount, MessageSinkEventFactory factory){
			this(barrier, latch, expectedCount, factory, false);
		}
		public MessageProcessor(CyclicBarrier barrier, CountDownLatch latch, int expectedCount, MessageSinkEventFactory factory, boolean exceptionOnOverflow){
			this.barrier = barrier;
			this.latch = latch;
			this.remainingCount = expectedCount;
			this.factory = factory;
			this.exceptionOnOverflow = exceptionOnOverflow;
			if (this.remainingCount == 0){
				latch.countDown();
			}
		}
		@Override
		public void onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
			LockSupport.parkNanos(1);
			
			// store the event
			MutableDirectBuffer buffer = factory.newInstance();
			buffer.putBytes(0, event, 0, event.capacity());
			this.receivedEvents.add(buffer);
			remainingCount--;
			if ((remainingCount + expectedCountAdjustment) <= 0){
				latch.countDown();
			}
			if (exceptionOnOverflow && remainingCount < 0){
				throw new IllegalStateException("remaining count should never be < 0");
			}
		}
		public ConcurrentLinkedQueue<MutableDirectBuffer> receivedEvents(){
			return receivedEvents;
		}
		@Override
		public void onStart() {
			try {
				barrier.await();
			} 
			catch (InterruptedException | BrokenBarrierException e) {
				throw new RuntimeException(e);
			}
		}
		@Override
		public void onShutdown() {
		}
		public void expectedCountAdjustment(int adjustment){
			this.expectedCountAdjustment = adjustment;
			if ((remainingCount + expectedCountAdjustment) <= 0){
				latch.countDown();
			}
		}
	}
	
	public ExceptionHandler<MutableDirectBuffer> reportableExceptionHandler = new ExceptionHandler<MutableDirectBuffer>() {
		@Override
		public void handleOnStartException(Throwable ex) {
			throw new RuntimeException("unexpected exception", ex);
		}
		
		@Override
		public void handleOnShutdownException(Throwable ex) {
			throw new RuntimeException("unexpected exception", ex);
		}
		
		@Override
		public void handleEventException(Throwable ex, long sequence, MutableDirectBuffer event) {
			throw new RuntimeException("unexpected exception", ex);
		}
	};
}
