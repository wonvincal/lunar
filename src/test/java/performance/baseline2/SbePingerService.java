package performance.baseline2;

import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

import org.HdrHistogram.Histogram;

import com.lmax.disruptor.RingBuffer;
import com.lunar.core.CommandTracker;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.EchoSbeDecoder;

import performance.baseline0.PerfHelper;

@SuppressWarnings("unused")
public final class SbePingerService implements ServiceLifecycleAware {

	private final MessageCodec messageCodec;
    private long counter = 0;
    private final Histogram histogram;
    private long t0;
    private long t1;
    private int runs;
    private final Frame frameBuffer;
    private final RingBuffer<Frame> receiver;
    private final CountDownLatch latch;
    private final long iterations;
    private final long pauseTimeNs;
    private final PerfHelper helper;

	public SbePingerService(RingBuffer<Frame> receiver, CountDownLatch latch, MessageCodec messageCodec, Frame frameBuffer){
		runs = 6;
		this.messageCodec = messageCodec;
		this.frameBuffer = frameBuffer;
		this.receiver = receiver;
		this.latch = latch;
		
		this.helper = new PerfHelper();
		this.histogram = helper.createHistogram();
		this.iterations = helper.iterations();
		this.pauseTimeNs = helper.pauseTimeNs();
	}
	
	private void handleEcho(Frame frame, int senderSinkId, int dstSinkId, EchoSbeDecoder codec) {
		t1 = System.nanoTime();
        histogram.recordValueWithExpectedInterval(t1 - t0, helper.pauseTimeNs());
//        long seq = codec.seq();
//        if (seq < iterations)
//        {
//            while (pauseTimeNs > (System.nanoTime() - t1))
//            {
//                Thread.yield();
//            }
//            sendEcho();
//        }
//        else {
//        	histogram.outputPercentileDistribution(System.out, 1, 1000.0);
//         	counter = 0;
//        	runs --;
//        	if (runs > 0){
//        		dumpHistogram(histogram, System.out);
//        		histogram.reset();
//                System.gc();
//        		sendEcho();
//        	}
//        	else {
//        		latch.countDown();
//        	}
//        }
	}

    private void sendEcho(){
//        t0 = System.nanoTime();
//		messageCodec.encoder().encodeEcho(frameBuffer, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, (int)counter, (int)counter, System.nanoTime(), BooleanType.FALSE);
//		receiver.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, frameBuffer);
//        counter++;    	
    }

    private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
    	out.println("Histogram in microseconds");
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }

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
//		sendEcho();
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit(ServiceStatusTracker serviceTracker,
						   RequestTracker requestTracker, 
						   CommandTracker commandTracker) {
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
