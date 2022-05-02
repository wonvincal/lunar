package com.lunar.marketdata;

import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

public class ArrayPrimaryHashSecondarySpreadTable implements SpreadTable {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(ArrayPrimaryHashSecondarySpreadTable.class);
	private static final int NUM_CONVERTERS = 2;
	private static final int ARRAY_BASED_CONVERTER_INDEX = 1;
	private static final int HASHMAP_BASED_CONVERTER_INDEX = 0;
	private final int decimalPlace;
	private final int scale;
	private final byte id;
	private final String name;
	private final TreeMap<Integer, SpreadTableDetails> detailsByRange; 
	private final int startingPriceForHashBased;
	private final int startingTickLevelForHashBased;
	private final Converter[] converters;
	
	ArrayPrimaryHashSecondarySpreadTable(byte id, String name, int decimalPlace, int startingPriceForHashBased, SpreadTableDetails... details){
		if (details.length <= 0){
			throw new IllegalArgumentException("there must be at least one spread table details entry");
		}

		this.id = id;
		this.name = name;
		this.decimalPlace = decimalPlace;
		this.scale = (int)Math.pow(10, decimalPlace);
		this.startingPriceForHashBased = startingPriceForHashBased;
		this.detailsByRange = new TreeMap<Integer, SpreadTableDetails>();
		this.converters = new Converter[NUM_CONVERTERS];
		
		for (int i = 0; i < details.length; i++){
			detailsByRange.put(details[i].fromPrice(), details[i]);
		}
		
		int currentPrice = detailsByRange.firstKey();
		int expectedTickCount = 0;
		for (Entry<Integer, SpreadTableDetails> entry : detailsByRange.entrySet()) {
			int fromPrice = entry.getValue().fromPrice();
			int toPrice = entry.getValue().toPriceExclusive();
			// gap detection
			if (currentPrice != fromPrice){
				throw new IllegalArgumentException("gap detected in spread table details entries; from (" + currentPrice + ") to (" + fromPrice + ")");
			}
			currentPrice = toPrice;
			if (fromPrice > startingPriceForHashBased || toPrice > startingPriceForHashBased){
				expectedTickCount += ((toPrice - fromPrice) / entry.getValue().spread());
			}
		}

		// generate tick-to-price and price-to-tick mapping
		converters[ARRAY_BASED_CONVERTER_INDEX] = new ArrayBasedConverter(startingPriceForHashBased,
																		  detailsByRange);
		converters[HASHMAP_BASED_CONVERTER_INDEX] = new HashMapBasedConverter(expectedTickCount,
																			  startingPriceForHashBased,
																			  converters[ARRAY_BASED_CONVERTER_INDEX].maxTickLevel() + 1, 
																			  detailsByRange);
		this.startingTickLevelForHashBased = converters[ARRAY_BASED_CONVERTER_INDEX].maxTickLevel() + 1;
	}
	
	static interface Converter {
		int priceToTick(int price);
		int priceToTickSize(int price);
		int tickLevelToPrice(int tickLevel);
		int maxTickLevel();
	}
	
	static class ArrayBasedConverter implements Converter {
		private final int priceOffset;
		private final int[] priceToTickLevel;
		private final int[] priceToTickSize;
		private final int[] tickLevelToPrice;
		private final int maxTickLevel;

		public static ArrayBasedConverter of(int toPrice, TreeMap<Integer, SpreadTableDetails> details){
			return new ArrayBasedConverter(toPrice, details);
		}
		
		private ArrayBasedConverter(int toPriceExclusive, TreeMap<Integer, SpreadTableDetails> details){
			int fromPrice = details.firstKey();
			int spread = details.firstEntry().getValue().spread();
			int numLevels = (toPriceExclusive - fromPrice) / spread;
			
			this.priceOffset = -fromPrice;
			this.priceToTickLevel = new int[numLevels];
			this.priceToTickSize = new int[numLevels];

			int currentTickLevel = 0;
			for (SpreadTableDetails entry : details.values()){
				for (int i = entry.fromPrice(); i < entry.toPriceExclusive(); i += entry.spread()){
					currentTickLevel++;
					if (i < toPriceExclusive){
						if (entry.spread() % spread != 0){
							throw new IllegalArgumentException("invalid value: entry spread(" + entry.spread() + ") is not divisble by smallest spread(" + spread + ")");
						}
						int end = Math.min(i + entry.spread(), toPriceExclusive);
						for (int j = i; j < end; j += spread){
							this.priceToTickLevel[j + this.priceOffset] = currentTickLevel;
							this.priceToTickSize[j + this.priceOffset] = entry.spread();
						}
					}
				}
			}
			maxTickLevel = this.priceToTickLevel[this.priceToTickLevel.length - 1];
			this.tickLevelToPrice = new int[maxTickLevel + 1];
			currentTickLevel = 0;
			for (int i = 0; i < this.priceToTickLevel.length; i++){
				if (currentTickLevel != this.priceToTickLevel[i]){
					currentTickLevel = this.priceToTickLevel[i];
					this.tickLevelToPrice[currentTickLevel] = i - this.priceOffset;
				}
			}
		}
		
