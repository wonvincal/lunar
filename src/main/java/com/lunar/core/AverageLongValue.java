package com.lunar.core;

public class AverageLongValue {
    private final long invalidValue;
    private final int scalingFactor;
    private long totalCount;
    private long totalValue;
    private long averageValue;
    
    public AverageLongValue() {
        this(1, 0);
    }
    
    public AverageLongValue(final int scalingFactor) {
        this(scalingFactor, 0);
    }

    public AverageLongValue(final int scalingFactor, final long invalidValue) {
        this.scalingFactor = scalingFactor;
        this.invalidValue = invalidValue;
    }
    
    public void reset() {
        this.totalCount = 0;
        this.totalValue = 0;
        this.averageValue = this.invalidValue;
    }
    
    public long totalCount() {
        return totalCount;
    }
    
    public long totalValue() {
        return totalValue;
    }
    
    public long averageValue() {
        return averageValue;
    }
    
    public boolean isValidAverage() {
        return averageValue != this.invalidValue;
    }
    
    public long calcAverage() {
        final long denom = totalCount / scalingFactor;
        if (totalValue == 0) {
            return 0;
        }
        if (denom != 0) {
            averageValue = totalValue / denom;
        }
        return invalidValue;
    }
    
    public void addValueNoCalc(final long value) {
        totalValue += value;
        totalCount++;
    }
    
    public void addValue(final long value) {
        addValueNoCalc(value);
        calcAverage();
    }

    public void addValueNoCalc(final long value, final long count) {
        this.totalValue += value;
        this.totalCount += count;
    }

    public void addValue(final long value, final long count) {
        addValueNoCalc(value, count);
        calcAverage();
    }
    
    public void copyTo(final AverageLongValue other) {
        other.totalCount = this.totalCount;
        other.totalValue = this.totalValue;
        other.calcAverage();
    }

}
