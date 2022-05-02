package com.lunar.strategy;

import static org.apache.logging.log4j.util.Unbox.box;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.HdrHistogram.Histogram;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.RollingWindowTimeFrame;
import com.lunar.entity.Issuer;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.parameters.GenericIssuerUndParams;

public class DeltaLimitAlertGenerator implements StrategySignalGenerator, MarketDataUpdateHandler {
    static final Logger LOG = LogManager.getLogger(DeltaLimitAlertGenerator.class);    

    final private List<DeltaLimitAlertHandler> m_handlers;
    final private RollingWindowTimeFrame.SimpleAdditionDiscreteTimeWindowUpdateHandler m_deltaSharesPerTradeUpdateHandler;
    final private RollingWindowTimeFrame m_deltaSharesPerTradeTimeFrame;
    final private StrategyInfoSender m_strategyInfoSender;
    final private StrategySecurity m_underlying;
	final private Issuer m_issuer;
    final private UnderlyingPrice m_underlyingPrice;
    final private GenericIssuerUndParams m_params;
    final private long m_spotScale;

    public DeltaLimitAlertGenerator(final StrategySecurity underlying, final Issuer issuer, final UnderlyingPrice underlyingPrice, final GenericIssuerUndParams params, final StrategyInfoSender strategyInfoSender) {
        m_handlers = new ArrayList<DeltaLimitAlertHandler>();
        m_deltaSharesPerTradeUpdateHandler = new RollingWindowTimeFrame.SimpleAdditionDiscreteTimeWindowUpdateHandler();
        m_deltaSharesPerTradeTimeFrame = new RollingWindowTimeFrame(m_deltaSharesPerTradeUpdateHandler, 10_000_000_000L, 512, 512);
        m_strategyInfoSender = strategyInfoSender;
        m_underlying = underlying;
        m_issuer = issuer;
        m_underlyingPrice = underlyingPrice;
        m_params = params;
        m_spotScale = underlying.spreadTable().scale() * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
    }
    
    @Override
    public void registerHandler(StrategySignalHandler handler) {
        final DeltaLimitAlertHandler deltaLimitAlertHandler = (DeltaLimitAlertHandler)handler;
        if (!m_handlers.contains(deltaLimitAlertHandler)) {
            m_handlers.add(deltaLimitAlertHandler);
            deltaLimitAlertHandler.security().registerMdUpdateHandler(this);
            LOG.debug("Delta limit signal is enabled: secCode {}", m_underlying.code());
        }
    }

    @Override
    public void unregisterHandler(StrategySignalHandler handler) {
        final DeltaLimitAlertHandler deltaLimitAlertHandler = (DeltaLimitAlertHandler)handler;
        if (m_handlers.remove(deltaLimitAlertHandler)) {
            deltaLimitAlertHandler.security().unregisterMdUpdateHandler(this);
            LOG.debug("Delta limit signal is disabled: secCode {}", m_underlying.code());
        }
    }

    @Override
    public void reset() {
        
    }

//    private static final long MAX_DELAY = 1000000000;
//    static final ThreadLocal<Histogram> histogramOnObUpdated = new ThreadLocal<Histogram>() {
//        @Override
//        protected Histogram initialValue() {
//            return new Histogram(MAX_DELAY, 1);
//        }
//    };
//    static final ThreadLocal<Histogram> histogramOnTradeReceived = new ThreadLocal<Histogram>() {
//        @Override
//        protected Histogram initialValue() {
//            return new Histogram(MAX_DELAY, 1);
//        }
//    };
//    static final ThreadLocal<ByteArrayOutputStream> outputByteStream = new ThreadLocal<ByteArrayOutputStream>() {
//        @Override
//        protected ByteArrayOutputStream initialValue() {
//            return new ByteArrayOutputStream();
//        }
//    };
//    static final ThreadLocal<PrintStream> outputStream = new ThreadLocal<PrintStream>() {
//        @Override
//        protected PrintStream initialValue() {
//            return new PrintStream(outputByteStream.get());
//
//        }
//    };
//    static final ThreadLocal<Long> timeLastPrintHisto = new ThreadLocal<Long>() {
//        @Override
//        protected Long initialValue() {
//            return new Long(0);
//
//        }
//    };

