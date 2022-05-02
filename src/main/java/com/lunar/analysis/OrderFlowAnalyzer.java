package com.lunar.analysis;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.ChartData;
import com.lunar.entity.ChartData.VolumeAtPrice;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;
import com.lunar.message.io.sbe.VolumeClusterType;
import com.lunar.order.Boobs;
import com.lunar.service.ServiceConstant;
import com.lunar.util.BitUtil;

import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Order flow produces two types of outputs
 * 1) chart data point
 * 2) order flow signal
 * 
 * We rely on the time in ticks to group ticks into different windows, as this is the only accurate way.  
 * The problem with this approach is that we cannot produce any ticks if the security has no trade or bid/ask.  
 * How to resolve this?
 * 1) if we use wall clock time, it may not be accurate because a trade update may arrive late due to our own problem.
 * 2) if we use only tick time, we run into problem if there is no update.
 * 
 * Use wall clock tick when there is no tick time.  Once tick time arrives, try to see if we need to fix any previous 
 * distributed data.
 * 
 * On each update, we need to take the wall clock as well, so that we have a good chance of telling how much time has been passed.
 * If we receive a tick time in the past, it is likely that some part of our process is slow.  Ideally, we should
 * 1) go back in time to change the ChartData that was sent.
 * 2) resend all affected ChartData (close of affected window may be impacted)
 * 
 * 
 * - Since there are two types of time
    - Data clock
    - Wall clock
	- Wall clock is not in sync with data clock
    - To do: sync them
	- How to tell if data is late?
    - If clock is synced, we can just check the diff between wall clock and data clock, the difference should be less than 10 milliseconds.
        - If a data arrives too late, we can go back in time to change all impacted chart data, and notify its subscriber to rerequest the data. And log them.
        - If our wall clock is a lot later than data clock, we should use data clock to decide window closing logic.
    - If clock is not synced, we need to capture the diff between wall clock and data clock, and produce a running average.once higher than let say 50% of the window size. Alert us.
 * 
 * Things will be better if we sync the data clock with wall clock.  However, if our window size is 1 second, we should be alright.
 * 
 * TODO
 * Alert if data clock is too off from wall clock
 * 
 * @author wongca
 *
 */
public class OrderFlowAnalyzer {
	static final Logger LOG = LogManager.getLogger(OrderFlowAnalyzer.class);
	public static final long MIN_WINDOW_SIZE_NS = TimeUnit.SECONDS.toNanos(1L);
	public static final double VALUE_AREA_HIGH_THRESHOLD = 0.85;
	public static final double VALUE_AREA_LOW_THRESHOLD = 0.15;
	
	private final long secSid;
	@SuppressWarnings("unused")
	private long lastDataTime;
	private ChartDataHandler chartDataHandler = ChartDataHandler.NULL_CHART_DATA_HANDLER;
	@SuppressWarnings("unused")
	private SignalHandler signalHandler = SignalHandler.NULL_SIGNAL_HANDLER;
	private final DataWindow[] windows;
	
	// TODO Make this immutable
	private static ObjectArrayList<ChartData> NULL_CHART_DATA_LIST = new ObjectArrayList<>();
	
	public static OrderFlowAnalyzer of(long secSid, long windowSizeNs, double[] supportedWindowSizeMultipliers){
		return new OrderFlowAnalyzer(secSid, windowSizeNs, supportedWindowSizeMultipliers);
	}
	
	OrderFlowAnalyzer(long secSid, long windowSizeNs, double[] supportedWindowSizeMultipliers){
		this.secSid = secSid;
		this.windows = new DataWindow[supportedWindowSizeMultipliers.length];		
		for (int i = 0; i < supportedWindowSizeMultipliers.length; i++){
			this.windows[i] = new DataWindow((long)(windowSizeNs * supportedWindowSizeMultipliers[i]));
		}
	}
	
