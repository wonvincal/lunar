package performance.baseline1;

import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

import org.HdrHistogram.Histogram;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.MessageBuffer;
import com.lunar.message.binary.Frame;
import com.lunar.message.binary.MessageCodec;
import com.lunar.message.io.sbe.EchoSbeDecoder;

import performance.baseline0.PerfHelper;

@SuppressWarnings("unused")
public class SbePingerService implements EventHandler<Frame>, LifecycleAware {

	private final MessageCodec messageCodec;
    private long counter = 0;
    private final Histogram histogram;
    private long t0;
    private long t1;
    private final PerfHelper helper;
    private int runs;
    private final Frame frameBuffer;
    private final RingBuffer<Frame> receiver;
    private final CountDownLatch latch;
    private final long iterations;
	private final long pauseTimeNs;

	public SbePingerService(RingBuffer<Frame> receiver, CountDownLatch latch){
		this.messageCodec = MessageCodec.of();
		helper = new PerfHelper();
		runs = 6;
		frameBuffer = MessageBuffer.frameBuferOnly(helper.frameSize());
		this.receiver = receiver;
		this.latch = latch;
		this.histogram = helper.createHistogram();
		this.iterations = helper.iterations();
		this.pauseTimeNs = helper.pauseTimeNs();
	}
	
	@Override
	public void onStart() {
//		messageCodec.decoder().addEchoHandler(this::handleEcho);
		sendEcho();
	}

	@Override
	public void onShutdown() {}

	@Override
	public void onEvent(Frame event, long sequence, boolean endOfBatch) throws Exception {
//		messageCodec.decoder().receive(event);
	}
	
	private void handleEcho(Frame frame, int senderSinkId, int dstSinkId, EchoSbeDecoder codec) {
		t1 = System.nanoTime();
        histogram.recordValueWithExpectedInterval(t1 - t0, helper.pauseTimeNs());
        /*long seq = codec.seq();
        if (seq < iterations)
        {
            while (pauseTimeNs > (System.nanoTime() - t1))
            {
                Thread.yield();
            }
            sendEcho();
        }
        else {
        	histogram.outputPercentileDistribution(System.out, 1, 1000.0);
         	counter = 0;
        	runs --;
        	if (runs > 0){
        		dumpHistogram(histogram, System.out);
        		histogram.reset();
                System.gc();
        		sendEcho();
        	}
        	else {
        		latch.countDown();
        	}
        }*/
	}

    private void sendEcho(){
/*        t0 = System.nanoTime();
		messageCodec.encoder().encodeEcho(frameBuffer, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, ServiceConstant.SERVICE_ID_NOT_APPLICABLE, (int)counter, (int)counter, System.nanoTime(), BooleanType.FALSE);
		receiver.publishEvent(FrameTranslator.FRAME_EVENT_TRANSLATOR, frameBuffer);
        counter++;    	
*/    }

	private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
    	out.println("Histogram in microseconds");
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }
}
