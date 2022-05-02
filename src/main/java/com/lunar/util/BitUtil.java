package com.lunar.util;

import org.agrona.DirectBuffer;

public class BitUtil {
    /** 2<sup>32</sup> &middot; &phi;, &phi; = (&#x221A;5 &minus; 1)/2. */
    private static final int INT_PHI = 0x9E3779B9;
    /** The reciprocal of {@link #INT_PHI} modulo 2<sup>32</sup>. */
    private static final int INV_INT_PHI = 0x144cbc89;
    /** 2<sup>64</sup> &middot; &phi;, &phi; = (&#x221A;5 &minus; 1)/2. */
    private static final long LONG_PHI = 0x9E3779B97F4A7C15L;
    /** The reciprocal of {@link #LONG_PHI} modulo 2<sup>64</sup>. */
    private static final long INV_LONG_PHI = 0xf1de83e19937733dL;

	/** 
	 * From {@code FastUtilHashCommon}
	 * 
	 * Quickly mixes the bits of an integer.
	 * 
	 * <p>This method mixes the bits of the argument by multiplying by the golden ratio and 
	 * xorshifting the result. It is borrowed from <a href="https://github.com/OpenHFT/Koloboke">Koloboke</a>, and
	 * it has slightly worse behaviour than {@link #murmurHash3(int)} (in open-addressing hash tables the average number of probes
	 * is slightly larger), but it's much faster.
	 * 
	 * @param x an integer.
	 * @return a hash value obtained by mixing the bits of {@code x}.
	 * @see #invMix(int)
	 */	
	public final static int mix( final int x ) {
		final int h = x * INT_PHI;
		return h ^ (h >>> 16);
	}


	/** 
	 * From {@code FastUtilHashCommon}
	 * 
	 * Quickly mixes the bits of a long integer.
	 * 
	 * <p>This method mixes the bits of the argument by multiplying by the golden ratio and 
	 * xorshifting twice the result. It is borrowed from <a href="https://github.com/OpenHFT/Koloboke">Koloboke</a>, and
	 * it has slightly worse behaviour than {@link #murmurHash3(long)} (in open-addressing hash tables the average number of probes
	 * is slightly larger), but it's much faster. 
	 * 
	 * @param x a long integer.
	 * @return a hash value obtained by mixing the bits of {@code x}.
	 */	
	public final static long mix( final long x ) {
		long h = x * LONG_PHI;
		h ^= h >>> 32;
		return h ^ (h >>> 16);
	}

	/** 
	 * From {@code FastUtilHashCommon}
	 * 
	 * The inverse of {@link #mix(long)}. This method is mainly useful to create unit tests.
	 * 
	 * @param x a long integer.
	 * @return a value that passed through {@link #mix(long)} would give {@code x}.
	 */	
	public final static long invMix( long x ) {
		x ^= x >>> 32;
		x ^= x >>> 16;
		return ( x ^ x >>> 32 ) * INV_LONG_PHI; 
	}


	/** 
	 * From {@code FastUtilHashCommon}
	 * 
	 * Returns the hash code that would be returned by {@link Float#hashCode()}.
	 *
	 * @param f a float.
	 * @return the same code as {@link Float#hashCode() new Float(f).hashCode()}.
	 */

	final public static int float2int( final float f ) {
		return Float.floatToRawIntBits( f );
	}

	/** 
	 * From {@code FastUtilHashCommon}
	 * 
	 * Returns the hash code that would be returned by {@link Double#hashCode()}.
	 *
	 * @param d a double.
	 * @return the same code as {@link Double#hashCode() new Double(f).hashCode()}.
	 */

	final public static int double2int( final double d ) {
		final long l = Double.doubleToRawLongBits( d );
		return (int)( l ^ ( l >>> 32 ) );
	}

	/**
	 * From {@code FastUtilHashCommon}
	 *  
	 * Returns the hash code that would be returned by {@link Long#hashCode()}.
	 * 
	 * @param l a long.
	 * @return the same code as {@link Long#hashCode() new Long(f).hashCode()}.
	 */
	final public static int long2int( final long l ) {
		return (int)( l ^ ( l >>> 32 ) );
	}
	
