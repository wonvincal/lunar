package com.lunar.strategy.scoreboard;

import static org.junit.Assert.assertEquals;

import java.time.LocalTime;

import org.junit.Test;

import com.lunar.core.RollingWindowTimeFrame;
import com.lunar.strategy.scoreboard.WarrantBehaviourAnalyser;

public class WarrantBehaviourAnalyserTest {
    @Test
    public void testSpreadUpdateContinuousTimeFrame() {
        final WarrantBehaviourAnalyser.SpreadTracker spreadTracker = new WarrantBehaviourAnalyser.SpreadTracker(1, true, true);
        final RollingWindowTimeFrame timeFrame = new RollingWindowTimeFrame(spreadTracker, LocalTime.of(1, 0).toNanoOfDay(), 4, 4);
        long currentTime = LocalTime.of(9, 30).toNanoOfDay();
        timeFrame.recordValue(currentTime, 1);
        assertEquals(Long.MAX_VALUE, spreadTracker.minSpread());
        assertEquals(Long.MAX_VALUE, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(Long.MAX_VALUE, spreadTracker.modeSpread());
        assertEquals(Long.MAX_VALUE, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(Long.MAX_VALUE, spreadTracker.twaSpread_3L());
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(9, 31).toNanoOfDay();
        timeFrame.recordValue(currentTime, 1);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(100000, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(100000, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1000, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 1).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));

        currentTime = LocalTime.of(9, 31, 30).toNanoOfDay();
        timeFrame.recordValue(currentTime, 2);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(100000, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(100000, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1000, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 1, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(9, 32).toNanoOfDay();
        timeFrame.recordValue(currentTime, 2);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(75000, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(75000, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1250, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 1, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(LocalTime.of(0, 0, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(9, 34).toNanoOfDay();
        timeFrame.recordValue(currentTime, 1);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(37500, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(2, spreadTracker.modeSpread());
        assertEquals(62500, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1625, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 1, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(LocalTime.of(0, 2, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 5).toNanoOfDay();
        timeFrame.recordValue(currentTime, 3);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(92857, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(92857, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1071, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 32, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(LocalTime.of(0, 2, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 26).toNanoOfDay();
        timeFrame.recordValue(currentTime, 3);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(58035, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(58035, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1794, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 32, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(LocalTime.of(0, 2, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 21).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 28).toNanoOfDay();
        timeFrame.recordValue(currentTime, 4);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(54867, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(54867, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1858, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 31).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(LocalTime.of(0, 2, 30).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 23).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 33).toNanoOfDay();
        timeFrame.recordValue(currentTime, 4);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(51666, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(51666, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(2033, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 31).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(LocalTime.of(0, 1).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 23).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(LocalTime.of(0, 5).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 34).toNanoOfDay();
        timeFrame.recordValue(currentTime, 3);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(51666, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(51666, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(2066, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 31).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 23).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(LocalTime.of(0, 6).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 38).toNanoOfDay();
        timeFrame.recordValue(currentTime, 3);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(45000, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(45000, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(2200, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 27).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 27).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(LocalTime.of(0, 6).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 39).toNanoOfDay();
        timeFrame.recordValue(currentTime, 3);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(43333, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(3, spreadTracker.modeSpread());
        assertEquals(46666, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(2233, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(0, 26).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 28).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(LocalTime.of(0, 6).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 40).toNanoOfDay();
        timeFrame.recordValue(currentTime, 4);
        assertEquals(3, spreadTracker.minSpread());
        assertEquals(82857, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(3, spreadTracker.modeSpread());
        assertEquals(82857, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(3171, spreadTracker.twaSpread_3L());
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 29).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(LocalTime.of(0, 6).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 4));
        
        currentTime = LocalTime.of(10, 42).toNanoOfDay();
        timeFrame.recordValue(currentTime, 1);
        assertEquals(3, spreadTracker.minSpread());
        assertEquals(42857, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(4, spreadTracker.modeSpread());
        assertEquals(57142, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(3571, spreadTracker.twaSpread_3L());
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(LocalTime.of(0, 6).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(LocalTime.of(0, 8).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 4));

        currentTime = LocalTime.of(11, 45).toNanoOfDay();
        timeFrame.recordValue(currentTime, 1);
        assertEquals(1, spreadTracker.minSpread());
        assertEquals(100000, spreadTracker.percentTimeInMinSpread_3L());
        assertEquals(1, spreadTracker.modeSpread());
        assertEquals(100000, spreadTracker.percentTimeInModeSpread_3L());
        assertEquals(1000, spreadTracker.twaSpread_3L());
        assertEquals(LocalTime.of(1, 0).toNanoOfDay(), spreadTracker.getTimeInSpread(currentTime, 1));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 2));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 3));
        assertEquals(0, spreadTracker.getTimeInSpread(currentTime, 4));
    }

    @Test
    public void testBidUpdateContinuousTimeFrame() {
        final WarrantBehaviourAnalyser.BidLevelTracker bidLevelTracer = new WarrantBehaviourAnalyser.BidLevelTracker();
        final RollingWindowTimeFrame timeFrame = new RollingWindowTimeFrame(bidLevelTracer, LocalTime.of(1, 0).toNanoOfDay(), 4, 4);
        long currentTime = LocalTime.of(10, 30).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(0, bidLevelTracer.modeBidLevel());
        assertEquals(Long.MAX_VALUE, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(10, 31).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(100000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 1).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(10, 32).toNanoOfDay();
        timeFrame.recordValue(currentTime, 7);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(100000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 2).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 02).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(7, bidLevelTracer.modeBidLevel());
        assertEquals(93750, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 2).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 28).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(7, bidLevelTracer.modeBidLevel());
        assertEquals(51724, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 28).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));

        currentTime = LocalTime.of(11, 30).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(50000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 30).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 45).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(71666, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 43).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 17).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 47).toNanoOfDay();
        timeFrame.recordValue(currentTime, 8);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(75000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 45).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 15).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 50).toNanoOfDay();
        timeFrame.recordValue(currentTime, 4);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(75000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 45).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 12).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 52).toNanoOfDay();
        timeFrame.recordValue(currentTime, 4);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(75000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(LocalTime.of(0, 2).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 45).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(LocalTime.of(0, 10).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 8));
        
        currentTime = LocalTime.of(11, 53).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(88235, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 5));
        assertEquals(LocalTime.of(0, 45).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 6));
        assertEquals(0, bidLevelTracer.getTimeInBid(currentTime, 7));
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(currentTime, 8));

        // Current time will be mapped to 12:00
        currentTime = LocalTime.of(12, 30).toNanoOfDay();
        long mappedCurrentTime = LocalTime.of(12, 0).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(89655, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(mappedCurrentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(mappedCurrentTime, 5));
        assertEquals(LocalTime.of(0, 52).toNanoOfDay(), bidLevelTracer.getTimeInBid(mappedCurrentTime, 6));
        assertEquals(0, bidLevelTracer.getTimeInBid(mappedCurrentTime, 7));
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(mappedCurrentTime, 8));
        
        // Current time will be mapped to 12:30
        currentTime = LocalTime.of(13, 30).toNanoOfDay();
        mappedCurrentTime = LocalTime.of(12, 30).toNanoOfDay();
        timeFrame.recordValue(currentTime, 6);
        assertEquals(6, bidLevelTracer.modeBidLevel());
        assertEquals(90000, bidLevelTracer.percentTimeInModeBid_3L());
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(mappedCurrentTime, 4));
        assertEquals(0, bidLevelTracer.getTimeInBid(mappedCurrentTime, 5));
        assertEquals(LocalTime.of(0, 54).toNanoOfDay(), bidLevelTracer.getTimeInBid(mappedCurrentTime, 6));
        assertEquals(0, bidLevelTracer.getTimeInBid(mappedCurrentTime, 7));
        assertEquals(LocalTime.of(0, 3).toNanoOfDay(), bidLevelTracer.getTimeInBid(mappedCurrentTime, 8));
    }
}
