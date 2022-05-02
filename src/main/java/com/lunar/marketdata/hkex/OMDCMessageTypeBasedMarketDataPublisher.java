package com.lunar.marketdata.hkex;

import com.lunar.marketdata.archive.MarketDataPublisher;
import com.lunar.message.binary.MessageCodec;

import org.agrona.concurrent.UnsafeBuffer;

public final class OMDCMessageTypeBasedMarketDataPublisher implements MarketDataPublisher{
	@SuppressWarnings("unused")
	private final OMDCSubscriptionManager subMgr;

	public static OMDCMessageTypeBasedMarketDataPublisher of(OMDCSubscriptionManager subMgr){
		return new OMDCMessageTypeBasedMarketDataPublisher(subMgr);
	}
	
	OMDCMessageTypeBasedMarketDataPublisher(OMDCSubscriptionManager subMgr){
		this.subMgr = subMgr;
	}

	@Override
	public boolean publish(long seq, int msgCount, int msgOffset, UnsafeBuffer buffer, int offset, int length, MessageCodec codec) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean publish(UnsafeBuffer buffer, int offset, int length, MessageCodec codec) {
		// TODO Auto-generated method stub
		return false;
	}

}
