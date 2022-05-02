package com.lunar.strategy.speedarbhybrid;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.RollingWindowTimeFrame;
import com.lunar.core.TriggerInfo;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.SpreadState;
import com.lunar.order.Tick;
import com.lunar.pricing.BucketPricer;
import com.lunar.pricing.BucketPricer.ViolationType;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.GreeksUpdateHandler;
import com.lunar.strategy.IssuerResponseTimeGenerator;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.UnderlyingSpotObserver;
import com.lunar.strategy.parameters.BucketOutputParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.parameters.GenericWrtStatsParams;
import com.lunar.strategy.statemachine.StateMachineEventBus;
import com.lunar.util.LongInterval;

import it.unimi.dsi.fastutil.ints.IntArrayPriorityQueue;

public class SpeedArbHybridWrtSignalGenerator implements MarketDataUpdateHandler, GreeksUpdateHandler, UnderlyingSpotObserver {
    static final Logger LOG = LogManager.getLogger(SpeedArbHybridWrtSignalGenerator.class);
    
    static final private long SPREAD_OBSERVATION_PERIOD_WHEN_TIGHTER = 100_000_000L;

    private interface PricingModeBridge {
        byte getId();
        long getSpot();
        long getPrevSpot();
        BucketPricer getBucketPricer();
        void incNumDownVols();
        void setNumDownVols(final int numDownVols);
        int getNumDownVols();
        void incNumUpVols();
        void setNumUpVols(final int numUpVols);
        int getNumUpVols();
        void incNumViolations();
        void setNumViolations(final int numViolations);
        int getNumViolations();
        LongInterval getOutInterval();
    }
    
    private class UnknownWeightedAveragePricingModeBridge implements PricingModeBridge {
        @Override
        public byte getId() {
            return PricingMode.UNKNOWN.value();
        }
        
        @Override
        public long getSpot() {
            return m_undSignalGenerator.getWeightedAverage();
        }

        @Override
        public long getPrevSpot() {
            return m_undSignalGenerator.getPrevWeightedAverage();
        }

        @Override
        public BucketPricer getBucketPricer() {
            return null;
        }

        @Override
        public void incNumDownVols() {
        }

        @Override
        public void setNumDownVols(int numDownVols) {
        }

        @Override
        public int getNumDownVols() {
            return 0;
        }

        @Override
        public void incNumUpVols() {
        }

        @Override
        public void setNumUpVols(int numUpVols) {
        }

        @Override
        public int getNumUpVols() {
            return 0;
        }

        @Override
        public void incNumViolations() {
        }

        @Override
        public void setNumViolations(final int numViolations) {
        }

        @Override
        public int getNumViolations() {
            return 0;
        }

        @Override
        public LongInterval getOutInterval() {
            return null;
        }
    }

    private class UnknownMidPricePricingModeBridge implements PricingModeBridge {
        @Override
        public byte getId() {
            return PricingMode.UNKNOWN.value();
        }
        
        @Override
        public long getSpot() {
            return m_undSignalGenerator.getMidPrice();
        }

        @Override
        public long getPrevSpot() {
            return m_undSignalGenerator.getPrevMidPrice();
        }

        @Override
        public BucketPricer getBucketPricer() {
            return null;
        }

        @Override
        public void incNumDownVols() {
        }

        @Override
        public void setNumDownVols(int numDownVols) {
        }

        @Override
        public int getNumDownVols() {
            return 0;
        }

        @Override
        public void incNumUpVols() {
        }

        @Override
        public void setNumUpVols(int numUpVols) {
        }

        @Override
        public int getNumUpVols() {
            return 0;
        }

        @Override
        public void incNumViolations() {
        }

        @Override
        public void setNumViolations(final int numViolations) {
        }

        @Override
        public int getNumViolations() {
            return 0;
        }

        @Override
        public LongInterval getOutInterval() {
            return null;
        }
    }

    private class WeightedAveragePricingModeBridge implements PricingModeBridge {
        private int numViolations;
        
        @Override
        public byte getId() {
            return PricingMode.WEIGHTED.value();
        }

        private LongInterval m_outInterval = LongInterval.of();
        
        @Override
        public long getSpot() {
            return m_undSignalGenerator.getWeightedAverage();
        }

        @Override
        public long getPrevSpot() {
            return m_undSignalGenerator.getPrevWeightedAverage();
        }

        @Override
        public BucketPricer getBucketPricer() {
            return m_waBucketPricer;
        }

        @Override
        public void incNumDownVols() {
            m_speedArbWrtParams.incNumWAvgDownVols();
        }

        @Override
        public void setNumDownVols(int numDownVols) {
            m_speedArbWrtParams.numWAvgDownVols(numDownVols);
        }

        @Override
        public int getNumDownVols() {
            return m_speedArbWrtParams.numWAvgDownVols();
        }

        @Override
        public void incNumUpVols() {
            m_speedArbWrtParams.incNumWAvgUpVols();
        }

        @Override
        public void setNumUpVols(int numUpVols) {
            m_speedArbWrtParams.numWAvgUpVols(numUpVols);
        }

        @Override
        public int getNumUpVols() {
            return m_speedArbWrtParams.numWAvgUpVols();
        }

        @Override
        public void incNumViolations() {
            numViolations++;
        }

        @Override
        public void setNumViolations(final int numViolations) {
            this.numViolations = numViolations;
        }

        @Override
        public int getNumViolations() {
            return numViolations;
        }

        @Override
        public LongInterval getOutInterval() {
            return m_outInterval;
        }
    }
    
    private class MidPricePricingModeBridge implements PricingModeBridge {
        private int numViolations;
        
        @Override
        public byte getId() {
            return PricingMode.MID.value();
        }

        private LongInterval m_outInterval = LongInterval.of();
        
        @Override
        public long getSpot() {
            return m_undSignalGenerator.getMidPrice();
        }

        @Override
        public long getPrevSpot() {
            return m_undSignalGenerator.getPrevMidPrice();
        }

        @Override
        public BucketPricer getBucketPricer() {
            return m_mpBucketPricer;
        }

        @Override
        public void incNumDownVols() {
            m_speedArbWrtParams.incNumMPrcDownVols();
        }

        @Override
        public void setNumDownVols(int numDownVols) {
            m_speedArbWrtParams.numMPrcDownVols(numDownVols);
        }

        @Override
        public int getNumDownVols() {
            return m_speedArbWrtParams.numMPrcDownVols();
        }

