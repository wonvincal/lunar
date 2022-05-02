package com.lunar.strategy.scoreboard;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.RollingWindowTimeFrame;
import com.lunar.core.RollingWindowTimeFrame.TimeNormalizer;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.order.Tick;
import com.lunar.pricing.Greeks;
import com.lunar.strategy.GreeksUpdateHandler;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.MarketHoursUtils;
import com.lunar.strategy.parameters.WrtOutputParams;
import com.lunar.strategy.scoreboard.stats.WarrantBehaviourStats;

public class WarrantBehaviourAnalyser implements MarketDataUpdateHandler, MarketOrderHandler, VolatilityChangedHandler, GreeksUpdateHandler, UnderlyingOrderBookUpdateHandler, OurTriggerHandler {
    static final Logger LOG = LogManager.getLogger(WarrantBehaviourAnalyser.class);

    static private final long STABLE_TIME = 5_000_000L;
    static private final long ATOMIC_TIME_DELAY = 100_000L;
    static private final long SPREAD_WINDOW_TIME = 0L;
    static private final int SPREAD_WINDOW_SIZE = 32;
    static private final long MM_START_TIME = MarketHoursUtils.MARKET_MORNING_OPEN_TIME + 300_000_000_000L;
    
    static public class SpreadTracker implements RollingWindowTimeFrame.WindowUpdateHandler {
        final private static int START_TIME_INDEX = 0;
        final private static int SPREAD_INDEX = 1;
        final private static int TIME_IN_SPREAD_INDEX = 2;
        final private static int SPREAD_X_TIME_INDEX = 3;
        final private static int MAX_SPREAD = 20;
        
        final private TimeNormalizer timeNormalizer;
        final private long[] timeBySpread;
        final private int scale;
        final private boolean canRemove;
        final private boolean trackMinAndMode;
        private long totalTime = 0;
        private long totalSpread = 0;
        private long minSpread = Long.MAX_VALUE;
        private long modeSpread = Long.MAX_VALUE;
        private long twaSpread_3L = Long.MAX_VALUE;
        private long percentTimeInMinSpread_3L = Long.MAX_VALUE;
        private long percentTimeInModeSpread_3L = Long.MAX_VALUE;
        private long [] currentEntry = null;
        private long [] stableEntry = null;
        private long stableTimeLastUpdated = 0;
        
        public SpreadTracker(final int scale, final boolean canRemove, final boolean trackMinAndMode) {
            timeBySpread = new long[MAX_SPREAD];
            timeNormalizer = RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER;
            this.scale = scale;
            this.canRemove = canRemove;
            this.trackMinAndMode = trackMinAndMode;
        }        
        
        public TimeNormalizer timeNormalizer() {
            return timeNormalizer;
        };
        
        public int scale() {
            return scale;
        }
        
        long getStableTimeLastUpdated() {
            return timeNormalizer.denormalizeTime(stableTimeLastUpdated);
        }
        
        long getTotalTime(final long nanoOfDay) {
            if (currentEntry == null)
                return 0;
            final long timeInCurrentSpread = nanoOfDay - currentEntry[START_TIME_INDEX];
            return totalTime + timeInCurrentSpread; 
        }
        
        long getTimeInSpread(final long nanoOfDay, final int spread) {
            if (currentEntry != null && areSpreadSame(currentEntry[SPREAD_INDEX], spread)) {
                return timeBySpread[getIndexFromTimeBySpread(spread)] + (nanoOfDay - currentEntry[START_TIME_INDEX]); 
            }
            return timeBySpread[getIndexFromTimeBySpread(spread)];
        }
        
        long getPercentTimeInSpread(final long nanoOfDay, final int spread) {
            final long timeInSpread = getTimeInSpread(nanoOfDay, spread);
            final long totalTime = getTotalTime(nanoOfDay);
            final long totalTimeScaled = totalTime / 100000L;
            if (totalTimeScaled > 0) {
                return timeInSpread / totalTimeScaled;
            }
            return Long.MAX_VALUE;
        }
        
        public long minSpread() {
            return this.minSpread;
        };
        
        public long modeSpread() {
            return this.modeSpread;
        }
        
        public long twaSpread_3L() {
            return this.twaSpread_3L;
        }
        
        public long percentTimeInMinSpread_3L() {
            return this.percentTimeInMinSpread_3L;
        }
        
        public long percentTimeInModeSpread_3L() {
            return this.percentTimeInModeSpread_3L;
        }
        
        @Override
        public boolean discreteTime() {
            return false;
        }

        @Override
        public int entrySize() {
            return 4;
        }
        
        @Override
        public void reset() {
            totalTime = 0;
            totalSpread = 0;
            minSpread = Long.MAX_VALUE;
            modeSpread = Long.MAX_VALUE;
            twaSpread_3L = Long.MAX_VALUE;
            percentTimeInMinSpread_3L = Long.MAX_VALUE;
            percentTimeInModeSpread_3L = Long.MAX_VALUE;
            currentEntry = null;
            stableEntry = null;
            stableTimeLastUpdated = 0;
            for (int i = 0; i < MAX_SPREAD; i++) {
                timeBySpread[i] = 0;
            }            
        }
        
        private void collectStats(final long nanoOfDay) {
            if (currentEntry != null && currentEntry[SPREAD_INDEX] != 0) {
                final long timeInCurrentSpread = nanoOfDay - currentEntry[START_TIME_INDEX];
                final long currentSpread = currentEntry[SPREAD_INDEX];
                final long actualTotalTime = totalTime + timeInCurrentSpread;                
                final long actualTotalSpread = totalSpread + timeInCurrentSpread * currentSpread;
                final long actualTotalTimeScaled = actualTotalTime / 100000L;
                if (actualTotalTimeScaled > 0) {
                    twaSpread_3L = actualTotalSpread / (actualTotalTimeScaled * 100);
                    if (this.trackMinAndMode) {
                        long timeInMinSpread = 0;
                        long timeInModeSpread = 0;
                        final long currentSpreadRounded = getSpreadFromTimeBySpreadIndex(getIndexFromTimeBySpread(currentSpread));
                        if (modeSpread == currentSpreadRounded && timeInCurrentSpread > 0) {
                            timeInModeSpread = timeBySpread[getIndexFromTimeBySpread(currentSpread)] + timeInCurrentSpread;
                            if (minSpread >= currentSpreadRounded) {
                                minSpread = currentSpreadRounded;
                                timeInMinSpread = timeInModeSpread;
                            }
                            else {
                                timeInMinSpread = timeBySpread[getIndexFromTimeBySpread(minSpread)];                            
                            }
                        }
                        else {
                            minSpread = Long.MAX_VALUE;
                            modeSpread = Long.MAX_VALUE;
                            int modeIndex = -1;
                            
                            final int index = getIndexFromTimeBySpread(currentSpread);
                            timeBySpread[index] += timeInCurrentSpread;
                            for (int i = 0; i < MAX_SPREAD; i++) {
                                long timeInSpread = timeBySpread[i];
                                if (minSpread == Long.MAX_VALUE && timeInSpread > 0) {
                                    minSpread = getSpreadFromTimeBySpreadIndex(i);
                                    timeInMinSpread = timeInSpread;
                                }
                                if (timeBySpread[i] > timeInModeSpread) {
                                    modeIndex = i;
                                    timeInModeSpread = timeInSpread;
                                }
                            }
                            timeBySpread[index] -= timeInCurrentSpread;

                            modeSpread = getSpreadFromTimeBySpreadIndex(modeIndex);
                        }
                        percentTimeInMinSpread_3L = Math.min(timeInMinSpread / actualTotalTimeScaled, 100000);
                        percentTimeInModeSpread_3L = Math.min(timeInModeSpread / actualTotalTimeScaled, 100000);
                    }
                }
            }
        }

        @Override
        public boolean isValueValid(final long value) {
            return value >= 0 && value <= MAX_SPREAD * scale;
        }

        @Override
        public void onEntryAdded(final long nanoOfDay, final long[] entry) {
            entry[TIME_IN_SPREAD_INDEX] = 0;
            entry[SPREAD_X_TIME_INDEX] = 0;
            currentEntry = entry;
        }