	class DataWindow {
		private final int windowSizeInSec;
		private final ObjectArrayList<ChartData> chartDataList;
		private final long windowSizeNs;
		private int open = ServiceConstant.NULL_CHART_PRICE;
		private int close = ServiceConstant.NULL_CHART_PRICE;
		private int high = Integer.MIN_VALUE;
		private int low = Integer.MAX_VALUE;
		private int currentBestBid;
		@SuppressWarnings("unused")
		private int currentBestAsk;
		private long windowEndWallClockNanoOfDay;
		private final Int2IntRBTreeMap quantityByPrice;
		private final Int2ObjectRBTreeMap<VolumeAtPrice> volAtPrices;
		
		// Sum of (shares bought x share price) / Total shares bought 
		private int vwap = ServiceConstant.NULL_CHART_PRICE;
		private long totalSharesTraded = 0;
		private long totalNotionalTraded = 0;
		
		// Value area high and value area low captured 70% of all volume
		private long totalSharesTradedInThisWindow = 0;
		
		// Volume cluster prices
		
		DataWindow(long windowSizeNs){
			this.windowSizeNs = windowSizeNs;
			this.windowSizeInSec = (int)TimeUnit.NANOSECONDS.toSeconds(this.windowSizeNs);
			this.chartDataList = new ObjectArrayList<>(1024);
			this.quantityByPrice = new Int2IntRBTreeMap();
			this.volAtPrices = new Int2ObjectRBTreeMap<>();
		}
		
		void init(long startWallClockTimeNanoOfDay){
			this.windowEndWallClockNanoOfDay = startWallClockTimeNanoOfDay + this.windowSizeNs;
		}
		
		public void handleBestChange(long wallClockNanoOfDay, long dataTimeNs, Boobs boobs){
			lastDataTime = wallClockNanoOfDay;
			currentBestBid = boobs.bestBid();
			currentBestAsk = boobs.bestAsk();
		}
		
		public void handleMarketDataTrade(long wallClockNanoOfDay, MarketDataTradeSbeDecoder codec){
			lastDataTime = wallClockNanoOfDay;
			
			int price = codec.price();					
			// Change current window
			if (open == ServiceConstant.NULL_CHART_PRICE){
				open = price;
			}
			high = BitUtil.max(high, price);
			low = BitUtil.min(low, price);
			close = price;
			int quantity = codec.quantity();
			quantityByPrice.addTo(price, quantity);
			totalSharesTraded += quantity;
			totalNotionalTraded += (long)quantity * price;
			totalSharesTradedInThisWindow += quantity;
			
			if (price > currentBestBid){
				// buyers lifting offer
				VolumeAtPrice volumeAtPrice = volAtPrices.get(price);
				if (volumeAtPrice == volAtPrices.defaultReturnValue()){
					volAtPrices.put(price, VolumeAtPrice.ofAskQty(price, quantity));
				}
				else {
					volumeAtPrice.addAskQty(quantity);
				}
			}
			else {
				// buyers hitting bid
				VolumeAtPrice volumeAtPrice = volAtPrices.get(price);
				if (volumeAtPrice == volAtPrices.defaultReturnValue()){
					volAtPrices.put(price, VolumeAtPrice.ofBidQty(price, quantity));
				}
				else {
					volumeAtPrice.addBidQty(quantity);
				}				
			}
			
		}

		/**
		 * Let say a security has no update, but we keep on getting clock tick.  A chart data of close will be sent out
		 * from time to time.
		 * @param wallClockNanoOfDay
		 */
		public void handleClockTick(long wallClockNanoOfDay){
			if (wallClockNanoOfDay > this.windowEndWallClockNanoOfDay){
				closeWindow();
				open = close;
				high = close;
				low = close;
				this.windowEndWallClockNanoOfDay += this.windowSizeNs;
			}
			else {
				// If there has been data change and
				// if data change time > previous sent time and
				// wallClockNanoOfDay > previous sent time + X
				if (lastDataTime > previousSentTime && wallClockNanoOfDay > (previousSentTime + ServiceConstant.DEFAULT_ORDER_FLOW_CLOCK_TICK_FREQ_IN_NS)){
					previousSentTime  = wallClockNanoOfDay;
					sendLatestChartData();
				}
			}
		}
		private long previousSentTime = -1;

