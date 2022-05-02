package com.lunar.strategy.scoreboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.strategy.scoreboard.stats.MarketStats;
import com.lunar.strategy.scoreboard.stats.WarrantTradesStats;
import com.lunar.strategy.scoreboard.stats.SpeedArbHybridStats;
import com.lunar.strategy.scoreboard.stats.WarrantBehaviourStats;

public class ScoreBoard {
    static final Logger LOG = LogManager.getLogger(ScoreBoard.class);
    static public final int MAX_SCORE = 1000;
    static public final int INITIAL_SCORE = 500;

    final private ScoreBoardSecurityInfo security;
    private long secSid;
    
    private int score;
    private int scoreThreshold = 501;
    final private MarketStats marketStats;
    final private WarrantTradesStats punterTradeSetStats;
    final private SpeedArbHybridStats speedArbHybridStats;
    final private WarrantBehaviourStats warrantBehaviourStats;
    
    public static ScoreBoard of() {
        return new ScoreBoard(null);
    }
    
    public static ScoreBoard of(final ScoreBoardSecurityInfo security) {
        return new ScoreBoard(security);
    }
    
    public ScoreBoard(final ScoreBoardSecurityInfo security) {
        this.marketStats = new MarketStats(this);
        this.punterTradeSetStats = new WarrantTradesStats(this);
        this.speedArbHybridStats = new SpeedArbHybridStats(this);
        this.warrantBehaviourStats = new WarrantBehaviourStats(this);
        this.security = security;
        if (this.security != null) {
            this.secSid = security.sid();
        }
    }
    
    public ScoreBoardSecurityInfo getSecurity() {
        return this.security;        
    }
    
    public long getSecSid() {
        return this.secSid;
    }
    
    public void setSecSid(long secSid) {
        if (this.security == null) {
            this.secSid = secSid;
        }
    }
    
    public int getScore() {
        return score;
    }
    
    public void setScore(final int score) {
        this.score = score;
    }

    public int getScoreThreshold() {
        return scoreThreshold;
    }
    
    public void setScoreThreshold(final int scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public MarketStats marketStats() { return marketStats; }
    public WarrantTradesStats punterTradeSetStats() { return punterTradeSetStats; }
    public SpeedArbHybridStats speedArbHybridStats() { return speedArbHybridStats; }
    public WarrantBehaviourStats warrantBehaviourStats() { return warrantBehaviourStats; } 

}