package com.lunar.marketdata.hkex;

import com.lunar.marketdata.archive.MarketDataPublisher;
import com.lunar.message.binary.MessageCodec;

import org.agrona.concurrent.UnsafeBuffer;

public final class OMDCSecurityBasedMarketDataPublisher implements MarketDataPublisher{
	@SuppressWarnings("unused")
	private final OMDCSubscriptionManager subMgr;

	public static OMDCSecurityBasedMarketDataPublisher of(OMDCSubscriptionManager subMgr){
		return new OMDCSecurityBasedMarketDataPublisher(subMgr);
	}
	
	OMDCSecurityBasedMarketDataPublisher(OMDCSubscriptionManager subMgr){
		this.subMgr = subMgr;
	}
	
	@Override
	public boolean publish(UnsafeBuffer buffer, int offset, int length, MessageCodec codec) {
		// extract security id from buffer
		// check if subscribed
		// publish
		return false;
	}

	@Override
	public boolean publish(long seq, int msgCount, int msgOffset, UnsafeBuffer buffer, int offset, int length, MessageCodec codec) {
		// TODO Auto-generated method stub
		return false;
	}

}