		private void sendLatestChartData(){
			int val = ServiceConstant.NULL_CHART_PRICE;
			int vah = ServiceConstant.NULL_CHART_PRICE;
			chartDataHandler.handleNewChartData(secSid, 
					this.windowEndWallClockNanoOfDay, 
					this.windowSizeInSec,
					open, 
					close, 
					high, 
					low, 
					0,
					vah,
					val, 
					(open < close) ? VolumeClusterDirectionType.UP : ((open > close) ? VolumeClusterDirectionType.DOWN: VolumeClusterDirectionType.NEUTRAL),
					volAtPrices);
		}
		
		private long count = 0;
		private void closeWindow(){
			// Completed a window
			int currentMaxQty = Integer.MIN_VALUE;
			int currentPriceWithMaxQty = ServiceConstant.NULL_CHART_PRICE;
			long totalSharesCountedInThisWindow = 0;
			long valueAreaLowThreshold = (long) ((double)totalSharesTradedInThisWindow * VALUE_AREA_LOW_THRESHOLD);
			long valueAreaHighThreshold = (long) ((double)totalSharesTradedInThisWindow * VALUE_AREA_HIGH_THRESHOLD);
			int val = ServiceConstant.NULL_CHART_PRICE;
			int vah = ServiceConstant.NULL_CHART_PRICE;
			for (Entry entry : quantityByPrice.int2IntEntrySet()){				
				int price = entry.getIntKey();
				int quantity = entry.getIntValue();
				if (quantity > currentMaxQty){
					// If there is more than on price with max quantity, do something
					currentPriceWithMaxQty = price;
					currentMaxQty = quantity;
				}
				totalSharesCountedInThisWindow += quantity;
				if (totalSharesCountedInThisWindow <= valueAreaLowThreshold){
					val = price;
//					LOG.info("[totalSharesCountedInThisWindow: {}, valThreshold:{}, val:{}]", totalSharesCountedInThisWindow, valueAreaLowThreshold, val);
				}
				if (totalSharesCountedInThisWindow <= valueAreaHighThreshold){
					vah = price; // TODO think carefully of whether to use prev price instead
//					LOG.info("[totalSharesCountedInThisWindow: {}, vahThreshold:{}, vah:{}]", totalSharesCountedInThisWindow, valueAreaHighThreshold, vah);
				}
			}			
			quantityByPrice.clear();
			totalSharesTradedInThisWindow = 0;
			if (totalSharesTraded != 0){
				vwap = (int)(totalNotionalTraded / totalSharesTraded);
			}
			
//			int fakeOpen = 93800;
//			int fakeClose = 94000;
//			int fakeHigh = 94150;
//			int fakeLow = 93650;
//			int fakePoc = 93850;
//			int[] fakeCluster = new int[]{93700, 93750}; 
//			int fakeVal = 93700;
//			int fakeVah = 93900;
//			int fakeVwap = ServiceConstant.NULL_CHART_PRICE;
//			VolumeClusterDirectionType fakeDir = VolumeClusterDirectionType.UP;
//			if (count % 2 == 0){
//				fakeOpen = 94000;
//				fakeClose = 93800;
//				fakeHigh = 94150;
//				fakeLow = 93650;
//				fakePoc = 93850;
//				fakeDir = VolumeClusterDirectionType.DOWN;
//			}
//			count++;
//			totalSharesTradedInThisWindow = 0;
//			ChartData data = ChartData.of(secSid, 
//					this.windowEndWallClockNanoOfDay, 
//					this.windowSizeInSec,
//					fakeOpen, 
//					fakeClose, 
//					fakeHigh, 
//					fakeLow, 
//					fakePoc, 
//					fakeVal,
//					fakeVah,
//					fakeVwap,
//					fakeDir);
//
//			for (int i = fakeLow; i < fakeLow + 250; i += 50){
//				data.addBidQty(i, 10);
//				data.addAskQty(i, 5);
//				data.significant(i, true);
//			}
//
//			data.volumeClusterType(fakePoc, VolumeClusterType.POC);
//			for (int i =  0; i < fakeCluster.length; i++){
//				data.volumeClusterType(fakeCluster[i], VolumeClusterType.HIGH);
//			}
			
//			count++;
//			int latestLow = low;
//			if (count % 2 == 0){
//				latestLow = ServiceConstant.NULL_CHART_PRICE;
//			}
			
			ChartData data = ChartData.of(secSid, 
					this.windowEndWallClockNanoOfDay, 
					this.windowSizeInSec,
					open, 
					close, 
					high, 
					low, 
					currentPriceWithMaxQty, 
					val,
					vah,
					vwap,
					(open < close) ? VolumeClusterDirectionType.UP : ((open > close) ? VolumeClusterDirectionType.DOWN: VolumeClusterDirectionType.NEUTRAL));
			for (VolumeAtPrice value : this.volAtPrices.values()){
				data.addVolAtPrice(value);
			}
            if (currentPriceWithMaxQty != ServiceConstant.NULL_CHART_PRICE){
			    data.volumeClusterType(currentPriceWithMaxQty, VolumeClusterType.POC);
            }
			volAtPrices.clear();			
			chartDataList.add(data);
			chartDataHandler.handleNewChartData(data);
		}
//		data.volumeClusterType(currentPriceWithMaxQty, VolumeClusterType.);
		
