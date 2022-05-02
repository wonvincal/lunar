package performance.baseline3;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.CommandTracker;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.message.MessageBuffer;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.EchoSbeDecoder;

@SuppressWarnings("unused")
public final class SbePongerService implements ServiceLifecycleAware {
	static final Logger LOG = LogManager.getLogger(SbePongerService.class);

	private final int receiverSinkId;
    private MessageCodec messageCodec;
	private MessageBuffer messageBuffer;
    private Frame frameBuffer;

	public SbePongerService(int receiverSinkId){
		this.receiverSinkId = receiverSinkId;
	}
	
	private void handleEcho(Frame frame, int senderSinkId, int dstSinkId, EchoSbeDecoder codec) {
//		LOG.info("sending echo back to sink {}", receiverSinkId);
//		messageCodec.encoder().encodeEcho(frameBuffer, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, codec.seq(), codec.seq(), codec.startTime(), BooleanType.TRUE);
//		messagingContext.publish(receiverSinkId, frameBuffer, messageBuffer.aeronBuffer());
	}

	@Override
	public StateTransitionEvent idleStart(BaseStateService baseStateService,
										  ServiceStatusTracker serviceTracker,
										  RequestTracker requestTracker, 
										  CommandTracker commandTracker) {
		messageCodec = baseStateService.messageCodec();
//		messagingContext = baseStateService.messagingContext();
		messageBuffer = baseStateService.messageBuffer();
		frameBuffer = baseStateService.frameBuffer();
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
		LOG.info("activeEnter - registered echo handler");
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