        @Override
        public void onEntryRemoved(final long nanoOfDay, final long[] entry) {
            if (canRemove && entry[SPREAD_INDEX] != 0) {
                totalTime -= entry[TIME_IN_SPREAD_INDEX];
                totalSpread -= entry[SPREAD_X_TIME_INDEX];
                if (trackMinAndMode) {
                    final long spread = entry[SPREAD_INDEX];
                    final int index = getIndexFromTimeBySpread(spread);
                    timeBySpread[index] -= entry[TIME_IN_SPREAD_INDEX];
                }
            }
        }

        @Override
        public void onEntryTrimmed(final long nanoOfDay, final long timeTrimmed, final long[] entry) {
            if (canRemove && entry != currentEntry && entry[SPREAD_INDEX] != 0) {
                totalTime -= timeTrimmed;
                final long spread = entry[SPREAD_INDEX];
                final long spreadTimeTrimmed = timeTrimmed * spread;
                entry[TIME_IN_SPREAD_INDEX] -= timeTrimmed;
                entry[SPREAD_X_TIME_INDEX] -= spreadTimeTrimmed;
                totalSpread -= spreadTimeTrimmed;
                if (trackMinAndMode) {
                    final int index = getIndexFromTimeBySpread(spread);
                    timeBySpread[index] -= timeTrimmed;
                }
            }
        }

        @Override
        public void onEntryCompleted(final long nanoOfDay, final long[] entry) {
            if (entry[SPREAD_INDEX] != 0) {
                updateStableEntry(nanoOfDay, entry);
                final long timeInSpread = nanoOfDay - entry[START_TIME_INDEX];
                entry[TIME_IN_SPREAD_INDEX] = timeInSpread;
                entry[SPREAD_X_TIME_INDEX] = entry[TIME_IN_SPREAD_INDEX] * entry[SPREAD_INDEX];
                totalTime += timeInSpread;
                totalSpread += timeInSpread * entry[SPREAD_INDEX];
                if (trackMinAndMode) {
                    final int index = getIndexFromTimeBySpread(entry[SPREAD_INDEX]);
                    timeBySpread[index] += timeInSpread;
                }
            }
        }
        
        private int getIndexFromTimeBySpread(final long spread) {
            return (int)Math.ceil(spread / scale) - 1;
        }
        
        private int getSpreadFromTimeBySpreadIndex(final int index) {
            return (index + 1) * scale;
        }

        @Override
        public void onWindowUpdated(long nanoOfDay, Iterator<long[]> localIterator) {
            updateStableEntry(nanoOfDay, currentEntry);
            collectStats(nanoOfDay);
        }
        
        private void updateStableEntry(final long nanoOfDay, final long [] entry) {
            if (entry != null && stableEntry != entry && nanoOfDay - entry[START_TIME_INDEX] >= STABLE_TIME) {
                if (stableEntry == null || stableEntry[SPREAD_INDEX] != entry[SPREAD_INDEX]) {
                    stableEntry = entry;
                    stableTimeLastUpdated = stableEntry[START_TIME_INDEX];
                }
            }
        }
        
        private boolean areSpreadSame(final long spread1, final long spread2) {
            return (long)Math.ceil(spread1 / scale) == (long)Math.ceil(spread2 / scale);
        }
    }



    
    static private class TimeInBidRingBuffer {
        static private final int DEFAULT_MAX_ENTRIES = 512;
        
        private final int maxEntries;
        private final int mask;
        private final long[] ringBuffer;
        private long timeWithNoBid;
        private int minBidLevel;
        private int maxBidLevel;
        private int modeBidLevel = 0;
        private long timeInModeBid;
        private long percentTimeInModeBid_3L = Long.MAX_VALUE;
        private int startIndex;
        private long totalTime;        
        
        public TimeInBidRingBuffer() {
            this(DEFAULT_MAX_ENTRIES);
        }
        public TimeInBidRingBuffer(final int maxEntries) {
            this.maxEntries = maxEntries;
            this.mask = this.maxEntries - 1;
            this.ringBuffer = new long[this.maxEntries];
        }
        
        public void reset() {
            for (int i = 0; i < maxEntries; i++) {
                ringBuffer[i] = 0;
            }
            timeWithNoBid = 0;
            minBidLevel = 0;
            maxBidLevel = 0;
            modeBidLevel = 0;
            percentTimeInModeBid_3L = Long.MAX_VALUE;
            startIndex = 0;
            totalTime = 0;
        }
        
        public int modeBidLevel() {
            return modeBidLevel;
        }
        
        public long percentTimeInModeBid_3L() {
            return percentTimeInModeBid_3L;
        }
        
        long getTotalTime(final long timeInCurrentBidLevel) {
            return this.totalTime + timeInCurrentBidLevel;
        }
        
        long getTimeInBid(final int bidLevel) {
            if (bidLevel >= minBidLevel && bidLevel <= maxBidLevel) {
                final int index = (startIndex + (bidLevel - minBidLevel)) & mask;
                return ringBuffer[index];
            }
            else if (bidLevel == 0) {
                return timeWithNoBid;
            }
            return 0;
        }
        
        private void collectStats(final int currentBidLevel, final long timeInCurrentBidLevel) {
            if (maxBidLevel > 0) {
                final long scaledDownTotalTime = (totalTime + timeInCurrentBidLevel) / 100000;
                if (modeBidLevel == currentBidLevel && timeInCurrentBidLevel > 0) {
                    // Optimization #1 - if previous mode bid is the current bid, then the previous mode bid has to be the current mode bid
                    if (currentBidLevel != 0) {
                        int index = (startIndex + (currentBidLevel - minBidLevel)) & mask;
                        timeInModeBid = ringBuffer[index] + timeInCurrentBidLevel;
                    }
                    else {
                        timeInModeBid = timeWithNoBid;
                    }
                    percentTimeInModeBid_3L = scaledDownTotalTime == 0 ? Long.MAX_VALUE : Math.min(timeInModeBid / scaledDownTotalTime, 100000);
                }
                else {
                    if (modeBidLevel != currentBidLevel && scaledDownTotalTime > 0) {
                        // Optimization #2 - if previous mode bid is not the current bid, but if time in previous mode bid is over 50% then it has to be the current mode bid
                        if (modeBidLevel != 0) {
                            int index = (startIndex + (modeBidLevel - minBidLevel)) & mask;
                            timeInModeBid = ringBuffer[index];
                        }
                        else {
                            timeInModeBid = timeWithNoBid;                            
                        }
                        percentTimeInModeBid_3L = Math.min(timeInModeBid / scaledDownTotalTime, 100000);
                        if (percentTimeInModeBid_3L > 50_000) {
                            return;
                        }                        
                    }
                    final int numEntries = maxBidLevel - minBidLevel + 1;
                    int minIndex = -1;
                    int maxIndex = -1;
                    long maxTimeInBid = 0;
                    int indexForMaxTime = -1;
                    int currentIndex = -1;
                    if (currentBidLevel != 0) {
                        currentIndex = (startIndex + (currentBidLevel - minBidLevel)) & mask;
                        ringBuffer[currentIndex] += timeInCurrentBidLevel;
                    }
                    else {
                        timeWithNoBid += timeInCurrentBidLevel;
                    }
                    for (int i = startIndex; i < startIndex + numEntries; i++) {
                        final int index = i & mask;
                        final long time = ringBuffer[index];
                        if (time > 0) {
                            if (minIndex == -1) {
                                minIndex = i;
                            }
                            maxIndex = i;
                            if (time > maxTimeInBid) {
                                maxTimeInBid = time;
                                indexForMaxTime = i;
                            }
                        }
                    }
                    if (maxIndex == -1) {
                        minBidLevel = currentBidLevel;
                        maxBidLevel = currentBidLevel;
                        startIndex = 0;
                        timeInModeBid = timeWithNoBid;
                        percentTimeInModeBid_3L = scaledDownTotalTime == 0 ? Long.MAX_VALUE : Math.min(timeInModeBid / scaledDownTotalTime, 100000);
                    }
                    else {
                        if (maxTimeInBid > timeWithNoBid) {
                            modeBidLevel = (indexForMaxTime - startIndex) + minBidLevel;
                            timeInModeBid = maxTimeInBid;
                        }
                        else {
                            modeBidLevel = 0;
                            timeInModeBid = timeWithNoBid;
                        }
                        percentTimeInModeBid_3L = scaledDownTotalTime == 0 ? Long.MAX_VALUE : Math.min(timeInModeBid / scaledDownTotalTime, 100000);
                        maxBidLevel = (maxIndex - startIndex) + minBidLevel;
                        if (minIndex > startIndex) {
                            minBidLevel += (minIndex - startIndex);
                            startIndex = minIndex & mask;
                        }
                    }
                    if (currentBidLevel != 0) {
                        ringBuffer[currentIndex] -= timeInCurrentBidLevel;
                    }                    
                    else {
                        timeWithNoBid -= timeInCurrentBidLevel;
                    }
                }
            }
        }
        
