package com.lunar.strategy.scoreboard.stats;

import com.lunar.core.SbeEncodable;
import com.lunar.message.io.sbe.ScoreBoardSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.strategy.scoreboard.ScoreBoard;
import com.lunar.strategy.scoreboard.ScoreBoardSecurityInfo;

abstract public class ScoreBoardStats implements SbeEncodable {
    final private ScoreBoard scoreBoard;

    public ScoreBoardStats(final ScoreBoard scoreBoard) {
        this.scoreBoard = scoreBoard;
    }
    
    public ScoreBoard getScoreBoard() {
        return this.scoreBoard;
    }
    
    public ScoreBoardSecurityInfo getSecurity() {
        return this.scoreBoard.getSecurity();        
    }
    
    public long getSecSid() {
        return this.scoreBoard.getSecSid();
    }
    
    @Override
    public TemplateType templateType(){
        return TemplateType.SCOREBOARD;
    }

    @Override
    public short blockLength() {
        return ScoreBoardSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int schemaId() {
        return ScoreBoardSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return ScoreBoardSbeEncoder.SCHEMA_VERSION;
    }

}
