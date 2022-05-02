package com.lunar.core;

import java.util.Iterator;

import com.lunar.strategy.MarketHoursUtils;

public class RollingWindowTimeFrame implements Iterable<long[]> {
    final private static int START_TIME_INDEX = 0;
    final private static int VALUE_INDEX = 1;
    
    public interface TimeNormalizer {
        long normalizeTime(final long nanoOfDay);
        long denormalizeTime(final long nanoOfDay);
    }
    
    public static TimeNormalizer DEFAULT_TIME_NORMALIZER = new TimeNormalizer() {
        @Override
        public long normalizeTime(long nanoOfDay) {
            return nanoOfDay;
        }

        @Override
        public long denormalizeTime(long nanoOfDay) {
            return nanoOfDay;
        }
    };
    
    public static TimeNormalizer MARKET_HOURS_TIME_NORMALIZER = new TimeNormalizer() {
        @Override
        public long normalizeTime(long nanoOfDay) {
            return MarketHoursUtils.adjustNanoOfDay(nanoOfDay);
        }

        @Override
        public long denormalizeTime(long nanoOfDay) {
            return MarketHoursUtils.restoreNanoOfDay(nanoOfDay);
        }
    };

    public interface WindowUpdateHandler {
        boolean discreteTime();
        int entrySize();
        void reset();
        boolean isValueValid(final long value);
        void onEntryAdded(final long nanoOfDay, final long[] entry);
        void onEntryRemoved(final long nanoOfDay, final long[] entry);
        void onEntryTrimmed(final long nanoOfDay, long timeTrimmed, final long[] entry);
        void onEntryCompleted(final long nanoOfDay, final long[] entry);
        void onWindowUpdated(final long nanoOfDay, final Iterator<long[]> localIterator);
        default TimeNormalizer timeNormalizer() { return DEFAULT_TIME_NORMALIZER; };
    }
    
    public static WindowUpdateHandler DEFAULT_DISCRETE_TIME_WINDOW_UPDATE_HANDLER = new WindowUpdateHandler() {
        @Override
        public boolean discreteTime() {
            return true;
        }

        @Override
        public int entrySize() {
            return 2;
        }

        @Override
        public void reset() {
            
        }
        
        @Override
        public boolean isValueValid(final long value) {
            return true;
        }

        @Override
        public void onEntryAdded(final long nanoOfDay, long[] entry) {
        }

        @Override
        public void onEntryRemoved(final long nanoOfDay, long[] entry) {
        }

        @Override
        public void onEntryTrimmed(long nanoOfDay, long timeTrimmed, long[] entry) {
        }

        @Override
        public void onEntryCompleted(long nanoOfDay, long[] entry) {
        }
        
        @Override
        public void onWindowUpdated(final long nanoOfDay, final Iterator<long[]> localIterator) {
        }
    };

    public static WindowUpdateHandler DEFAULT_CONTINUOUS_TIME_WINDOW_UPDATE_HANDLER = new WindowUpdateHandler() {
        final private static int TIME_IN_ENTRY_INDEX = 2;

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
        }
        
        @Override
        public boolean isValueValid(final long value) {
            return true;
        }

        @Override
        public void onEntryAdded(final long nanoOfDay, long[] entry) {
            entry[TIME_IN_ENTRY_INDEX] = 0;
        }

        @Override
        public void onEntryRemoved(final long nanoOfDay, long[] entry) {
        }

        @Override
        public void onEntryTrimmed(long nanoOfDay, long timeTrimmed, long[] entry) {
            entry[TIME_IN_ENTRY_INDEX] -= timeTrimmed;
        }

        @Override
        public void onEntryCompleted(long nanoOfDay, long[] entry) {
            entry[TIME_IN_ENTRY_INDEX] = nanoOfDay - entry[START_TIME_INDEX];
        }
        
