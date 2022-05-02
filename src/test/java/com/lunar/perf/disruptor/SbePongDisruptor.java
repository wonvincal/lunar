package com.lunar.perf.disruptor;

public class SbePongDisruptor {
	/*
	private static final int FRAME_SIZE = 64;
	private static final Logger LOG = LogManager.getLogger(SbePongDisruptor.class);
	private final MessageCodec messageCodec;
	private final ExecutorService executor;
	private final Disruptor<Frame> disruptor;
	private RingBuffer<Frame> senderRingBuffer;
	private final Frame frameBuffer;
	private final int mySvcId;
	private long totalLatency = 0;
	private int messageReceived = 0;

	@SuppressWarnings("unchecked")
	public SbePongDisruptor(int mySvcId, ExecutorService executor, int ringBufferSize){
		this.messageCodec = MessageCodec.of();
		this.messageCodec.addEchoHandler(this::handleEcho);
		this.frameBuffer = messageCodec.createMessage();
		this.mySvcId = mySvcId;

		this.executor = executor;
		this.disruptor = new Disruptor<Frame>(new MessageSinkEventFactory(FRAME_SIZE), ringBufferSize, executor);
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
	}

	public SbePongDisruptor senderRingBuffer(RingBuffer<Frame> senderRingBuffer){
		this.senderRingBuffer = senderRingBuffer;
		return this;
	}
	
	public RingBuffer<Frame> start(){
		LOG.debug("started");
		return this.disruptor.start();
	}
	
	public void stop(){
		this.disruptor.shutdown();
		this.executor.shutdown();
		LOG.debug("shutdown");
	}
	
	private void handleEvent(Frame message, long sequence, boolean endOfBatch){
		// LOG.debug("handleEvent");
		messageCodec.receive(message);
	}

	private void handleEcho(final Frame message, int srcSvcId, int dstSvcId, final EchoSbe codec){
		// LOG.debug("got echo");
		final EchoResultType result = ServiceUtil.handleEcho(message, messageCodec, srcSvcId, mySvcId, codec, -1);
		
		switch (result){
		case INVALID_SEQ:
			break;
		case OK:
			LOG.error("we shouldn't be getting {}", result);
			break;
		case OK_TO_SEND_RESPONSE:
			int seq = codec.seq();
			messageReceived++;
			long latency = System.nanoTime() - codec.startTime();
			totalLatency += latency;
			if (seq == 1){
				LOG.debug("latency: received {} in {} ns, {} ns/one-way", messageReceived, totalLatency, totalLatency/messageReceived);
				totalLatency = 0;
				messageReceived = 0;
			}
			// LOG.debug("sending seq:{}, isResponse:{} back", codec.seq(), BooleanType.TRUE);
			messageCodec.encodeEcho(frameBuffer, mySvcId, srcSvcId, codec.key(), codec.seq(), codec.startTime(), BooleanType.TRUE);
			senderRingBuffer.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, frameBuffer);
			break;
		default:
			break;
		}
	}
	*/
}
