package com.lunar.strategy.parameters;

public interface IssuerUndOutputParams {
	public long undTradeVol();
	public IssuerUndOutputParams undTradeVol(final long value);
	
	public long undDeltaShares();
	public IssuerUndOutputParams undDeltaShares(final long undDeltaShares);
	
	public long pendingUndDeltaShares();
	public IssuerUndOutputParams pendingUndDeltaShares(final long pendingUndDeltaShares);	
	
    default public void copyTo(final IssuerUndOutputParams o) {
        o.undTradeVol(this.undTradeVol());
        o.undDeltaShares(this.undDeltaShares());
        o.pendingUndDeltaShares(this.pendingUndDeltaShares());
    }
}
