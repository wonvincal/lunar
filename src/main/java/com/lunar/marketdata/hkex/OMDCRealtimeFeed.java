package com.lunar.marketdata.hkex;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.ServiceConfig;
import com.lunar.marketdata.archive.MarketDataPublisher;
import com.lunar.marketdata.archive.MarketDataRealtimeFeed;
import com.lunar.marketdata.archive.MarketDataSubscriptionManager;

/**
 * Get multicast real time data
 * 
 * msg type, channel id: 
 * 303, 101
 * 305, 101
 * 303, 201
 * 305, 201
 * 301, 151
 * 302, 151
 * 304, 151
 * 320, 161
 * 321, 161
 * 322, 161
 * 336, 164
 * 366, 191
 * 364, 131
 * 353, 131
 * 350, 131
 * 364, 231
 * 353, 231
 * 350, 231
 * 350, 167
 * 356, 167
 * 363, 174
 * 323, 177
 * 365, 199
 * 367, 194
 * 
 * 303, 102
 * 305, 102 
 *
 * @author wongca
 *
 */
@SuppressWarnings("unused")
public final class OMDCRealtimeFeed extends MarketDataRealtimeFeed {
	private static final Logger LOG = LogManager.getLogger(OMDCRealtimeFeed.class);
	private final ExecutorService rtExecutor;
	private final OMDCChannel[] channels;
	private final OMDCSubscriptionManager subscriptionManager;
	private final OMDCRetransmissionService retranService;
	private final ExecutorService retransExecutor;
	private final Disruptor<RetransmissionCommand> retransDisruptor;

	@SuppressWarnings("unchecked")
	public OMDCRealtimeFeed(ServiceConfig config){
		super(config);
		
		// use that channel arbitrator
		this.subscriptionManager = new OMDCSubscriptionManager();
		this.retranService = OMDCRetransmissionService.of();
		// create retransmission
		this.retransExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("omdc-retrans", "omdc-retrans"));
		this.retransDisruptor = new Disruptor<>(new EventFactory<RetransmissionCommand>() {
				@Override
				public RetransmissionCommand newInstance() {
					return new RetransmissionCommand();
				}
			}, 100, retransExecutor);
		this.retransDisruptor.handleEventsWith(retranService);

		// create a set of channel arbitrators from configuration file
		this.rtExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("omdc-rt",  "omdc-rt"));
		int numChannels = 20;
		channels = new OMDCChannel[numChannels];
		
		MarketDataPublisher secBasedPub = OMDCSecurityBasedMarketDataPublisher.of(subscriptionManager);
		MarketDataPublisher msgTypeBasedPub = OMDCMessageTypeBasedMarketDataPublisher.of(subscriptionManager); 
		for (int i = 0; i < numChannels; i++){
			OMDCChannel channel = OMDCChannel.of(i, String.valueOf(i), (i % 2 == 0) ? secBasedPub : msgTypeBasedPub, new ChannelArbitratorBuilder(), this.retransDisruptor.getRingBuffer());
			channels[i] = channel;
			CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> { channel.run(); return true; }, this.rtExecutor);
			
			// TODO attach the exception handler here using future
			// let's think...we need to restart the service if it fails, unless we stop it intentionally
			future.exceptionally(new Function<Throwable, Boolean>() {
				@Override
				public Boolean apply(Throwable t) {
					// TODO Auto-generated method stub
					return null;
				}
			});
		}
	}

	@Override
	public MarketDataSubscriptionManager subscriptionManager(){
		return subscriptionManager;
	}
	
	@Override
	public void run() {
		Thread thread = Thread.currentThread();
		try{
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			long updateTime = 0;
			while (!thread.isInterrupted()){
//				updateTime = this.mdInfoMap.volatileUpdateTime();
				int id = 100;
				Object data = null;
//				this.mdInfoMap.publish(id, data);
			}
			LOG.info("run stopped, last update time was {}", updateTime);
		}
		catch (Exception e)
		{
			LOG.error("buffer receive failed, exiting...", e);
		}
	}
}
