package com.lunar.marketdata;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.ServiceConfig;
import com.lunar.core.MultiThreadEntitySubscriptionManager.EntitySubscription;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.marketdata.ReplayMarketDataSource;
import com.lunar.marketdata.ReplayMarketDataSource.MarketDataReplayerCallbackHandler;
import com.lunar.marketdata.MarketDataSource;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.service.MarketDataService;
import com.lunar.service.ServiceConstant;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
public class ReplayMarketDataService extends MarketDataService {
    @SuppressWarnings("unused")
    private static final Logger LOG = LogManager.getLogger(ReplayMarketDataService.class);

    public static ReplayMarketDataService of(final ServiceConfig config, final LunarService messageService){
        return new ReplayMarketDataService(config, messageService);
    }

    ReplayMarketDataService(final ServiceConfig config, final LunarService messageService){
        super(config, messageService);
    }

    @Override
    protected MarketDataSource createMarketDataSource() {
        final ReplayMarketDataSource marketDataSource = ReplayMarketDataSource.instanceOf();
        marketDataSource.setSystemClock(messageService.systemClock());
        marketDataSource.setTimerService(messenger.timerService());
        marketDataSource.setSinkId((byte)messenger.self().sinkId());
        return marketDataSource;
    }

    @Override
    protected void registerMarketDataSourceCallbacks() {
        final Messenger childMessenger = messenger.createChildMessenger();
        ((ReplayMarketDataSource)marketDataSource).registerCallbackHandler(new MarketDataReplayerCallbackHandler() {
            @Override
            public int onTradingPhase(MarketStatusType marketStatus) {
                final EntitySubscription subscription = exchangeSubscriptionManager.getSubscription(ServiceConstant.PRIMARY_EXCHANGE);
                if (subscription == null || subscription.sinks == null)
                    return 0;
                childMessenger.marketStatusSender().sendMarketStatus(subscription.sinks, (int)subscription.sid, marketStatus);
                return 0;
            }

            @Override
            public int onMarketData(long secSid, MarketOrderBook orderBook) {
                final EntitySubscription subscription = securitySubscriptionManager.getSubscription(secSid);
                if (subscription == null)
                    return 0;
                if (subscription.sinks != null) {
                    childMessenger.performanceSender().sendGenericTracker(performanceSubscriptions, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_MARKETDATA, (byte)messenger.self().sinkId(), orderBook.triggerInfo().triggerSeqNum(), orderBook.triggerInfo().triggerNanoOfDay(), messageService.systemClock().timestamp());
                    childMessenger.marketDataSender().sendOrderBookSnapshot(messenger.referenceManager().mdsss(), subscription.sinks, orderBook);
                }
                else {
                    childMessenger.marketDataSender().sendOrderBookSnapshot(messenger.referenceManager().mdsss(), orderBook);
                }
                return 0;
            }

            @Override
            public int onTrade(long secSid, MarketTrade trade) {
                final EntitySubscription subscription = securitySubscriptionManager.getSubscription(secSid);
                if (subscription == null)
                    return 0;
                if (subscription.sinks != null) {
                    childMessenger.performanceSender().sendGenericTracker(performanceSubscriptions, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_MARKETDATA, (byte)messenger.self().sinkId(), trade.triggerInfo().triggerSeqNum(), trade.triggerInfo().triggerNanoOfDay(), messageService.systemClock().timestamp());
                    childMessenger.marketDataTradeSender().sendMarketDataTrade(messenger.referenceManager().mdsss(), subscription.sinks, trade);
                }
                else {
                    childMessenger.marketDataTradeSender().sendMarketDataTrade(messenger.referenceManager().mdsss(), trade);
                }
                return 0;
            }
        });        
    }	

}
