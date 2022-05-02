package com.lunar.strategy.scoreboard;

import com.lunar.entity.LongEntityManager;
import com.lunar.strategy.StrategyIssuer;

public class IssuerScoreCalculatorFactory {
    public class GenericIssuerScoreCalculator implements ScoreBoardCalculator.IssuerScoreCalculator {
        @Override
        public int initialize(final ScoreBoard scoreBoard) {
            return 0;
        }
        
        @Override
        public int calculateScore(final long nanoOfDay, final ScoreBoard scoreBoard) {
            int score = 1000;
            score = adjustScoreForPunterPnl(nanoOfDay, scoreBoard, score);
            score = adjustScoreForDropVol(nanoOfDay, scoreBoard, score);
            score = adjustScoreForSmoothing(nanoOfDay, scoreBoard, score);
            score = adjustScoreForSpreadType(nanoOfDay, scoreBoard, score);
            score = adjustScoreForOutstanding(nanoOfDay, scoreBoard, score);
            return score;
        }
        
        protected int adjustScoreForPunterPnl(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
        
        protected int adjustScoreForDropVol(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
        
        protected int adjustScoreForSmoothing(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
        
        protected int adjustScoreForSpreadType(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
         
        protected int adjustScoreForOutstanding(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
    }

    // similar to GenericIssuerScoreCalculator except for the following changes:
    // 1. No drop vol adjustment
    public class JpIssuerScoreCalculator extends GenericIssuerScoreCalculator {
        @Override
        protected int adjustScoreForDropVol(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
    }

    // similar to GenericIssuerScoreCalculator except for the following changes:
    // 1. No drop vol adjustment
    // 2. No outstanding adjustment for low OS %
    public class SgIssuerScoreCalculator extends GenericIssuerScoreCalculator {
        @Override
        protected int adjustScoreForDropVol(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
        
        @Override
        protected int adjustScoreForOutstanding(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
    }
    
    public class HtIssuerScoreCalculator extends GenericIssuerScoreCalculator {
        protected int adjustScoreForSpreadType(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            return score;
        }
        
        protected int adjustScoreForDropVol(final long nanoOfDay, final ScoreBoard scoreBoard, int score) {
            //if (scoreBoard.warrantBehaviourStats().getNumBuyTrades() >= 5) {
            //    if ((scoreBoard.warrantBehaviourStats().getNumAutoDropVolsOnBuy() + scoreBoard.warrantBehaviourStats().getNumManualDropVolsOnBuy()) * 100 / scoreBoard.warrantBehaviourStats().getNumBuyTrades() >= 50) {
            //        return 0;
            //    }
            //}
            return score;
        }
    }
    
    public class OurScoreWithPuntersOnlyIssuerScoreCalculator implements ScoreBoardCalculator.IssuerScoreCalculator {
        @Override
        public int initialize(final ScoreBoard scoreBoard) {
            return scoreBoard.speedArbHybridStats().getOurScoreWithPunter();
        }        
        
        @Override
        public int calculateScore(final long nanoOfDay, final ScoreBoard scoreBoard) {
            return scoreBoard.speedArbHybridStats().getOurScoreWithPunter();
        }

    }
    
    public class GsIssuerScoreCalculator extends OurScoreWithPuntersOnlyIssuerScoreCalculator {
        @Override
        public int initialize(final ScoreBoard scoreBoard) {
            scoreBoard.setScoreThreshold(500);
            return super.initialize(scoreBoard);
        }        
    }
    
    public class CsIssuerScoreCalculator extends OurScoreWithPuntersOnlyIssuerScoreCalculator {
        @Override
        public int initialize(final ScoreBoard scoreBoard) {
            scoreBoard.setScoreThreshold(525);
            return super.initialize(scoreBoard);
        }        
    }

    
    private final LongEntityManager<StrategyIssuer> issuers;
    //private final ScoreBoardCalculator.IssuerScoreCalculator genericIssuerScoreCalculator;
    //private final ScoreBoardCalculator.IssuerScoreCalculator jpIssuerScoreCalculator;
    //private final ScoreBoardCalculator.IssuerScoreCalculator sgIssuerScoreCalculator;    
    //private final ScoreBoardCalculator.IssuerScoreCalculator htIssuerScoreCalculator;
    private final ScoreBoardCalculator.IssuerScoreCalculator gsIssuerScoreCalculator;
    private final ScoreBoardCalculator.IssuerScoreCalculator csIssuerScoreCalculator;
    private final ScoreBoardCalculator.IssuerScoreCalculator ourScoreOnlyIssuerScoreCalculator;
    public IssuerScoreCalculatorFactory(final LongEntityManager<StrategyIssuer> issuers) {
        this.issuers = issuers;
        //this.genericIssuerScoreCalculator = new GenericIssuerScoreCalculator();
        //this.jpIssuerScoreCalculator = new JpIssuerScoreCalculator();
        //this.sgIssuerScoreCalculator = new SgIssuerScoreCalculator();
        this.gsIssuerScoreCalculator = new GsIssuerScoreCalculator();
        this.csIssuerScoreCalculator = new CsIssuerScoreCalculator();
        this.ourScoreOnlyIssuerScoreCalculator = new OurScoreWithPuntersOnlyIssuerScoreCalculator();
    }
    
    public ScoreBoardCalculator.IssuerScoreCalculator getIssuerScoreCalculator(final long issuerSid) {
        final StrategyIssuer issuer = issuers.get(issuerSid);
        if (issuer == null) {
            return null;
        }
        switch (issuer.code()) {
        //case "JP":
            //return jpIssuerScoreCalculator;
        //case "SG":
            //return sgIssuerScoreCalculator;
        //case "HT":
            //return htIssuerScoreCalculator;
        case "MB":
        case "BP":
        case "GS":
            // reuse gs calculator for other issuers because for now the only change is the threshold
            return gsIssuerScoreCalculator;
        case "CS":
            return csIssuerScoreCalculator;
        default:
            return ourScoreOnlyIssuerScoreCalculator;
        }
    }

}
