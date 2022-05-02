package performance.baseline1;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.MessageBuffer;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.EchoSbeDecoder;

import performance.baseline0.PerfHelper;

@SuppressWarnings("unused")
public class SbePongerService implements EventHandler<Frame>, LifecycleAware {

	private final MessageCodec messageCodec;
    private final PerfHelper helper;
    private final Frame frameBuffer;
    private final RingBuffer<Frame> receiver;

	public SbePongerService(RingBuffer<Frame> receiver){
		this.messageCodec = MessageCodec.of();
		helper = new PerfHelper();
		frameBuffer = MessageBuffer.frameBuferOnly(helper.frameSize());
		this.receiver = receiver;
	}
	
	@Override
	public void onStart() {
//		messageCodec.decoder().addEchoHandler(this::handleEcho);
	}

	@Override
	public void onShutdown() {}

	@Override
	public void onEvent(Frame event, long sequence, boolean endOfBatch) throws Exception {
//		messageCodec.decoder().receive(event);
	}
	
	private void handleEcho(Frame frame, int senderSinkId, int dstSinkId, EchoSbeDecoder codec) {
//		messageCodec.encoder().encodeEcho(frameBuffer, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, codec.seq(), codec.seq(), codec.startTime(), BooleanType.TRUE);
//		receiver.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, frameBuffer);
	}

}
