package com.lunar.service;

import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.archive.MarketDataRefreshFeed;

/**
 * Contains an actual refresh feed that gets data from exchange refresh service
 * @author wongca
 *
 */
@SuppressWarnings("unused")
public class MarketDataRefreshService implements ServiceLifecycleAware {
	private final MarketDataRefreshFeed feed;
    private LunarService messageService;
    private final String name;
	
    public MarketDataRefreshService(ServiceConfig config, LunarService messageService, MarketDataRefreshFeed feed) {
		this.name = config.name();
		this.messageService = messageService;
		this.feed = feed;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public StateTransitionEvent activeEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
	}
}
