package com.lunar.marketdata;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.core.UserControlledTimerService;
import com.lunar.entity.Security;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.TradeType;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class ReplayMarketDataSource implements MarketDataSource {
    static final Logger LOG = LogManager.getLogger(ReplayMarketDataSource.class);

    static private final int BOOK_DEPTH = 10;
	static private final long TICKS_START_TIME = LocalTime.of(9, 0).toNanoOfDay();
	private Long2IntOpenHashMap m_seqNums;
    private Long2ObjectOpenHashMap<Security> securityCodeToSecurity;
	private SystemClock m_systemClock;
	private TimerService m_timerService;
	private volatile MarketStatusType m_marketStatus = MarketStatusType.DC;
	private int m_triggerSeqNum;
	private byte m_sinkId;
	
    private MarketDataReplayerCallbackHandler handler;

    public interface MarketDataReplayerCallbackHandler extends CallbackHandler {
        int onMarketData(long secSid, MarketOrderBook orderBook);
        int onTrade(long secSid, MarketTrade marketTrade);
        int onTradingPhase(MarketStatusType marketStatus);
    }

    private class MultiCallbackHandler implements MarketDataReplayerCallbackHandler {
        private final MarketDataReplayerCallbackHandler[] handlers;
        private int numHandlers;
        
        public MultiCallbackHandler() {
            handlers = new MarketDataReplayerCallbackHandler[ServiceConstant.MAX_SUBSCRIBERS];
            numHandlers = 0;
        }
        
        public void registerCallbackHandler(final MarketDataReplayerCallbackHandler handler) {
            handlers[numHandlers++] = handler;
        }
        
        @Override
        public int onMarketData(long secSid, MarketOrderBook orderBook) {
            for (int i = 0; i < numHandlers; i++) {
                handlers[i].onMarketData(secSid, orderBook);
            }
            return 0;
        }

        @Override
        public int onTrade(long secSid, MarketTrade marketTrade) {
            for (int i = 0; i < numHandlers; i++) {
                handlers[i].onTrade(secSid, marketTrade);
            }
            return 0;
        }

        @Override
        public int onTradingPhase(MarketStatusType marketStatus) {
            for (int i = 0; i < numHandlers; i++) {
                handlers[i].onTradingPhase(marketStatus);
            }
            return 0;
        }
    }    
    
    static private volatile ReplayMarketDataSource s_instance;

    static public ReplayMarketDataSource instanceOf() {
        if (s_instance == null) {
            synchronized (ReplayMarketDataSource.class) {
                if (s_instance == null) {
                    s_instance = new ReplayMarketDataSource();
                }
            }
        }
        return s_instance;
    }
    
	public ReplayMarketDataSource() {
		m_triggerSeqNum = 0;
	}

	public int getTriggerSeqNum() {
	    return m_triggerSeqNum;
	}
	
	public MarketStatusType marketStatus() {
		return m_marketStatus;
	}
	
	public void setSystemClock(final SystemClock systemClock) {
	    m_systemClock = systemClock;
	}

	public void setTimerService(final TimerService timerService){
		m_timerService = timerService;
	}
	
	public void setSinkId(final byte sinkId) {
	    m_sinkId = sinkId;
	}
	
	@Override
    public void initialize(final Collection<Security> securities) {		
        securityCodeToSecurity = new Long2ObjectOpenHashMap<Security>(securities.size());
        m_seqNums = new Long2IntOpenHashMap(securities.size());
        m_seqNums.defaultReturnValue(1);
        long hsiSid = 0;
        long hsceiSid = 0;
        final ArrayList<Security> futuresList = new ArrayList<Security>(2);
        for (final Security security : securities) {
        	if (security.securityType().equals(SecurityType.INDEX)) {
        		if (security.code().equals(ServiceConstant.HSI_CODE)) {
        			hsiSid = security.sid();        			
        		}
        		else if (security.code().equals(ServiceConstant.HSCEI_CODE)) {
        			hsceiSid = security.sid();
        		}
        	}
        	else if (security.securityType().equals(SecurityType.FUTURES)) {
        		futuresList.add(security);
        	}
        	else {
	        	try {
	        		Integer secCode = Integer.parseInt(security.code());
	        		securityCodeToSecurity.put(secCode, security);
	        	}
	        	catch (NumberFormatException ex) {
	        		LOG.warn("Cannot convert security code {} to an Integer", security.code());
	        	}
        	}
        }
        
        final MarketDataReplayer replayer = MarketDataReplayer.instanceOf();
        for (final Security futures : futuresList) {
        	if (futures.underlyingSid() == hsiSid) {
        		replayer.registerHsiFuturesCode(futures.sid(), futures.code());
        		securityCodeToSecurity.put(futures.sid(), futures);
        	}
        	else if (futures.underlyingSid() == hsceiSid) {
        		replayer.registerHsceiFuturesCode(futures.sid(), futures.code());
        		securityCodeToSecurity.put(futures.sid(), futures);
        	}
        }
        m_marketStatus = replayer.getMarketStatus();
        replayer.addMarketStatusHandler((exchangeCode, status) -> {
            m_marketStatus = status;
            handler.onTradingPhase(m_marketStatus);
        });
        final MarketOrderBook[] orderBooks = new MarketOrderBook[SpreadTableBuilder.NUM_SUPPORTED_SPREAD_TABLES];
        for (int i = 0; i < SpreadTableBuilder.NUM_SUPPORTED_SPREAD_TABLES; i++){
        	orderBooks[i] = MarketOrderBook.of(BOOK_DEPTH, SpreadTableBuilder.getById(i), Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
        final MarketTrade trade = MarketTrade.of();
        replayer.addOrderBookSnapshotHandler((scty, nanoOfDay, numBids, bidPrices, bidQuantities, numAsks, askPrices, askQuantities) -> {        	
            final UserControlledSystemClock userControlledClock = (UserControlledSystemClock) m_systemClock;
            if (userControlledClock != null)
                userControlledClock.nanoOfDay(nanoOfDay);
            if (m_timerService instanceof UserControlledTimerService){
                final UserControlledTimerService userControlledTimerService = (UserControlledTimerService)m_timerService;
                if (userControlledTimerService != null){
                	userControlledTimerService.nanoOfDay(nanoOfDay);
                }            	
            }
            if (nanoOfDay < TICKS_START_TIME)
                return;
            final Security security = securityCodeToSecurity.get(scty);
            if (security == securityCodeToSecurity.defaultReturnValue())
                return;

            int seqNum = m_seqNums.addTo(security.sid(), 1);
            MarketOrderBook orderBook = orderBooks[security.spreadTable().id()];
            try {
	            orderBook.secSid(security.sid()).channelSeqNum(seqNum).transactNanoOfDay(nanoOfDay).isRecovery(false).triggerInfo().triggeredBy(m_sinkId).triggerSeqNum(m_triggerSeqNum++).triggerNanoOfDay(nanoOfDay);
	            orderBook.bidSide().clear();
	            orderBook.askSide().clear();
	            if (security.spreadTable().scale() == 1) {
	                // our ticks file always write prices with a scale of 1000
	                for (int i = 0; i < numBids; i++) {
	                    orderBook.bidSide().create(bidPrices[i] / 1000, bidQuantities[i]);
	                }
	                for (int i = 0; i < numAsks; i++) {
	                    orderBook.askSide().create(askPrices[i] / 1000, askQuantities[i]);
	                }
	            }
	            else {
	                for (int i = 0; i < numBids; i++) {
	                    orderBook.bidSide().create(bidPrices[i], bidQuantities[i]);
	                }
	                for (int i = 0; i < numAsks; i++) {
	                    orderBook.askSide().create(askPrices[i], askQuantities[i]);
	                }
	            }
	            handler.onMarketData(security.sid(), orderBook);
            }
            catch (final Exception e) {
        		LOG.error("Error encountered when handling market data for {}", security.code(), e);
            }
        });
        replayer.addTradeHandler((scty, nanoOfDay, price, quantity, tradeType) -> {
            final UserControlledSystemClock userControlledClock = (UserControlledSystemClock)m_systemClock;
            if (userControlledClock != null)
                userControlledClock.nanoOfDay(nanoOfDay);
            if (m_timerService instanceof UserControlledTimerService){
                final UserControlledTimerService userControlledTimerService = (UserControlledTimerService)m_timerService;
                if (userControlledTimerService != null){
                	userControlledTimerService.nanoOfDay(nanoOfDay);
                }
            }
            if (nanoOfDay < TICKS_START_TIME)
                return;
            final Security security = securityCodeToSecurity.get(scty);
            if (security == securityCodeToSecurity.defaultReturnValue())
                return;
            if (security.spreadTable().scale() == 1) {
                // our ticks file always write prices with a scale of 1000
                price = price / 1000;
            }
            trade.secSid(security.sid()).tradeNanoOfDay(nanoOfDay).isRecovery(false).price(price).quantity(quantity).tradeType(TradeType.AUTOMATCH_NORMAL).numActualTrades(1).triggerInfo().triggeredBy(m_sinkId).triggerSeqNum(m_triggerSeqNum++).triggerNanoOfDay(nanoOfDay);
            handler.onTrade(security.sid(), trade);
        });
	}
	
	public void close() {
		
	}

    @Override
    public MarketDataSource registerCallbackHandler(final CallbackHandler handler) {
        MarketDataReplayerCallbackHandler ctpCallbackHandler = (MarketDataReplayerCallbackHandler)handler;
        if (this.handler == null) {
            this.handler = ctpCallbackHandler;
        }
        else {
            if (!(this.handler instanceof MultiCallbackHandler)) {
                final MultiCallbackHandler multiCallbackHandler = new MultiCallbackHandler();
                multiCallbackHandler.registerCallbackHandler(this.handler);
                this.handler = multiCallbackHandler;
            }
            final MultiCallbackHandler multiCallbackHandler = (MultiCallbackHandler)this.handler;
            multiCallbackHandler.registerCallbackHandler(ctpCallbackHandler);          
        }
        return this;
    }

    @Override
    public void requestSnapshot(long secSid) {
        
    }
	
}
