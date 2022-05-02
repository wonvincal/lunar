package com.lunar.marketdata.archive;

/**
 * Some type of buffering that looks at sequence number
 * 
 * The size of buffer must not be unlimited. if you keep receiving realtime updates without getting any snapshot, you should
 * begin throwing away real-time updates.  The buffer should only be the number of possible updates / second x snapshot frequency + 1 
 * 
 * Should provide a handler for overflow, gap detection 
 * 
 * If a gap is detected, we should buffer things out, at the same time notify the owner
 * 
 * 
 * Below is pseudo code for implementing a buffer layer, not fastest at the moment.
 * but it should work
 * 
 * @author Calvin
 *
 */
public class MarketDataBuffer2 {

	int expectedSeq;
	boolean broadcastUpdate = false;
	
	void handleUpdate(int seq){
		if (!broadcastUpdate){
//			store update in buffer
			return;
		}
		if (seq == expectedSeq){
			expectedSeq++;
//			forward update downstream;
			return;
		}
		broadcastUpdate = false;
//		store update in buffer
	}
	
	void handleSnapshot(int seq){
		if (!broadcastUpdate){
//			forward ss;
			expectedSeq = seq;
			broadcastUpdate = true;
			forwardUpdate();
		}
	}
	
	void forwardUpdate(){
//		clear buffered updates for smaller seq
//		forward remaining in sequence buffered updates
//		if gap detected, broadcastUpdate = false;
	}
}
