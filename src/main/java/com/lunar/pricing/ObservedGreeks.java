package com.lunar.pricing;

public class ObservedGreeks {
//	static final Logger LOG = LogManager.getLogger(ObservedGreeks.class);
	private final Greeks greeks;
	private long updatedTimeNs;
	private long partialAdj;
	private boolean isReady;
	
	public static ObservedGreeks of(final long secSid){
		final Greeks greeks = Greeks.of(secSid);
		return new ObservedGreeks(greeks);
	}
	
	public ObservedGreeks(Greeks greeks){
		this.greeks = greeks;
	}

    public long secSid() {
        return greeks.secSid();
    }
    
    public int delta() {
        return greeks.delta();
    }
    
    public int gamma() {
        return greeks.gamma();
    }
    
    public int vega() {
        return greeks.vega();
    }
    
    public int impliedVol() {
        return greeks.impliedVol();
    }
    
    public int refSpot() {
        return greeks.refSpot();
    }
    
    public long updatedTimeNs() {
        return updatedTimeNs;
    }
    
    public void clear(){
    	this.greeks.clear();
    }
    
    public boolean isReady(){
    	return isReady;
    }
    
    public boolean hasRefSpot(){
    	return this.greeks.refSpot() != Greeks.NULL_VALUE;
    }
    
    public boolean hasDelta(){
    	return this.greeks.delta() != Greeks.NULL_VALUE;
    }

    public void merge(long nanoOfDay, Greeks g){
    	int refSpot = g.refSpot();
//    	if (g.gamma() == Greeks.NULL_VALUE){
//    		refSpot = 1; 
//    		LOG.info("Force set refSpot to 1");
//    	}
//    	
    	this.greeks.delta(g.delta())
    		.gamma(g.gamma())
    		.impliedVol(g.impliedVol())
    		.refSpot(refSpot)
    		.vega(g.vega());
    	updatedTimeNs = nanoOfDay;
    	partialAdj = -g.refSpot() * 1_000L * g.gamma()  + g.delta() * 1_000_000L;
    	isReady = (updatedTimeNs != 0 && g.delta() != 0 );
    }
    
    public long calculateAdjDelta(long value){
    	return (value * greeks.gamma() + partialAdj) / 1_000_000L;
    }
}
