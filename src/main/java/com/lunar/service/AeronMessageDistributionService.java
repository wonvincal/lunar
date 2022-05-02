package com.lunar.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.CommunicationConfig;
import com.lunar.core.Lifecycle;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.sink.MessageSink;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;

/**
 * Distribute incoming data to local message sink
 * @author wongca
 *
 */
public class AeronMessageDistributionService implements Lifecycle {
	static final Logger LOG = LogManager.getLogger(AeronMessageDistributionService.class);
	private final Messenger messenger;
	private final String name;
	private final AeronSubscription[] aeronSubscriptions;
	private static final int FRAGMENT_COUNT_LIMIT = 256;
	private final ExecutorService executorService;
	private final AtomicBoolean running;

	private final LifecycleController controller;
	private CompletableFuture<Boolean> runningFuture;
	private CompletableFuture<Boolean> stopFuture;
	
	private LifecycleExceptionHandler lifecycleExceptionHandler = LifecycleExceptionHandler.DEFAULT_HANDLER; 
	private final LifecycleStateHook lifecyclePendingStateHandler = new LifecycleStateHook() {
		@Override
		public CompletableFuture<Boolean> onPendingStop() {
			if (!AeronMessageDistributionService.this.running.compareAndSet(true, false)){
				LOG.warn("Service is already stopped");
				return CompletableFuture.completedFuture(false);
			}
			for (int i = 0; i < aeronSubscriptions.length; i++){
				Subscription subscription = aeronSubscriptions[i].subcription;
				if (!subscription.isClosed()){
					LOG.info("Closing aeron subscription [channel:{}, streamId:{}]", subscription.channel(), subscription.streamId());
					subscription.close();
				}
			}
			return stopFuture;
		};
		@Override
		public CompletableFuture<Boolean> onPendingActive() {
			if (!AeronMessageDistributionService.this.running.compareAndSet(false, true)){
				LOG.warn("Service is already running");
				return CompletableFuture.completedFuture(false);
			}
			runningFuture = new CompletableFuture<Boolean>();
			stopFuture = new CompletableFuture<Boolean>();
			executorService.execute(core);
			return runningFuture;
		};
		@Override
		public void onActiveEnter() {
		};		
		@Override
		public void onStopped() {
			AeronMessageDistributionService.this.running.set(false);			
		};
	};
	
	public static AeronMessageDistributionService of(String name, Aeron aeron, List<CommunicationConfig> configs, Messenger newMessenger){
		return new AeronMessageDistributionService(name, aeron, configs, newMessenger);
	}
	
	AeronMessageDistributionService(String name, Aeron aeron, List<CommunicationConfig> configs, Messenger messenger){
		this.name = name;
		this.messenger = messenger;
		this.aeronSubscriptions = new AeronSubscription[configs.size()];
		for (int i = 0; i < configs.size(); i++){
			CommunicationConfig config = configs.get(i);
			if (config.streamId() > configs.size()){
				throw new IllegalArgumentException("Aeron stream ID must be a zero-based index value less than number of total streams [streamId:" + config.streamId() + ", numStreams:" + config.channel() + "]");
			}
			this.aeronSubscriptions[i] = AeronSubscription.of(aeron.addSubscription(config.channel(), config.streamId()));
		}
		this.running = new AtomicBoolean(false);
		this.controller = LifecycleController.of(this.name + "-dist-service", lifecyclePendingStateHandler);
		this.executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory("aeron", "aeron-dist-service"));
	}
	
	public static class AeronSubscription {
		private final Subscription subcription;
		static AeronSubscription of(Subscription subscription){
			return new AeronSubscription(subscription);
		}
		AeronSubscription(Subscription subscription){
			this.subcription = subscription;
		}
	}
	
	public final Runnable core = new Runnable(){
		public void run() {
			LOG.info("{} started", name);

			// Connect to the exchange
			final IdleStrategy idleStrategy = new BackoffIdleStrategy(
					100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MICROSECONDS.toNanos(100));

			// Not sure if message polling is done on separate thread for different (channel, stream)
			MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
			FragmentAssembler fragmentAssembler = new FragmentAssembler(reassembledSbeMessage(messenger, headerDecoder));

			runningFuture.complete(true);
			while (running.get()){
				try{
					int totalFragmentsRead = 0;
					// We can adjust fragment count according to its template type.  
					for (int i = 0; i < aeronSubscriptions.length; i++){
						AeronSubscription aeronSubscription = aeronSubscriptions[i];
						totalFragmentsRead += aeronSubscription.subcription.poll(fragmentAssembler, FRAGMENT_COUNT_LIMIT);
					}
					idleStrategy.idle(totalFragmentsRead);
				}
				catch (Throwable t){
					LOG.error("Running thread caught exception", t);
					if (!lifecycleExceptionHandler.handle(controller.state(), t)){
						running.set(false);					
					}
				}
			}
			stopFuture.complete(true);
			LOG.info("{} has been stopped", name);
		}
	};
	
	public static FragmentHandler reassembledSbeMessage(Messenger messenger, MessageHeaderDecoder messageHeader) {
		return (buffer, offset, length, header) ->
		{
			// Send message
			messageHeader.wrap(buffer, offset);
			final int templateId = messageHeader.templateId();
			final byte dstSinkId = messageHeader.dstSinkId();
			
			long result = messenger.trySend(dstSinkId, buffer, offset, length);
			if (result != MessageSink.OK){
				LOG.error("Could not send message to sink [dstSinkId:{}, templateId:{}]", dstSinkId, templateId);
			}

			LOG.info("message to stream %d from session %x term id %x term offset %d (%d@%d)%n",
					header.streamId(), header.sessionId(), header.termId(), header.termOffset(), length, offset);

			if (length != 10000)
			{
				System.out.format(
						"Received message was not assembled properly; received length was %d, but was expecting 10000%n",
						length);
			}
		};
	}
	
	public String name(){
		return name;
	}


	@Override
	public LifecycleController controller() {
		return controller;
	}

	@Override
	public void lifecycleExceptionHandler(LifecycleExceptionHandler handler) {
		this.lifecycleExceptionHandler = handler;
	}
	
	boolean running(){
		return running.get();
	}
}