        @Override
        public void incNumUpVols() {
            m_speedArbWrtParams.incNumMPrcUpVols();
        }

        @Override
        public void setNumUpVols(int numUpVols) {
            m_speedArbWrtParams.numMPrcUpVols(numUpVols);
            
        }

        @Override
        public int getNumUpVols() {
            return m_speedArbWrtParams.numMPrcUpVols();
        }
        
        @Override
        public void incNumViolations() {
            numViolations++;
        }

        @Override
        public void setNumViolations(final int numViolations) {
            this.numViolations = numViolations;
        }

        @Override
        public int getNumViolations() {
            return numViolations;
        }
        
        @Override
        public LongInterval getOutInterval() {
            return m_outInterval;
        }
    }
  
    private final StrategyType m_strategyType;
    private final StrategySecurity m_security;
    private final StrategySecurity m_underlying;
    private final SpreadTable m_spreadTable;

    private final IssuerResponseTimeGenerator m_issuerResponseLagMonitor;
    private final SpeedArbHybridUndSignalGenerator m_undSignalGenerator;
    private final StateMachineEventBus m_warrantEventBus;	
    private final BucketPricer m_waBucketPricer;
    private final BucketPricer m_mpBucketPricer;
    private final PricingModeBridge m_ukWaPricingModeBridge;
    private final PricingModeBridge m_ukMpPricingModeBridge;
    private final PricingModeBridge m_waPricingModeBridge;
    private final PricingModeBridge m_mpPricingModeBridge;
    private final SpreadTableScaleFormulaBridge m_spreadTableScaleFormulaBridge;
    
    private PricingModeBridge m_activePricingModeBridge;
    private PricingModeBridge m_standbyPricingModeBridge;
    
    private final StrategyInfoSender m_strategyInfoSender;

    // configurations
    @SuppressWarnings("unused")
    private final GenericStrategyTypeParams m_speedArbParams;
    private final GenericWrtParams m_speedArbWrtParams; 
    private final GenericWrtStatsParams m_speedArbWrtStats;
    private final BucketOutputParams m_bucketOutputParams;
    
    // states
    private long m_lastTickNanoOfDay;
    private int m_bestBidPrice;
    private int m_bestBidLevel;
    private long m_bestBidQty;
    private int m_bestAskPrice;
    private int m_bestAskLevel;
    private int m_bestAskLevelNotOurs;
    private int m_tickBelowBestAskPriceNotOurs;
    private int m_prevBestBidPrice;
    private int m_prevBestAskPrice;
    private float m_pricePerUndTick;    
    private int m_mmBidPrice;
    private int m_mmBidLevel;
    private int m_mmAskPrice;
    private int m_mmAskLevel;
    private int m_prevMmBidPrice;
    private int m_prevMmAskPrice;
    private int m_mmAskTickSize;
    private int m_prevMmSpread = Integer.MAX_VALUE;
    private int m_mmSpread = Integer.MAX_VALUE;
    private int m_targetSpread = Integer.MAX_VALUE;
    private long m_targetSpreadEndTime = Long.MAX_VALUE;
    private long m_lastMmSpreadUpdateTime = Long.MAX_VALUE;
    private int m_askPriceAtPrevUndTick;
    private boolean m_isAtTargetSpread;
    private boolean m_isPrevAtTargetSpread;
    private boolean m_isLooselyTight;
    private int m_bucketSize;
    private TriggerInfo m_triggerInfo;
    private int m_delta;
    private boolean m_canCollectBuckets;
    private IntArrayPriorityQueue m_holdBidBanPrices;
    private int m_underlyingTickSize;
    private RollingWindowTimeFrame.SimpleAdditionDiscreteTimeWindowUpdateHandler m_20msTradesVolumeUpdateHandler;
    private RollingWindowTimeFrame m_20msTradesVolumeTimeFrame;
    
    // statistics
    private long m_startPricingModeNanoOfDay;
    private long m_timeInWeightedMode;
    private long m_timeInMidMode;
    private int m_numOfDownVolsWhileLong;
    private int m_numOfUpVolsWhileLong;
    
    public SpeedArbHybridWrtSignalGenerator(final StrategyType strategyType, final StrategySecurity security, final SpreadTableScaleFormulaBridge spreadTableScaleFormulaBridge, final SpeedArbHybridUndSignalGenerator undSignalGenerator, final StateMachineEventBus warrantEventBus, final StrategyInfoSender strategyInfoSender, final GenericWrtParams speedArbWrtParams, final GenericStrategyTypeParams speedArbParams, final IssuerResponseTimeGenerator issuerResponseLagMonitor, final BucketOutputParams bucketOutputParams) {
        m_strategyType = strategyType;
        m_security = security;
        m_underlying = security.underlying();
        m_warrantEventBus = warrantEventBus;
        m_spreadTable = security.spreadTable();		
        m_speedArbWrtParams = speedArbWrtParams;
        m_speedArbParams = speedArbParams;
        m_speedArbWrtStats = new GenericWrtStatsParams(m_speedArbWrtParams);
        m_spreadTableScaleFormulaBridge = spreadTableScaleFormulaBridge;
        m_strategyInfoSender = strategyInfoSender;
        m_undSignalGenerator = undSignalGenerator;
        if (m_underlying.securityType().equals(SecurityType.INDEX)) {
            //TODO hack for testing
            m_waBucketPricer = BucketPricer.of(PricingMode.WEIGHTED.value(), security.underlyingSid(), security.sid(), security.putOrCall(), security.spreadTable(), security.convRatio() / 1000, speedArbWrtParams.issuerMaxLag(), SpeedArbHybridContext.DELTA_ALLOWANCE);
            m_mpBucketPricer = BucketPricer.of(PricingMode.MID.value(), security.underlyingSid(), security.sid(), security.putOrCall(), security.spreadTable(), security.convRatio() / 1000, speedArbWrtParams.issuerMaxLag(), SpeedArbHybridContext.DELTA_ALLOWANCE);
        }
        else {
            m_waBucketPricer = BucketPricer.of(PricingMode.WEIGHTED.value(), security.underlyingSid(), security.sid(), security.putOrCall(), security.spreadTable(), security.convRatio(), speedArbWrtParams.issuerMaxLag(), SpeedArbHybridContext.DELTA_ALLOWANCE);
            m_mpBucketPricer = BucketPricer.of(PricingMode.MID.value(), security.underlyingSid(), security.sid(), security.putOrCall(), security.spreadTable(), security.convRatio(), speedArbWrtParams.issuerMaxLag(), SpeedArbHybridContext.DELTA_ALLOWANCE);
        }
        m_bucketOutputParams = bucketOutputParams;
        m_ukWaPricingModeBridge = new UnknownWeightedAveragePricingModeBridge();
        m_ukMpPricingModeBridge = new UnknownMidPricePricingModeBridge();
        m_waPricingModeBridge = new WeightedAveragePricingModeBridge();
        m_mpPricingModeBridge = new MidPricePricingModeBridge();
        m_activePricingModeBridge = m_ukWaPricingModeBridge;
        m_standbyPricingModeBridge = m_ukWaPricingModeBridge;
        m_issuerResponseLagMonitor = issuerResponseLagMonitor;        
        m_triggerInfo = m_security.orderBook().triggerInfo();
        m_holdBidBanPrices = new IntArrayPriorityQueue(8);
        m_20msTradesVolumeUpdateHandler = new RollingWindowTimeFrame.SimpleAdditionDiscreteTimeWindowUpdateHandler();
        m_20msTradesVolumeTimeFrame = new RollingWindowTimeFrame(m_20msTradesVolumeUpdateHandler, 20_000_000L, 32, 32);
    }

