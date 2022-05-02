package com.lunar.strategy.parameters;

public interface IssuerOutputParams {
    public int numActiveWarrants();
    public IssuerOutputParams numActiveWarrants(final int numActiveWarrants);    
    public void incActiveWarrants();
    public void decActiveWarrants();

    public int numTotalWarrants();
    public IssuerOutputParams numTotalWarrants(final int numTotalWarrants);
    public void incTotalWarrants();
    
    default public void copyTo(final IssuerOutputParams o) {
        o.numActiveWarrants(this.numActiveWarrants());
        o.numTotalWarrants(this.numTotalWarrants());
    }

}
