package com.lunar.marketdata.archive;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.TimerService;
import com.lunar.message.sink.MessageSinkRef;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A feed that does the following:
 * 1. build snapshot (e.g. from external sources)
 * 2. send snapshots to parent periodically
 * @author wongca
 *
 */
@SuppressWarnings("unused")
public class MarketDataSnapshotFeed implements Runnable {
	private static final int DEFAULT_BROADCAST_FREQ_NS = 2000000;
	private static final int DEFAULT_EXPECTED_SEC_UPDATED_WITHIN_BROADCAST_PERIOD = 200;
	private static final Logger LOG = LogManager.getLogger(MarketDataSnapshotFeed.class);

	
	private final MessageSinkRef parent;
	private final TimerService timerService;
	private volatile long broadcastFreqInNs;
	private volatile boolean shouldBroadcast = false;
	private final TimerTask broadcastTimerTask;
	private Timeout timeout;
	private final MarketDataInfoBitSet mdi;
	private final ObjectArrayList<MarketOrderBook> updated;
	
	public static MarketDataSnapshotFeed of(MessageSinkRef parent, TimerService timerService, MarketDataInfoBitSet mdi){
		return new MarketDataSnapshotFeed(parent, timerService, mdi, DEFAULT_BROADCAST_FREQ_NS, DEFAULT_EXPECTED_SEC_UPDATED_WITHIN_BROADCAST_PERIOD);
	}
	
	MarketDataSnapshotFeed(MessageSinkRef parent, TimerService timerService, MarketDataInfoBitSet mdi, int broadcastFreqInNs, int numUpdatedSecsInBroadcastPeriod){
		this.parent = parent;
		this.mdi = mdi;
		this.timerService = timerService;
		this.broadcastFreqInNs = broadcastFreqInNs;
		this.broadcastTimerTask = new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				shouldBroadcast = true;
			}
		};
		this.updated = new ObjectArrayList<MarketOrderBook>(numUpdatedSecsInBroadcastPeriod);
	}

	public MarketDataSnapshotFeed broadcastFreqInNs(long value){
		this.broadcastFreqInNs = value;
		return this;
	}
	
	@Override
	public void run() {
		Thread thread = Thread.currentThread();
		// on updates, send it off to 
		timerService.newTimeout(this.broadcastTimerTask, this.broadcastFreqInNs, TimeUnit.NANOSECONDS);
		try {
			while (!thread.isInterrupted()){
				// spawn out more threads to handle data from external sources
				long updateTime = mdi.volatileUpdateTime();				
				if (shouldBroadcast){
					shouldBroadcast = false;
					broadcast();
					timeout = timerService.newTimeout(this.broadcastTimerTask, this.broadcastFreqInNs, TimeUnit.NANOSECONDS);
				}
			}
			LOG.info("market data snapshot feed stopped");
		}
		catch (Exception e){
			LOG.error("market data snapshot feed is down", e);
		}
		if (timeout != null){
			timeout.cancel();
			timeout = null;
		}
	}
	
	private void broadcast(){
		updated.clear();
	}
}
