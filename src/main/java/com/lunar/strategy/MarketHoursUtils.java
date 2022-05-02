package com.lunar.strategy;

import java.time.LocalTime;

public class MarketHoursUtils {
    public static final long MARKET_MORNING_OPEN_TIME = LocalTime.of(9, 30).toNanoOfDay();    
    public static final long MARKET_PREP_MORNING_CLOSE_TIME = LocalTime.of(11, 45).toNanoOfDay();
    public static final long MARKET_MORNING_CLOSE_TIME = LocalTime.of(12, 0).toNanoOfDay();
    public static final long MARKET_AFTERNOON_OPEN_TIME = LocalTime.of(13, 0).toNanoOfDay();
    public static final long MARKET_PREP_CLOSE_TIME = LocalTime.of(15, 45).toNanoOfDay();
    public static final long MARKET_PREP_VERY_NEAR_CLOSE_TIME = LocalTime.of(15, 56).toNanoOfDay();
    public static final long MARKET_CLOSE_TIME = LocalTime.of(16, 0).toNanoOfDay();
    
    public static final long LUNCH_DURATION = MARKET_AFTERNOON_OPEN_TIME - MARKET_MORNING_CLOSE_TIME;

    static public long adjustNanoOfDay(final long nanoOfDay) {
        if (nanoOfDay < MARKET_MORNING_CLOSE_TIME) {
            return nanoOfDay;
        }
        else if (nanoOfDay > MARKET_AFTERNOON_OPEN_TIME) {
            return nanoOfDay - LUNCH_DURATION;
        }
        else {
            return MARKET_MORNING_CLOSE_TIME;
        }
    }
    
    static public long restoreNanoOfDay(final long nanoOfDay) {
        if (nanoOfDay > MARKET_MORNING_CLOSE_TIME) {
            return nanoOfDay + LUNCH_DURATION;
        }
        return nanoOfDay;
    }

    static public long calcNanoOfDayTimeDifference(final long fromNanoOfDay, final long toNanoOfDay) {        
        if (fromNanoOfDay > MARKET_AFTERNOON_OPEN_TIME || toNanoOfDay < MARKET_MORNING_CLOSE_TIME) {
            return toNanoOfDay - fromNanoOfDay;
        }
        if (fromNanoOfDay < MARKET_MORNING_CLOSE_TIME) {
            if (toNanoOfDay > MARKET_AFTERNOON_OPEN_TIME) {
                return toNanoOfDay - fromNanoOfDay - LUNCH_DURATION;
            }
            return MARKET_MORNING_CLOSE_TIME - fromNanoOfDay;
        }
        if (toNanoOfDay > MARKET_AFTERNOON_OPEN_TIME) {
            return toNanoOfDay - MARKET_AFTERNOON_OPEN_TIME;
        }
        return 0;
    }
    
}