        public void increaseTimeInBid(final int bidLevel, final long timeInBid) {
            if (bidLevel != 0) {
                adjustRingBufferForBidLevel(bidLevel);
                final int index = (startIndex + (bidLevel - minBidLevel)) & mask;
                ringBuffer[index] += timeInBid;
            }
            else {
                timeWithNoBid += timeInBid;
            }
            totalTime += timeInBid;
        }

        public void decreaseTimeInBid(final int bidLevel, final long timeInBid) {
            if (bidLevel != 0) {
                adjustRingBufferForBidLevel(bidLevel);
                final int index = (startIndex + (bidLevel - minBidLevel)) & mask;
                ringBuffer[index] -= timeInBid;
            }
            else {
                timeWithNoBid -= timeInBid;
            }
            totalTime -= timeInBid;
        }
        
        private void adjustRingBufferForBidLevel(final int bidLevel) {
            if (maxBidLevel == 0) {
                minBidLevel = bidLevel;
                maxBidLevel = bidLevel;
                startIndex = 0;
            }
            else if (bidLevel < minBidLevel) {
                final int newMaxBidLevel = bidLevel + maxEntries - 1;
                if (newMaxBidLevel < maxBidLevel) {
                    clear(newMaxBidLevel + 1, maxBidLevel);
                    maxBidLevel = newMaxBidLevel;
                }
                startIndex = (startIndex + bidLevel - minBidLevel) & mask;
                minBidLevel = bidLevel;
            }
            else if (bidLevel > maxBidLevel) {
                final int newMinBidLevel = bidLevel - maxEntries + 1;
                if (newMinBidLevel > minBidLevel) {
                    clear(minBidLevel, newMinBidLevel - 1);
                    startIndex = (startIndex + newMinBidLevel - minBidLevel) & mask;
                    minBidLevel = newMinBidLevel;
                }
                maxBidLevel = bidLevel;                
            }
        }
        
        private void clear(final int fromBidLevel, final int toBidLevel) {
            final int fromIndex = startIndex + fromBidLevel - minBidLevel;
            final int toIndex = startIndex + toBidLevel - minBidLevel;
            for (int i = fromIndex; i <= toIndex; i++) {
                final int index = i & mask;
                totalTime -= ringBuffer[index];
                ringBuffer[index] = 0;
            }
        }
    }
    
    static public class BidLevelTracker implements RollingWindowTimeFrame.WindowUpdateHandler {
        private final static int START_TIME_IDX = 0;
        private final static int BID_IDX = 1;
        private final static int TIME_IN_ENTRY_IDX = 2;
        
        final private TimeNormalizer timeNormalizer = RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER;
        final private TimeInBidRingBuffer timeInBidRingBuffer = new TimeInBidRingBuffer();
        private long [] currentEntry = null;
        private long [] stableEntry = null;
        private long stableTimeLastUpdated = 0;

        public TimeNormalizer timeNormalizer() {
            return timeNormalizer;
        };
        
        long getStableTimeLastUpdated() {
            return timeNormalizer.denormalizeTime(stableTimeLastUpdated);
        }
        
        long getTotalTime(final long nanoOfDay) {
            if (currentEntry == null) {
                return 0;
            }
            return timeInBidRingBuffer.getTotalTime(nanoOfDay - currentEntry[START_TIME_IDX]);
        }
        
        public long getTimeInBid(final long nanoOfDay, final int bidLevel) {
            if (currentEntry != null && currentEntry[BID_IDX] == bidLevel) {
                return timeInBidRingBuffer.getTimeInBid(bidLevel) + (nanoOfDay - currentEntry[START_TIME_IDX]); 
            }
            return timeInBidRingBuffer.getTimeInBid(bidLevel);
        }
                
        public int modeBidLevel() {
            return timeInBidRingBuffer.modeBidLevel();
        }
        
        public long percentTimeInModeBid_3L() {
            return timeInBidRingBuffer.percentTimeInModeBid_3L();
        }
        
        @Override
        public boolean discreteTime() {
            return false;
        }

        @Override
        public int entrySize() {
            return 3;
        }

        @Override
        public void reset() {
            currentEntry = null;
            stableEntry = null;
            stableTimeLastUpdated = 0;
            timeInBidRingBuffer.reset();
        }
        
        private void collectStats(final long nanoOfDay) {
            if (currentEntry != null) {
                timeInBidRingBuffer.collectStats((int)currentEntry[BID_IDX], nanoOfDay - currentEntry[START_TIME_IDX]);
            }
        }        

        @Override
        public boolean isValueValid(long value) {
            return true;
        }

        @Override
        public void onEntryAdded(long nanoOfDay, long[] entry) {
            entry[TIME_IN_ENTRY_IDX] = 0;
            currentEntry = entry;
            timeInBidRingBuffer.increaseTimeInBid((int)entry[BID_IDX], 0);
        }

        @Override
        public void onEntryRemoved(long nanoOfDay, long[] entry) {
            final int bidLevel = (int)entry[BID_IDX];
            timeInBidRingBuffer.decreaseTimeInBid(bidLevel, entry[TIME_IN_ENTRY_IDX]);
        }

        @Override
        public void onEntryTrimmed(long nanoOfDay, long timeTrimmed, long[] entry) {
            final int bidLevel = (int)entry[BID_IDX];
            entry[TIME_IN_ENTRY_IDX] -= timeTrimmed;            
            timeInBidRingBuffer.decreaseTimeInBid(bidLevel, timeTrimmed);
        }

        @Override
        public void onEntryCompleted(long nanoOfDay, long[] entry) {
            updateStableEntry(nanoOfDay, currentEntry);
            final int bidLevel = (int)entry[BID_IDX];
            final long timeInBid = nanoOfDay - entry[START_TIME_IDX];
            entry[TIME_IN_ENTRY_IDX] = timeInBid;
            timeInBidRingBuffer.increaseTimeInBid(bidLevel, timeInBid);
        }

        @Override
        public void onWindowUpdated(long nanoOfDay, Iterator<long[]> localIterator) {
            updateStableEntry(nanoOfDay, currentEntry);
            collectStats(nanoOfDay);
        }
        
        private void updateStableEntry(final long nanoOfDay, final long [] entry) {
            if (entry != null && stableEntry != entry && nanoOfDay - entry[START_TIME_IDX] >= STABLE_TIME) {
                if (stableEntry == null || stableEntry[BID_IDX] != entry[BID_IDX]) {
                    stableEntry = entry;
                    stableTimeLastUpdated = stableEntry[START_TIME_IDX];
                }
            }
        }

    };

    static public class BuyTradesTracker implements RollingWindowTimeFrame.WindowUpdateHandler {
        static final public int TRADE_TIME_IDX = 0;
        static final public int PRICE_FLAGS_IDX = 1;
        static final public int TRADE_PRICE_IDX = 2;
        static final public int BID_AT_TRADE_IDX = 3;
        static final public int PENDING_STATE_IDX = 4;
        static final public int BREAKEVEN_START_TIME_IDX = 5;
        static final public int PROFIT_START_TIME_IDX = 6;
        static final public int LOSS_START_TIME_IDX = 7;
        
        static final public int PENDING_BREAKEVEN = 0;
        static final public int PENDING_PROFIT = 1;
        static final public int COMPLETED_AT_BREAKEVEN = 2;
        static final public int COMPLETED_AT_PROFIT = 3;
        static final public int COMPLETED_AT_LOSS = 4;
        
        final private TimeNormalizer timeNormalizer = RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER;
        
