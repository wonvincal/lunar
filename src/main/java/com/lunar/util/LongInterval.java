package com.lunar.util;

public class LongInterval {
	public static final long NULL_INTERVAL_BEGIN_VALUE = Long.MAX_VALUE;
	public static final long NULL_INTERVAL_END_VALUE = Long.MIN_VALUE;
	public static final int NULL_DATA_VALUE = Integer.MIN_VALUE;
	private long begin = NULL_INTERVAL_BEGIN_VALUE;
	private long endExclusive = NULL_INTERVAL_END_VALUE;
	private int data = NULL_DATA_VALUE;
	private long theoBucketSize = NULL_INTERVAL_BEGIN_VALUE;
	private long last = NULL_INTERVAL_BEGIN_VALUE;
	
	public static LongInterval of(){
		return new LongInterval(NULL_INTERVAL_BEGIN_VALUE, NULL_INTERVAL_END_VALUE, NULL_DATA_VALUE, NULL_INTERVAL_BEGIN_VALUE);
	}

	public static LongInterval of(ImmutableLongInterval interval){
		return new LongInterval(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());
	}
	
	public static LongInterval of(long begin, long end, int data, long theoBucketSize){
		return new LongInterval(begin, end, data, theoBucketSize);
	}
	
	LongInterval(long begin, long endExclusive, int data, long theoBucketSize){
		this.begin = begin;
		this.endExclusive = endExclusive;
		this.data = data;
		this.theoBucketSize = theoBucketSize;
	}
	
	public long begin(){
		return begin;
	}
	
	public long endExclusive(){
		return endExclusive;
	}

	public int data(){
		return data;
	}

	public long theoBucketSize(){
		return theoBucketSize;
	}

	public LongInterval begin(long value){
		this.begin = value;
		return this;
	}
	
	public LongInterval endExclusive(long value){
		this.endExclusive = value;
		return this;
	}

	public LongInterval data(int value){
		this.data = value;
		return this;
	}

	public long last(){
		return this.last;
	}
	
	public LongInterval last(long value){
		this.last = value;
		return this;
	}
	
	public LongInterval theoBucketSize(long value){
		theoBucketSize = value;
		return this;
	}

	public boolean hasValidRange(){
		return this.begin < this.endExclusive;
	}
	
	public boolean isEmpty(){
		return this.data == NULL_DATA_VALUE || this.begin == NULL_INTERVAL_BEGIN_VALUE || this.endExclusive == NULL_INTERVAL_END_VALUE;
	}
	
	public void clear(){
		this.begin = NULL_INTERVAL_BEGIN_VALUE;
		this.endExclusive = NULL_INTERVAL_END_VALUE;
		this.data = NULL_DATA_VALUE;
		this.theoBucketSize = NULL_INTERVAL_BEGIN_VALUE;
		this.last = NULL_INTERVAL_BEGIN_VALUE;
	}

	public LongInterval set(long begin, long endExclusive, int data, long theoBucketSize5Dp){
		this.begin = begin;
		this.endExclusive = endExclusive;
		this.data = data;
		this.theoBucketSize = theoBucketSize5Dp;
		this.last = NULL_INTERVAL_BEGIN_VALUE;
		return this;
	}

	public LongInterval set(long begin, long endExclusive, int data){
		this.begin = begin;
		this.endExclusive = endExclusive;
		this.data = data;
		this.theoBucketSize = NULL_INTERVAL_BEGIN_VALUE;
		this.last = NULL_INTERVAL_BEGIN_VALUE;
		return this;
	}
	
	public LongInterval setIntervalAndLast(long begin, long endExclusive, int data, long last){
		this.begin = begin;
		this.endExclusive = endExclusive;
		this.data = data;
		this.theoBucketSize = NULL_INTERVAL_BEGIN_VALUE;
		this.last = last;
		return this;
	}

	public LongInterval copyFrom(ImmutableLongInterval interval){
		this.begin = interval.begin();
		this.endExclusive = interval.endExclusive();
		this.data = interval.data();
		this.theoBucketSize = interval.theoBucketSize();
		this.last = interval.last();
		return this;
	}

	public LongInterval copyFrom(LongInterval interval){
		this.begin = interval.begin;
		this.endExclusive = interval.endExclusive;
		this.data = interval.data;
		this.theoBucketSize = interval.theoBucketSize;
		this.last = interval.last;
		return this;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (begin ^ (begin >>> 32));
		result = prime * result + (int) (data ^ (data >>> 32));
		result = prime * result + (int) (endExclusive ^ (endExclusive >>> 32));
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LongInterval other = (LongInterval) obj;
		if (begin != other.begin)
			return false;
		if (data != other.data)
			return false;
		if (endExclusive != other.endExclusive)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "begin: " + begin + ", end: " + endExclusive + ", data: " + data + ((theoBucketSize != NULL_INTERVAL_BEGIN_VALUE) ? (", theoBucketSize: " + theoBucketSize) : ", theoBucketSize not avail");
	}
}
