package com.lunar.strategy.parameters;

public interface IssuerUndInputParams {
	public interface Validator {
		public boolean validateUndTradeVolThreshold(final IssuerUndInputParams params, final long value);
	}

    public interface PostUpdateHandler {
        public void onUpdatedUndTradeVolThreshold(IssuerUndInputParams params);
    }
    
    public long undTradeVolThreshold();
    public IssuerUndInputParams undTradeVolThreshold(final long undTradeVolThreshold);
    public IssuerUndInputParams userUndTradeVolThreshold(final long undTradeVolThreshold);
    
    default public void copyTo(final IssuerUndInputParams o){
    	o.undTradeVolThreshold(this.undTradeVolThreshold());
    }
}
