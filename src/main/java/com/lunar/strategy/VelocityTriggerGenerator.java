package com.lunar.strategy;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.strategy.parameters.GenericUndParams;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class VelocityTriggerGenerator implements TriggerGenerator, MarketDataUpdateHandler {
    static final Logger LOG = LogManager.getLogger(VelocityTriggerGenerator.class);

    final static int TIMESTAMP_INDEX = 0;
    final static int VELOCITY_INDEX = 1;
    
    private final StrategySecurity security;
    private final GenericUndParams speedArbUndParams;
    
    final private int maxVelocityWindowEntries;
    final private int velocityWindowMask;
    final private long windowTime;
    final private long[][] m_velocityWindow;
    
    private int velocityWindowStartIndex = 0;
    private int velocityWindowCount = 0;
    private long velocity = 0;
    
    final ObjectArrayList<VelocityTriggerHandler> m_handlers;

    public VelocityTriggerGenerator(final StrategySecurity security, final GenericUndParams params, final long windowTime, final int maxVelocityWindowEntries) {
        this.security = security;
        this.speedArbUndParams = params;
        this.windowTime = windowTime;
        this.maxVelocityWindowEntries = maxVelocityWindowEntries;
        this.velocityWindowMask = maxVelocityWindowEntries - 1;
        this.m_velocityWindow = new long[maxVelocityWindowEntries][2];
        
        m_handlers = ObjectArrayList.wrap(new VelocityTriggerHandler[32]);
        m_handlers.size(0);
    }
    
    @Override
    public void onTradeReceived(final Security baseSecurity, final long timestamp, final MarketTrade trade) {
        final long netDelta = -1L * trade.side() * trade.quantity() * trade.price();
        velocity += netDelta;
        maintainWindow(timestamp);
        if (velocityWindowCount == maxVelocityWindowEntries) {
            velocity -= m_velocityWindow[velocityWindowStartIndex][VELOCITY_INDEX];
            velocityWindowCount--;                
            velocityWindowStartIndex = (velocityWindowStartIndex + 1) & velocityWindowMask;
        }
        final int index = (velocityWindowStartIndex + velocityWindowCount) & velocityWindowMask;
        final long[] timeVelocityPair = m_velocityWindow[index];
        timeVelocityPair[TIMESTAMP_INDEX] = timestamp;
        timeVelocityPair[VELOCITY_INDEX] = netDelta;
        velocityWindowCount++;
    }

    @Override
    public void onOrderBookUpdated(final Security srcSecurity, final long timestamp, final MarketOrderBook orderBook) {
        maintainWindow(timestamp);
    }
    
    private void maintainWindow(final long timestamp) {
        final long minTime = timestamp - windowTime;
        final int endIndex = velocityWindowStartIndex + velocityWindowCount;
        int index = velocityWindowStartIndex;
        int i;
        long localVelocity = velocity;
        for (i = velocityWindowStartIndex; i < endIndex; i++)
        {
            index = i & (maxVelocityWindowEntries - 1);
            final long[] timeVelocityPair = m_velocityWindow[index];
            if (timeVelocityPair[TIMESTAMP_INDEX] >= minTime) {
                break;
            }
            localVelocity -= timeVelocityPair[VELOCITY_INDEX];
        }
        velocity = localVelocity;
        velocityWindowCount = velocityWindowCount - (i - velocityWindowStartIndex);
        velocityWindowStartIndex = index;
    }

    @Override
    public boolean isTriggeredForCall() {
        return velocity >= speedArbUndParams.velocityThreshold();
    }
    
    @Override
    public boolean isTriggeredForPut() {
        return -velocity >= speedArbUndParams.velocityThreshold();
    }

    @Override
    public int getTriggerStrengthForCall() {
        if (velocity > speedArbUndParams.velocityThreshold3()) {
            return STRONG_TRIGGER;
        }
        else if (velocity > speedArbUndParams.velocityThreshold2()) {
            return MEDIUM_TRIGGER;
        }
        else if (velocity > speedArbUndParams.velocityThreshold()) {
            return WEAK_TRIGGER;
        }
        return NO_TRIGGER;
    }

    @Override
    public int getTriggerStrengthForPut() {
        if (-velocity > speedArbUndParams.velocityThreshold3()) {
            return STRONG_TRIGGER;
        }
        else if (-velocity > speedArbUndParams.velocityThreshold2()) {
            return MEDIUM_TRIGGER;
        }
        else if (-velocity > speedArbUndParams.velocityThreshold()) {
            return WEAK_TRIGGER;
        }
        return NO_TRIGGER;
    }
    
    @Override
    public void reset() {
        velocity = 0;
        velocityWindowStartIndex = 0;
        velocityWindowCount = 0;
    }

    @Override
    public long getExplainValue() {
        return velocity;
    }

    @Override
    public void registerHandler(final StrategySignalHandler handler) {
        if (handler instanceof VelocityTriggerHandler) {
            final VelocityTriggerHandler velocityTriggerHandler = (VelocityTriggerHandler)handler;
            if (!m_handlers.contains(velocityTriggerHandler)) {
                m_handlers.add(velocityTriggerHandler);
                velocityTriggerHandler.onTriggerGeneratorSubscribed(this);
                if (m_handlers.size() == 1) {
                    security.registerMdUpdateHandler(this);
                    LOG.debug("Velocity trigger is enabled: secCode {}, windowTime {}", security.code(), box(windowTime));
                }
            }
        }
    }

    @Override
    public void unregisterHandler(final StrategySignalHandler handler) {
        if (handler instanceof VelocityTriggerHandler) {
            final VelocityTriggerHandler velocityTriggerHandler = (VelocityTriggerHandler)handler;
            if (m_handlers.remove(velocityTriggerHandler)) {
                velocityTriggerHandler.onTriggerGeneratorUnsubscribed(this);
                if (m_handlers.isEmpty()) {
                    security.unregisterMdUpdateHandler(this);
                    LOG.debug("Velocity trigger is disabled: secCode {}, windowTime {}", security.code(), box(windowTime));
                }
            }
        }
    }
    
}