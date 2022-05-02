package performance.baseline0;

import org.HdrHistogram.Histogram;

public final class PerfHelper {
    private static final int BUFFER_SIZE = 1024;
    private static final int FRAME_SIZE = 64;
    private static long ITERATIONS = 3L * 1000L * 1000L; 
    private static final long PAUSE_NANOS = 10000L;

    private int bufferSize;
    private int frameSize;
    private long iterations;
    private long pauseTimeNs;
    
    public PerfHelper(){
    	this.bufferSize = BUFFER_SIZE;
    	this.frameSize = FRAME_SIZE;
    	this.iterations = ITERATIONS;
    	this.pauseTimeNs = PAUSE_NANOS;
    }
    
    public Histogram createHistogram(){
    	return new Histogram(10000000000L, 4);
    }

    public int frameSize(){
    	return frameSize;
    }
    
    public PerfHelper frameSize(int value){
    	this.frameSize = value;
    	return this;
    }

    public int bufferSize(){
    	return bufferSize;
    }
    
    public PerfHelper bufferSize(int value){
    	this.bufferSize = value;
    	return this;
    }

    public long iterations(){
    	return iterations;
    }
    
    public PerfHelper iterations(long value){
    	this.iterations = value;
    	return this;
    }
    
    public long pauseTimeNs(){
    	return pauseTimeNs;
    }
    
    public PerfHelper pauseTimeNs(long value){
    	this.pauseTimeNs = value;
    	return this;
    }
}
