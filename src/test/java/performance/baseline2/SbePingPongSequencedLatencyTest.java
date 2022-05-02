package performance.baseline2;

import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;

public class SbePingPongSequencedLatencyTest {
	public static interface ServiceLifecycleAwareInstanceBuilder {
		ServiceLifecycleAware build(MessageCodec messageCodec, Frame frameBuffer);
	}
	public static void main(final String[] args) throws Exception
    {
/*        final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        final PerfHelper helper = new PerfHelper();
        final CountDownLatch latch = new CountDownLatch(1);
        Disruptor<Frame> pingDisruptor = new Disruptor<Frame>(new MessageSinkEventFactory(helper.bufferSize()), helper.bufferSize(), executor, ProducerType.MULTI, new YieldingWaitStrategy());
        Disruptor<Frame> pongDisruptor = new Disruptor<Frame>(new MessageSinkEventFactory(helper.bufferSize()), helper.bufferSize(), executor, ProducerType.MULTI, new YieldingWaitStrategy());

        BaseStateService pingerBaseService = new BaseStateService(new ServiceLifecycleAwareInstanceBuilder() {
			@Override
			public ServiceLifecycleAware build(MessageCodec messageCodec, Frame frameBuffer) {
				return new SbePingerService(pongDisruptor.getRingBuffer(), latch, messageCodec, frameBuffer);
			}
		});
        BaseStateService pongerBaseService = new BaseStateService(new ServiceLifecycleAwareInstanceBuilder() {
			
			@Override
			public ServiceLifecycleAware build(MessageCodec messageCodec, Frame frameBuffer) {
				return new SbePongerService(pingDisruptor.getRingBuffer(), messageCodec, frameBuffer);
			}
		});
        pingDisruptor.handleEventsWith(pingerBaseService);
        pongDisruptor.handleEventsWith(pongerBaseService);
        
        pongDisruptor.start();
        pingDisruptor.start();
        
        latch.await();
        
        pongDisruptor.shutdown();
        pingDisruptor.shutdown();*/

    }
}
