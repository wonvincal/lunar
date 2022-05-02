package com.lunar.core;

import static org.junit.Assert.assertEquals;

import java.time.LocalTime;

import org.junit.Test;

public class RollingWindowTimeFrameTest {
    @Test    
    public void testSimpleStatsDiscreteTimeFrame() {
        final RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler stats = new RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler(1000);
        final RollingWindowTimeFrame timeFrame = new RollingWindowTimeFrame(stats, LocalTime.of(1, 0).toNanoOfDay(), 4, 4);
        timeFrame.recordValue(LocalTime.of(9, 30).toNanoOfDay(), 1234);
        assertEquals(1234L, stats.minValue());
        assertEquals(1234L, stats.maxValue());
        assertEquals(1234000L, stats.avgValue());
        assertEquals(1, stats.numValues());

        timeFrame.recordValue(LocalTime.of(9, 40).toNanoOfDay(), 2345);
        assertEquals(1234L, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(1789500L, stats.avgValue());
        assertEquals(2, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(9, 50).toNanoOfDay(), 1500);
        assertEquals(1234L, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(1693000L, stats.avgValue());
        assertEquals(3, stats.numValues());
        
        // first record expired
        timeFrame.recordValue(LocalTime.of(10, 30, 1).toNanoOfDay(), 2000);
        assertEquals(1500, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(1948333L, stats.avgValue());
        assertEquals(3, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(10, 35).toNanoOfDay(), 2300);
        assertEquals(1500, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(2036250L, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        // all other records expired
        timeFrame.recordValue(LocalTime.of(11, 35, 1).toNanoOfDay(), 1750);
        assertEquals(1750, stats.minValue());
        assertEquals(1750, stats.maxValue());
        assertEquals(1750000L, stats.avgValue());
        assertEquals(1, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 45).toNanoOfDay(), 3750);
        assertEquals(1750, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2750000L, stats.avgValue());
        assertEquals(2, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 50).toNanoOfDay(), 3000);
        assertEquals(1750, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2833333L, stats.avgValue());
        assertEquals(3, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 51).toNanoOfDay(), 2000);
        assertEquals(1750, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2625000, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 52).toNanoOfDay(), 2200);
        assertEquals(2000, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2737500, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 53).toNanoOfDay(), 2200);
        assertEquals(2000, stats.minValue());
        assertEquals(3000, stats.maxValue());
        assertEquals(2350000, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(12, 53).toNanoOfDay(), 2100);
        assertEquals(2100, stats.minValue());
        assertEquals(2200, stats.maxValue());
        assertEquals(2150000, stats.avgValue());
        assertEquals(2, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(13, 53, 1).toNanoOfDay(), 2200);
        assertEquals(2200, stats.minValue());
        assertEquals(2200, stats.maxValue());
        assertEquals(2200000, stats.avgValue());
        assertEquals(1, stats.numValues());
    }
    
    @Test    
    public void testSimpleStatsDiscreteTimeFrameDiffWinSize() {
        final RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler stats = new RollingWindowTimeFrame.SimpleStatsDiscreteTimeWindowUpdateHandler(1000);
        final RollingWindowTimeFrame timeFrame = new RollingWindowTimeFrame(stats, LocalTime.of(1, 0).toNanoOfDay(), 4, 8);
        timeFrame.recordValue(LocalTime.of(9, 30).toNanoOfDay(), 1234);
        assertEquals(1234L, stats.minValue());
        assertEquals(1234L, stats.maxValue());
        assertEquals(1234000L, stats.avgValue());
        assertEquals(1, stats.numValues());

        timeFrame.recordValue(LocalTime.of(9, 40).toNanoOfDay(), 2345);
        assertEquals(1234L, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(1789500L, stats.avgValue());
        assertEquals(2, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(9, 50).toNanoOfDay(), 1500);
        assertEquals(1234L, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(1693000L, stats.avgValue());
        assertEquals(3, stats.numValues());
        
        // first record expired
        timeFrame.recordValue(LocalTime.of(10, 30, 1).toNanoOfDay(), 2000);
        assertEquals(1500, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(1948333L, stats.avgValue());
        assertEquals(3, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(10, 35).toNanoOfDay(), 2300);
        assertEquals(1500, stats.minValue());
        assertEquals(2345, stats.maxValue());
        assertEquals(2036250L, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        // all other records expired
        timeFrame.recordValue(LocalTime.of(11, 35, 1).toNanoOfDay(), 1750);
        assertEquals(1750, stats.minValue());
        assertEquals(1750, stats.maxValue());
        assertEquals(1750000L, stats.avgValue());
        assertEquals(1, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 45).toNanoOfDay(), 3750);
        assertEquals(1750, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2750000L, stats.avgValue());
        assertEquals(2, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 50).toNanoOfDay(), 3000);
        assertEquals(1750, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2833333L, stats.avgValue());
        assertEquals(3, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 51).toNanoOfDay(), 2000);
        assertEquals(1750, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2625000, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 52).toNanoOfDay(), 2200);
        assertEquals(2000, stats.minValue());
        assertEquals(3750, stats.maxValue());
        assertEquals(2737500, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(11, 53).toNanoOfDay(), 2200);
        assertEquals(2000, stats.minValue());
        assertEquals(3000, stats.maxValue());
        assertEquals(2350000, stats.avgValue());
        assertEquals(4, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(12, 53).toNanoOfDay(), 2100);
        assertEquals(2100, stats.minValue());
        assertEquals(2200, stats.maxValue());
        assertEquals(2150000, stats.avgValue());
        assertEquals(2, stats.numValues());
        
        timeFrame.recordValue(LocalTime.of(13, 53, 1).toNanoOfDay(), 2200);
        assertEquals(2200, stats.minValue());
        assertEquals(2200, stats.maxValue());
        assertEquals(2200000, stats.avgValue());
        assertEquals(1, stats.numValues());
    }

}
