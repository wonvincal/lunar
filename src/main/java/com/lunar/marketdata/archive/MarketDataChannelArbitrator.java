package com.lunar.marketdata.archive;

import java.util.concurrent.atomic.AtomicLong;

import com.lunar.message.binary.MessageCodec;
import com.lunar.util.BitUtil;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * An arbitrator - this can be an interface, or it can also provide the underlying buffer (all these sound weird.)
 * We should be using this per multicast channel
 * 
 * Use case
 * 1) create an arbitrator
 * 2) send events to arbitrator
 * 3) set destination of arbitrator
 * 4) provide ways for exception handling (e.g. gap detected)
 * 
 * @author wongca
 *
 */
public class MarketDataChannelArbitrator {
	public static interface GapHandler {
		void handleDetected(int channelId, long firstMissingSeq, long lastReceivedSeq);
	}
	
	private final AtomicLong nextExpectedSeq;
	private final int channelId;
	/**
	 * publisher/subscribers to the output of this arbitrator
	 */
	private final MarketDataPublisher publisher;
	private final GapHandler gapHandler;
	private final long gapAlertThreshold;
	private volatile long largestReceivedSeq;
	/**
	 * Thread safe
	 */
	private final ByteBasedSpool spool;
	
	public static MarketDataChannelArbitrator of(int capacity, int byteBufferCapacity, MarketDataPublisher publisher, GapHandler handler, long gapAlertThreshold, ByteBasedSpool spool, int channelId){
		return new MarketDataChannelArbitrator(capacity, byteBufferCapacity, publisher, handler, gapAlertThreshold, spool, channelId);
	}
	
	MarketDataChannelArbitrator(int capacity, int byteBufferCapacity, MarketDataPublisher publisher, GapHandler handler, long gapAlertThreshold, ByteBasedSpool spool, int channelId){
		this.nextExpectedSeq = new AtomicLong();
		this.spool = spool;
		this.publisher = publisher;
		this.gapHandler = handler;
		this.gapAlertThreshold = gapAlertThreshold;
		this.largestReceivedSeq = 0;
		this.channelId = channelId;
	}
	
	public int channelId(){
		return channelId;
	}
	
	public long nextExpectedSeq(){
		return nextExpectedSeq.get();
	}

	/**
	 * This method can be accessed by multiple thread.  It needs to be thread safe.
	 * 
	 * Thread safe: Yes
	 * 
	 * @param fromSeq
	 * @param msgCount
	 * @param srcBuffer
	 * @param offset
	 * @param length
	 * @return
	 */
	public boolean publish(long fromSeq, int msgCount, UnsafeBuffer srcBuffer, int offset, int length, MessageCodec codec){
		long expectedNextSeq = this.nextExpectedSeq.get();
		int gap = (int)(fromSeq - expectedNextSeq);
		long nextExpectedNextSeq = fromSeq + msgCount;
		largestReceivedSeq = BitUtil.max(largestReceivedSeq, nextExpectedNextSeq - 1);
		
		if (gap <= 0){
			if (nextExpectedNextSeq >= expectedNextSeq){
				// hold CAS
				while (!this.nextExpectedSeq.compareAndSet(expectedNextSeq, nextExpectedNextSeq)){
					expectedNextSeq = this.nextExpectedSeq.get();
					// lose to CAS, these messages are obsolete - skip
					if (nextExpectedNextSeq < expectedNextSeq){
						return true;
					}
				}
				gap = (int)(fromSeq - expectedNextSeq);
				publisher.publish(fromSeq, 
								  msgCount,
								  gap,
								  srcBuffer, 
								  offset, 
								  length,
								  codec);				
			}
		}
		else {
			// No CAS - this action will be done right away
			spool.store(expectedNextSeq,
						fromSeq, 
						msgCount, 
						srcBuffer, 
						offset,
						length);
			
			// let handler know that a gap is detected
			if (largestReceivedSeq - this.nextExpectedSeq.get() > this.gapAlertThreshold){
				gapHandler.handleDetected(this.channelId, this.nextExpectedSeq.get(), largestReceivedSeq);
			}
		}
		
		// publish from spool if necessary
		// Arb waiting for 101: [ ]
		// spool:                   [ ] [X] [X] [X] [X] [ ]
		//                      101 102 103 104 105 105 106
		//
		// At roughly the same time
		// Time 0010: Thread A: receives 101. 
		// Time 0010: Thread B: receives 102.
		// Time 0011: Thread A: knows the gap is filled and look at the spool, but it cannot find 102.
		// Time 0011: Thread B: checks that 102 hasn't been received, gap is detected, so it spools 102.
		// 
		// arb waiting for 102: [X] [ ]
		// spool:                   [X] [X] [X] [X] [X] [ ]
		//                      101 102 103 104 105 105 106
		//
		// Therefore, we need this logic below to check if the spool has what we want
		expectedNextSeq = this.nextExpectedSeq.get();
		if (spool.isAvailable(expectedNextSeq)){
			long nextEmptySeq = spool.loadAndPublish(expectedNextSeq, publisher, codec);
			if (nextEmptySeq >= expectedNextSeq){
				nextExpectedNextSeq = nextEmptySeq + 1;
				while (!this.nextExpectedSeq.compareAndSet(expectedNextSeq, nextExpectedNextSeq)){
					expectedNextSeq = this.nextExpectedSeq.get();
					// lose to CAS, these messages are obsolete - skip
					if (nextExpectedNextSeq < expectedNextSeq){
						return true;
					}							
				}
			}
		}

		return false;
	}
}
