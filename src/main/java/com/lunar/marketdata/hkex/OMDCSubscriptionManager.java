package com.lunar.marketdata.hkex;

import com.lunar.marketdata.archive.MarketDataSubscriptionManager;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;

public class OMDCSubscriptionManager implements MarketDataSubscriptionManager {

	@Override
	public void subscribe(MessageSinkRef sink, RequestSbeDecoder request) {
		// depending different parameter
	}

	@Override
	public void unsubscribe(MessageSinkRef sink, RequestSbeDecoder request) {
	}

	@Override
	public boolean isSubscribedBySecSid(long secSid){
		return false;
	}
	
	@Override
	public boolean sendToSubscribers(long secSid){
		return false;
	}

	public boolean isSubscribedByMsgTypeId(int msgTypeId){
		return false;
	}
	
	public boolean sendToSubscribers(int msgTypeId){
		return false;
	}
}
