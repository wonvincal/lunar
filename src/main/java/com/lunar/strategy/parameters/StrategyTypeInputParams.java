package com.lunar.strategy.parameters;

import com.lunar.message.io.sbe.StrategyExitMode;

public interface StrategyTypeInputParams {
    public interface Validator {
        boolean validateStrategyExitMode(final StrategyTypeInputParams params, final StrategyExitMode exitMode);
    }
    
    public interface PostUpdateHandler {
        void onUpdatedStrategyExitMode(final StrategyTypeInputParams params);
    }
    
    public StrategyExitMode exitMode();    
    public StrategyTypeInputParams exitMode(final StrategyExitMode exitMode);
    public StrategyTypeInputParams userExitMode(final StrategyExitMode exitMode);

    UndInputParams defaultUndInputParams();
    IssuerInputParams defaultIssuerInputParams();
    WrtInputParams defaultWrtInputParams();
    IssuerUndInputParams defaultIssuerUndInputParams();
    
    default public void copyTo(final StrategyTypeInputParams o) {
        o.exitMode(this.exitMode());
        this.defaultUndInputParams().copyTo(o.defaultUndInputParams());
        this.defaultIssuerInputParams().copyTo(o.defaultIssuerInputParams());
        this.defaultWrtInputParams().copyTo(o.defaultWrtInputParams());
        this.defaultIssuerUndInputParams().copyTo(o.defaultIssuerUndInputParams());
    }

}
