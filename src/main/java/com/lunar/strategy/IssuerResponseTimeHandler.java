package com.lunar.strategy;

public interface IssuerResponseTimeHandler extends StrategySignalHandler {
    public void onIssuerLagUpdated(final long issuerMaxLag) throws Exception;
    public void onIssuerSmoothingUpdated(final long issuerSmoothing) throws Exception;

}