	/** 
	 * From {@code FastUtilHashCommon}
	 *  
	 * The inverse of {@link #mix(int)}. This method is mainly useful to create unit tests.
	 * 
	 * @param x an integer.
	 * @return a value that passed through {@link #mix(int)} would give {@code x}.
	 */	
	public final static int invMix( final int x ) {
		return ( x ^ x >>> 16 ) * INV_INT_PHI; 
	}
	
	/** 
	 * From {@code FastUtilHashCommon}
	 * 
	 * Return the least power of two greater than or equal to the specified value.
	 * 
	 * <p>Note that this function will return 1 when the argument is 0.
	 * 
	 * @param x an integer smaller than or equal to 2<sup>30</sup>.
	 * @return the least power of two greater than or equal to the specified value.
	 */
	public static int nextPowerOfTwo( int x ) {
		if ( x == 0 ) return 1;
		x--;
		x |= x >> 1;
		x |= x >> 2;
		x |= x >> 4;
		x |= x >> 8;
		return ( x | x >> 16 ) + 1;
	}

	/*
	 * From {@code FastUtilHashCommon}
	 * 
	 * Return the least power of two greater than or equal to the specified value.
	 * 
	 * <p>Note that this function will return 1 when the argument is 0.
	 * 
	 * @param x a long integer smaller than or equal to 2<sup>62</sup>.
	 * @return the least power of two greater than or equal to the specified value.
	 */
	public static long nextPowerOfTwo( long x ) {
		if ( x == 0 ) return 1;
		x--;
		x |= x >> 1;
		x |= x >> 2;
		x |= x >> 4;
		x |= x >> 8;
		x |= x >> 16;
		return ( x | x >> 32 ) + 1;
	}
	
	public static int returnOneIfNonZero(int x){
		return (((-x) | x) >> (31)) & 1;
	}
	
	public static int max(int x, int y){
		return Integer.max(x, y);
	}
	
	public static long max(long x, long y){
		return Long.max(x, y);
	}
	
	public static int min(int x, int y){
		return Integer.min(x, y);
	}
	
	/**
	 * Slowest
	 * @param a
	 * @param b
	 * @return 0 or 1
	 */
	public static int isGreater(int a, int b){
		int diff = a ^ b;
		diff |= diff >> 1;
		diff |= diff >> 2;
		diff |= diff >> 4;
		diff |= diff >> 8;
		diff |= diff >> 16;

		//1+ on GT, 0 otherwise.
		diff &= ~(diff >> 1) | 0x80000000;
		diff &= (a ^ 0x80000000) & (b ^ 0x7fffffff);

		//flatten back to range of 0 or 1.
		diff |= diff >> 1;
		diff |= diff >> 2;
		diff |= diff >> 4;
		diff |= diff >> 8;
		diff |= diff >> 16;
		diff &= 1;

		return diff;
	}
	
	/**
	 * Fastest
	 * @param a
	 * @param b
	 * @return
	 */
	public static int isGreaterCond(int a, int b){
		return (a > b) ? 1 : 0;
	}

	/**
	 * Between {@link isGreaterCond} and {@link isGreater}
	 * @param a
	 * @param b
	 * @return
	 */
	public static int isGreaterThanSubAndShift(int a, int b){
		return (b - a) >>> 31; // if (b >= a), 0, else 1.  if a < b, 0, else 1.
	}
	
	/**
	 * Compare two DirectBuffers
	 * @param one
	 * @param oneOffset
	 * @param two
	 * @param twoOffset
	 * @param length
	 * @return
	 */
	public static boolean compare(DirectBuffer one, int oneOffset, DirectBuffer two, int twoOffset, int length){
		if (oneOffset + length > one.capacity()){
			return false;
		}
		if (twoOffset + length > two.capacity()){
			return false;
		}
		byte[] oneArray = new byte[length];
		one.getBytes(oneOffset, oneArray, 0, length);
		byte[] twoArray = new byte[length];
		two.getBytes(twoOffset, twoArray, 0, length);
		for (int i = 0; i < length; i++){
			if (oneArray[i] != twoArray[i]){
				return false;
			}
		}
		return true;
	}
	
	public static boolean isPowerOfTwo(final int value)
	{
		return value > 0 && ((value & (~value + 1)) == value);
	}
	
	public static int compare(int o1, int o2){
		return Integer.signum(o1 - o2);
	}
	
	public static int compare(long o1, long o2){
		return Long.signum(o1 - o2);
	}

}
