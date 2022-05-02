package com.lunar.marketdata.hkex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketDataSource;
import com.lunar.marketdata.hkex.CtpMduApi.CtpApiCallbackHandler;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CtpMduMarketDataSource implements MarketDataSource {
    private static final Logger LOG = LogManager.getLogger(CtpMduMarketDataSource.class);

	final ByteBuffer bufferOrder;
	final MutableDirectBuffer directBufferOrder;
	final ByteBuffer bufferTrade;
	final MutableDirectBuffer directBufferTrade;
	final ByteBuffer bufferStats;
	final MutableDirectBuffer directBufferStats;
	
	private CtpMduApi api;
	private String connectorFile;	
	private String omdcConfigFile;
	private String omddConfigFile;
	private int sinkId;
	
	private volatile MarketStatusType marketStatus = MarketStatusType.DC;

    public interface CtpCallbackHandler extends CallbackHandler {
        int onMarketData(long secSid, MutableDirectBuffer directBuffer, int bufferSize);
        int onTrade(long secSid, MutableDirectBuffer directBuffer, int bufferSize);
        int onMarketStats(long secSid, MutableDirectBuffer directBuffer, int bufferSize);
        int onTradingPhase(MarketStatusType marketStatus);
        int onSessionState(int sessionState);
    }
    
    private class MultiCallbackHandler implements CtpCallbackHandler {
        private final CtpCallbackHandler[] handlers;
        private int numHandlers;
        
        public MultiCallbackHandler() {
            handlers = new CtpCallbackHandler[ServiceConstant.MAX_SUBSCRIBERS];
            numHandlers = 0;
        }
        
        public void registerCallbackHandler(final CtpCallbackHandler handler) {
            handlers[numHandlers++] = handler;
        }
        
        @Override
        public int onMarketData(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
            for (int i = 0; i < numHandlers; i++) {
                try {
                    handlers[i].onMarketData(secSid, directBuffer, bufferSize);
                }
                catch (final Exception e) {
                    LOG.error("Error handling market data...", e);
                }
            }
            return 0;
        }

        @Override
        public int onTrade(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
            for (int i = 0; i < numHandlers; i++) {
                try {
                    handlers[i].onTrade(secSid, directBuffer, bufferSize);
                }
                catch (final Exception e) {
                    LOG.error("Error handling trade...", e);
                }
            }
            return 0;
        }
        
        @Override
        public int onMarketStats(long secSid, MutableDirectBuffer directBuffer, int bufferSize) {
            for (int i = 0; i < numHandlers; i++) {
                try {
                    handlers[i].onMarketStats(secSid, directBuffer, bufferSize);
                }
                catch (final Exception e) {
                    LOG.error("Error handling market stats...", e);
                }
            }
            return 0;
        }

        @Override
        public int onTradingPhase(MarketStatusType marketStatus) {
            for (int i = 0; i < numHandlers; i++) {
                try {
                    handlers[i].onTradingPhase(marketStatus);
                }
                catch (final Exception e) {
                    LOG.error("Error handling trading phase...", e);
                }
            }
            return 0;
        }

        @Override
        public int onSessionState(int sessionState) {
            for (int i = 0; i < numHandlers; i++) {
                try {
                    handlers[i].onSessionState(sessionState);
                }
                catch (final Exception e) {
                    LOG.error("Error handling session state...", e);
                }
            }
            return 0;
        }       
    }
    
    private CtpCallbackHandler handler;
    
    static private volatile CtpMduMarketDataSource s_instance;

    static public CtpMduMarketDataSource instanceOf() {
        if (s_instance == null) {
            synchronized (CtpMduMarketDataSource.class) {
                if (s_instance == null) {
                    s_instance = new CtpMduMarketDataSource();
                }
            }
        }
        return s_instance;
    }

	public CtpMduMarketDataSource() {
		bufferOrder = ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE);
		directBufferOrder = new UnsafeBuffer(bufferOrder);
		bufferTrade = ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE);
		directBufferTrade = new UnsafeBuffer(bufferTrade);
		bufferStats = ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE);
		directBufferStats = new UnsafeBuffer(bufferStats);
	}
	
	@Override
	public MarketStatusType marketStatus() {
		return marketStatus;
	}

	public void setConfig(final String connectorFile, final String omdcConfigFile, final String omddConfigFile, final int sinkId) {
        this.connectorFile = connectorFile;
        this.omdcConfigFile = omdcConfigFile;
        this.omddConfigFile = omddConfigFile;
        this.sinkId = sinkId;
	}
	
	@Override
    public MarketDataSource registerCallbackHandler(final CallbackHandler handler) {
	    CtpCallbackHandler ctpCallbackHandler = (CtpCallbackHandler)handler;
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
	public void initialize(final Collection<Security> securities) {
	    api = new CtpMduApi(new CtpApiCallbackHandler() {
            @Override
            public int onMarketData(long secSid, int bufferSize) {
                //LOG.info("Received market data for {}...", secSid);
                return handler.onMarketData(secSid, directBufferOrder, bufferSize);
            }

            @Override
            public int onTrade(long secSid, int bufferSize) {
                return handler.onTrade(secSid, directBufferTrade, bufferSize);
            }
            
            @Override
            public int onMarketStats(long secSid, int bufferSize) {
                return handler.onMarketStats(secSid, directBufferStats, bufferSize);
            }

            @Override
            public int onTradingPhase(int tradingPhase) {
                //LOG.info("received trading phase {} message from CtpMdu", tradingPhase);
                final MarketStatusType newStatus;
                switch (tradingPhase) {
                case CtpMduApi.OPEN:
                    newStatus = MarketStatusType.CT;
                    break;
                case CtpMduApi.CLOSE:
                    newStatus = MarketStatusType.DC;
                    break;
                default:
                    newStatus = marketStatus;                       
                }
                if (newStatus != marketStatus) {
                    marketStatus = newStatus;
                    return handler.onTradingPhase(marketStatus);
                }
                return 0;
            }

            @Override
            public int onSessionState(int sessionState) {
                return handler.onSessionState(sessionState);
            }
	        
	    });

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
        		api.registerSecurity(security.sid(), security.code());
        	}
		}
        for (final Security futures : futuresList) {
    		if (futures.underlyingSid() == hsiSid) {
    			api.registerHsiFutures(futures.sid(), futures.code());
    		}
    		else if (futures.underlyingSid() == hsceiSid) {
    			api.registerHsceiFutures(futures.sid(), futures.code());
    		}
        }
		api.initialize(connectorFile, omdcConfigFile, omddConfigFile, sinkId, 10, bufferOrder, bufferTrade, bufferStats, ServiceConstant.MAX_MESSAGE_SIZE);
	}

	@Override
	public void close() {
	    if (api != null)
	        api.close();
	}

    @Override
    public void requestSnapshot(long secSid) {
        if (api != null)
            api.requestSnapshot(secSid);        
    }

}
