package com.lunar.marketdata.archive;

import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;

public interface MarketDataSubscriptionManager {
	void subscribe(MessageSinkRef sink, RequestSbeDecoder request);
	void unsubscribe(MessageSinkRef sink, RequestSbeDecoder request);
	boolean isSubscribedBySecSid(long secSid);
	boolean sendToSubscribers(long secSid);
//	boolean isSubscribedBySecSid(long secSid);
//		return false;
//	}
//	public boolean sendIfSubscribedBySecId(long secSid){
//		return false;
//	}
}
