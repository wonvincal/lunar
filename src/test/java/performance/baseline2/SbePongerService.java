package performance.baseline2;

import com.lmax.disruptor.RingBuffer;
import com.lunar.core.CommandTracker;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.EchoSbeDecoder;

@SuppressWarnings("unused")
public final class SbePongerService implements ServiceLifecycleAware {

	private final MessageCodec messageCodec;
    private final Frame frameBuffer;
    private final RingBuffer<Frame> receiver;

	public SbePongerService(RingBuffer<Frame> receiver, MessageCodec messageCodec, Frame frameBuffer){
		this.messageCodec = messageCodec;
		this.frameBuffer = frameBuffer;
		this.receiver = receiver;
	}
	
	private void handleEcho(Frame frame, int senderSinkId, int dstSinkId, EchoSbeDecoder codec) {
/*		messageCodec.encoder().encodeEcho(frameBuffer, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, codec.seq(), codec.seq(), codec.startTime(), BooleanType.TRUE);
		receiver.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, frameBuffer);
*/	}

	@Override
	public StateTransitionEvent idleStart(ServiceStatusTracker serviceTracker,
										  RequestTracker requestTracker, 
										  CommandTracker commandTracker) {
		return StateTransitionEvent.NULL;
	}

	@Override
	public StateTransitionEvent idleRecover(ServiceStatusTracker serviceTracker, 
											RequestTracker requestTracker,
											CommandTracker commandTracker) {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent waitingForServicesEnter(ServiceStatusTracker serviceTracker, 
														RequestTracker requestTracker,
														CommandTracker commandTracker) {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForServicesExit() {
	}

	@Override
	public StateTransitionEvent activeEnter(ServiceStatusTracker serviceTracker, 
											RequestTracker requestTracker,
											CommandTracker commandTracker) {
//		messageCodec.decoder().addEchoHandler(this::handleEcho);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit(ServiceStatusTracker serviceTracker,
			RequestTracker requestTracker, CommandTracker commandTracker) {
//		messageCodec.decoder().removeEchoHandler(this::handleEcho);
	}

	@Override
	public StateTransitionEvent stopEnter(ServiceStatusTracker serviceTracker,
										  RequestTracker requestTracker, 
										  CommandTracker commandTracker) {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stopExit() {
	}

	@Override
	public StateTransitionEvent stoppedEnter(ServiceStatusTracker serviceTracker, 
											 RequestTracker requestTracker,
											 CommandTracker commandTracker) {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit(ServiceStatusTracker serviceTracker,
							RequestTracker requestTracker, 
							CommandTracker commandTracker) {
	}

}
