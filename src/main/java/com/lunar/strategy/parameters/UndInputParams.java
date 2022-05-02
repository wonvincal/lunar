package com.lunar.strategy.parameters;

public interface UndInputParams {
    public interface Validator {
        boolean validateSizeThreshold(final UndInputParams params, final int sizeThreshold);
        boolean validateVelocityThreshold(final UndInputParams params, final long velocityThreshold);
    }
    
    public interface PostUpdateHandler {
        void onUpdatedSizeThreshold(final UndInputParams params);
        void onUpdatedVelocityThreshold(final UndInputParams params);
    }

    public int sizeThreshold();
    public UndInputParams sizeThreshold(final int sizeThreshold);
    public UndInputParams userSizeThreshold(final int sizeThreshold);
    
    public long velocityThreshold();
    public UndInputParams velocityThreshold(final long velocityThreshold);
    public UndInputParams userVelocityThreshold(final long velocityThreshold);

    default public void copyTo(final UndInputParams o) {
        o.sizeThreshold(this.sizeThreshold());
        o.velocityThreshold(this.velocityThreshold());
    }
}
