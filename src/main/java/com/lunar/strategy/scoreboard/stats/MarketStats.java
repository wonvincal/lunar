package com.lunar.strategy.scoreboard.stats;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class MarketStats extends ScoreBoardStats {
    private int prevDayOsPercent = 0;
    private int prevDayOsPercentChange = 0;
    private long prevDayOutstanding = 0;
    private long prevDayNetSold = 0;
    private long prevDayNetVegaSold = 0;
    private int impliedVol = 0;
    private int prevDayImpliedVol = 0;
    private int bidImpliedVol = 0;
    private int askImpliedVol = 0;    
    private int tickSensitivity = 0;
    private long issuerSmoothing = 0;
    private int volPerTick = 0;

    public MarketStats(final ScoreBoard scoreBoard) {
        super(scoreBoard);
    }

    public long getIssuerSmoothing() {
        return issuerSmoothing;
    }

    public void setIssuerSmoothing(final long issuerSmoothing) {
        this.issuerSmoothing = issuerSmoothing;
    }

    public int getPrevDayOsPercent() {
        return prevDayOsPercent;
    }

    public void setPrevDayOsPercent(int prevDayOsPercent) {
        this.prevDayOsPercent = prevDayOsPercent;
    }

    public int getPrevDayOsPercentChange() {
        return prevDayOsPercentChange;
    }

    public void setPrevDayOsPercentChange(int prevDayOsPercentChange) {
        this.prevDayOsPercentChange = prevDayOsPercentChange;
    }

    public long getPrevDayOutstanding() {
        return prevDayOutstanding;
    }

    public void setPrevDayOutstanding(long prevDayOutstanding) {
        this.prevDayOutstanding = prevDayOutstanding;
    }
    
    public long getPrevDayNetSold() {
        return prevDayNetSold;
    }

    public void setPrevDayNetSold(long prevDayNetSold) {
        this.prevDayNetSold = prevDayNetSold;
    }

    public long getPrevDayNetVegaSold() {
        return prevDayNetVegaSold;
    }

    public void setPrevDayNetVegaSold(long prevDayNetVegaSold) {
        this.prevDayNetVegaSold = prevDayNetVegaSold;
    }
    
    public int getImpliedVol() {
        return impliedVol;
    }

    public void setImpliedVol(int impliedVol) {
        this.impliedVol = impliedVol;
    }

    public int getPrevDayImpliedVol() {
        return prevDayImpliedVol;
    }

    public void setPrevDayImpliedVol(int prevImpliedVol) {
        this.prevDayImpliedVol = prevImpliedVol;
    }

    public int getBidImpliedVol() {
        return bidImpliedVol;
    }

    public void setBidImpliedVol(int impliedVol) {
        this.bidImpliedVol = impliedVol;
    }

    public int getAskImpliedVol() {
        return askImpliedVol;
    }

    public void setAskImpliedVol(int impliedVol) {
        this.askImpliedVol = impliedVol;
    }

    public int getTickSensitivity() {
        return tickSensitivity;
    }

    public void setTickSensitivity(int tickSensitivity) {
        this.tickSensitivity = tickSensitivity;
    }
    
    public int getVolPerTick() {
        return volPerTick;
    }

    public void setVolPerTick(int volPerTick) {
        this.volPerTick = volPerTick;
    }

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return ScoreBoardSender.encodeMarketStats(buffer, offset, stringBuffer, encoder.scoreBoardSbeEncoder(), this);
    }

    @Override
    public int expectedEncodedLength() {
        return ScoreBoardSender.expectedEncodedLength(this);
    }
}
