package performance.baseline0;

import java.io.PrintStream;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.HdrHistogram.Histogram;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class SbePingPongSequencedLatencyTest {
    private final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
    private final PerfHelper helper = new PerfHelper();
    private final Histogram histogram;
    
    public SbePingPongSequencedLatencyTest(){
    	histogram = helper.createHistogram();
    }

    public static void main(final String[] args) throws Exception
    {
        final SbePingPongSequencedLatencyTest test = new SbePingPongSequencedLatencyTest();
        test.shouldCompareDisruptorVsQueues();
    } 

    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        final int runs = 5;

        for (int i = 0; i < runs; i++)
        {
            System.gc();
            histogram.reset();

            if (i == runs - 1){
            	helper.iterations(100L);
            }
            runDisruptorPass();

            System.out.format("%s run %d Sbe Disruptor %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(histogram, System.out);
        }
    }

    private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
    	out.println("Histogram in microseconds");
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }

    private void runDisruptorPass() throws InterruptedException, BrokenBarrierException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final CyclicBarrier barrier = new CyclicBarrier(3);

    	SbePinger pinger = new SbePinger(executor, ProducerType.MULTI, new BusySpinWaitStrategy(), helper.bufferSize(), helper.iterations(), helper.pauseTimeNs());
    	SbePonger ponger = new SbePonger(executor, ProducerType.MULTI, new BusySpinWaitStrategy(), helper.bufferSize());
    	
    	pinger.sendRingBuffer(ponger.recvRingBuffer());
    	ponger.sendRingBuffer(pinger.recvRingBuffer());

        pinger.reset(barrier, latch, histogram);
        ponger.reset(barrier);
        
        pinger.start();
        ponger.start();

        barrier.await();
        latch.await();

        pinger.stop();
        ponger.stop();
    }

}
