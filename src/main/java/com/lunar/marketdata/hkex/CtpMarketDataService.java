package com.lunar.marketdata.hkex;

import com.lunar.config.ServiceConfig;
import com.lunar.core.MultiThreadEntitySubscriptionManager.EntitySubscription;
import com.lunar.core.RealSystemClock;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.marketdata.MarketDataSource;
import com.lunar.marketdata.hkex.CtpMduMarketDataSource;
import com.lunar.marketdata.hkex.CtpMduMarketDataSource.CtpCallbackHandler;
import com.lunar.message.Command;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.MarketDataService;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Subscribe base on config
 * Send order to omes
 * @author wongca
 *
 */
public class CtpMarketDataService extends MarketDataService {
    private static final Logger LOG = LogManager.getLogger(CtpMarketDataService.class);

	public static CtpMarketDataService of(final ServiceConfig config, final LunarService messageService){
		return new CtpMarketDataService(config, messageService);
	}
	
	CtpMarketDataService(final ServiceConfig config, final LunarService messageService) {
	    super(config, messageService);
	}

    @Override
    protected MarketDataSource createMarketDataSource() {
        final CtpMduMarketDataSource marketDataSource = CtpMduMarketDataSource.instanceOf();
        marketDataSource.setConfig(connectorFile.get(), omdcConfigFile.get(), omddConfigFile.get(), messenger.self().sinkId());
        return marketDataSource;
    }

    final OrderBookSnapshotSbeDecoder orderBookSnapshotSbeDecoder = new OrderBookSnapshotSbeDecoder();
    final MarketDataTradeSbeDecoder marketDataTradeSbeDecoder = new MarketDataTradeSbeDecoder();
    final MarketStatsSbeDecoder marketStatsSbeDecoder = new MarketStatsSbeDecoder();
    
    @Override
    protected void registerMarketDataSourceCallbacks() {
        final Messenger childMessenger = messenger.createChildMessenger();
        ((CtpMduMarketDataSource)marketDataSource).registerCallbackHandler(new CtpCallbackHandler() {
            @Override
            public int onMarketData(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
                final EntitySubscription subscription = securitySubscriptionManager.getSubscription(secSid);
                if (subscription == null)
                    return 0;
                orderBookSnapshotSbeDecoder.wrap(directBuffer, MessageHeaderEncoder.ENCODED_LENGTH, OrderBookSnapshotSbeDecoder.BLOCK_LENGTH, OrderBookSnapshotSbeDecoder.SCHEMA_VERSION);
                //LOG.info("Sending market data for {}: {}", secSid, orderBookSnapshotSbeDecoder.seqNum());
                if (subscription.sinks != null) {
                    final TriggerInfoDecoder triggerInfo = orderBookSnapshotSbeDecoder.triggerInfo();
                    childMessenger.performanceSender().sendGenericTracker(performanceSubscriptions, (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_RAWMARKETDATA, (byte)messenger.self().sinkId(), triggerInfo.triggerSeqNum(), triggerInfo.nanoOfDay(), orderBookSnapshotSbeDecoder.transactTime() - RealSystemClock.NANO_OF_DAY_OFFSET);
//                    final long sendTimestamp = messageService.systemClock().timestamp();
                    childMessenger.marketDataSender().sendOrderBookSnapshot(childMessenger.referenceManager().mdsss(), subscription.sinks, directBuffer, bufferSize);
//                    childMessenger.performanceSender().sendGenericTracker(performanceSubscriptions, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_MARKETDATA, (byte)messenger.self().sinkId(), triggerInfo.triggerSeqNum(), triggerInfo.nanoOfDay(), sendTimestamp);
                }
                else {
                    childMessenger.marketDataSender().sendOrderBookSnapshot(childMessenger.referenceManager().mdsss(), directBuffer, bufferSize);
                }
                return 0;
            }

            @Override
            public int onTrade(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
                final EntitySubscription subscription = securitySubscriptionManager.getSubscription(secSid);
                if (subscription == null)
                    return 0;
                marketDataTradeSbeDecoder.wrap(directBuffer, MessageHeaderEncoder.ENCODED_LENGTH, MarketDataTradeSbeDecoder.BLOCK_LENGTH, MarketDataTradeSbeDecoder.SCHEMA_VERSION);
                if (subscription.sinks != null) {
                    final TriggerInfoDecoder triggerInfo = marketDataTradeSbeDecoder.triggerInfo();
                    childMessenger.performanceSender().sendGenericTracker(performanceSubscriptions, (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_RAWMARKETDATA, (byte)messenger.self().sinkId(), triggerInfo.triggerSeqNum(), triggerInfo.nanoOfDay(), marketDataTradeSbeDecoder.tradeTime() - RealSystemClock.NANO_OF_DAY_OFFSET);
//                    final long sendTimestamp = messageService.systemClock().timestamp();
                    childMessenger.marketDataTradeSender().sendMarketDataTrade(childMessenger.referenceManager().mdsss(), subscription.sinks, directBuffer, bufferSize);
//                    childMessenger.performanceSender().sendGenericTracker(performanceSubscriptions, (byte)messenger.self().sinkId(), TrackerStepType.SENDING_MARKETDATA, (byte)messenger.self().sinkId(), triggerInfo.triggerSeqNum(), triggerInfo.nanoOfDay(), sendTimestamp);
                }
                else {
                    childMessenger.marketDataTradeSender().sendMarketDataTrade(childMessenger.referenceManager().mdsss(), directBuffer, bufferSize);
                }
                return 0;
            }
            
            @Override
            public int onMarketStats(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
                final MessageSinkRef[] sinks = securitySubscriptionManager.getMessageSinks(secSid);             
                if (sinks == null)
                    return 0;
                marketStatsSbeDecoder.wrap(directBuffer, MessageHeaderEncoder.ENCODED_LENGTH, MarketStatsSbeDecoder.BLOCK_LENGTH, MarketStatsSbeDecoder.SCHEMA_VERSION);
                childMessenger.marketStatsSender().sendMarketStats(childMessenger.referenceManager().mdsss(), sinks, directBuffer, bufferSize);
                return 0;
            }

            @Override
            public int onTradingPhase(MarketStatusType marketStatus) {
                final EntitySubscription subscription = exchangeSubscriptionManager.getSubscription(ServiceConstant.PRIMARY_EXCHANGE);
                if (subscription == null || subscription.sinks == null)
                    return 0;                
                LOG.info("Market status update: {}", marketStatus);
                childMessenger.marketStatusSender().sendMarketStatus(subscription.sinks, (int)subscription.sid, marketStatus);
                
                if (marketStatus == MarketStatusType.DC){
                	// Send request to OMES to print order info
                	LOG.info("Session closed. Send command to print all order info");
                	childMessenger.sendCommand(childMessenger.referenceManager().omes().sinkId(), 
                			Command.of(childMessenger.self().sinkId(), 
                					childMessenger.getNextClientKeyAndIncrement(), 
                					CommandType.PRINT_ALL_ORDER_INFO_IN_LOG));
                }
                
                return 0;
            }

            @Override
            public int onSessionState(int sessionState) {
                return 0;
            }           
        });        
    }
	
}