    public void start() throws Exception {
        m_security.registerMdUpdateHandler(this);
        m_security.registerGreeksUpdateHandler(this);
        LOG.debug("Switching pricing mode: secCode {}, pricingMode {}, trigger seqNum {}, violations[mid:0, weighted:0]", m_security.code(), box(m_speedArbWrtParams.defaultPricingMode().value()), box(m_triggerInfo.triggerSeqNum()));
        m_speedArbWrtParams.pricingMode(m_speedArbWrtParams.defaultPricingMode());
        refreshPricingModeBridge();
    }

    public void reset() throws Exception {
        m_bestBidPrice = 0;
        m_bestBidLevel = 0;
        m_bestBidQty = 0;
        m_bestAskPrice = 0;        
        m_bestAskLevel = 0;
        m_bestAskLevelNotOurs = 0;
        m_tickBelowBestAskPriceNotOurs = 0;
        m_prevBestBidPrice = 0;
        m_prevBestAskPrice = 0;
        m_pricePerUndTick = 0;
        m_prevMmSpread = Integer.MAX_VALUE;
        m_mmSpread = Integer.MAX_VALUE;
        m_targetSpread = Integer.MAX_VALUE;
        m_targetSpreadEndTime = Long.MAX_VALUE;
        m_lastTickNanoOfDay = 0;
        m_askPriceAtPrevUndTick = 0;
        m_isAtTargetSpread = false;
        m_isPrevAtTargetSpread = false;
        m_isLooselyTight = false;
        m_delta = 0;
        m_speedArbWrtParams.pricingMode(m_speedArbWrtParams.defaultPricingMode());
        m_bucketOutputParams.reset();
        refreshPricingModeBridge();
        m_speedArbWrtParams.tickSensitivity(0);
        m_waPricingModeBridge.setNumDownVols(0);
        m_waPricingModeBridge.setNumUpVols(0);
        m_waPricingModeBridge.setNumViolations(0);
        m_mpPricingModeBridge.setNumDownVols(0);
        m_mpPricingModeBridge.setNumUpVols(0);
        m_mpPricingModeBridge.setNumViolations(0);
        m_waBucketPricer.clear();
        m_mpBucketPricer.clear();
        m_canCollectBuckets = false;
        m_holdBidBanPrices.clear();        
        m_startPricingModeNanoOfDay = 0;
        m_timeInWeightedMode = 0;
        m_timeInMidMode = 0;
        m_numOfDownVolsWhileLong = 0;
        m_numOfUpVolsWhileLong = 0;
        m_20msTradesVolumeTimeFrame.clear();
    }	

    @Override
    public void onOrderBookUpdated(final Security srcSecurity, final long timestamp, final MarketOrderBook orderBook) throws Exception {
        m_lastTickNanoOfDay = timestamp;
        if (m_startPricingModeNanoOfDay == 0) {
            m_startPricingModeNanoOfDay = m_lastTickNanoOfDay;
        }
        m_triggerInfo = m_security.orderBook().triggerInfo();
        updateWarrantOrderBook();
    }
    
