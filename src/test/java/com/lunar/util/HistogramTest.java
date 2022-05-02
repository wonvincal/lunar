package com.lunar.util;

import org.HdrHistogram.Histogram;
import org.junit.Test;

public class HistogramTest {

	@Test
	public void testHistogramForOneItem(){
		final Histogram histogram = new Histogram(10000000000L, 4);
		histogram.reset();
		//histogram.recordValueWithExpectedInterval(8984147, 1000);
		histogram.recordValueWithExpectedInterval(12345, 1000);
		histogram.recordValueWithExpectedInterval(750, 1000);
		histogram.recordValueWithExpectedInterval(250, 1000);
        histogram.outputPercentileDistribution(System.out, 1, 1000.0);
	}
}
