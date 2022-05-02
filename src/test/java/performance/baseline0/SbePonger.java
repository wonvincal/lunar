package performance.baseline0;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;

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

public final class SbePonger implements EventHandler<MutableDirectBuffer>, LifecycleAware {
	private static final int FRAME_SIZE = 64;
	private final Disruptor<MutableDirectBuffer> disruptor;
    private final MessageSender sender;
    private final EchoSender echoSender;
	private MessageSinkRef sinkRef;
	private final MessageReceiver receiver;
	private RingBuffer<MutableDirectBuffer> sendRingBuffer;
    private CyclicBarrier barrier;

	@SuppressWarnings("unchecked")
	public SbePonger(final ExecutorService executor, ProducerType producerType, WaitStrategy waitStrategy, final int ringBufferSize){
		this.disruptor = new Disruptor<MutableDirectBuffer>(new MessageSinkEventFactory(FRAME_SIZE), ringBufferSize, executor, producerType, waitStrategy);
		this.disruptor.handleEventsWith(this);

		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-any");
		sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				  refSenderSink);
		echoSender = EchoSender.of(sender);
		receiver = MessageReceiver.of();
		receiver.echoHandlerList().add(this::handleEcho);
	}
	
    public SbePonger sendRingBuffer(final RingBuffer<MutableDirectBuffer> buffer){
    	this.sendRingBuffer = buffer;
    	this.sinkRef = MessageSinkRef.of(TestMessageSinkBuilder.createRingBufferMessageSink(1, 2, ServiceType.EchoService, "pong", this.sendRingBuffer));
    	return this;
    }
    
    public RingBuffer<MutableDirectBuffer> recvRingBuffer(){
    	return this.disruptor.getRingBuffer();
    }

	@Override
	public void onStart() {
        try
        {
            barrier.await();
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }	
    }

	@Override
	public void onShutdown() {
	}
	
	@Override
    public void onEvent(final MutableDirectBuffer event, final long sequence, final boolean endOfBatch) throws Exception
    {
        this.receiver.receive(event, 0);
    }

	private void handleEcho(final DirectBuffer buffer, int offset, MessageHeaderDecoder header, EchoSbeDecoder codec){
    	echoSender.sendEcho(sinkRef, header.seq(), codec.startTime(), BooleanType.TRUE);
	}

    public void reset(final CyclicBarrier barrier)
    {
        this.barrier = barrier;
    }
    
    public void start(){
    	this.disruptor.start();
    }
    
    public void stop(){
    	this.disruptor.shutdown();
    }
}
