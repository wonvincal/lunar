package performance.baseline3;

import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;

public class SbePingPongSequencedLatencyTest {
	public static interface ServiceLifecycleAwareInstanceBuilder {
		ServiceLifecycleAware build(MessageCodec messageCodec, Frame frameBuffer);
	}
	public static void main(final String[] args) throws Exception
    {
/*		final String systemName = "tiger";
		final String akkaSystemName = "lunar";
		final String host = "127.0.0.1";
		final int port = 8192;
		final String configPath = Paths.get("config", "simple", "lunar.admin.perf.dashboard.conf").toString();

		System.setProperty(LunarSystem.PARAM_HOST_KEY, host);
		System.setProperty(LunarSystem.PARAM_PORT_KEY, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_KEY, configPath);
		System.setProperty(LunarSystem.LUNAR_SYSTEM_KEY, akkaSystemName);

    	ConfigFactory.invalidateCaches();
		final Config rawConfig = ConfigFactory.load().resolve();
		final Config config = rawConfig.getConfig(systemName).withFallback(rawConfig);
		final SystemConfig systemConfig = new SystemConfig(systemName, host, port, config);
    	
		final MessageFactory messageFactory = new MessageFactory(systemConfig.messagingConfig());
		final HashedWheelTimerService timerService = new HashedWheelTimerService(new NamedThreadFactory(systemConfig.akkaSystemName(), systemConfig.akkaSystemName() + "timer"), 
				  100,
				  TimeUnit.MILLISECONDS, 
				  512,
				  messageFactory.createStandaloneTimerEventSender()
				  );

		final MessageSinkRefMgr messageSinkRefMgr = new MessageSinkRefMgr(systemConfig.numServiceInstances(),
				   systemConfig.aeronConfig().aeronDir(),
				   systemConfig.aeronConfig().channel(),
				   systemConfig.aeronConfig().port(),
				   systemConfig.deadLettersSinkId(),
				   timerService);

        final int pingerSinkId = 2;
        final int pongerSinkId = 3;
        
        final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        final PerfHelper helper = new PerfHelper();
        Disruptor<Frame> pingerDisruptor = new Disruptor<Frame>(new FrameFactory(helper.bufferSize()), helper.bufferSize(), executor, ProducerType.MULTI, new YieldingWaitStrategy());
		messageSinkRefMgr.createAndRegisterRingBufferMessageSink(pingerSinkId,
																 ServiceType.MarketDataService,
																 pingerDisruptor.getRingBuffer());
		
        Disruptor<Frame> pongerDisruptor = new Disruptor<Frame>(new FrameFactory(helper.bufferSize()), helper.bufferSize(), executor, ProducerType.MULTI, new YieldingWaitStrategy());
		messageSinkRefMgr.createAndRegisterRingBufferMessageSink(pongerSinkId,
																 ServiceType.EchoService,
																 pongerDisruptor.getRingBuffer());

        // create a messaging context
    	MessagingContext messagingContext = new MessagingContext(messageSinkRefMgr, 
    															 messageFactory,
    															 timerService);
    	
        final CountDownLatch latch = new CountDownLatch(1);

        BaseStateService pingerBaseService = new BaseStateService(pingerSinkId, messagingContext, new ServiceLifecycleAwareInstanceBuilder() {
			@Override
			public ServiceLifecycleAware build(MessageCodec messageCodec, Frame frameBuffer) {
				return new SbePingerService(pongerSinkId, latch);
			}
		});
        BaseStateService pongerBaseService = new BaseStateService(pongerSinkId, messagingContext, new ServiceLifecycleAwareInstanceBuilder() {
			
			@Override
			public ServiceLifecycleAware build(MessageCodec messageCodec, Frame frameBuffer) {
				return new SbePongerService(pingerSinkId);
			}
		});
        pingerDisruptor.handleEventsWith(pingerBaseService);
        pongerDisruptor.handleEventsWith(pongerBaseService);
        
        pongerDisruptor.start();
        pingerDisruptor.start();
        
        latch.await();
        
        pongerDisruptor.shutdown();
        pingerDisruptor.shutdown();*/

    }
}
