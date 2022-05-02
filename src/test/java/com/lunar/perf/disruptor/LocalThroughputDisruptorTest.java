package com.lunar.perf.disruptor;

/**
 * Ping pong with {@link SbePongDisruptor} and {@link SbeSenderDisruptor}.  It measures the latency of inter-thread communication using
 * disruptor, ring buffer and sbe.
 *  
 * @author Calvin
 *
 */
public class LocalThroughputDisruptorTest {
	/*
	static MessageCodec messageCodec;

	@BeforeClass
	public static void setup(){
		messageCodec = MessageCodec.of();
	}
	
	@Test
	public void testEcho() throws InterruptedException{
		try {
			ExecutorService pongExecutor = Executors.newSingleThreadExecutor();
			final int ringBufferSize = 262144;
			final int pongSvcId = 102;
			final SbePongDisruptor pong = new SbePongDisruptor(pongSvcId, pongExecutor, ringBufferSize);

			ExecutorService senderExecutor = Executors.newSingleThreadExecutor();
			final int iterations = 5;
			final int senderSvcId = 101;
			CountDownLatch latch = new CountDownLatch(1);			 
			final SbeSenderDisruptor sender = new SbeSenderDisruptor(senderSvcId, senderExecutor, ringBufferSize, latch, iterations);
			
			pong.senderRingBuffer(sender.ringBuffer());
			sender.pongRingBuffer(pong.start()).pongSvcId(pongSvcId);			
			sender.start();
			
			Frame message = messageCodec.createMessage();
			messageCodec.encodeCommandEcho(message, MessageCodec.SERVICE_NOT_SPECIFIED, senderSvcId, 0, 0, 1000000);
			sender.tell(message);
			latch.await();
			
			pong.stop();
			sender.stop();
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
*/
}
