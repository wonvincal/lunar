package performance.baseline1;

public class SbePingPongSequencedLatencyTest {
	public static void main(final String[] args) throws Exception
    {
/*        final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        final PerfHelper helper = new PerfHelper();
        final CountDownLatch latch = new CountDownLatch(1);
        Disruptor<Frame> pingDisruptor = new Disruptor<Frame>(new MessageSinkEventFactory(helper.bufferSize()), helper.bufferSize(), executor, ProducerType.MULTI, new YieldingWaitStrategy());
        Disruptor<Frame> pongDisruptor = new Disruptor<Frame>(new MessageSinkEventFactory(helper.bufferSize()), helper.bufferSize(), executor, ProducerType.MULTI, new YieldingWaitStrategy());
        SbePingerService pinger = new SbePingerService(pongDisruptor.getRingBuffer(), latch);
        SbePongerService ponger = new SbePongerService(pingDisruptor.getRingBuffer());
        pingDisruptor.handleEventsWith(pinger);
        pongDisruptor.handleEventsWith(ponger);
        pongDisruptor.start();
        pingDisruptor.start();
        latch.await();
        pongDisruptor.shutdown();
        pingDisruptor.shutdown();
*/
    }
}
