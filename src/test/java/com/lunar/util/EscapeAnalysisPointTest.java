package com.lunar.util;

import java.io.IOException;

/**
 * Test with these jvm options.  Runs this separately on command line; running in eclipse has unexpected gc behaviour
 * -server 
 * -XX:BCEATraceLevel=3 
 * -XX:+PrintCompilation 
 * -XX:+UnlockDiagnosticVMOptions 
 * -XX:+PrintInlining 
 * -verbose:gc 
 * -XX:MaxInlineSize=256 
 * -XX:FreqInlineSize=1024 
 * -XX:MaxBCEAEstimateSize=1024 
 * -XX:MaxInlineLevel=22 
 * -XX:CompileThreshold=10 
 * -Xmx4g 
 * -Xms4g
 * @author wongca
 *
 */
public class EscapeAnalysisPointTest {
	public static class Point {
		private final int x, y;
		public Point(int x, int y) {
			this.x = x; this.y = y;
		}
		@Override public String toString() {
			final StringBuilder sb = new StringBuilder()
					.append("(")
							.append(x)
							.append(", ")
							.append(y)
							.append(")");
			return sb.toString();
		}
	}

	public static void main(String[] args)  throws IOException {
		Point p = new Point(100, 200);
		sum(p);
		System.gc();
		System.out.println("Press any key to continue");
		System.in.read();
		long sum = sum(p);
		System.out.println(sum);
		System.out.println("Press any key to continue2");
		System.in.read();
		sum = sum(p);
		System.out.println(sum);
		System.out.println("Press any key to exit");
		System.in.read();
	}
	
	private static long sum(Point p) {
		long sumLen = 0;
		for (int i = 0; i < 1_000_000; i++) {
			sumLen += p.toString().length();
		}
		return sumLen;
	}
}
