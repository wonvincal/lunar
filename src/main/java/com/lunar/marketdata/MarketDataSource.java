package com.lunar.marketdata;

import java.util.Collection;

import com.lunar.entity.Security;
import com.lunar.message.io.sbe.MarketStatusType;

public interface MarketDataSource {
    public interface CallbackHandler {
    }
    
	MarketStatusType marketStatus();
	
    MarketDataSource registerCallbackHandler(final CallbackHandler handler);

    void initialize(final Collection<Security> securities);

	void close();
	
	void requestSnapshot(final long secSid);

}
