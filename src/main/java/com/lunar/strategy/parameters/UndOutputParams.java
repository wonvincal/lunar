package com.lunar.strategy.parameters;

public interface UndOutputParams {
    public int numActiveWarrants();
    public UndOutputParams numActiveWarrants(final int numActiveWarrants);    
    public void incActiveWarrants();
    public void decActiveWarrants();

    public int numTotalWarrants();
    public UndOutputParams numTotalWarrants(final int numTotalWarrants);
    public void incTotalWarrants();
    
    default public void copyTo(final UndOutputParams o) {
        o.numActiveWarrants(this.numActiveWarrants());
        o.numTotalWarrants(this.numTotalWarrants());
    }

}
