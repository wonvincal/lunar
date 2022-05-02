package com.lunar.util;

import java.io.IOException;

public class EscapeAnalysisLongNonOverlappingIntervalTreeTest {

	public static void main(String[] args) throws IOException{
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		
		// Add N intervals
		tree.add(100, 110, 900);
		tree.add(115, 125, 900);
		tree.add(128, 140, 900);
		tree.add(142, 160, 900);
		tree.add(163, 173, 900);
		tree.add(175, 180, 900);

		// Search X times
		int times = 1000000;
		int count = 0;
		LongNonOverlappingIntervalTree.Interval outInterval = LongNonOverlappingIntervalTree.Interval.of();
		for (int i = 0; i < times; i++){
			if (tree.search(129, outInterval)){
				count++;
			}
		}
		System.out.println("Count: " + count);
		System.out.println("Enter to continue (run jmap to check heap)");
		System.in.read();
		
		for (int i = 0; i < times; i++){
			if (tree.search(129, outInterval)){
				count++;
			}
		}
		System.out.println("Count: " + count);
		System.out.println("Enter to continue (run jmap to check heap)");
		System.in.read();

		for (int i = 0; i < times; i++){
			if (tree.search(129, outInterval)){
				count++;
			}
		}
		System.out.println("Count: " + count);
		System.out.println("Enter to continue (run jmap to check heap)");
		System.in.read();
	}
}
