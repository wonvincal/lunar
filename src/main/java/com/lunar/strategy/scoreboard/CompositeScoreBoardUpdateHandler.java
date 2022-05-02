package com.lunar.strategy.scoreboard;

public interface CompositeScoreBoardUpdateHandler extends ScoreBoardUpdateHandler {
    void registerScoreUpdateHandler(final ScoreBoardUpdateHandler scoreUpdateHandler);
    void unregisterScoreUpdateHandler(final ScoreBoardUpdateHandler scoreUpdateHandler);

}