        public TimeNormalizer timeNormalizer() {
            return timeNormalizer;
        };        
        
        @Override
        public boolean discreteTime() {
            return true;
        }

        @Override
        public int entrySize() {
            return 8;
        }

        @Override
        public void reset() {
            
        }

        @Override
        public boolean isValueValid(long value) {
            return true;
        }

        @Override
        public void onEntryAdded(long nanoOfDay, long[] entry) {
            final long price = entry[PRICE_FLAGS_IDX];
            entry[TRADE_PRICE_IDX] = (price >> Integer.SIZE) & 0xFFFF;
            entry[BID_AT_TRADE_IDX] = price & 0xFFFF;
            entry[PENDING_STATE_IDX] = PENDING_BREAKEVEN;
            entry[BREAKEVEN_START_TIME_IDX] = 0;
            entry[PROFIT_START_TIME_IDX] = 0;
            entry[LOSS_START_TIME_IDX] = 0;
        }

        @Override
        public void onEntryRemoved(long nanoOfDay, long[] entry) {
            
        }

        @Override
        public void onEntryTrimmed(long nanoOfDay, long timeTrimmed, long[] entry) {
            
        }

        @Override
        public void onEntryCompleted(long nanoOfDay, long[] entry) {
            
        }

        @Override
        public void onWindowUpdated(long nanoOfDay, Iterator<long[]> localIterator) {
            
        }
        
    }
    
    static public class BuyTradesGroup {
        private long firstTradeTime;
        private long lastTradeTime;
        private long firstTradeTriggerSeqNum;
        private int price;
        private int mmBid;
        private long totalQuantity;
        private int numBuyTrades;
        private boolean isPunterTrigger;
        private boolean isPendingTrades = false;
        
        public void setFirstTrade(final long tradeTime, int price, int mmBid, long quantity, long triggerSeqNum) {
            this.firstTradeTriggerSeqNum = triggerSeqNum;
            this.firstTradeTime = tradeTime;
            this.lastTradeTime = tradeTime;
            this.price = price;
            this.mmBid = mmBid;
            this.totalQuantity = quantity;
            this.numBuyTrades = 1;
            this.isPunterTrigger = false;
            this.isPendingTrades = true;
        }
        
        public void addTrade(final long tradeTime, long quantity) {
            this.lastTradeTime = tradeTime;
            this.totalQuantity += quantity;
            this.numBuyTrades++;
        }
        
        public void endGroup() {
            this.isPendingTrades = false;
        }
        
        public long firstTradeTime() {
            return firstTradeTime;
        }
        
        public long lastTradeTime() {
            return lastTradeTime;
        }
        
        public int numBuyTrades() {
            return numBuyTrades;
        }
        
        public long price() {
            return price;
        }
        
        public long mmBid() {
            return mmBid;
        }
        
        public long totalQuantity() {
            return totalQuantity;
        } 
    
        public boolean isPendingTrades() {
            return isPendingTrades;
        }

        public boolean isPunterTrigger() {
            return isPunterTrigger;
        }

        public void setAsPunterTrigger() {
            this.isPunterTrigger = true;
        }
    
        public long firstTradeTriggerSeqNum() {
            return this.firstTradeTriggerSeqNum;
        }
    }
    
    
    final private ScoreBoardSecurityInfo security;
    final private ScoreBoardSecurityInfo underlying;
    final private ScoreBoardCalculator scoreBoardCalculator;
    final private WarrantBehaviourStats stats;
    
    final private RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler bidUpSmoothingTimeTracker;
    final private RollingWindowTimeFrame bidUpSmoothingTimeTimeFrame;

    final private RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler bidUpSmoothingTimePerTickTracker;
    final private RollingWindowTimeFrame bidUpSmoothingTimePerTickTimeFrame;

    final private RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler breakEvenTracker;
    final private RollingWindowTimeFrame breakEvenTimeFrame;

    final private RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler profitTracker;
    final private RollingWindowTimeFrame profitTimeFrame;

    final private BuyTradesTracker buyTradesTracker;
    final private RollingWindowTimeFrame buyTradesTimeFrame;
    
    final private SpreadTracker spreadChangeTracker;
    final private SpreadTracker normalizedSpreadChangeTracker;
    final private SpreadTracker spreadChangeInPositionTracker;
    final private SpreadTracker normalizedSpreadChangeInPositionTracker;
    final private RollingWindowTimeFrame spreadChangeTimeFrame;
    final private RollingWindowTimeFrame normalizedSpreadChangeTimeFrame;
    final private RollingWindowTimeFrame spreadChangeInPositionTimeFrame;
    final private RollingWindowTimeFrame normalizedSpreadChangeInPositionTimeFrame;
    
    final private BuyTradesGroup lastBuyTradesGroup;
    
    private long lastBuyTradeTime = 0;    
    private long lastSellTradePrice = 0;
    private long lastSellTradeTime = 0;
    private int numDropVols;
    private int numAutoDropVolsOnBuy;
    private int numAutoDropVolsOnSell;
    private int numManualDropVolsOnBuy;
    private int numManualDropVolsOnSell;
    private int numRaiseVols;
    private int numAutoRaiseVolsOnSell;
    private int numManualRaiseVolsOnSell;
    
    private int lastTriggerSeqNum = 0;
    private int mmAskPrice = 0;
    private int mmAskLevel = 0;
    private int mmBidPrice = 0;
    private int mmBidLevel = 0;
    private int mmSpread = Integer.MAX_VALUE;
    private int markedMmAskLevelForSmoothing = 0;
    private int undSpread = 1;
    private double tickSensitivity;

    private long beginSmoothingTime;
    private int smoothingSpread;
    private int smoothingBidLevel;
    
    private long lastMarkedTime;
    private long timeMmBidBelowSmoothingLevel;
    
    private long lastTimeSpotChanged;
    private long lastTimeOurTrigger;
    private long punterPositionOutstanding;
    
    private long lastLoggingTime;
    
    public WarrantBehaviourAnalyser(final ScoreBoardSecurityInfo security, final ScoreBoardSecurityInfo underlying) {
        this.security = security;
        this.underlying = underlying;
        this.scoreBoardCalculator = this.security.scoreBoardCalculator();
        security.registerMdUpdateHandler(this);
        security.registerMarketOrderHandler(this);
        security.registerVolatilityChangedHandler(this);
        security.registerGreeksUpdateHandler(this);
        security.registerOurTriggerHandler(this);
        underlying.registerUnderlyingOrderBookUpdateHandler(this);
        
        this.stats = this.security.scoreBoard().warrantBehaviourStats();
        this.bidUpSmoothingTimeTracker = new RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler(RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER, 1);
        this.bidUpSmoothingTimePerTickTracker = new RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler(RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER, 1);

        this.bidUpSmoothingTimeTimeFrame = new RollingWindowTimeFrame(bidUpSmoothingTimeTracker, 3600_000_000_000L, 32, 32);
        this.bidUpSmoothingTimePerTickTimeFrame = new RollingWindowTimeFrame(bidUpSmoothingTimePerTickTracker, 3600_000_000_000L, 32, 32);

        this.buyTradesTracker = new BuyTradesTracker();
        this.buyTradesTimeFrame = new RollingWindowTimeFrame(buyTradesTracker, 1200_000_000_000L, 32, 32);
        
        this.breakEvenTracker = new RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler(RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER, 1);
        this.breakEvenTimeFrame = new RollingWindowTimeFrame(breakEvenTracker, 0, 128, 128);
        this.profitTracker = new RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler(RollingWindowTimeFrame.MARKET_HOURS_TIME_NORMALIZER, 1);
        this.profitTimeFrame = new RollingWindowTimeFrame(profitTracker, 0, 128, 128);
        
        this.spreadChangeTracker = new SpreadTracker(1, SPREAD_WINDOW_SIZE != 0, true);
        this.normalizedSpreadChangeTracker = new SpreadTracker(1, SPREAD_WINDOW_SIZE != 0, false);
        this.spreadChangeInPositionTracker = new SpreadTracker(1, SPREAD_WINDOW_SIZE != 0, false);
        this.normalizedSpreadChangeInPositionTracker = new SpreadTracker(1, SPREAD_WINDOW_SIZE != 0, false);
        this.spreadChangeTimeFrame = new RollingWindowTimeFrame(spreadChangeTracker, SPREAD_WINDOW_TIME, SPREAD_WINDOW_SIZE, SPREAD_WINDOW_SIZE);
        this.normalizedSpreadChangeTimeFrame = new RollingWindowTimeFrame(normalizedSpreadChangeTracker, SPREAD_WINDOW_TIME, SPREAD_WINDOW_SIZE, SPREAD_WINDOW_SIZE);
        this.spreadChangeInPositionTimeFrame = new RollingWindowTimeFrame(spreadChangeInPositionTracker, SPREAD_WINDOW_TIME, SPREAD_WINDOW_SIZE, SPREAD_WINDOW_SIZE);
        this.normalizedSpreadChangeInPositionTimeFrame = new RollingWindowTimeFrame(normalizedSpreadChangeInPositionTracker, SPREAD_WINDOW_TIME, SPREAD_WINDOW_SIZE, SPREAD_WINDOW_SIZE);
        
        this.lastBuyTradesGroup = new BuyTradesGroup();
    }

