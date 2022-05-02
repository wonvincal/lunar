package com.lunar.perf.disruptor;

public class SbeSenderDisruptor {
	/*
	private static final Logger LOG = LogManager.getLogger(SbeSenderDisruptor.class);
	private static final int FRAME_SIZE = 64;
	private final ExecutorService executor;
	private final Disruptor<Frame> disruptor;
	private final CountDownLatch latch;
	private final MessageCodec messageCodec;
	private final MessageCodec receivingMessageCodec;
	private final RingBuffer<Frame> ringBuffer;
	private int mySvcId;	
	private RingBuffer<Frame> pongRingBuffer;
	private int pongSvcId;
	private final AtomicInteger msgKeySeq;
	private long startTime;
	private long roundTrips;
	private long expectedSeq;
	private int iterations;
	private long totalLatency = 0;
	private final ExecutorService senderExecutor;
	private final int pauseTimeNs = 1000;
	
	@SuppressWarnings("unchecked")
	public SbeSenderDisruptor(int mySvcId, ExecutorService executor, int ringBufferSize, CountDownLatch latch, int iterations){
		msgKeySeq = new AtomicInteger();
		this.latch = latch;
		this.messageCodec = MessageCodec.of();
		
		this.receivingMessageCodec = MessageCodec.of();
		this.receivingMessageCodec.addCommandHandler(this::handleCommand);
		this.receivingMessageCodec.addEchoHandler(this::handleEcho);

		this.mySvcId = mySvcId;
		this.iterations = iterations;		

		this.executor = executor;
		this.disruptor = new Disruptor<Frame>(new MessageSinkEventFactory(FRAME_SIZE), ringBufferSize, executor, ProducerType.SINGLE, new BusySpinWaitStrategy());
		this.disruptor.handleEventsWith(this::handleEvent);
		this.disruptor.handleExceptionsWith(new ExceptionHandler<Frame>(){

			@Override
			public void handleEventException(Throwable ex, long sequence, Frame event) {
				LOG.error("got exception when handling event: " + event, ex);
			}

			@Override
			public void handleOnStartException(Throwable ex) {
				LOG.error("got exception on start of " + mySvcId, ex);
			}

			@Override
			public void handleOnShutdownException(Throwable ex) {
				LOG.error("got exception on shutdown of " + mySvcId, ex);
			}
			
		});
		this.ringBuffer = this.disruptor.getRingBuffer();
		
		this.senderExecutor = Executors.newSingleThreadExecutor();
	}

	public RingBuffer<Frame> ringBuffer(){
		return this.ringBuffer;
	}
	
	public SbeSenderDisruptor pongRingBuffer(RingBuffer<Frame> pongRingBuffer){
		this.pongRingBuffer = pongRingBuffer;
		return this;
	}
	
	public SbeSenderDisruptor pongSvcId(int pongSvcId){
		this.pongSvcId = pongSvcId;
		return this;
	}
	
	public RingBuffer<Frame> start(){
		LOG.debug("started");
		return this.disruptor.start();
	}
	
	public void stop(){
		this.disruptor.shutdown();
		this.executor.shutdown();
		this.senderExecutor.shutdown();
		LOG.debug("shutdown");
	}
	
	public void tell(Frame frame){
		ringBuffer.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, frame);
	}

	private void handleCommand(final Frame message, int srcSvcId, int dstSvcId, CommandSbe codec){
		// reply to the command
		if (codec.commandType() == CommandType.SEND_ECHO){
			handleEchoCommand(message, srcSvcId, dstSvcId, codec);
		}
		else{
//			messageCodec.sendInvalidMsgNtf(getSender(), getSelf(), mySvcId, srcSvcId, CommandSbe.TEMPLATE_ID, CommandSbe.commandTypeId(), ReasonType.NOT_SUPPORTED);
		}
	}
	
	private void handleEvent(Frame message, long sequence, boolean endOfBatch){
		receivingMessageCodec.receive(message);
	}

	private void handleEchoCommand(final Frame message, int srcSvcId, int dstSvcId, CommandSbe codec){
		Parameters parameters = codec.parameters();
		if (parameters.count() != 1){
			LOG.error("received echo command with not one parameter, current count:{}", parameters.count());
			//messageCodec.encodeInvalidMsgNtf(message, srcSvcId, dstSvcId, CommandSbe.TEMPLATE_ID, (int)CommandSbe.parametersId(), ReasonType.INVALID, null);
			return;
		}
		parameters = parameters.next();
		if (parameters.parameterType() != ParameterType.ECHO_REMAINING){
			LOG.error("received echo command with invalid parameter, parameterType:{}", parameters.parameterType());
			return;
		}
		LOG.debug("got echo command");
		final long remaining = parameters.parameterValueLong(); 
		sendEventBatch(remaining);
	}

	private void sendEventBatch(long numEvents){
		this.senderExecutor.execute(new Runnable() {			
			@Override
			public void run() {
				roundTrips = numEvents;
				expectedSeq = 1;
				startTime = System.nanoTime();
				totalLatency = 0;
				final int key = msgKeySeq.getAndIncrement();
				// Keep sending
				Frame m = messageCodec.createMessage();
				for (int i = 1; i <= numEvents; i++){
					// LOG.debug("send remaining {}", i);
					long t0 = System.nanoTime();
	                while (pauseTimeNs > (System.nanoTime() - t0))
	                {
	                    Thread.yield();
	                }
					messageCodec.encodeEcho(m, mySvcId, pongSvcId, key, i, System.nanoTime(), BooleanType.FALSE);
					pongRingBuffer.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, m);
				}
			}
		});
	}
	
	private void handleEcho(final Frame message, int srcSvcId, int dstSvcId, EchoSbe codec){
		final EchoResultType result = ServiceUtil.handleEcho(message, receivingMessageCodec, srcSvcId, mySvcId, codec, expectedSeq);
		switch (result){
		case INVALID_SEQ:
			break;
		case OK:
			long currentTime = System.nanoTime();
			long latency = currentTime - codec.startTime();
			totalLatency += latency;
			if (expectedSeq == roundTrips){
				long duration = currentTime - startTime;
				LOG.debug("throughput: {} ops in {} ns: {} rt/sec, latency: {} ns in total, {} ns/trip", 
						roundTrips, duration, (double)roundTrips*TimeUnit.SECONDS.toNanos(1)/duration, totalLatency, totalLatency/roundTrips/2);
				iterations--;
				if (iterations > 0){
					if (iterations == 1){
						sendEventBatch(100);
					}
					else{
						sendEventBatch((int)this.roundTrips);						
					}
				}
				else{
					this.latch.countDown();
				}				
			}
			else{
				expectedSeq++;
			}
			break;
		case OK_TO_SEND_RESPONSE:
			LOG.error("we shouldn't be getting {}", result);
			break;
		default:
			break;
		}
	}
	*/
}
