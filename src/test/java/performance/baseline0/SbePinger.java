package performance.baseline0;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;

import org.HdrHistogram.Histogram;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.MessageSinkEventFactory;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EchoSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.EchoSender;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.TestMessageSinkBuilder;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class SbePinger implements EventHandler<MutableDirectBuffer>, LifecycleAware {
	private static final int FRAME_SIZE = 64;
	private final Disruptor<MutableDirectBuffer> disruptor;
	private RingBuffer<MutableDirectBuffer> sendRingBuffer;
    private final long maxEvents;
    private final long pauseTimeNs;
    private final MessageSender sender;
    private final EchoSender echoSender;
    private final MessageReceiver receiver;
    private MessageSinkRef sinkRef;

    private long counter = 0;
    private long expected = 0;
    private CyclicBarrier barrier;
    private CountDownLatch latch;
    private Histogram histogram;
    private long t0;
    private long t1;

    @SuppressWarnings("unchecked")
	public SbePinger(final ExecutorService executor, ProducerType producerType, WaitStrategy waitStrategy, final int ringBufferSize, final long maxEvents, final long pauseTimeNs){
    	this.disruptor = new Disruptor<MutableDirectBuffer>(new MessageSinkEventFactory(FRAME_SIZE), ringBufferSize, executor, producerType, waitStrategy);
		this.disruptor.handleEventsWith(this);
        this.maxEvents = maxEvents;
        this.pauseTimeNs = pauseTimeNs;
        
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-pinger");
		sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
								  refSenderSink);
		echoSender = EchoSender.of(sender);
		receiver = MessageReceiver.of();
		receiver.echoHandlerList().add(this::handleEcho);
    }

    public SbePinger sendRingBuffer(final RingBuffer<MutableDirectBuffer> buffer){
    	this.sendRingBuffer = buffer;
    	this.sinkRef = MessageSinkRef.of(TestMessageSinkBuilder.createRingBufferMessageSink(1, 1, ServiceType.EchoService, "ping", this.sendRingBuffer));
    	return this;
    }
    
    public RingBuffer<MutableDirectBuffer> recvRingBuffer(){
    	return this.disruptor.getRingBuffer();
    }
    
	private void handleEcho(final DirectBuffer message, int offset, MessageHeaderDecoder header, EchoSbeDecoder codec){
        histogram.recordValueWithExpectedInterval(t1 - t0, pauseTimeNs);
        int seq = header.seq();
        if (seq != expected){
            latch.countDown();        	
        }
        expected++;
        if (seq < maxEvents)
        {
            while (pauseTimeNs > (System.nanoTime() - t1))
            {
                Thread.yield();
            }
            send();
        }
        else
        {
            latch.countDown();
        }
	}
	
	@Override
    public void onEvent(final MutableDirectBuffer event, final long sequence, final boolean endOfBatch) throws Exception {
        t1 = System.nanoTime();
        this.receiver.receive(event, 0);
    }

    
    private void send(){
        t0 = System.nanoTime();
    	echoSender.sendEcho(sinkRef, (int)counter, System.nanoTime(), BooleanType.FALSE);
        counter++;
    }
    
    @Override
    public void onStart(){
    	try{
    		barrier.await();
    		Thread.sleep(1000);
    		send();
    	}
    	catch (Exception e){
    		throw new RuntimeException(e);
    	}
    }
    
    @Override
    public void onShutdown(){
    }
    
    public void reset(final CyclicBarrier barrier, final CountDownLatch latch, final Histogram histogram)
    {
        this.histogram = histogram;
        this.barrier = barrier;
        this.latch = latch;

        counter = 0;
        expected = counter;
    } 

    
    public void start(){
    	this.disruptor.start();
    }
    
    public void stop(){
    	this.disruptor.shutdown();
    }
}