        @Override
        public void onWindowUpdated(final long nanoOfDay, final Iterator<long[]> localIterator) {
        }
    };

    public static class SimpleAdditionDiscreteTimeWindowUpdateHandler implements WindowUpdateHandler {
        private long accumValue = 0;
        
        @Override
        public boolean discreteTime() {
            return true;
        }

        @Override
        public int entrySize() {
            return 2;
        }

        @Override
        public void reset() {
            accumValue = 0;
        }

        @Override
        public boolean isValueValid(long value) {
            return true;
        }

        @Override
        public void onEntryAdded(long nanoOfDay, long[] entry) {
            final long value = entry[VALUE_INDEX];
            accumValue += value;
        }

        @Override
        public void onEntryRemoved(long nanoOfDay, long[] entry) {
            final long value = entry[VALUE_INDEX];
            accumValue -= value;
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
        
        public long accumValue() {
            return accumValue;
        }
    }
    
    static public class SimpleAbsMaxDiscreteTimeWindowUpdateHandler implements WindowUpdateHandler {
        private boolean needRecalc = false;
        private long maxValue = 0;

        public SimpleAbsMaxDiscreteTimeWindowUpdateHandler() {
        }

        @Override
        public boolean discreteTime() {
            return true;
        }

        @Override
        public int entrySize() {
            return 2;
        }

        @Override
        public void reset() {
            needRecalc = false;
            maxValue = 0;
        }
        
        @Override
        public boolean isValueValid(final long value) {
            return true;
        }

        @Override
        public void onEntryAdded(final long nanoOfDay, long[] entry) {
            final long value = entry[VALUE_INDEX];
            if (value > maxValue) {
                maxValue = value;
            }
        }

        @Override
        public void onEntryRemoved(final long nanoOfDay, long[] entry) {
            final long value = entry[VALUE_INDEX];
            if (value == maxValue) {
                needRecalc = true;
            }
        }

        @Override
        public void onEntryTrimmed(long nanoOfDay, long timeTrimmed, long[] entry) {
        }

        @Override
        public void onEntryCompleted(long nanoOfDay, long[] entry) {
        }
        
        @Override
        public void onWindowUpdated(final long nanoOfDay, final Iterator<long[]> localIterator) {
            if (needRecalc) {
                maxValue = 0;
                while (localIterator.hasNext()) {
                    final long[] entry = localIterator.next();
                    final long value = entry[VALUE_INDEX]; 
                    if (value > maxValue) {
                        maxValue = value;
                    }
                }
                needRecalc = false;
            }
        }
        
        public long maxValue() {
            return maxValue;
        }
    };
    
    static public class SimpleStatsDiscreteTimeWindowUpdateHandler implements WindowUpdateHandler {
        final TimeNormalizer timeNormalizer;
        final private int avgScale;
        private boolean needRecalc = false;
        private long lastValue = Long.MAX_VALUE;
        private long maxValue = Long.MAX_VALUE;
        private long minValue = Long.MAX_VALUE;
        private long avgValue = Long.MAX_VALUE;
        private long accumValue = 0;
        private int numValues = 0;        

        public SimpleStatsDiscreteTimeWindowUpdateHandler() {
            this(DEFAULT_TIME_NORMALIZER, 1);
        }

        public SimpleStatsDiscreteTimeWindowUpdateHandler(final TimeNormalizer timeNormalizer) {
            this(timeNormalizer, 1);
        }

        public SimpleStatsDiscreteTimeWindowUpdateHandler(final int avgScale) {
            this(DEFAULT_TIME_NORMALIZER, avgScale);
        }

        public SimpleStatsDiscreteTimeWindowUpdateHandler(final TimeNormalizer timeNormalizer, final int avgScale) {
            this.timeNormalizer = timeNormalizer;
            this.avgScale = avgScale; 
        }
        
        @Override
        public boolean discreteTime() {
            return true;
        }

        @Override
        public int entrySize() {
            return 2;
        }

        @Override
        public void reset() {
            needRecalc = false;
            maxValue = Long.MAX_VALUE;
            minValue = Long.MAX_VALUE;
            avgValue = Long.MAX_VALUE;
            accumValue = 0;
            numValues = 0;
        }
        
        @Override
        public boolean isValueValid(final long value) {
            return true;
        }

        @Override
        public void onEntryAdded(final long nanoOfDay, long[] entry) {
            final long value = entry[VALUE_INDEX];
            lastValue = value;
            accumValue += value;
            numValues++;
            if (value > maxValue || maxValue == Long.MAX_VALUE) {
                maxValue = value;
            }
            if (value < minValue) {
                minValue = value;
            }
        }

        @Override
        public void onEntryRemoved(final long nanoOfDay, long[] entry) {
            final long value = entry[VALUE_INDEX];
            accumValue -= value;
            numValues--;
            if (value == maxValue || value == minValue) {
                needRecalc = true;
            }
        }

        @Override
        public void onEntryTrimmed(long nanoOfDay, long timeTrimmed, long[] entry) {
        }

        @Override
        public void onEntryCompleted(long nanoOfDay, long[] entry) {
        }
        
        @Override
        public void onWindowUpdated(final long nanoOfDay, final Iterator<long[]> localIterator) {
            if (needRecalc) {
                maxValue = Long.MAX_VALUE;
                minValue = Long.MAX_VALUE;
                avgValue = Long.MAX_VALUE;
                accumValue = 0;
                numValues = 0;
                while (localIterator.hasNext()) {
                    final long[] entry = localIterator.next();
                    final long value = entry[VALUE_INDEX]; 
                    if (value < minValue) {
                        minValue = value;
                    }
                    if (value > maxValue || maxValue == Long.MAX_VALUE) {
                        maxValue = value;
                    }
                    accumValue += value;
                    numValues++;
                }
                needRecalc = false;
            }
            avgValue = numValues > 0 ? (accumValue * avgScale) / numValues : Long.MAX_VALUE;            
        }

        public long lastValue() {
            return lastValue;
        }
        
        public long maxValue() {
            return maxValue;
        }
        
        public long minValue() {
            return minValue;
        }
        
        public long avgValue() {
            return avgValue;
        }
        
        public int numValues() {
            return numValues;
        }
    };

    private class WindowIterator implements Iterator<long[]> {
        private int index = 0;
        
        public void reset() {
            index = 0;
        }
        
        @Override
        public long[] next() {
            final int bufferIndex = (startIndex + index++) & windowMask;
            return window[bufferIndex];
        }

        @Override
        public boolean hasNext() {
            return index < count;
        }
    }
    
    private final WindowUpdateHandler entryUpdateHandler;
    private final TimeNormalizer timeNormalizer;
    private final long windowTime;
    private final int maxEntries;
    private final long[][] window;
    private final int windowCapacity;
    private final int windowMask;
    private final WindowIterator localIterator;

    private int startIndex;
    private int endIndex;
    private int count;
    private long lastNanoOfDay;
    private long prevValue;

    public RollingWindowTimeFrame(final WindowUpdateHandler entryUpdateHandler, final long windowTime, final int maxEntries, final int windowCapacity) {
        this.entryUpdateHandler = entryUpdateHandler;
        this.timeNormalizer = entryUpdateHandler.timeNormalizer();
        this.windowTime = windowTime;
        this.maxEntries = maxEntries;
        this.windowCapacity = windowCapacity;
        this.windowMask = this.windowCapacity - 1;
        this.window = new long[this.windowCapacity][entryUpdateHandler.entrySize()];
        this.localIterator = new WindowIterator();
        this.startIndex = 0;
        this.endIndex = 0;
        this.count = 0;
    }
    
    public void clear() {
        this.startIndex = 0;
        this.endIndex = 0;
        this.count = 0;
        this.lastNanoOfDay = 0;
        this.entryUpdateHandler.reset();
    }
    
    public long timeOfLastRecordedValue() {
        return timeNormalizer.denormalizeTime(this.lastNanoOfDay);
    }
    
    public boolean recordValue(final long nanoOfDay, final long value) {
        return recordValueWithNormalizedTime(timeNormalizer.normalizeTime(nanoOfDay), value);
    }
    
    private boolean recordValueWithNormalizedTime(final long nanoOfDay, final long value) {
        if (!this.entryUpdateHandler.isValueValid(value) || nanoOfDay < lastNanoOfDay) {
            return false;
        }
        this.lastNanoOfDay = nanoOfDay;
        if (entryUpdateHandler.discreteTime() || count == 0 || prevValue != value) { 
            if (this.count == this.maxEntries) {
                this.count--;
                this.entryUpdateHandler.onEntryRemoved(nanoOfDay, this.window[this.startIndex]);
                this.startIndex = (this.startIndex + 1) & this.windowMask;
            }
            if (this.count > 0) {
                this.entryUpdateHandler.onEntryCompleted(nanoOfDay, this.window[this.endIndex]);
            }
            this.endIndex = (this.startIndex + this.count) & this.windowMask;
            long[] entry = this.window[this.endIndex];
            entry[START_TIME_INDEX] = nanoOfDay;
            entry[VALUE_INDEX] = value;
            this.entryUpdateHandler.onEntryAdded(nanoOfDay, entry);
            this.count++;
            this.prevValue = value;
        }        
        maintainWindow(nanoOfDay);
        return true;
    }
    
    public boolean recordOrUpdateValue(final long nanoOfDay, final long value) {
        return recordOrUpdateValueWithNormalizedTime(timeNormalizer.normalizeTime(nanoOfDay), value);
    }
    
    private boolean recordOrUpdateValueWithNormalizedTime(final long nanoOfDay, final long value) {
        if (entryUpdateHandler.discreteTime() && prevValue == value && count > 0) {
            long[] entry = this.window[this.endIndex];
            this.entryUpdateHandler.onEntryRemoved(nanoOfDay, entry);
            entry[START_TIME_INDEX] = nanoOfDay;
            this.entryUpdateHandler.onEntryAdded(nanoOfDay, entry);
            maintainWindow(nanoOfDay);
            return true;
        }
        return recordValue(nanoOfDay, value);        
    }

    public boolean updateTimeFrame(final long nanoOfDay) {
        return updateTimeFrameWithNormalizedTime(timeNormalizer.normalizeTime(nanoOfDay));
    }
    
    private boolean updateTimeFrameWithNormalizedTime(final long nanoOfDay) {
        if (nanoOfDay < lastNanoOfDay) {
            return false;
        }
        maintainWindow(nanoOfDay);
        return true;
    }
    
    public long[] popValue(final long nanoOfDay) {
        if (this.count > 0) {
            final long[] entry = window[startIndex];
            this.entryUpdateHandler.onEntryRemoved(nanoOfDay, entry);
            this.count--;
            this.startIndex = (this.startIndex + 1) & this.windowMask;
            maintainWindow(nanoOfDay);
            return entry;
        }
        return null;
    }
    
    public void removeHeadValues(final long nanoOfDay, final int numEntriesToRemove) {
    	final int actualNumEntriesToRemove = Math.min(this.count, numEntriesToRemove);
		for (int i = 0; i < actualNumEntriesToRemove; i++) {
            final long[] entry = window[startIndex];
            this.entryUpdateHandler.onEntryRemoved(nanoOfDay, entry);
            this.count--;
            this.startIndex = (this.startIndex + 1) & this.windowMask;
		}
        maintainWindow(nanoOfDay);
    }

    private void maintainWindow(final long nanoOfDay) {
        if (windowTime != 0) {
            final long minTime = nanoOfDay - windowTime;
            int i;
            int index = startIndex;
            int prevIndex = index;
            long[] prevEntry = null;
            int entriesRemoved = 0;
            for (i = startIndex; i < startIndex + count; i++) {
                index = i & this.windowMask;
                final long[] entry = this.window[index];
                if (entry[START_TIME_INDEX] >= minTime) {
                    break;
                }
                prevIndex = index;
                if (prevEntry != null) {
                    this.entryUpdateHandler.onEntryRemoved(nanoOfDay, prevEntry);
                    entriesRemoved++;
                }
                prevEntry = entry;
            }
            if (prevEntry != null) {
                if (this.entryUpdateHandler.discreteTime()) {
                    this.entryUpdateHandler.onEntryRemoved(nanoOfDay, prevEntry);
                    prevIndex = index;
                    entriesRemoved++;
                }
                else {
                    final long timeTrimmed = minTime - prevEntry[START_TIME_INDEX];
                    prevEntry[START_TIME_INDEX] = minTime;
                    if (prevIndex != ((startIndex + count - 1) & this.windowMask)) {
                        this.entryUpdateHandler.onEntryTrimmed(nanoOfDay, timeTrimmed, prevEntry);
                    }
                }
            }
            count = count - entriesRemoved;
            startIndex = prevIndex;
        }
        this.entryUpdateHandler.onWindowUpdated(nanoOfDay, localIterator());
    }
    
    public int count() {
        return count;
    }
    
    public boolean isFull() {
        return count == maxEntries;
    }

    public Iterator<long[]> localIterator() {
        localIterator.reset();
        return localIterator;
    }

    @Override
    public Iterator<long[]> iterator() {
        return localIterator();
    }
}