    @Override
    public void onOrderBookUpdated(Security security, long timestamp, MarketOrderBook orderBook) throws Exception {
//        final long startTime = System.nanoTime();
        m_deltaSharesPerTradeTimeFrame.updateTimeFrame(timestamp);
        final long deltaShares = m_deltaSharesPerTradeUpdateHandler.accumValue();
        if (m_params.undDeltaShares() != deltaShares) {
            final long delta = calcDeltaNotional(deltaShares);
            final long absDelta = Math.abs(delta);
            if (LOG.isDebugEnabled()) {
                LOG.debug("DeltaShares updated: issuerCode {}, secCode {}, warrantCode {}, deltaShares {}, delta {}, wavg {}, trigger seqNum {}", m_issuer.code(), m_underlying.code(), security.code(), box(deltaShares), box(delta), box(m_underlyingPrice.getWeightedAverage()), box(orderBook.triggerInfo().triggerSeqNum()));
            }
            m_params.undDeltaShares(deltaShares);
            m_params.undTradeVol(absDelta);
            if (m_params.undTradeVolThreshold() != 0 && absDelta >= m_params.undTradeVolThreshold()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Delta limit exceeded. Broadcasting signal and reset delta: issuerCode {}, secCode {}, warrantCode {}, delta {}, wavg {}, trigger seqNum {}", m_issuer.code(), m_underlying.code(), security.code(), box(delta), box(m_underlyingPrice.getWeightedAverage()), box(orderBook.triggerInfo().triggerSeqNum()));
                }
                for (final DeltaLimitAlertHandler handler : m_handlers) {
                    handler.onDeltaLimitExceeded(security, timestamp, absDelta);
                }
                m_params.undDeltaShares(0);
                m_params.undTradeVol(0);
                m_deltaSharesPerTradeTimeFrame.clear();
            }
            m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_params);
        }
//        final long timeElasped = System.nanoTime() - startTime;
//        if (timeElasped <= MAX_DELAY) {
//            histogramOnObUpdated.get().recordValue(timeElasped);
//            if (timestamp - timeLastPrintHisto.get().longValue() > 60_000_000_000L) {
//                timeLastPrintHisto.set(timestamp);
//                outputStream.get().println();
//                outputStream.get().println("Histogram for DeltaLimitAlertGenerator:onOrderBookUpdated");
//                outputStream.get().println("=============================");
//                histogramOnObUpdated.get().outputPercentileDistribution(outputStream.get(), 1, 1.0);
//                outputStream.get().println();
//                
//                outputStream.get().println("Histogram for DeltaLimitAlertGenerator:onTradeReceived");
//                outputStream.get().println("=============================");
//                histogramOnTradeReceived.get().outputPercentileDistribution(outputStream.get(), 1, 1.0);
//                outputStream.get().println();
//
//                LOG.info(outputByteStream.get().toString("UTF8"));
//                outputByteStream.get().reset();
//            }
//        }
    }

    @Override
    public void onTradeReceived(Security security, long timestamp, MarketTrade marketTrade) throws Exception {
//        final long startTime = System.nanoTime();

        final int delta = ((StrategySecurity)security).greeks().delta();
        final long deltaShares = (long)marketTrade.quantity() * -marketTrade.side() * delta / (security.convRatio() * 100);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Recording DeltaShares for Trade: issuerCode {}, secCode {}, warrantCode {}, delta {}, convRatio {}, deltaShares {}, side {}, quantity {}, trigger seqNum {}",
                m_issuer.code(), m_underlying.code(), security.code(), box(delta), box(security.convRatio()), box(deltaShares), box(marketTrade.side()), box(marketTrade.quantity()), box(marketTrade.triggerInfo().triggerSeqNum()));
        }
        m_deltaSharesPerTradeTimeFrame.recordValue(timestamp, deltaShares);
//        final long timeElasped = System.nanoTime() - startTime;
//        if (timeElasped <= MAX_DELAY) {
//            histogramOnTradeReceived.get().recordValue(timeElasped);
//        }
    }

    public long calcDeltaNotional(final long deltaShares) {
    	return (deltaShares * m_underlyingPrice.getWeightedAverage()) / m_spotScale;
    }
    
    public long calcDeltaShares(final long deltaNotional) {
        return (deltaNotional * m_spotScale) / m_underlyingPrice.getWeightedAverage();
    }

}
