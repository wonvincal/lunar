package com.lunar.strategy.scoreboard.stats;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class WarrantTradesStats extends ScoreBoardStats {
    private int numberTrades;
    private int numberUncertainTrades;
    private int numberBigTrades;
    private long numBuy;
    private long totalBuyPrice;
    private long numSell;
    private long totalSellPrice;
    private long netSold;
    private long netRevenue;
    private long netPnl;
    private long grossSold;
    private long grossRevenue;
    private long grossPnl;

    public WarrantTradesStats(final ScoreBoard scoreBoard) {
        super(scoreBoard);
    }

    public int getNumberTrades() {
        return numberTrades;
    }

    public void setNumberTrades(int numberTrades) {
        this.numberTrades = numberTrades;
    }

    public int getNumberUncertainTrades() {
        return numberUncertainTrades;
    }

    public void setNumberUncertainTrades(int numberUncertainTrades) {
        this.numberUncertainTrades = numberUncertainTrades;
    }
    
    public int getNumberBigTrades() {
        return numberBigTrades;
    }

    public void setNumberBigTrades(int numberBigTrades) {
        this.numberBigTrades = numberBigTrades;
    }

    public long getNetSold() {
        return netSold;
    }

    public void setNetSold(long netSold) {
        this.netSold = netSold;
    }

    public long getNetRevenue() {
        return netRevenue;
    }

    public void setNetRevenue(long netRevenue) {
        this.netRevenue = netRevenue;
    }

    public long getNetPnl() {
        return netPnl;
    }

    public void setNetPnl(long netPnl) {
        this.netPnl = netPnl;
    }
    
    public long getNumBuy() {
        return numBuy;
    }

    public void setNumBuy(long numBuy) {
        this.numBuy = numBuy;
    }

    public long getTotalBuyPrice() {
        return totalBuyPrice;
    }

    public void setTotalBuyPrice(long totalBuyPrice) {
        this.totalBuyPrice = totalBuyPrice;
    }

    public long getNumSell() {
        return numSell;
    }

    public void setNumSell(long numSell) {
        this.numSell = numSell;
    }

    public long getTotalSellPrice() {
        return totalSellPrice;
    }

    public void setTotalSellPrice(long totalSellPrice) {
        this.totalSellPrice = totalSellPrice;
    }

    public long getGrossSold() {
        return grossSold;
    }

    public void setGrossSold(long grossSold) {
        this.grossSold = grossSold;
    }
    
    public long getGrossRevenue() {
        return grossRevenue;
    }

    public void setGrossRevenue(long grossRevenue) {
        this.grossRevenue = grossRevenue;
    }

    public long getGrossPnl() {
        return grossPnl;
    }

    public void setGrossPnl(long grossPnl) {
        this.grossPnl = grossPnl;
    }

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return ScoreBoardSender.encodePunterTradeSetStats(buffer, offset, stringBuffer, encoder.scoreBoardSbeEncoder(), this);
    }

    @Override
    public int expectedEncodedLength() {
        return ScoreBoardSender.expectedEncodedLength(this);
    }
    
}
