package com.lunar.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.marketdata.hkex.CtpMduApi;

public class RealSystemClock implements SystemClock {
    static final Logger LOG = LogManager.getLogger(RealSystemClock.class);
    
    public static final long START_SYSTEM_NS;
    public static final long START_NANO_OF_DAY;
    public static final long NANO_OF_DAY_OFFSET;
    public static final LocalDate TODAY;
    public static final String TODAY_DATE_STRING;
    
    private interface NanoOfDayGetter {
        long getNanoOfDay();
    }
    
    /**
     * NOTE Each class loader that loads this class has its own copy of {@link START_SYSTEM_NS}, {@link START_NANO_OF_DAY}, {@link TODAY}
     */
    static {
        NanoOfDayGetter getter;
        // get the nano time from cpp (more precise) if possible
        try {
            getter = new CtpMduNanoOfDayGetter();
        }
        catch (final UnsatisfiedLinkError e) {
            LOG.info("Failed to load CtpMduApi, using Java LocalTime nanoOfDay");
            getter = new JavaNanoOfDayGetter();
        }
        // warmup to minimize latency of the two calls
        long nanoTime = 0;
        long nanoOfDay = 0;
        for (int i = 0; i < 10000; i++) {
            nanoTime = System.nanoTime();
            nanoOfDay = getter.getNanoOfDay();
        }
        START_SYSTEM_NS = nanoTime;
        START_NANO_OF_DAY = nanoOfDay;
        NANO_OF_DAY_OFFSET = START_NANO_OF_DAY - START_SYSTEM_NS;
        long sourceTime = getter.getNanoOfDay();
        long clockTime = System.nanoTime() + NANO_OF_DAY_OFFSET;
        LOG.info("Difference between clock time and source time: {}", clockTime - sourceTime);
        TODAY = LocalDate.now();
        TODAY_DATE_STRING = TODAY.format(DateTimeFormatter.ISO_LOCAL_DATE);
        LOG.info("Today is {}",  TODAY_DATE_STRING);
    }    
    
    static final private class CtpMduNanoOfDayGetter implements NanoOfDayGetter {
        private CtpMduApi ctpMdu;
        
        public CtpMduNanoOfDayGetter() {
            ctpMdu = new CtpMduApi(null);
        }
        
        @Override
        public long getNanoOfDay() {
            return ctpMdu.getNanoOfDay();
        }        
    }
    
    static final private class JavaNanoOfDayGetter implements NanoOfDayGetter {
        public JavaNanoOfDayGetter() {
        }
        
        public long getNanoOfDay() {
            return LocalTime.now().toNanoOfDay();
        }
    }
    
    public RealSystemClock(){}
    
    @Override
    public String dateString() {
        return TODAY_DATE_STRING;
    }
    
	@Override
	public LocalDate date() {
		return TODAY;
	}

	@Override
	public LocalTime time() {
	    return LocalTime.ofNanoOfDay(nanoOfDay());
	}

	@Override
	public long nanoOfDay() {
	    return (System.nanoTime() + NANO_OF_DAY_OFFSET);
	}

	@Override
	public long timestamp() {
		return System.nanoTime();
	}

	long startSystemNs(){
	    return START_SYSTEM_NS;
	}
	
	long startNanoOfDay(){
	    return START_NANO_OF_DAY;
	}
}
