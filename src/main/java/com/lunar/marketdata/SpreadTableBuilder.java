package com.lunar.marketdata;

import com.lunar.message.io.sbe.SecurityType;

/**
 * TODO modify logic to get source from database
 * @author Calvin
 *
 */
public class SpreadTableBuilder {
	private static final SpreadTable hkse01;
	private static final SpreadTable hkse02;
	private static final SpreadTable[] SPREAD_TABLES;
	public static final int NUM_SUPPORTED_SPREAD_TABLES;
	
	static {
		NUM_SUPPORTED_SPREAD_TABLES = 2;
		SPREAD_TABLES = new SpreadTable[NUM_SUPPORTED_SPREAD_TABLES];
		
		// https://www.hkex.com.hk/eng/rulesreg/traderules/sehk/Documents/sch-2_eng.pdf
		SpreadTableDetails details1 = SpreadTableDetails.of(10, 250, 1);
		SpreadTableDetails details2 = SpreadTableDetails.of(250, 500, 5, details1);
		SpreadTableDetails details3 = SpreadTableDetails.of(500, 10_000, 10, details2);
		SpreadTableDetails details4 = SpreadTableDetails.of(10_000, 200_00, 20, details3);
		SpreadTableDetails details5 = SpreadTableDetails.of(20_000, 100_000, 50, details4);
		SpreadTableDetails details6 = SpreadTableDetails.of(100_000, 200_000, 100, details5);
		SpreadTableDetails details7 = SpreadTableDetails.of(200_000, 500_000, 200, details6);
		SpreadTableDetails details8 = SpreadTableDetails.of(500_000, 1_000_000, 500, details7);
		SpreadTableDetails details9 = SpreadTableDetails.of(1_000_000, 2_000_000, 1000, details8);
		SpreadTableDetails details10 = SpreadTableDetails.of(2_000_000, 5_000_000, 2000, details9);
		SpreadTableDetails details11 = SpreadTableDetails.of(5_000_000, 9_995_000, 5000, details10);
		
		hkse01 = SpreadTable.of((byte)0, 
				   "HKSE 01",
				   3,
				   200_000,
				   details1,
				   details2,
				   details3,
				   details4,
				   details5,
				   details6,
				   details7,
				   details8,
				   details9,
				   details10,
				   details11
				   );
	
		SPREAD_TABLES[0] = hkse01;
		
		hkse02 = SpreadTable.of((byte)1, "HKSE 02", 0);
		SPREAD_TABLES[1] = hkse02;		
	}
	
	public static SpreadTable getById(int id){
		return SPREAD_TABLES[id];
	}
	
	public static SpreadTable get(){
		return hkse01;
	}
	
	public static SpreadTable get(SecurityType secType){
		switch (secType){
		case FUTURES:
		case INDEX:
			return hkse02;
		default:
			return hkse01;
		}
	}

}
