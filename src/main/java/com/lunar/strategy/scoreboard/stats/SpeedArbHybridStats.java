package com.lunar.strategy.scoreboard.stats;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.strategy.scoreboard.ScoreBoard;

public class SpeedArbHybridStats extends ScoreBoardStats {
    private int ourScore = ScoreBoard.INITIAL_SCORE;
    private int ourScoreWithPunter = ScoreBoard.INITIAL_SCORE;
    private int ourPrevScoreWithPunter = ScoreBoard.INITIAL_SCORE;
    private int ourPnlTicks = 0;
    private int ourPnlTicksWithPunter = 0;
    private int numOurWins = 0;
    private int numOurWinsWithPunter = 0;
    private int numOurLosses = 0;
    private int numOurBreakEvens = 0;
    private int ourMtmScore = ScoreBoard.INITIAL_SCORE;
    private int ourMtmScoreWithPunter = ScoreBoard.INITIAL_SCORE;
    private int ourTheoreticalPenalty = 0;
    private int ourMtmTheoreticalPenalty = 0;

    
    public SpeedArbHybridStats(final ScoreBoard scoreBoard) {
        super(scoreBoard);
    }

    public int getOurScore() {
        return ourScore;
    }
    
    public void setOurScore(int ourScore) {
    	this.ourScore = Math.max(0, Math.min(ScoreBoard.MAX_SCORE, ourScore));
    }
    
    public int getOurScoreWithPunter() {
        return ourScoreWithPunter;
    }
    
    public void setOurScoreWithPunter(int ourScore) {
    	this.ourScoreWithPunter = Math.max(0, Math.min(ScoreBoard.MAX_SCORE, ourScore));
    }
    
    public int getOurPrevScoreWithPunter() {
        return ourPrevScoreWithPunter;
    }
    
    public void setOurPrevScoreWithPunter(int ourScore) {
    	this.ourPrevScoreWithPunter = Math.max(0, Math.min(ScoreBoard.MAX_SCORE, ourScore));
    }
    
    public int getOurPnlTicks() {
        return ourPnlTicks;
    }
    
    public void setOurPnlTicks(int ourPnlTicks) {
        this.ourPnlTicks = ourPnlTicks;
    }
    
    public void incrementOurPnlTicks(int increment) {
        this.ourPnlTicks += increment;
        if (increment > 0) {
            this.numOurWins++;
        }
        else if (increment < 0) {
            this.numOurLosses++;
        }
        else {
            this.numOurBreakEvens++;
        }
    }
    
    public int getOurPnlTicksWithPunter() {
        return ourPnlTicksWithPunter;
    }
    
    public void setOurPnlTicksWithPunter(int ourPnlTicks) {
        this.ourPnlTicksWithPunter = ourPnlTicks;
    }

    public void incrementOurPnlTicksWithPunter(int increment) {
        this.ourPnlTicksWithPunter += increment;
        if (increment > 0) {
            this.numOurWinsWithPunter++;
        }
    }

    public int getNumOurWins() {
        return numOurWins;
    }

    public int getNumOurWinsWithPunter() {
        return numOurWinsWithPunter;
    }
    
    public int getNumOurLosses() {
        return numOurLosses; 
    }
    
    public int getNumOurBreakEvens() {
        return numOurBreakEvens;
    }    
    
    public int getOurMtmScore() {
        return ourMtmScore;
    }
    
    public void setOurMtmScore(int ourScore) {
    	this.ourMtmScore = Math.max(0, Math.min(ScoreBoard.MAX_SCORE, ourScore));
    }
    
    public int getOurMtmScoreWithPunter() {
        return ourMtmScoreWithPunter;
    }
    
    public void setOurMtmScoreWithPunter(int ourScore) {
    	this.ourMtmScoreWithPunter = Math.max(0, Math.min(ScoreBoard.MAX_SCORE, ourScore));
    }

    public int getOurTheoreticalPenalty() {
        return ourTheoreticalPenalty;
    }
    
    public void setOurTheoreticalPenalty(int ourTheoreticalPenalty) {
        this.ourTheoreticalPenalty = ourTheoreticalPenalty;
    }
    
    public void incrementOurTheoreticalPenalty(int increment) {
        this.ourTheoreticalPenalty += increment;
    }

    public int getOurMtmTheoreticalPenalty() {
        return ourMtmTheoreticalPenalty;
    }

    public void setOurMtmTheoreticalPenalty(int ourMtmTheoreticalPenalty) {
        this.ourMtmTheoreticalPenalty = ourMtmTheoreticalPenalty;
    }    
    
    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return ScoreBoardSender.encodeSpeedArbHybridStats(buffer, offset, stringBuffer, encoder.scoreBoardSbeEncoder(), this);
    }

    @Override
    public int expectedEncodedLength() {
        return ScoreBoardSender.expectedEncodedLength(this);
    }
    
}