		@Override
		public int priceToTick(int price){
			return this.priceToTickLevel[price + this.priceOffset];
		}
		
		@Override
		public int priceToTickSize(int price){
			return this.priceToTickSize[price + this.priceOffset];
		}
		
		@Override
		public int tickLevelToPrice(int tickLevel){
			return this.tickLevelToPrice[tickLevel];
		}
		
		@Override
		public int maxTickLevel() {
			return maxTickLevel;
		}
	}
	
	static class HashMapBasedConverter implements Converter {
		private final Int2IntLinkedOpenHashMap priceToTickLevel;
		private final Int2IntLinkedOpenHashMap priceToTickSize;
		private final Int2IntLinkedOpenHashMap tickLevelToPrice;
		private final int maxTickLevel;

		public static HashMapBasedConverter of(int expected, int start, int initialTickLevel, TreeMap<Integer, SpreadTableDetails> details){
			return new HashMapBasedConverter(expected, start, initialTickLevel, details);
		}
		
		private HashMapBasedConverter(int expected, int start, int initialTickLevel, TreeMap<Integer, SpreadTableDetails> details){
			priceToTickLevel = new Int2IntLinkedOpenHashMap(expected);
			priceToTickSize = new Int2IntLinkedOpenHashMap(expected);
			tickLevelToPrice = new Int2IntLinkedOpenHashMap(expected);

			int tickLevel = initialTickLevel;
			
			// Loop thru each SpreadTableDetails until we've reached our 'start' level
			for (Entry<Integer, SpreadTableDetails> entry : details.entrySet()) {
				SpreadTableDetails item = entry.getValue();
				int currentPrice = item.fromPrice();
				while (currentPrice < item.toPriceExclusive()){
					if (currentPrice >= start){
						priceToTickLevel.put(currentPrice, tickLevel); 
						priceToTickSize.put(currentPrice, item.spread());
						tickLevelToPrice.put(tickLevel, currentPrice);
						tickLevel++;
					}
					currentPrice += item.spread();
				}
			}
			this.maxTickLevel = (tickLevel - 1);
		}
		
		@Override
		public int priceToTick(int price){
			int result = this.priceToTickLevel.get(price);
			if (result != this.priceToTickLevel.defaultReturnValue()){
				return result;
			}
			throw new IllegalArgumentException("price(" + price + ") not found in this spread table");
		}
				
		@Override
		public int priceToTickSize(int price){
			int result = this.priceToTickSize.get(price);
			if (result != this.priceToTickSize.defaultReturnValue()){
				return result;
			}
			throw new IllegalArgumentException("price(" + price + ") not found in this spread table");
		}

		@Override
		public int tickLevelToPrice(int tickLevel){
			int result = this.tickLevelToPrice.get(tickLevel);
			if (result != this.tickLevelToPrice.defaultReturnValue()){
				return result;
			}
			throw new IllegalArgumentException("tickLevel(" + tickLevel + ") not found in this spread table");
		}

		@Override
		public int maxTickLevel() {
			return maxTickLevel;
		}
	}
	
	@Override
	public int priceToTick(int price){
		return this.converters[(price - this.startingPriceForHashBased) >>> 31].priceToTick(price);
	}

	@Override
	public int priceToTickSize(int price){
		return this.converters[(price - this.startingPriceForHashBased) >>> 31].priceToTickSize(price);
	}

	@Override
	public int tickToPrice(int tickLevel){
		return this.converters[(tickLevel - this.startingTickLevelForHashBased) >>> 31].tickLevelToPrice(tickLevel);
	}
	
	@Override
	public String toString() {
		return "id:" + this.id + ", name:" + name;
	}
	
	@Override
	public SpreadTableDetails detailsAtPrice(int price){
		return detailsByRange.floorEntry(price).getValue();
	}
	
	@Override
	public int decimalPlace(){
		return decimalPlace;
	}
	
	@Override
	public int scale() {
		return scale;
	}
	
	@Override
	public int maxLevel(){
		int maxValue = 0;
		for (int i = 0; i < this.converters.length; i++){
			maxValue = Math.max(maxValue, this.converters[i].maxTickLevel());
		}
		return maxValue;
	}

	@Override
	public byte id() {
		return id;
	}
}
