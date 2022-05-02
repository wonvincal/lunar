package com.lunar.strategy.speedarbhybrid;

import static org.junit.Assert.assertEquals;

import java.time.LocalTime;

import org.junit.Test;

import com.lunar.strategy.MarketHoursUtils;

public class MarketHoursUtilsTest {
    @Test
    public void testMarketHoursUtils() {
        assertEquals(LocalTime.of(9, 0).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(9, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(9, 30).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(9, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(11, 30).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(11, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(12, 0).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(12, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(12, 0).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(12, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(12, 0).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(13, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(12, 30).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(13, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(15, 0).toNanoOfDay(), MarketHoursUtils.adjustNanoOfDay(LocalTime.of(16, 0).toNanoOfDay()));
        
        assertEquals(LocalTime.of(1, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(9, 30).toNanoOfDay(), LocalTime.of(11, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(2, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(9, 30).toNanoOfDay(), LocalTime.of(12, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(2, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(9, 30).toNanoOfDay(), LocalTime.of(12, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(2, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(9, 30).toNanoOfDay(), LocalTime.of(13, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(3, 0).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(9, 30).toNanoOfDay(), LocalTime.of(13, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(12, 0).toNanoOfDay(), LocalTime.of(13, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(12, 30).toNanoOfDay(), LocalTime.of(13, 30).toNanoOfDay()));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(13, 0).toNanoOfDay(), LocalTime.of(13, 30).toNanoOfDay()));
        assertEquals(0, MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(12, 0).toNanoOfDay(), LocalTime.of(13, 0).toNanoOfDay()));
        assertEquals(0, MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(12, 30).toNanoOfDay(), LocalTime.of(13, 0).toNanoOfDay()));
        assertEquals(LocalTime.of(1, 0).toNanoOfDay(), MarketHoursUtils.calcNanoOfDayTimeDifference(LocalTime.of(13, 30).toNanoOfDay(), LocalTime.of(14, 30).toNanoOfDay()));
    }
}
