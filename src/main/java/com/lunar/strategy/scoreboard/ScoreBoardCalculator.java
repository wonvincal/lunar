package com.lunar.strategy.scoreboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.apache.logging.log4j.util.Unbox.box;

public class ScoreBoardCalculator {
    static final Logger LOG = LogManager.getLogger(ScoreBoardCalculator.class);
    
    public interface IssuerScoreCalculator {
        public int initialize(final ScoreBoard scoreBoard);
        public int calculateScore(final long nanoOfDay, final ScoreBoard scoreBoard);
    }
    
    final static public long MIN_TIME_BETWEEN_TRADE_TRIGGERS = 20_000_000L; // for grouping multiple punter trades based on the same trigger
    final static public int POINT_PER_PNL_INCREASE = 50;
    final static public int POINT_PER_PNL_SPREAD_LOSS = 30;
    final static public int POINT_PER_PNL_DECREASE = 50;
    final static public long MAX_ISSUER_LAG = 20_000_000L;
    final static public long MAX_TIME_TO_BREAK_EVEN = 60_000_000_000L;
    final static public long MAX_TIME_TO_PROFIT = 600_000_000_000L;
    final static public long MAX_TIME_TO_HOLD = 1800_000_000_000L;
    final static public int LONG_TIME_TO_BREAKEVEN_PENALTY = 80;
    final static public int LONG_TIME_TO_PROFIT_PENALTY = 50;
    final static public int LONG_TIME_TO_HOLD_TO_BREAKEVEN_PENALTY = 10;
    final static public int MAX_PENALTY = 90;

    private final ScoreBoardSecurityInfo security;
    private final ScoreBoard scoreBoard;
    private final IssuerScoreCalculator issuerScoreCalculator; 

    public static ScoreBoardCalculator of(final ScoreBoardSecurityInfo security, final IssuerScoreCalculator issuerScoreCalculator) {
        return new ScoreBoardCalculator(security, issuerScoreCalculator);
    }
    
    public ScoreBoardCalculator(final ScoreBoardSecurityInfo security, final IssuerScoreCalculator issuerScoreCalculator) {
        this.security = security;
        this.scoreBoard = security.scoreBoard();
        this.issuerScoreCalculator = issuerScoreCalculator;
    }

    public int calcScoreFromPnlTicks(final int pnlTicks, final long timeToBreakEven, final long timeToProfit, final long holdingTime, final long buySpread_3L) {
        int scoreToAdd = 0;
        if (pnlTicks > 0) {
            scoreToAdd += pnlTicks * POINT_PER_PNL_INCREASE;
            int scaleDown = 0;
            if (timeToBreakEven > MAX_ISSUER_LAG) {
                if (timeToBreakEven < MAX_TIME_TO_BREAK_EVEN) {
                    scaleDown += ((timeToBreakEven - MAX_ISSUER_LAG) * LONG_TIME_TO_BREAKEVEN_PENALTY) / (MAX_TIME_TO_BREAK_EVEN - MAX_ISSUER_LAG);
                }
                else {
                    scaleDown += LONG_TIME_TO_BREAKEVEN_PENALTY;
                }
            }
            if (timeToProfit > MAX_TIME_TO_BREAK_EVEN) {
                if (timeToProfit < MAX_TIME_TO_PROFIT) {
                    scaleDown += ((timeToProfit - MAX_TIME_TO_BREAK_EVEN) * LONG_TIME_TO_PROFIT_PENALTY) / (MAX_TIME_TO_PROFIT - MAX_TIME_TO_BREAK_EVEN);
                }
                else {
                    scaleDown += LONG_TIME_TO_PROFIT_PENALTY;
                }                
            }
            scaleDown = Math.min(scaleDown, MAX_PENALTY);
            scoreToAdd = (scoreToAdd * (100 - scaleDown) / 100);
            
            if (holdingTime > MAX_TIME_TO_PROFIT) {
                if (holdingTime < MAX_TIME_TO_HOLD) {
                    scaleDown = (int)(((holdingTime - MAX_TIME_TO_PROFIT) * 100) / (MAX_TIME_TO_HOLD - MAX_TIME_TO_PROFIT));
                    scoreToAdd = (scoreToAdd * (100 - scaleDown) / 100);
                }
                else {
                    scoreToAdd = 0;
                }
            } 
        }
        else if (pnlTicks < 0) {
            if (buySpread_3L != Long.MAX_VALUE && (-pnlTicks * 1000) <= buySpread_3L) {
                scoreToAdd += pnlTicks * POINT_PER_PNL_SPREAD_LOSS;
            }
            else {
                scoreToAdd += pnlTicks * POINT_PER_PNL_DECREASE;
            }
        }
        else {
            if (holdingTime > MAX_TIME_TO_BREAK_EVEN) {
                if (holdingTime < MAX_TIME_TO_PROFIT) {
                    scoreToAdd -= ((holdingTime - MAX_TIME_TO_BREAK_EVEN) * LONG_TIME_TO_HOLD_TO_BREAKEVEN_PENALTY) / (MAX_TIME_TO_PROFIT - MAX_TIME_TO_BREAK_EVEN);
                }
                else {
                    scoreToAdd -= LONG_TIME_TO_HOLD_TO_BREAKEVEN_PENALTY;
                }
            }
        }
        return scoreToAdd;
    }
    
    public void initializeScore() {
        final int newScore = this.issuerScoreCalculator.initialize(scoreBoard);
        scoreBoard.setScore(newScore);
    }
    
    public void updateScore(final long nanoOfDay, final long triggerSeqNum) {
        final int prevScore = scoreBoard.getScore();
        final int newScore = this.issuerScoreCalculator.calculateScore(nanoOfDay, scoreBoard);
        scoreBoard.setScore(newScore);
        if (newScore != prevScore) {
            LOG.info("Updating score for security: secCode {}, prevScore {}, newScore {}", scoreBoard.getSecurity().code(), box(prevScore), box(newScore));
        }
        if (security.scoreBoardUpdateHandler() != null) {
            security.scoreBoardUpdateHandler().onScoreBoardUpdated(nanoOfDay, scoreBoard);
        }
    }
    
}
