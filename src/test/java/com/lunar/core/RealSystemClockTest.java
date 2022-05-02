package com.lunar.core;

import static org.junit.Assert.assertTrue;

import java.time.LocalTime;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lunar.marketdata.hkex.CtpMduApi;
import com.lunar.util.ConcurrentUtil;

public class RealSystemClockTest {
    private static final Logger LOG = LoggerFactory.getLogger(RealSystemClockTest.class);
    
    @Test
    public void test(){
        RealSystemClock clock = new RealSystemClock();
        LOG.debug("clock {} vs real {}", clock.timestamp(), System.nanoTime());
        LOG.debug("clock {} vs real {}", clock.nanoOfDay(), LocalTime.now().toNanoOfDay());
        LOG.debug("clock {} vs real {}", clock.nanoOfDay(), LocalTime.now().toNanoOfDay());
        LOG.debug("clock {} vs real {}", clock.nanoOfDay(), LocalTime.now().toNanoOfDay());
        ConcurrentUtil.sleep(1000);
        LOG.debug("clock {} vs real {}", clock.nanoOfDay(), LocalTime.now().toNanoOfDay());
        LOG.debug("clock {} vs real {}", clock.nanoOfDay(), LocalTime.now().toNanoOfDay());
        LOG.debug("clock {} vs real {}", clock.nanoOfDay(), LocalTime.now().toNanoOfDay());
    }
    
    @Test
    public void testCompareClocks(){
        RealSystemClock clock = new RealSystemClock();
        ConcurrentUtil.sleep(1000);
        RealSystemClock clock2 = new RealSystemClock();
        
        /* Aim for 10 microsecond precision */
        final long toleranceInNs = 20_000;
        for (int i = 0; i < 100; i ++){
            long nanoOfDay1 = clock.nanoOfDay();
            long nanoOfDay2 = clock2.nanoOfDay();
            long diff = nanoOfDay2 - nanoOfDay1;  
            assertTrue(diff + " exceeds tolerance", Math.abs(diff) <= toleranceInNs); 
        }
    }
    
    @Test
    @Ignore
    public void testCompareJniClocks() {
        final RealSystemClock clock = new RealSystemClock();
        final CtpMduApi ctpMdu = new CtpMduApi(null);
        for (int i = 0; i < 100; i++) {
            long ctpMduTime = ctpMdu.getNanoOfDay();
            long clockTime = clock.nanoOfDay();
            System.out.println(clockTime - ctpMduTime);
        }
    }
}