    @Override
    public void onOrderBookUpdated(final Security srcSecurity, final long nanoOfDay, MarketOrderBook orderBook) throws Exception {
        processTick(nanoOfDay);
        if (nanoOfDay < MarketHoursUtils.MARKET_MORNING_OPEN_TIME || nanoOfDay > MarketHoursUtils.MARKET_CLOSE_TIME) {
            return;
        }
        if (this.lastBuyTradesGroup.isPendingTrades() && (nanoOfDay - this.lastBuyTradesGroup.lastTradeTime() > ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS || mmBidPrice != this.lastBuyTradesGroup.mmBid())) {
            completeLastBuyTradesGroup(nanoOfDay);
        }
        boolean hasUpdated = false;
        hasUpdated = detectSpread(nanoOfDay) || hasUpdated;
        hasUpdated = detectSmoothing(nanoOfDay) || hasUpdated;
        hasUpdated = detectTradeProfitability(nanoOfDay) || hasUpdated;
        lastMarkedTime = nanoOfDay;
        if (hasUpdated) {
            this.logScoreBoard(nanoOfDay);
            scoreBoardCalculator.updateScore(nanoOfDay, lastTriggerSeqNum);
        }
    }
    
    @Override
    public void onTradeReceived(final Security srcSecurity, long timestamp, MarketTrade trade) throws Exception {
    }

    @Override
    public void onBuyTrade(final long nanoOfDay, final MarketOrder marketOrder) {
        this.lastBuyTradeTime = nanoOfDay;
        if (lastBuyTradesGroup.isPendingTrades()) {
            if (nanoOfDay - this.lastBuyTradesGroup.lastTradeTime() > ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS || marketOrder.price != this.lastBuyTradesGroup.price()) {
                completeLastBuyTradesGroup(nanoOfDay);
                lastBuyTradesGroup.setFirstTrade(nanoOfDay, marketOrder.price, marketOrder.mmBid, marketOrder.quantity, marketOrder.triggerSeqNum);
            }
            else {
                lastBuyTradesGroup.addTrade(nanoOfDay, marketOrder.quantity);
            }
        }
        else {
            lastBuyTradesGroup.setFirstTrade(nanoOfDay, marketOrder.price, marketOrder.mmBid, marketOrder.quantity, marketOrder.triggerSeqNum);
        }        
        if (nanoOfDay - this.lastTimeSpotChanged <= 50_000_000L || nanoOfDay - this.lastTimeOurTrigger <= ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS) {
            lastBuyTradesGroup.setAsPunterTrigger();
        }
    }

    @Override
    public void onSellTrade(final long nanoOfDay, final MarketOrder marketOrder, final MarketOrder matchedBuyOrder) {
    	this.lastSellTradePrice = marketOrder.price;
        this.lastSellTradeTime = nanoOfDay;
        if (this.punterPositionOutstanding > 0) {
            this.punterPositionOutstanding -= marketOrder.quantity;
            if (this.punterPositionOutstanding <= 0) {
                this.punterPositionOutstanding = 0;
                spreadChangeInPositionTimeFrame.recordValue(nanoOfDay, 0);
                normalizedSpreadChangeInPositionTimeFrame.recordValue(nanoOfDay, 0);
                LOG.info("No more punter position outstanding: secCode {}, trigger seqNum {}", security.code(), marketOrder.triggerSeqNum);
            }
        }         
    }

    @Override
    public void onVolDropped(final long nanoOfDay) {
        this.numDropVols++;
        if (nanoOfDay - this.lastBuyTradeTime <= 5_000_000_000L) {
            this.numAutoDropVolsOnBuy++;
            stats.setNumAutoDropVolsOnBuy(numAutoDropVolsOnBuy);
        }
        else if (this.lastBuyTradeTime > this.lastSellTradeTime && nanoOfDay - this.lastBuyTradeTime <= 60_000_000_000L) {
            this.numManualDropVolsOnBuy++;
            stats.setNumManualDropVolsOnBuy(numManualDropVolsOnBuy);
        }
        if (nanoOfDay - this.lastSellTradeTime <= 5_000_000_000L) {
            this.numAutoDropVolsOnSell++;
            stats.setNumAutoDropVolsOnSell(numAutoDropVolsOnSell);
        }
        else if (this.lastSellTradeTime > this.lastBuyTradeTime && nanoOfDay - this.lastSellTradeTime <= 60_000_000_000L) {
            this.numManualDropVolsOnSell++;
            stats.setNumManualDropVolsOnSell(numManualDropVolsOnSell);
        }
        stats.setNumDropVols(numDropVols);
        this.logScoreBoard(nanoOfDay);
        scoreBoardCalculator.updateScore(nanoOfDay, lastTriggerSeqNum);
    }
    
    @Override
    public void onVolRaised(final long nanoOfDay) {
        this.numRaiseVols++;
        if (nanoOfDay - this.lastSellTradeTime <= 5_000_000_000L) {
            this.numAutoRaiseVolsOnSell++;
            stats.setNumAutoRaiseVolsOnSell(numAutoRaiseVolsOnSell);
        }
        else if (this.lastSellTradeTime > this.lastBuyTradeTime && nanoOfDay - this.lastSellTradeTime <= 60_000_000_000L) {
            this.numManualRaiseVolsOnSell++;
            stats.setNumManualRaiseVolsOnSell(numManualRaiseVolsOnSell);
        }
        stats.setNumRaiseVols(numRaiseVols);
        this.logScoreBoard(nanoOfDay);
        scoreBoardCalculator.updateScore(nanoOfDay, lastTriggerSeqNum);
    }
    
    
    @Override
    public void onGreeksUpdated(final Greeks greeks) throws Exception {
        //this.delta = greeks.delta();
    }

    @Override
    public void onUnderlyingOrderBookUpdated(final long nanoOfDay, final UnderlyingOrderBook underlyingOrderBook) {
        lastTriggerSeqNum = underlying.orderBook().triggerInfo().triggerSeqNum();
        if (nanoOfDay < MarketHoursUtils.MARKET_MORNING_OPEN_TIME || nanoOfDay > MarketHoursUtils.MARKET_CLOSE_TIME) {
            return;
        }
        if (underlyingOrderBook.spread > 0 && underlyingOrderBook.spread != Integer.MAX_VALUE) {
            undSpread = underlyingOrderBook.spread;
        }
        this.lastTimeSpotChanged = security.putOrCall() == PutOrCall.CALL ? Math.max(underlyingOrderBook.lastTimeMidPriceUp, underlyingOrderBook.lastTimeWeightedAverageUp) : Math.max(underlyingOrderBook.lastTimeMidPriceDown, underlyingOrderBook.lastTimeWeightedAverageDown);
        if (this.lastBuyTradesGroup.isPendingTrades() && (nanoOfDay - this.lastBuyTradesGroup.lastTradeTime() > ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS)) {
            completeLastBuyTradesGroup(nanoOfDay);
        }
        if (nanoOfDay - lastMarkedTime > 60_000_000_000L) {
            boolean hasUpdated = detectSpread(nanoOfDay);
            hasUpdated = detectTradeProfitability(nanoOfDay) || hasUpdated;
            lastMarkedTime = nanoOfDay;
            if (hasUpdated) {
                this.logScoreBoard(nanoOfDay);
                scoreBoardCalculator.updateScore(nanoOfDay, lastTriggerSeqNum);
            }
        }
    }

