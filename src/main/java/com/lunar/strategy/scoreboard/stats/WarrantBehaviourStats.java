package com.lunar.strategy.scoreboard.stats;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class WarrantBehaviourStats extends ScoreBoardStats {
    private int numDropVols;
    private int numAutoDropVolsOnBuy;
    private int numAutoDropVolsOnSell;
    private int numManualDropVolsOnBuy;
    private int numManualDropVolsOnSell;
    private int numRaiseVols;
    private int numAutoRaiseVolsOnSell;
    private int numManualRaiseVolsOnSell;
    
    private int numPunterBuyTrades;
    private int numPunterTriggers;
    private long lastTimePunterTrigger;
    private long avgBreakEvenTime;
    private long minBreakEvenTime;
    private long lastBreakEvenTime;
    private int numBreakEvens;
    private long lastTimeBreakEven;
    private long avgProfitTime;
    private long minProfitTime;
    private long lastProfitTime;
    private int numProfits;
    private long lastTimeProfit;
    private int numLosses;
    private int numPendingProfits;
    
    private int numConsecutiveBreakEvensOrProfits;
    private int numConsecutiveLosses;
    
    private long maxProfitableQuantity;
    private long modeProfitableQuantity;
    
    private long lastTriggerTotalBuyQuantity = 0;
    private long lastTriggerTotalTrades = 0;

    private long smoothingTime;
    private long smoothingTimePerTick;
    private long lastTimeSmoothing;
    private long avgSmoothingTime;
    private long avgSmoothingTimePerTick;
    
    private long modeSpread;
    private long twaSpread_3L;    
    private long normalizedModeSpread;
    private long normalizedTwaSpread_3L;
    private long twaSpreadInPosition_3L;
    private long normalizedTwaSpreadInPosition_3L;

    public WarrantBehaviourStats(final ScoreBoard scoreBoard) {
        super(scoreBoard);
    }

    public int getNumDropVols() {
        return numDropVols;
    }

    public void setNumDropVols(int numDropVols) {
        this.numDropVols = numDropVols;
    }

    public int getNumAutoDropVolsOnBuy() {
        return numAutoDropVolsOnBuy;
    }

    public void setNumAutoDropVolsOnBuy(int numAutoDropVolsOnBuy) {
        this.numAutoDropVolsOnBuy = numAutoDropVolsOnBuy;
    }

    public int getNumAutoDropVolsOnSell() {
        return numAutoDropVolsOnSell;
    }

    public void setNumAutoDropVolsOnSell(int numAutoDropVolsOnSell) {
        this.numAutoDropVolsOnSell = numAutoDropVolsOnSell;
    }

    public int getNumManualDropVolsOnBuy() {
        return numManualDropVolsOnBuy;
    }

    public void setNumManualDropVolsOnBuy(int numManualDropVolsOnBuy) {
        this.numManualDropVolsOnBuy = numManualDropVolsOnBuy;
    }

    public int getNumManualDropVolsOnSell() {
        return numManualDropVolsOnSell;
    }

    public void setNumManualDropVolsOnSell(int numManualDropVolsOnSell) {
        this.numManualDropVolsOnSell = numManualDropVolsOnSell;
    }

    public int getNumRaiseVols() {
        return numRaiseVols;
    }

    public void setNumRaiseVols(int numRaiseVols) {
        this.numRaiseVols = numRaiseVols;
    }

    public int getNumAutoRaiseVolsOnSell() {
        return numAutoRaiseVolsOnSell;
    }

    public void setNumAutoRaiseVolsOnSell(int numAutoRaiseVolsOnSell) {
        this.numAutoRaiseVolsOnSell = numAutoRaiseVolsOnSell;
    }

    public int getNumManualRaiseVolsOnSell() {
        return numManualRaiseVolsOnSell;
    }

    public void setNumManualRaiseVolsOnSell(int numManualRaiseVolsOnSell) {
        this.numManualRaiseVolsOnSell = numManualRaiseVolsOnSell;
    }

    public int getNumPunterBuyTrades() {
        return numPunterBuyTrades;
    }

    public void setNumPunterBuyTrades(int numPunterBuyTrades) {
        this.numPunterBuyTrades = numPunterBuyTrades;
    }

    public void incrementNumPunterBuyTrades() {
        incrementNumPunterBuyTrades(1);
    }
    
    public void incrementNumPunterBuyTrades(final int numTrades) {
        this.numPunterBuyTrades += numTrades;
    }

    public int getNumPunterTriggers() {
        return numPunterTriggers;
    }

    public void setNumPunterTriggers(int numPunterTriggers) {
        this.numPunterTriggers = numPunterTriggers;
    }
    
    public void incrementNumPunterTriggers() {
        this.numPunterTriggers++;
    }

    public long getLastTimePunterTrigger() {
        return lastTimePunterTrigger;
    }
    
    public void setLastTimePunterTrigger(long lastTimePunterTrigger) {
        this.lastTimePunterTrigger = lastTimePunterTrigger;
    }

    public long getAvgBreakEvenTime() {
        return avgBreakEvenTime;
    }

    public void setAvgBreakEvenTime(long avgBreakEvenTime) {
        this.avgBreakEvenTime = avgBreakEvenTime;
    }

    public long getMinBreakEvenTime() {
        return minBreakEvenTime;
    }

    public void setMinBreakEvenTime(long minBreakEvenTime) {
        this.minBreakEvenTime = minBreakEvenTime;
    }
   
    public long getLastBreakEvenTime() {
        return lastBreakEvenTime;
    }

    public void setLastBreakEvenTime(long lastBreakEvenTime) {
        this.lastBreakEvenTime = lastBreakEvenTime;
    }

    public int getNumTotalBreakEvensOrProfits() {
        return numBreakEvens + numProfits + numPendingProfits;
    }

    public int getNumBreakEvens() {
        return numBreakEvens;
    }

    public int getNumTotalBreakEvens() {
        return numBreakEvens + numPendingProfits;
    }

    public void setNumBreakEvens(int numBreakEvens) {
        this.numBreakEvens = numBreakEvens;
    }

    public void incrementNumBreakEvens() {
        this.numBreakEvens++;
    }

    public long getLastTimeBreakEven() {
        return lastTimeBreakEven;
    }

    public void setLastTimeBreakEven(long lastTimeBreakEven) {
        this.lastTimeBreakEven = lastTimeBreakEven;
    }

    public long getAvgProfitTime() {
        return avgProfitTime;
    }

    public void setAvgProfitTime(long avgProfitTime) {
        this.avgProfitTime = avgProfitTime;
    }

    public long getMinProfitTime() {
        return minProfitTime;
    }

    public void setMinProfitTime(long minProfitTime) {
        this.minProfitTime = minProfitTime;
    }

    public long getLastProfitTime() {
        return lastProfitTime;
    }

    public void setLastProfitTime(long lastProfitTime) {
        this.lastProfitTime = lastProfitTime;
    }

    public int getNumProfits() {
        return numProfits;
    }

    public void setNumProfits(int numProfits) {
        this.numProfits = numProfits;
    }

    public void incrementNumProfits() {
        this.numProfits++;
    }

    public long getLastTimeProfits() {
        return lastTimeProfit;
    }

    public void setLastTimeProfits(long lastTimeProfits) {
        this.lastTimeProfit = lastTimeProfits;
    }

    public int getNumLosses() {
        return numLosses;
    }

    public void setNumLosses(int numLosses) {
        this.numLosses = numLosses;
    }
    
    public void incrementNumLosses() {
    	this.numLosses++;
    }
    
    public int getNumPendingProfits() {
        return numPendingProfits;
    }

    public void setNumPendingProfits(int numPendingProfits) {
        this.numPendingProfits = numPendingProfits;
    }
    
    public void incrementNumPendingProfits() {
    	this.numPendingProfits++;
    }
    
    public void decrementNumPendingProfits() {
    	this.numPendingProfits--;
    }
    
    public int getNumConsecutiveBreakEvensOrProfits() {
    	return numConsecutiveBreakEvensOrProfits;
    }
    
    public void setNumConsecutiveBreakEvensOrProfits(int numConsecutiveBreakEvensOrProfits) {
    	this.numConsecutiveBreakEvensOrProfits = numConsecutiveBreakEvensOrProfits;
    }
    
    public void incrementNumConsecutiveBreakEvensOrProfits() {
    	this.numConsecutiveBreakEvensOrProfits++;
    }
    
    public int getNumConsecutiveLosses() {
    	return numConsecutiveLosses;
    }
    
    public void setNumConsecutiveLosses(int numConsecutiveLosses) {
    	this.numConsecutiveLosses = numConsecutiveLosses;
    }
    
    public void incrementNumConsecutiveLosses() {
    	this.numConsecutiveLosses++;
    }
    
    public long getMaxProfitableQuantity() {
    	return maxProfitableQuantity;
    }

    public void setMaxProfitableQuantity(long maxProfitableQuantity) {
    	this.maxProfitableQuantity = maxProfitableQuantity;
    }

    public long getModeProfitableQuantity() {
    	return modeProfitableQuantity;
    }

    public void setModeProfitableQuantity(long modeProfitableQuantity) {
    	this.modeProfitableQuantity = modeProfitableQuantity;
    }

    public long getLastTriggerTotalBuyQuantity() {
        return lastTriggerTotalBuyQuantity;
    }

    public void setLastTriggerTotalBuyQuantity(long lastTriggerTotalBuyQuantity) {
        this.lastTriggerTotalBuyQuantity = lastTriggerTotalBuyQuantity;
    }

    public long getLastTriggerTotalTrades() {
        return lastTriggerTotalTrades;
    }

    public void setLastTriggerTotalTrades(long lastTriggerTotalTrades) {
        this.lastTriggerTotalTrades = lastTriggerTotalTrades;
    }

    public long getSmoothingTime() {
        return this.smoothingTime;
    }

    public void setSmoothingTime(long smoothingTime) {
        this.smoothingTime = smoothingTime;
    }

    public long getSmoothingTimePerTick() {
        return this.smoothingTimePerTick;
    }

    public void setSmoothingTimePerTick(long smoothingTimePerTick) {
        this.smoothingTimePerTick = smoothingTimePerTick;
    }

    public long getLastTimeSmoothing() {
        return this.lastTimeSmoothing;
    }
    
    public void setLastTimeSmoothing(long lastTimeSmoothing) {
        this.lastTimeSmoothing = lastTimeSmoothing;
    }
    
    public long getAvgSmoothingTime() {
        return this.avgSmoothingTime;
    }

    public void setAvgSmoothingTime(long avgSmoothingTime) {
        this.avgSmoothingTime = avgSmoothingTime;
    }
    
    public long getAvgSmoothingTimePerTick() {
        return this.avgSmoothingTimePerTick;
    }

    public void setAvgSmoothingTimePerTick(long avgSmoothingTimePerTick) {
        this.avgSmoothingTimePerTick = avgSmoothingTimePerTick;
    }
    
    public long getModeSpread() {
    	return this.modeSpread;
    }
    
    public void setModeSpread(final long modeSpread) {
    	this.modeSpread = modeSpread;
    }

    public long getTwaSpread() {
    	return this.twaSpread_3L;
    }
    
    public void setTwaSpread(final long twaSpread_3L) {
    	this.twaSpread_3L = twaSpread_3L;
    }
    
    public long getNormalizedModeSpread() {
    	return this.normalizedModeSpread;
    }
    
    public void setNormalizedModeSpread(final long normalizedModeSpread) {
    	this.normalizedModeSpread = normalizedModeSpread;
    }

    public long getNormalizedTwaSpread() {
    	return this.normalizedTwaSpread_3L;
    }
    
    public void setNormalizedTwaSpread(final long normalizedTwaSpread_3L) {
    	this.normalizedTwaSpread_3L = normalizedTwaSpread_3L;
    }

    public long getTwaSpreadInPosition() {
        return this.twaSpreadInPosition_3L;
    }
    
    public void setTwaSpreadInPosition(final long twaSpreadInPosition_3L) {
        this.twaSpreadInPosition_3L = twaSpreadInPosition_3L;
    }
    
    public long getNormalizedTwaSpreadInPosition() {
        return this.normalizedTwaSpreadInPosition_3L;
    }
    
    public void setNormalizedTwaSpreadInPosition(final long normalizedTwaSpreadInPosition_3L) {
        this.normalizedTwaSpreadInPosition_3L = normalizedTwaSpreadInPosition_3L;
    }


    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return ScoreBoardSender.encodeWarrantBehaviourStats(buffer, offset, stringBuffer, encoder.scoreBoardSbeEncoder(), this);
    }

    @Override
    public int expectedEncodedLength() {
        return ScoreBoardSender.expectedEncodedLength(this);
    }

}
