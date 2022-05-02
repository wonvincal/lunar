package com.lunar.strategy.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class MultiCompositeScoreBoardUpdateHandler implements CompositeScoreBoardUpdateHandler {
    private List<ScoreBoardUpdateHandler> m_handlers;
    
    public MultiCompositeScoreBoardUpdateHandler() {
        m_handlers = new ArrayList<ScoreBoardUpdateHandler>();
    }
    
    @Override
    public void onScoreBoardUpdated(long nanoOfDay, ScoreBoard scoreBoard) {
        for (final ScoreBoardUpdateHandler handler : m_handlers) {
            handler.onScoreBoardUpdated(nanoOfDay, scoreBoard);
        }
    }

    @Override
    public void registerScoreUpdateHandler(ScoreBoardUpdateHandler scoreUpdateHandler) {
        if (!m_handlers.contains(scoreUpdateHandler)) {
            m_handlers.add(scoreUpdateHandler);
        }        
    }

    @Override
    public void unregisterScoreUpdateHandler(ScoreBoardUpdateHandler scoreUpdateHandler) {
        m_handlers.remove(scoreUpdateHandler);        
    }

}