    private long mmBidSize() {
        return security.marketContext().mmBidSizeThreshold() == 0 ? security.wrtParams().mmBidSize() : security.marketContext().mmBidSizeThreshold(); 
    }
    
    private long mmAskSize() {
        return security.marketContext().mmAskSizeThreshold() == 0 ? security.wrtParams().mmAskSize() : security.marketContext().mmAskSizeThreshold(); 
    }
    
    private void processTick(final long nanoOfDay) {
        lastTriggerSeqNum = security.orderBook().triggerInfo().triggerSeqNum();
        mmBidPrice = 0;
        mmBidLevel = 0;
        mmAskPrice = 0;
        mmAskLevel = 0;
        mmSpread = Integer.MAX_VALUE;
        if (!security.orderBook().bidSide().isEmpty()) {
            Tick tick = security.orderBook().bidSide().bestOrNullIfEmpty();
            if (tick.qty() >= mmBidSize()) {
                mmBidPrice = tick.price();
                mmBidLevel = tick.tickLevel();
            }
            else {
                final Iterator<Tick> iterator = security.orderBook().bidSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= mmBidSize()) {
                        mmBidPrice = tick.price();
                        mmBidLevel = tick.tickLevel();
                        break;
                    }
                }
            }
        }
        if (!security.orderBook().askSide().isEmpty()) {
            Tick tick = security.orderBook().askSide().bestOrNullIfEmpty();
            if (tick.qty() >= mmAskSize()) {
                mmAskPrice = tick.price();
                mmAskLevel = tick.tickLevel();
            }
            else {
                final Iterator<Tick> iterator = security.orderBook().askSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= mmAskSize()) {
                        mmAskPrice = tick.price();
                        mmAskLevel = tick.tickLevel();
                        break;
                    }
                }
            }
        }
        if (mmBidLevel > 0 && mmAskLevel > 0) {
        	mmSpread = mmAskLevel - mmBidLevel;
        }
    }
    
    private void completeLastBuyTradesGroup(final long nanoOfDay) {
        lastBuyTradesGroup.endGroup();
        if (lastBuyTradesGroup.isPunterTrigger()) {
            stats.setLastTimePunterTrigger(lastBuyTradesGroup.firstTradeTime());
            stats.setLastTriggerTotalBuyQuantity(lastBuyTradesGroup.totalQuantity());
            stats.setLastTriggerTotalTrades(lastBuyTradesGroup.numBuyTrades());
            stats.incrementNumPunterBuyTrades(lastBuyTradesGroup.numBuyTrades());
            stats.incrementNumPunterTriggers();
            final long price = ((long)lastBuyTradesGroup.price() << Integer.SIZE) | (long)lastBuyTradesGroup.mmBid();
            this.buyTradesTimeFrame.recordValue(lastBuyTradesGroup.lastTradeTime(), price);
            punterPositionOutstanding += lastBuyTradesGroup.totalQuantity();
            LOG.info("Found punter trigger: secCode {}, buyPrice {}, totalBuyQuantity {}, totalTrades {}, firstTradeTime {}, lastTradeTime {}, firstTradeTriggerSeqNum {}",
                   security.code(), box(lastBuyTradesGroup.price()), box(lastBuyTradesGroup.totalQuantity()), box(lastBuyTradesGroup.numBuyTrades()), box(lastBuyTradesGroup.firstTradeTime()), box(lastBuyTradesGroup.lastTradeTime()), box(lastBuyTradesGroup.firstTradeTriggerSeqNum()));
        }
    }
    
    private boolean detectSpread(final long nanoOfDay) {
        final int tickSensitivity_3L = ((WrtOutputParams)security.wrtParams()).tickSensitivity();
        final double tickSensitivity = tickSensitivity_3L / 1000.0;
        if (tickSensitivity != 0) {
            this.tickSensitivity = tickSensitivity;
        }
        boolean hasUpdated = false;
        if (mmSpread != Integer.MAX_VALUE || nanoOfDay >= MM_START_TIME) {
        	final int spreadToRecord = mmSpread < SpreadTracker.MAX_SPREAD ? mmSpread : SpreadTracker.MAX_SPREAD;
        	spreadChangeTimeFrame.recordValue(nanoOfDay, spreadToRecord);
            if (punterPositionOutstanding > 0) {
                spreadChangeInPositionTimeFrame.recordValue(nanoOfDay, spreadToRecord);
            }
        	if (this.tickSensitivity != 0) {
        		final int normalizedSpreadToRecord = mmSpread != Integer.MAX_VALUE ? Math.min((int)Math.ceil((double)mmSpread / Math.max(1, undSpread * this.tickSensitivity)), SpreadTracker.MAX_SPREAD) : SpreadTracker.MAX_SPREAD;
        		normalizedSpreadChangeTimeFrame.recordValue(nanoOfDay, normalizedSpreadToRecord);
                if (punterPositionOutstanding > 0) {
                    normalizedSpreadChangeInPositionTimeFrame.recordValue(nanoOfDay, normalizedSpreadToRecord);
                }
        	}
        }
        final long modeSpread = spreadChangeTracker.modeSpread() == Long.MAX_VALUE ? 0 : spreadChangeTracker.modeSpread() / spreadChangeTracker.scale();
        final long twaSpread_3L = spreadChangeTracker.twaSpread_3L() == Long.MAX_VALUE ? 0 : spreadChangeTracker.twaSpread_3L() / spreadChangeTracker.scale();
        final long normalizedTwaSpread_3L = normalizedSpreadChangeTracker.twaSpread_3L() == Long.MAX_VALUE ? 0 : normalizedSpreadChangeTracker.twaSpread_3L() / normalizedSpreadChangeTracker.scale();
        final long twaSpreadInPosition_3L = spreadChangeInPositionTracker.twaSpread_3L() == Long.MAX_VALUE ? 0 : spreadChangeInPositionTracker.twaSpread_3L() / spreadChangeInPositionTracker.scale();
        final long normalizedTwaSpreadInPosition_3L = normalizedSpreadChangeInPositionTracker.twaSpread_3L() == Long.MAX_VALUE ? 0 : normalizedSpreadChangeInPositionTracker.twaSpread_3L() / normalizedSpreadChangeInPositionTracker.scale();
        
        if (stats.getModeSpread() != modeSpread) {
        	stats.setModeSpread(modeSpread);
        	hasUpdated = true;
        }
        if (stats.getTwaSpread() != twaSpread_3L) {
            if (Math.abs(stats.getTwaSpread() - twaSpread_3L) > 250) {
                hasUpdated = true;
            }
        	stats.setTwaSpread(twaSpread_3L);
        }
        if (stats.getNormalizedTwaSpread() != normalizedTwaSpread_3L) {
            if (Math.abs(stats.getNormalizedTwaSpread() - normalizedTwaSpread_3L) > 250) {
                hasUpdated = true;
            }
        	stats.setNormalizedTwaSpread(normalizedTwaSpread_3L);
        }
        if (stats.getTwaSpreadInPosition() != twaSpreadInPosition_3L) {
            if (Math.abs(stats.getTwaSpreadInPosition() - twaSpreadInPosition_3L) > 250) {
                hasUpdated = true;
            }
            stats.setTwaSpreadInPosition(twaSpreadInPosition_3L);
        }
        if (stats.getNormalizedTwaSpreadInPosition() != normalizedTwaSpreadInPosition_3L) {
            if (Math.abs(stats.getNormalizedTwaSpreadInPosition() - normalizedTwaSpreadInPosition_3L) > 250) {
                hasUpdated = true;
            }
            stats.setNormalizedTwaSpreadInPosition(normalizedTwaSpreadInPosition_3L);
        }
        return hasUpdated;
    }
    
    private boolean detectSmoothing(final long nanoOfDay) {
        final boolean result = detectSmoothingInternal(nanoOfDay);
        if (beginSmoothingTime != 0 && mmBidLevel < smoothingBidLevel && timeMmBidBelowSmoothingLevel == 0) {
            timeMmBidBelowSmoothingLevel = nanoOfDay;
        }
        else {
            timeMmBidBelowSmoothingLevel = 0;
        }
        if (mmAskLevel != 0) {
            markedMmAskLevelForSmoothing = mmAskLevel;
        }
        return result;
    }
    
    private boolean detectSmoothingInternal(final long nanoOfDay) {
        if (beginSmoothingTime == 0) {
            if (mmSpread != Integer.MAX_VALUE && undSpread == 1 && mmAskLevel > markedMmAskLevelForSmoothing && markedMmAskLevelForSmoothing != 0) {
                smoothingBidLevel = mmBidLevel;
                smoothingSpread = mmSpread;
                beginSmoothingTime = nanoOfDay;
            }
            return false;
        }
        boolean hasUpdated = false;
        if (mmBidLevel > smoothingBidLevel && mmBidLevel + mmSpread == smoothingBidLevel + smoothingSpread) {
            final long smoothingTime = (nanoOfDay - beginSmoothingTime);
            final long smoothingTimePerTick = smoothingTime / (smoothingSpread - mmSpread);
            bidUpSmoothingTimeTimeFrame.recordValue(nanoOfDay, smoothingTime);
            bidUpSmoothingTimePerTickTimeFrame.recordValue(nanoOfDay, smoothingTimePerTick);
            smoothingBidLevel = mmBidLevel;
            smoothingSpread = mmSpread;
            beginSmoothingTime = nanoOfDay;
            stats.setSmoothingTime(smoothingTime);
            stats.setSmoothingTimePerTick(smoothingTimePerTick);
            stats.setLastTimeSmoothing(nanoOfDay);
            stats.setAvgSmoothingTime(bidUpSmoothingTimeTracker.avgValue() != Long.MAX_VALUE ? bidUpSmoothingTimeTracker.avgValue() : 0);
            stats.setAvgSmoothingTimePerTick(bidUpSmoothingTimePerTickTracker.avgValue() != Long.MAX_VALUE ? bidUpSmoothingTimePerTickTracker.avgValue() : 0);
            hasUpdated = true;
        }
        else if (mmBidLevel != 0 && mmBidLevel < smoothingBidLevel) {
            if (timeMmBidBelowSmoothingLevel != 0 && nanoOfDay - timeMmBidBelowSmoothingLevel > ATOMIC_TIME_DELAY) {
                beginSmoothingTime = 0;
            }            
        }
        else if (mmSpread != Integer.MAX_VALUE && mmSpread != smoothingSpread) {
            if (mmSpread != Integer.MAX_VALUE && undSpread == 1 && mmAskLevel > markedMmAskLevelForSmoothing && markedMmAskLevelForSmoothing != 0) {
                smoothingBidLevel = mmBidLevel;
                smoothingSpread = mmSpread;
                beginSmoothingTime = nanoOfDay;
            }
            else if (mmAskLevel < markedMmAskLevelForSmoothing && mmAskLevel != 0) {
                beginSmoothingTime = 0;
            }
        }
        return hasUpdated;
    }

    private boolean detectTradeProfitability(final long nanoOfDay) {
        boolean hasUpdated = false;
        int numTradesToRemove = 0;
        boolean hasNotCompletedTrades = false;
        for (final long[] buyTrade : this.buyTradesTimeFrame) {
        	// cleaner to use a statemachine per trade...
            int state = (int)buyTrade[BuyTradesTracker.PENDING_STATE_IDX];
            if (state == BuyTradesTracker.COMPLETED_AT_BREAKEVEN) {
                if (!hasNotCompletedTrades) {
                    numTradesToRemove++;
                }
                continue;
            }
            if (state == BuyTradesTracker.COMPLETED_AT_PROFIT) {
                if (!hasNotCompletedTrades) {
                    numTradesToRemove++;
                }
                continue;
            }
            if (state == BuyTradesTracker.COMPLETED_AT_LOSS) {
                if (!hasNotCompletedTrades) {
                    numTradesToRemove++;
                }
                continue;
            }
            hasNotCompletedTrades = true;
            final long markedLossTime = buyTrade[BuyTradesTracker.LOSS_START_TIME_IDX];
            if (markedLossTime > 0) {
                if ((nanoOfDay - markedLossTime) >= 5_000_000L) {
                    if (state == BuyTradesTracker.PENDING_BREAKEVEN) {
                        buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.COMPLETED_AT_LOSS;
                        stats.incrementNumLosses();
                        stats.incrementNumConsecutiveLosses();
                        stats.setNumConsecutiveBreakEvensOrProfits(0);
                        hasUpdated = true;
                        continue;
                    }
                    else if (state == BuyTradesTracker.PENDING_PROFIT) {
                        buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.COMPLETED_AT_BREAKEVEN;
                        stats.incrementNumBreakEvens();
                        stats.decrementNumPendingProfits();
                        hasUpdated = true;
                        continue;
                    }
                }
            }
            final long tradeTime = buyTradesTracker.timeNormalizer.denormalizeTime(buyTrade[BuyTradesTracker.TRADE_TIME_IDX]);
            final long markedBreakevenTime = buyTrade[BuyTradesTracker.BREAKEVEN_START_TIME_IDX];
            if (markedBreakevenTime > 0) {
                if ((nanoOfDay - markedBreakevenTime) >= 5_000_000_000L) {
                    state = BuyTradesTracker.PENDING_PROFIT;
                    buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.PENDING_PROFIT;                    
                    buyTrade[BuyTradesTracker.BREAKEVEN_START_TIME_IDX] = 0;
                    this.breakEvenTimeFrame.recordValue(nanoOfDay, MarketHoursUtils.calcNanoOfDayTimeDifference(tradeTime, markedBreakevenTime));
                    stats.incrementNumConsecutiveBreakEvensOrProfits();
                    stats.incrementNumPendingProfits();
                    stats.setNumConsecutiveLosses(0);
                    hasUpdated = true;
                }
            }
            final long markedProfitTime = buyTrade[BuyTradesTracker.PROFIT_START_TIME_IDX];
            if (markedProfitTime > 0) {
                if ((nanoOfDay - markedProfitTime) >= 5_000_000_000L) {
                    if (state == BuyTradesTracker.PENDING_PROFIT) {
                    	stats.decrementNumPendingProfits();
                    }
                    buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.COMPLETED_AT_PROFIT;
                    stats.incrementNumProfits();                    
                    this.profitTimeFrame.recordValue(nanoOfDay, MarketHoursUtils.calcNanoOfDayTimeDifference(tradeTime, markedProfitTime));
                    hasUpdated = true;
                    continue;
                }
            }
            final long tradePrice = (int)buyTrade[BuyTradesTracker.TRADE_PRICE_IDX];
            final long mmBidAtTrade = (int)buyTrade[BuyTradesTracker.BID_AT_TRADE_IDX];            
            if (mmBidPrice == 0) {
                
            }
            else if (mmBidPrice < mmBidAtTrade) {
                if (markedLossTime == 0) {
                    buyTrade[BuyTradesTracker.LOSS_START_TIME_IDX] = nanoOfDay;    
                }
                buyTrade[BuyTradesTracker.BREAKEVEN_START_TIME_IDX] = 0;
                buyTrade[BuyTradesTracker.PROFIT_START_TIME_IDX] = 0;
            }
            else if (mmBidPrice == tradePrice) {
                if (state == BuyTradesTracker.PENDING_BREAKEVEN && markedBreakevenTime == 0) {
                    buyTrade[BuyTradesTracker.BREAKEVEN_START_TIME_IDX] = nanoOfDay;
                }
                else if (state == BuyTradesTracker.PENDING_PROFIT && MarketHoursUtils.calcNanoOfDayTimeDifference(tradeTime, nanoOfDay) >= ScoreBoardCalculator.MAX_TIME_TO_PROFIT) {
                    buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.COMPLETED_AT_BREAKEVEN;
                    stats.decrementNumPendingProfits();
                    stats.incrementNumBreakEvens();
                    hasUpdated = true;
                }
                buyTrade[BuyTradesTracker.PROFIT_START_TIME_IDX] = 0;
                buyTrade[BuyTradesTracker.LOSS_START_TIME_IDX] = 0;
            }
            else if (mmBidPrice > tradePrice) {
                if (state == BuyTradesTracker.PENDING_BREAKEVEN && markedBreakevenTime == 0) {
                    buyTrade[BuyTradesTracker.BREAKEVEN_START_TIME_IDX] = nanoOfDay;
                }
                if (markedProfitTime == 0) {
                    buyTrade[BuyTradesTracker.PROFIT_START_TIME_IDX] = nanoOfDay;
                }
                buyTrade[BuyTradesTracker.LOSS_START_TIME_IDX] = 0;
            }
            else {
            	if (state == BuyTradesTracker.PENDING_PROFIT) {
            		if (MarketHoursUtils.calcNanoOfDayTimeDifference(tradeTime, nanoOfDay) >= ScoreBoardCalculator.MAX_TIME_TO_PROFIT ||
            				tradeTime < this.lastSellTradeTime && tradePrice >= this.lastSellTradePrice) {
	                    buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.COMPLETED_AT_BREAKEVEN;
	                    stats.decrementNumPendingProfits();
	                    stats.incrementNumBreakEvens();
	                    hasUpdated = true;
            		}
            	}
            	else if (state == BuyTradesTracker.PENDING_BREAKEVEN) {
            		if (MarketHoursUtils.calcNanoOfDayTimeDifference(tradeTime, nanoOfDay) >= ScoreBoardCalculator.MAX_TIME_TO_BREAK_EVEN ||
            				tradeTime < this.lastSellTradeTime && tradePrice >= this.lastSellTradePrice) {
	                    buyTrade[BuyTradesTracker.PENDING_STATE_IDX] = BuyTradesTracker.COMPLETED_AT_LOSS;
	                    stats.incrementNumLosses();
	                    hasUpdated = true;
            		}
            	}
                buyTrade[BuyTradesTracker.BREAKEVEN_START_TIME_IDX] = 0;
                buyTrade[BuyTradesTracker.PROFIT_START_TIME_IDX] = 0;
                buyTrade[BuyTradesTracker.LOSS_START_TIME_IDX] = 0;
            }
        }
        if (numTradesToRemove != 0) {
            buyTradesTimeFrame.removeHeadValues(nanoOfDay, numTradesToRemove);
        }
        final long lastBreakEvenTime = this.breakEvenTracker.lastValue() == Long.MAX_VALUE ? 0 : this.breakEvenTracker.lastValue();
        final long avgBreakEvenTime = this.breakEvenTracker.avgValue() == Long.MAX_VALUE ? 0 : this.breakEvenTracker.avgValue();
        final long minBreakEvenTime = this.breakEvenTracker.minValue() == Long.MAX_VALUE ? 0 : this.breakEvenTracker.minValue();
        final long lastTimeBreakEven = this.breakEvenTimeFrame.timeOfLastRecordedValue();
        final long lastProfitTime = this.profitTracker.lastValue() == Long.MAX_VALUE ? 0 : this.profitTracker.lastValue();
        final long avgProfitTime = this.profitTracker.avgValue() == Long.MAX_VALUE ? 0 : this.profitTracker.avgValue();
        final long minProfitTime = this.profitTracker.minValue() == Long.MAX_VALUE ? 0 : this.profitTracker.minValue();
        final long lastTimeProfit = this.profitTimeFrame.timeOfLastRecordedValue();
       
        if (stats.getLastBreakEvenTime() != lastBreakEvenTime) {
            stats.setLastBreakEvenTime(lastBreakEvenTime);
            hasUpdated = true;
        }       
        if (stats.getAvgBreakEvenTime() != avgBreakEvenTime) {
            stats.setAvgBreakEvenTime(avgBreakEvenTime);
            hasUpdated = true;
        }
        if (stats.getMinBreakEvenTime() != minBreakEvenTime) {
            stats.setMinBreakEvenTime(minBreakEvenTime);
            hasUpdated = true;
        }
        if (stats.getLastTimeBreakEven() != lastTimeBreakEven) {
            stats.setLastTimeBreakEven(lastTimeBreakEven);
            hasUpdated = true;
        }
        if (stats.getLastProfitTime() != lastProfitTime) {
            stats.setLastProfitTime(lastProfitTime);
            hasUpdated = true;
        }
        if (stats.getAvgProfitTime() != avgProfitTime) {
            stats.setAvgProfitTime(avgProfitTime);
            hasUpdated = true;
        }
        if (stats.getMinProfitTime() != minProfitTime) {
            stats.setMinProfitTime(minProfitTime);
            hasUpdated = true;
        }
        if (stats.getLastTimeProfits() != lastTimeProfit) {
            stats.setLastTimeProfits(lastTimeProfit);
            hasUpdated = true;
        }
        return hasUpdated;
    }
    
    private void logScoreBoard(final long nanoOfDay) {
        if (nanoOfDay - lastLoggingTime > 60_000_000_000L) {
            LOG.info("Warrant behavior analysis 1: secCode {}, numPunterBuyTrades {}, numPunterTriggers {}, numBreakEvens {}, numPendingProfits {}, numProfits {}, numLosses {}, "
            		+ "minBreakEvenTime {}, avgBreakEvenTime {}, minProfitTime {}, avgProfitTime {}, "
            		+ "lastTimeBreakEven {}, lastTimeProfit {}, "
            		+ "getLastTriggerTotalTrades {}, getLastTriggerTotalBuyQuantity {}, "
            		+ "trigger seqNum {}",
                    stats.getSecurity().code(), box(stats.getNumPunterBuyTrades()), box(stats.getNumPunterTriggers()), box(stats.getNumBreakEvens()), box(stats.getNumPendingProfits()), box(stats.getNumProfits()), box(stats.getNumLosses()),
                    box(stats.getMinBreakEvenTime()), box(stats.getAvgBreakEvenTime()), box(stats.getMinProfitTime()), box(stats.getAvgProfitTime()), 
                    box(stats.getLastTimeBreakEven()), box(stats.getLastTimeProfits()),
                    box(stats.getLastTriggerTotalTrades()), box(stats.getLastTriggerTotalBuyQuantity()),
                    box(lastTriggerSeqNum));
            LOG.info("Warrant behavior analysis 2: secCode {}, numDropVols {}, numAutoDropVolsOnBuy {}, numAutoDropVolsOnSell {}, numManualDropVolsOnBuy {}, numManualDropVolsOnSell {}, numRaiseVols {}, numAutoRaiseVolsOnSell {}, numManualRaiseVolsOnSell {}, trigger seqNum {}",
                    stats.getSecurity().code(), box(stats.getNumDropVols()), box(stats.getNumAutoDropVolsOnBuy()), box(stats.getNumAutoDropVolsOnSell()), box(stats.getNumManualDropVolsOnBuy()), box(stats.getNumManualDropVolsOnSell()),
                    box(stats.getNumRaiseVols()), box(stats.getNumAutoRaiseVolsOnSell()), box(stats.getNumManualRaiseVolsOnSell()), box(lastTriggerSeqNum));
            LOG.info("Warrant behavior analysis 3: secCode {}, smoothingTime1hr {}, smoothingTimePerTick1hr {}, smoothingTime {}, smoothingTimePerTick {}, lastTimeSmoothing {}, trigger seqNum {}",
                    stats.getSecurity().code(), box(stats.getAvgSmoothingTime()), box(stats.getAvgSmoothingTimePerTick()), box(stats.getSmoothingTime()), box(stats.getSmoothingTimePerTick()), box(stats.getLastTimeSmoothing()), box(lastTriggerSeqNum));
            lastLoggingTime = nanoOfDay;
        }
    }

    @Override
    public void onOurTriggerBuyReceived(long timestamp, int price, OurTriggerExplain ourTriggerExplain, boolean isAdditionalTrigger, long triggerSeqNum) {
        this.lastTimeOurTrigger = timestamp;
        if (this.lastBuyTradesGroup.isPendingTrades() && timestamp - this.lastBuyTradesGroup.lastTradeTime() <= ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS) {
            this.lastBuyTradesGroup.setAsPunterTrigger();
        }
    }

    @Override
    public void onOurTriggerSellReceived(long timestamp, int price, OurTriggerExplain ourTriggerExplain, long triggerSeqNum) {
    }

}