		int windowSizeInSec(){
			return windowSizeInSec;
		}
	}
	
	public void init(long startWallClockNanoOfDay){
		for (int i = 0; i < windows.length; i++){
			windows[i].init(startWallClockNanoOfDay);
		}
	}
	
	public void handleBestChange(long wallClockNanoOfDay, long dataTimeNanoOfDay, Boobs boobs){
		for (int i = 0; i < windows.length; i++){
			windows[i].handleBestChange(wallClockNanoOfDay, dataTimeNanoOfDay, boobs);
		}
	}
	
	public void handleMarketDataTrade(long wallClockNanoOfDay, MarketDataTradeSbeDecoder codec){
		for (int i = 0; i < windows.length; i++){
			windows[i].handleMarketDataTrade(wallClockNanoOfDay, codec);
		}
	}
	
	public void handleClockTick(long wallClockNanoOfDay){
		for (int i = 0; i < windows.length; i++){
			windows[i].handleClockTick(wallClockNanoOfDay);
		}
	}
	
	public OrderFlowAnalyzer signalHandler(SignalHandler handler){
		this.signalHandler = handler;
		return this;
	}

	public OrderFlowAnalyzer chartDataHandler(ChartDataHandler handler){
		this.chartDataHandler = handler;
		return this;
	}
	
	public ObjectArrayList<ChartData> chartDataList(int windowSizeInSec){
		for (int i = 0; i < windows.length; i++){
			if (windows[i].windowSizeInSec == windowSizeInSec){
				return windows[i].chartDataList;
			}
		}
		return NULL_CHART_DATA_LIST;
	}

	public ObjectArrayList<ChartData> chartDataList(){
		if (windows.length > 0){
			return windows[0].chartDataList;
		}
		return NULL_CHART_DATA_LIST;
	}
	
	DataWindow[] windows(){
		return windows;
	}
	
	long secSid(){
		return secSid;
	}
	
	double vahThreshold(){
		return VALUE_AREA_HIGH_THRESHOLD;
	}

	double valThreshold(){
		return VALUE_AREA_LOW_THRESHOLD;
	}
}
