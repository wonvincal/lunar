package com.lunar.order;

import com.lunar.util.BitUtil;

public class OrderChannelIdFinder {
	private int mask;
	OrderChannelIdFinder(int numChannels){
		if (!BitUtil.isPowerOfTwo(numChannels)){
			throw new IllegalArgumentException("Number of channels must be a power of two [now:" + numChannels + "]");
		}
		this.mask = numChannels - 1;
	}
	public int fromSecSid(long secSid){
		// if numChannels = 8, mask = 111
		// result = 0 to 7
		// if numChannels = 2, mask = 1
		// result = 0 to 1
		return (int)secSid & this.mask;
	}
}