    public void updateWarrantOrderBook() throws Exception {
        boolean sendParamUpdate = false; 
        m_mmSpread = Integer.MAX_VALUE;        
        m_isLooselyTight = false;
        m_isAtTargetSpread = false;
        if (!m_security.orderBook().bidSide().isEmpty()) {
            Tick tick = m_security.orderBook().bidSide().bestOrNullIfEmpty();
            m_bestBidPrice = tick.price();
            m_bestBidLevel = tick.tickLevel();
            m_bestBidQty = tick.qty();
            
            if (tick.qty() >= m_speedArbWrtParams.mmBidSize()) {
                m_mmBidPrice = m_bestBidPrice;
                m_mmBidLevel = m_bestBidLevel;
            }
            else {
                m_mmBidPrice = 0;
                m_mmBidLevel = 0;            	
                final Iterator<Tick> iterator = m_security.orderBook().bidSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= m_speedArbWrtParams.mmBidSize()) {
                        m_mmBidPrice = tick.price();
                        m_mmBidLevel = tick.tickLevel();
                        break;
                    }
                }
            }
        }
        else {
            m_bestBidPrice = 0;
            m_bestBidLevel = 0;
            m_bestBidQty = 0;
            m_mmBidPrice = 0;
            m_mmBidLevel = 0;            
        }

        if (!m_security.orderBook().askSide().isEmpty()) {
            Tick tick = m_security.orderBook().askSide().bestOrNullIfEmpty();
            m_bestAskPrice = tick.price();
            m_bestAskLevel = tick.tickLevel();
            if (m_security.limitOrderQuantity() == 0 || m_security.limitOrderPrice() != m_bestAskPrice || tick.qty() > m_security.limitOrderQuantity()) {
                m_bestAskLevelNotOurs = m_bestAskLevel;
            }
            else {
                m_bestAskLevelNotOurs = 0;
                final Iterator<Tick> iterator = m_security.orderBook().askSide().localPriceLevelsIterator();
                iterator.next();
                if (iterator.hasNext()) {
                    final Tick secondTick = iterator.next();
                    m_bestAskLevelNotOurs = secondTick.tickLevel();
                }
            }

            if (tick.qty() >= m_speedArbWrtParams.mmAskSize()) {
                m_mmAskPrice = m_bestAskPrice;
                m_mmAskLevel = m_bestAskLevel;
            }
            else {
                m_mmAskPrice = 0;
                m_mmAskLevel = 0;
                final Iterator<Tick> iterator = m_security.orderBook().askSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= m_speedArbWrtParams.mmAskSize()) {
                        m_mmAskPrice = tick.price();
                        m_mmAskLevel = tick.tickLevel();
                        break;
                    }
                }
            }            

            // additional logic when we have mm ask
            if (m_mmAskPrice > 0) {
                // additional logic when we have mm spread
                if (m_mmBidPrice > 0) {
                    m_mmSpread = m_mmAskLevel - m_mmBidLevel;
                }
                // find mm ask tick size and calculate tick sensitivity
	            final int warrantTickSize = m_spreadTable.priceToTickSize(m_mmAskPrice);
	            if (warrantTickSize != m_mmAskTickSize) {
	                m_mmAskTickSize = warrantTickSize;
	                m_speedArbWrtParams.tickSensitivity((int)(m_pricePerUndTick * 1000.0f / warrantTickSize));
	                sendParamUpdate = true;
	            }
            }
            else {
                m_mmAskTickSize = 0;
                m_speedArbWrtParams.tickSensitivity(0);
            }
            m_tickBelowBestAskPriceNotOurs = m_bestAskLevelNotOurs > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(Math.max(m_bestAskLevelNotOurs - 1 - m_speedArbWrtParams.wideSpreadBuffer(), SpreadTable.SPREAD_TABLE_MIN_LEVEL)) : 0;
            m_isLooselyTight = calcIsLooselyTightWithKnownSpread();
        }
        else {
            m_speedArbWrtParams.tickSensitivity(0);
            m_bestAskPrice = 0;
            m_bestAskLevel = 0;
            m_bestAskLevelNotOurs = 0;
            m_tickBelowBestAskPriceNotOurs = 0;
            m_mmAskPrice = 0;
            m_mmAskLevel = 0;
            m_mmAskTickSize = 0;
        }
        maintainHoldBidBanPrices(m_mmBidPrice);
        if (m_mmSpread != m_speedArbWrtParams.warrantSpread()) {
            m_speedArbWrtParams.warrantSpread(m_mmSpread);
            m_lastMmSpreadUpdateTime = m_lastTickNanoOfDay;
            sendParamUpdate = true;
        }
        // detect target spread updates
        m_isAtTargetSpread = detectTargetSpreadUpdates();
        if (checkForTargetSpreadReset()) {
            setTargetSpreadToMmSpread();
            setPricersTargetSpread();
        }
        if (updateSpreadState()) {
            sendParamUpdate = true;
        }

        if (m_mmSpread == Integer.MAX_VALUE) {
            m_issuerResponseLagMonitor.onMmOrderBookUpdated(m_lastTickNanoOfDay, m_mmBidLevel, m_mmAskLevel, m_targetSpread, false);
        }
        else if (m_mmSpread == m_targetSpread) {
            m_issuerResponseLagMonitor.onMmOrderBookUpdated(m_lastTickNanoOfDay, m_mmBidLevel, m_mmAskLevel, m_targetSpread, true);
        }
        else if (m_mmSpread >= m_prevMmSpread && m_prevMmSpread != Integer.MAX_VALUE) {
            m_issuerResponseLagMonitor.onMmOrderBookUpdated(m_lastTickNanoOfDay, m_mmBidLevel, m_mmAskLevel, m_targetSpread, false);
        }
        //if (m_mmSpread == m_targetSpread || m_mmSpread == Integer.MAX_VALUE || (m_mmSpread >= m_prevMmSpread && m_prevMmSpread != Integer.MAX_VALUE)) {
        //    m_issuerResponseLagMonitor.onMmOrderBookUpdated(m_lastTickNanoOfDay, m_mmBidLevel, m_mmAskLevel, m_targetSpread, m_isAtTargetSpread);
        //}

        if (m_canCollectBuckets) {
            final ViolationType waViolationType = m_waBucketPricer.observeDerivTick(m_lastTickNanoOfDay, 
                    m_bestBidPrice, 
                    m_bestAskPrice, 
                    m_mmBidPrice, 
                    m_mmAskPrice,
                    m_mmSpread,
                    m_triggerInfo);
            final ViolationType mpViolationType = m_mpBucketPricer.observeDerivTick(m_lastTickNanoOfDay, 
                    m_bestBidPrice, 
                    m_bestAskPrice, 
                    m_mmBidPrice, 
                    m_mmAskPrice,
                    m_mmSpread,
                    m_triggerInfo);
            final boolean hasResetStandBy = handleDerivTickViolationType(m_waPricingModeBridge, m_lastTickNanoOfDay, waViolationType, false);
            handleDerivTickViolationType(m_mpPricingModeBridge, m_lastTickNanoOfDay, mpViolationType, hasResetStandBy);        
            detectPricingMode(waViolationType, mpViolationType);
        }
        m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.WARRANT_TICK_RECEIVED);

        m_prevMmSpread = m_mmSpread;
        m_prevBestBidPrice = m_bestBidPrice;
        m_prevBestAskPrice = m_bestAskPrice;
        m_prevMmAskPrice = m_mmAskPrice;
        m_prevMmBidPrice = m_mmBidPrice;
        m_isPrevAtTargetSpread = m_isAtTargetSpread;
        if (sendParamUpdate) {
            sendStatsUpdatesThrottled();
        }
    }
    
    private void maintainHoldBidBanPrices(final int mmBidPrice) {
        if (!m_holdBidBanPrices.isEmpty()) {
            while (!m_holdBidBanPrices.isEmpty() && mmBidPrice > m_holdBidBanPrices.firstInt()) {
                m_holdBidBanPrices.dequeueInt();
            }
            if (mmBidPrice > m_prevTradePriceForHoldBidBan) {
                m_prevTradePriceForHoldBidBan = 0;
            }
        }
    }

    private int m_prevTradePriceForHoldBidBan;
    @Override
    public void onTradeReceived(final Security baseSecurity, final long timestamp, final MarketTrade trade) throws Exception {
        m_lastTickNanoOfDay = timestamp;
        if (trade.side() == -1) {
            if (m_speedArbWrtParams.useHoldBidBan() && trade.price() != m_prevTradePriceForHoldBidBan) {
                m_prevTradePriceForHoldBidBan = trade.price();
                m_holdBidBanPrices.enqueue(trade.price());
                LOG.debug("Hold bid ban price set: secCode {}, priceAdded {}, bannedBuyPrice {}, trigger seqNum {}", m_security.code(), box(trade.price()), box(this.m_holdBidBanPrices.firstInt()), box(m_triggerInfo.triggerSeqNum()));
            }
            if (m_speedArbWrtParams.tradesVolumeThreshold() != 0) {
                m_20msTradesVolumeTimeFrame.recordValue(timestamp, trade.quantity());
            }
        }
        else {
            if (m_speedArbWrtParams.tradesVolumeThreshold() != 0) {
                m_20msTradesVolumeTimeFrame.recordValue(timestamp, -trade.quantity());
            }
        }
        m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.MARKET_TRADE_RECEIVED);
    }

    public long getAndRefresh20msNetTradesVolume() {
        m_20msTradesVolumeTimeFrame.updateTimeFrame(m_lastTickNanoOfDay);
        return m_20msTradesVolumeUpdateHandler.accumValue();
    }

    public long get20msNetTradesVolume() {        
        return m_20msTradesVolumeUpdateHandler.accumValue();
    }
    
    public void resetHoldBidBanPrice() {
        m_holdBidBanPrices.clear();
        LOG.debug("Hold bid ban price cleared: secCode {}, trigger seqNum {}", m_security.code(), box(m_triggerInfo.triggerSeqNum()));
    }
    
    public int getHoldBidBanPrice() {
        return m_holdBidBanPrices.isEmpty() ? Integer.MAX_VALUE : m_holdBidBanPrices.firstInt();
    }

    public int getTickSensitivity() {
        return m_speedArbWrtParams.tickSensitivity();
    }

    public boolean isTickSensitivityMet() {
        return m_speedArbWrtParams.tickSensitivity() >= m_speedArbWrtParams.tickSensitivityThreshold();
    }

    public long lastTickNanoOfDay() {
        return m_lastTickNanoOfDay;
    }
    
    public TriggerInfo triggerInfo() {
        return m_triggerInfo;
    }

    public int getMmSpread() {
        return m_mmSpread;
    }
    
    public int getMMBidPrice() {
        return m_mmBidPrice;
    }
    
    public int getMMBidLevel() {
        return m_mmBidLevel;
    }
    
    public int getMMAskPrice() {
        return m_mmAskPrice;
    }
    
    public int getMMAskLevel() {
        return m_mmAskLevel;
    }
    
    public int getPrevMMAskPrice() {
        return m_prevMmAskPrice;
    }
    
    public int getPrevMMBidPrice() {
        return m_prevMmBidPrice;
    }    
    
    public int getBidPrice() {
        return m_bestBidPrice;
    }

    public long getBidQty() {
        return m_bestBidQty;
    }
    
    public int getBidLevel() {
        return m_bestBidLevel;
    }

    public int getAskPrice() {
        return m_bestAskPrice;
    }

    public int getAskLevel() {
        return m_bestAskLevel;
    }

    public int getPrevBidPrice() {
        return m_prevBestBidPrice;
    }

    public int getPrevAskPrice() {
        return m_prevBestAskPrice;
    }

    public int getTargetSpread() {
        return m_targetSpread;
    }
    
    public boolean isLooselyTight() {
        return m_isLooselyTight;
    }
    
    public int getAskPriceAtPrevUndTick() {
        return m_askPriceAtPrevUndTick;
    }
    
    public int getBucketSize() {
        return m_bucketSize;
    }

    public GenericWrtParams getSpeedArbWrtParams() {
        return m_speedArbWrtParams;
    }

    public void sendStatsUpdates() {
        this.m_strategyInfoSender.broadcastStrategyParamsNoPersist(m_speedArbWrtStats);
    }

    public void sendStatsUpdatesThrottled() {
        this.m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_speedArbWrtStats);
    }

    public void sendStatsUpdatesBatched() {
        this.m_strategyInfoSender.broadcastStrategyParamsNoPersistBatched(m_speedArbWrtStats);
    }
    
    public void sendStatsUpdatesBatchedPerist() {
        this.m_strategyInfoSender.broadcastStrategyParamsBatched(m_speedArbWrtParams);
    }

    public void sendBucketUpdatesThrottled(){
		this.m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_bucketOutputParams);
    }
   
    private void calcPricePerUndTick() {   
        if (m_underlyingTickSize != 0 && m_speedArbWrtParams.greeks().delta() != 0) {
            final float pricePerUndTick = m_spreadTableScaleFormulaBridge.calcPricePerUnderlyingTick(m_underlyingTickSize, m_speedArbWrtParams.greeks(), m_security.convRatio());
            if (m_pricePerUndTick != pricePerUndTick) {
                m_pricePerUndTick = pricePerUndTick;        
                if (m_mmAskTickSize != 0) {
                    final float tickSensitivity = (m_pricePerUndTick * 1000.0f / m_mmAskTickSize);
                    final int prevTickSensitivity = m_speedArbWrtParams.tickSensitivity();
                    m_speedArbWrtParams.tickSensitivity((int)tickSensitivity);
                    m_bucketSize = (int)(m_underlyingTickSize * m_mmAskTickSize / m_pricePerUndTick);
                    if (Math.abs(prevTickSensitivity - m_speedArbWrtParams.tickSensitivity()) > 500) { 
                        //LOG.info("Tick sensitivity/Delta for {} updated: Tick sensitivity {}, delta {}, delta per tick {}", m_security.code(), m_speedArbWrtParams.tickSensitivity(), m_deltaC, m_deltaPerTick);
                        sendStatsUpdatesThrottled();
                    }
                }
            }
            m_isLooselyTight = calcIsLooselyTight();
        }
        else if (m_pricePerUndTick != 0) {
            m_pricePerUndTick = 0;
            m_speedArbWrtParams.tickSensitivity(0);
            m_isLooselyTight = calcIsLooselyTight();
        }
        if (updateSpreadState()) {
            sendStatsUpdatesBatched();
        }
    }
    
    @Override
    public void onGreeksUpdated(final Greeks greeks) throws Exception {
        m_speedArbWrtParams.greeks(greeks);
        if (m_delta != greeks.delta()) {            
            m_delta = greeks.delta();
            m_waBucketPricer.observeGreeks(1, greeks);
            m_mpBucketPricer.observeGreeks(1, greeks);
            calcPricePerUndTick();
        }
    }
    
    @Override
    public void onUnderlyingTickSizeChanged(final int tickSize) throws Exception {
        m_underlyingTickSize = tickSize;
        calcPricePerUndTick();        
    }
   
    @Override
    public void observeUndSpot(final long nanoOfDay, final long weightedAverage, final long midPrice, final boolean isTightSpread, final TriggerInfo triggerInfo) throws Exception {
        m_triggerInfo = triggerInfo;
        m_lastTickNanoOfDay = nanoOfDay;
        if (m_startPricingModeNanoOfDay == 0) {
            m_startPricingModeNanoOfDay = m_lastTickNanoOfDay;
        }
        if (checkForTargetSpreadReset()) {
            setTargetSpreadToMmSpreadFromNonDerivTick();
            setPricersTargetSpread();
            if (updateSpreadState()) {
                sendStatsUpdatesBatched();
            }
        }
        if (m_canCollectBuckets) {
            final ViolationType waViolationType = m_waBucketPricer.observeUndTick(nanoOfDay, weightedAverage, isTightSpread, m_triggerInfo, m_waPricingModeBridge.getOutInterval());
            final ViolationType mpViolationType = m_mpBucketPricer.observeUndTick(nanoOfDay, midPrice, isTightSpread, m_triggerInfo, m_mpPricingModeBridge.getOutInterval());
            final boolean hasResetStandBy = handleStockTickViolationType(m_waPricingModeBridge, nanoOfDay, waViolationType, false);
            handleStockTickViolationType(m_mpPricingModeBridge, nanoOfDay, mpViolationType, hasResetStandBy);
            detectPricingMode(waViolationType, mpViolationType);
        }
        m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.STOCK_SPOT_UPDATED);
        m_askPriceAtPrevUndTick = m_bestAskPrice;
    }
    
    private boolean handleStockTickViolationType(final PricingModeBridge pricingModeBridge, final long nanoOfDay, final ViolationType violationType, boolean hasResetStandBy) throws Exception {
        if (violationType != ViolationType.NO_VIOLATION) {            
            final boolean isActive = pricingModeBridge == m_activePricingModeBridge; 
            if (isActive) {
                setTargetSpreadToMmSpreadFromNonDerivTick();
                LOG.debug("Bucket violation for active pricer detected, resetting pricer (stock): secCode {}, pricer {}, violation {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(pricingModeBridge.getId()), violationType, box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                resetPricerAndRegister(m_activePricingModeBridge.getBucketPricer(), m_activePricingModeBridge.getOutInterval(), nanoOfDay);
                if (m_targetSpread != m_standbyPricingModeBridge.getBucketPricer().targetSpreadInTick()) {
                    LOG.debug("Set targetSpread and reset for standby pricer due to active pricer bucket violation: secCode {}, pricer {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(m_standbyPricingModeBridge.getId()), box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                    resetPricerAndRegister(m_standbyPricingModeBridge.getBucketPricer(), m_standbyPricingModeBridge.getOutInterval(), nanoOfDay);
                    hasResetStandBy = true;
                }
            }
            else if (hasResetStandBy) {
                LOG.debug("Bucket violation for standby pricer detected, pricer has already been reset: secCode {}, pricer {}, violation {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(pricingModeBridge.getId()), violationType, box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));                
            }
            else {
                LOG.debug("Bucket violation for standby pricer detected, resetting pricer: secCode {}, pricer {}, violation {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(pricingModeBridge.getId()), violationType, box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                resetPricerAndRegister(pricingModeBridge.getBucketPricer(), pricingModeBridge.getOutInterval(), nanoOfDay);
                hasResetStandBy = true;
            }
            handleVolViolationType(pricingModeBridge, isActive, nanoOfDay, violationType, false);
        }
        return hasResetStandBy;
    }
    
    private boolean handleDerivTickViolationType(final PricingModeBridge pricingModeBridge, final long nanoOfDay, final ViolationType violationType, boolean hasResetStandBy) throws Exception {
        if (violationType != ViolationType.NO_VIOLATION) {
            final boolean isActive = pricingModeBridge == m_activePricingModeBridge;
            if (isActive) {
                setTargetSpreadToMmSpreadFromNonDerivTick();
                LOG.debug("Bucket violation for active pricer detected, resetting pricer: secCode {}, pricer {}, violation {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(pricingModeBridge.getId()), violationType, box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                resetPricerAndRegister(m_activePricingModeBridge.getBucketPricer(), m_targetSpread, nanoOfDay);
                if (m_targetSpread != m_standbyPricingModeBridge.getBucketPricer().targetSpreadInTick()) {                    
                    LOG.debug("Set targetSpread and reset for standby pricer due to active pricer bucket violation: secCode {}, pricer {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(m_standbyPricingModeBridge.getId()), box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                    resetPricerAndRegister(m_standbyPricingModeBridge.getBucketPricer(), m_targetSpread, nanoOfDay);
                    hasResetStandBy = true;
                }
            }
            else if (hasResetStandBy) {
                LOG.debug("Bucket violation for standby pricer detected, pricer has already been reset: secCode {}, pricer {}, violation {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(pricingModeBridge.getId()), violationType, box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));                
            }
            else {
                LOG.debug("Bucket violation for standby pricer detected, resetting pricer: secCode {}, pricer {}, violation {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(pricingModeBridge.getId()), violationType, box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                resetPricerAndRegister(pricingModeBridge.getBucketPricer(), m_mmSpread, nanoOfDay);
                hasResetStandBy = true;
            }
            handleVolViolationType(pricingModeBridge, isActive, nanoOfDay, violationType, true);            
        }
        return hasResetStandBy;
    }
    
    private void handleVolViolationType(final PricingModeBridge pricingModeBridge, final boolean isActive, final long nanoOfDay, final ViolationType violationType, final boolean fromWarrantTick) throws Exception {
        pricingModeBridge.incNumViolations();
        // LOG.info("Handle violation type, increase violations [pricerType:{}, violationType:{}, numViolations:{}]", pricingModeBridge.getId(), violationType.name(), pricingModeBridge.getNumViolations());
        if (violationType == ViolationType.DOWN_VOL) {
            pricingModeBridge.incNumDownVols();            
            if (isActive) {
                // convention: if we are not holding position, then pass numDownVols into the event as a negative number
                m_strategyInfoSender.sendEventBatched(m_strategyType, m_security, EventType.VOL_DOWN_SIGNAL, nanoOfDay, EventValueType.HAS_POSITION, m_security.position() == 0 ? -pricingModeBridge.getNumDownVols() : pricingModeBridge.getNumDownVols());
                if (m_security.position() > 0) {
                    m_numOfDownVolsWhileLong++;
                }
                m_warrantEventBus.fireEvent(fromWarrantTick ? SpeedArbHybridStrategySignalHandler.EventIds.ISSUER_DOWN_VOL_FROM_WARRANT_TICK : SpeedArbHybridStrategySignalHandler.EventIds.ISSUER_DOWN_VOL_FROM_UNDERLYING_TICK);
            }
            else {
                m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.ISSUER_DOWN_VOL_FOR_STANDBY_PRICER);
            }
        }
        else if (violationType == ViolationType.UP_VOL) {
            pricingModeBridge.incNumUpVols();
            if (isActive) {
                // convention: if we are not holding position, then pass numUpVols into the event as a negative number
                m_strategyInfoSender.sendEventBatched(m_strategyType, m_security, EventType.VOL_UP_SIGNAL, nanoOfDay, EventValueType.HAS_POSITION, m_security.position() == 0 ? -pricingModeBridge.getNumUpVols() : pricingModeBridge.getNumUpVols());
                if (m_security.position() > 0) {
                    m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.NON_DOWN_VOL_VIOLATION);
                    m_numOfUpVolsWhileLong++;
                }
            }
        }
        else {
            if (isActive) {
                if (m_security.position() > 0) {
                    m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.NON_DOWN_VOL_VIOLATION);
                }
            }            
        }
        sendStatsUpdatesThrottled();
    }
    
    private void resetPricerAndRegister(final BucketPricer bucketPricer, final LongInterval outInterval, final long nanoOfDay) {
        if (!outInterval.isEmpty())
            bucketPricer.resetAndRegister(nanoOfDay, outInterval);
        else
            bucketPricer.reset(nanoOfDay);
    }
    
    private void resetPricerAndRegister(final BucketPricer bucketPricer, final int targetSpread, final long nanoOfDay) {
        bucketPricer.reset(nanoOfDay);
        bucketPricer.hasTargetSpreadInTickBeenChangedAndRegisterUndInterval(nanoOfDay, m_mmBidPrice, m_mmAskPrice, m_mmSpread);
    }

    private boolean detectTargetSpreadUpdates() {
        if (m_mmSpread == m_targetSpread && m_mmSpread != Integer.MAX_VALUE) {
            if (!m_isPrevAtTargetSpread) {
                m_targetSpreadEndTime = Long.MAX_VALUE;
            }
            return true;
        }
        else if (m_mmSpread < m_targetSpread) {
            return m_isPrevAtTargetSpread;
        }
        else if (m_isPrevAtTargetSpread) {
            m_targetSpreadEndTime = m_lastTickNanoOfDay;
        }
        return false;
    }
    
    private boolean checkForTargetSpreadReset() {
        if (m_lastTickNanoOfDay - m_targetSpreadEndTime > m_speedArbWrtParams.spreadObservationPeriod()) {
            if (m_security.position() == 0) {
                return true;
            }
        }
        if (((m_targetSpread == Integer.MAX_VALUE && m_mmSpread != Integer.MAX_VALUE) || (m_mmSpread < m_targetSpread)) && (m_lastTickNanoOfDay - m_lastMmSpreadUpdateTime) > SPREAD_OBSERVATION_PERIOD_WHEN_TIGHTER) {
            return true;
        }
        return false;
    }
    
    private void setTargetSpreadToMmSpread() {
        m_targetSpread = m_mmSpread;
        m_targetSpreadEndTime = Long.MAX_VALUE;
        m_isAtTargetSpread = true;
    }
    
    private void setTargetSpreadToMmSpreadFromNonDerivTick() throws Exception {
        setTargetSpreadToMmSpread();
        m_isPrevAtTargetSpread = true;
        m_issuerResponseLagMonitor.onMmOrderBookUpdated(m_lastTickNanoOfDay, m_mmBidLevel, m_mmAskLevel, m_targetSpread, true);
    }
    
    private void setPricersTargetSpread() {
        LOG.debug("Target spread updated, resetting pricer: secCode {}, targetSpread {}, mmSpread {}, trigger seqNum {}", m_security.code(), box(m_targetSpread), box(m_mmSpread), box(m_triggerInfo.triggerSeqNum()));
        if (m_waBucketPricer.hasTargetSpreadInTickBeenChanged()) {
            m_waBucketPricer.resetTargetSpread(m_lastTickNanoOfDay, m_targetSpread);
        }
        if (m_mpBucketPricer.hasTargetSpreadInTickBeenChanged()) {
            m_mpBucketPricer.resetTargetSpread(m_lastTickNanoOfDay, m_targetSpread);
        }
        m_speedArbWrtParams.incNumSpreadResets();
        sendStatsUpdatesThrottled();
    }

    public boolean updateSpreadState() {
        final SpreadState newState = getSpreadState();
        if (!m_speedArbWrtParams.spreadState().equals(newState)) {
            m_speedArbWrtParams.spreadState(newState);
            return true;
        }
        return false;
    }
    
    private SpreadState getSpreadState() {
        if (m_security.position() > 0 && m_bestBidPrice < m_speedArbWrtParams.enterPrice() && m_mmSpread > m_speedArbWrtParams.enterMMSpread() && !m_isLooselyTight) {
            return SpreadState.TOO_WIDE;
        }
        return m_isAtTargetSpread ? SpreadState.NORMAL : SpreadState.WIDE; 
    }

    public void updateIsLooselyTight() {
        m_isLooselyTight = calcIsLooselyTight();
        updateSpreadState();
    }
    
    private boolean calcIsLooselyTight() {
        return (m_bestAskLevelNotOurs >= SpreadTable.SPREAD_TABLE_MIN_LEVEL && m_bestBidLevel >= SpreadTable.SPREAD_TABLE_MIN_LEVEL && calcIsLooselyTightWithKnownSpread());
    }
    
    private boolean calcIsLooselyTightWithKnownSpread() {
        return (m_bestAskLevelNotOurs - m_bestBidLevel < 3) || ((m_tickBelowBestAskPriceNotOurs - m_bestBidPrice) < m_pricePerUndTick);
    }
    
    public long getSpotPrice() {
        return m_activePricingModeBridge.getSpot();
    }
    
    public long getPrevSpotPrice() {
        return m_activePricingModeBridge.getPrevSpot();
    }
    
    public final BucketPricer getBucketPricer() {
        return m_activePricingModeBridge.getBucketPricer();
    }
    
    public long getStandbySpotPrice() {
        return m_standbyPricingModeBridge.getSpot();
    }

    public long getStandbyPrevSpotPrice() {
        return m_standbyPricingModeBridge.getPrevSpot();
    }

    public final BucketPricer getStandbyBucketPricer() {
        return m_standbyPricingModeBridge.getBucketPricer();
    }

    public void refreshIssuerMaxLagForBucketPricer() {
        this.m_waBucketPricer.issuerMaxLagNs(m_speedArbWrtParams.issuerMaxLag());
        this.m_mpBucketPricer.issuerMaxLagNs(m_speedArbWrtParams.issuerMaxLag());
    }
    
    private void detectPricingMode(final ViolationType waViolationType, final ViolationType mpViolationType) throws Exception {
        if (waViolationType != ViolationType.NO_VIOLATION && mpViolationType != ViolationType.NO_VIOLATION) {
            switchPricingMode(PricingMode.ADJUSTVOL);
        }
        else {
            if (m_waPricingModeBridge.getNumViolations() + 1 < m_mpPricingModeBridge.getNumViolations()) {
                switchPricingMode(PricingMode.WEIGHTED);
            }
            else if (m_waPricingModeBridge.getNumViolations() > m_mpPricingModeBridge.getNumViolations() + 1) {
                switchPricingMode(PricingMode.MID);
            }
            else {
                switchPricingMode(m_speedArbWrtParams.defaultPricingMode());
            }
        }
    }
    
    public PricingMode getPricingMode() {
        return m_speedArbWrtParams.pricingMode();
    }
    
    private void switchPricingMode(final PricingMode newMode) throws Exception {
        if (m_speedArbWrtParams.pricingMode() != newMode) {
//        	LOG.debug("Switching pricing mode: secCode {}, pricingMode {}, trigger seqNum {}", m_security.code(), box(newMode.value()), box(m_triggerInfo.triggerSeqNum()));
            LOG.debug("Switching pricing mode: secCode {}, pricingMode {}, trigger seqNum {}, violations[mid:{}, weighted:{}]", m_security.code(), box(newMode.value()), box(m_triggerInfo.triggerSeqNum()), box(m_mpPricingModeBridge.getNumViolations()), box(m_waPricingModeBridge.getNumViolations()));
            collectPricingModeStats();
            m_speedArbWrtParams.pricingMode(newMode);
            sendStatsUpdatesThrottled();
            refreshPricingModeBridge();
            if (m_activePricingModeBridge.getBucketPricer() != null) {
                m_warrantEventBus.fireEvent(SpeedArbHybridStrategySignalHandler.EventIds.PRICING_MODE_UPDATED);
                final int targetSpread = m_activePricingModeBridge.getBucketPricer().targetSpreadInTick();
                if (targetSpread != Integer.MAX_VALUE) {
                    m_targetSpread = targetSpread;
                    LOG.debug("Target spread updated by new active pricer: secCode {}, targetSpread {}, trigger seqNum {}", m_security.code(), box(m_targetSpread), box(m_triggerInfo.triggerSeqNum()));
                }
            }
        }
    }
    
    private void collectPricingModeStats() {
        switch (m_speedArbWrtParams.pricingMode()) {
        case MID:
            m_timeInMidMode += (m_lastTickNanoOfDay - m_startPricingModeNanoOfDay);
            break;
        case WEIGHTED:
            m_timeInWeightedMode += (m_lastTickNanoOfDay - m_startPricingModeNanoOfDay);
            break;
        default:
            break;
        }
        m_startPricingModeNanoOfDay = m_lastTickNanoOfDay;
    }
    
    private void refreshPricingModeBridge() {
        switch (m_speedArbWrtParams.pricingMode()) {
        case MID:
            m_activePricingModeBridge = m_mpPricingModeBridge;
            m_standbyPricingModeBridge = m_waPricingModeBridge;
            break;
        case WEIGHTED:
            m_activePricingModeBridge = m_waPricingModeBridge;
            m_standbyPricingModeBridge = m_mpPricingModeBridge;
            break;
        default:
            if (m_activePricingModeBridge == m_mpPricingModeBridge) {
                m_activePricingModeBridge = m_ukMpPricingModeBridge;
                m_standbyPricingModeBridge = m_ukWaPricingModeBridge;               
            }
            else {
                m_activePricingModeBridge = m_ukWaPricingModeBridge;
                m_standbyPricingModeBridge = m_ukMpPricingModeBridge;                
            }
        }
    }

    public void enableCollectBuckets() {
    	if (!m_canCollectBuckets) {
            m_canCollectBuckets = true;
            m_waBucketPricer.resetAndSetTargetSpreadInTick(m_targetSpread);
            m_mpBucketPricer.resetAndSetTargetSpreadInTick(m_targetSpread);
            LOG.debug("Enabled collecting buckets: secCode {}, trigger seqNum {}",  m_security.code(),  box(m_triggerInfo.triggerSeqNum()));
    	}
    }
    
    public void disableCollectBuckets() {
        if (m_canCollectBuckets) {
            m_canCollectBuckets = false;
            LOG.debug("Disabled collecting buckets: secCode {}, trigger seqNum {}",  m_security.code(),  box(m_triggerInfo.triggerSeqNum()));
        }
    }
    
    public void printStats() {
        LOG.info("Adjust vol statistics: secCode {}, numDownVolsWhileLong {}, numUpVolsWhileLong {}", m_security.code(), box(m_numOfDownVolsWhileLong), box(m_numOfUpVolsWhileLong)); 
        LOG.info("Pricing mode statistics: secCode {}, timeInMidMode {}, timeInWeightedMode {}", m_security.code(), box(m_timeInMidMode), box(m_timeInWeightedMode)); 
        m_issuerResponseLagMonitor.printStats();
    }

}
