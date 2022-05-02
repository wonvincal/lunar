package com.lunar.analysis;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.UserControlledTimerService;
import com.lunar.entity.ChartData;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeEncoder;
import com.lunar.message.io.sbe.TradeType;
import com.lunar.service.ServiceConstant;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class OrderFlowAnalyzerTest {
	private static final Logger LOG = LogManager.getLogger(OrderFlowAnalyzerTest.class);
	private OrderFlowAnalyzer analyser;
	private long secSid;
	private long windowSizeNs;
	private double[] supportedWindowSizeMultipliers;
	private TestHelper helper = TestHelper.of();
	private UserControlledTimerService timerService;
	private MarketDataTradeSbeEncoder tradeEncoder = new MarketDataTradeSbeEncoder();
	private MarketDataTradeSbeDecoder tradeDecoder = new MarketDataTradeSbeDecoder();
	private MutableDirectBuffer buffer;
	

	@Mock
	private ChartDataHandler chartDataHandler;
	
	@Before
	public void setup(){
		buffer = helper.createDirectBuffer();
		secSid = 12345L;
		windowSizeNs = OrderFlowAnalyzer.MIN_WINDOW_SIZE_NS;
		supportedWindowSizeMultipliers = new double[]{1, 5, 10};
		analyser = OrderFlowAnalyzer.of(secSid,
				windowSizeNs,
				supportedWindowSizeMultipliers);
		analyser.chartDataHandler(chartDataHandler);
		timerService = helper.timerService();
		long nanoOfDay = timerService.toNanoOfDay();
		analyser.init(nanoOfDay);
	}
	
	@Test
	public void testCreate(){
		long secSid = 12345L;
		OrderFlowAnalyzer item = OrderFlowAnalyzer.of(secSid, windowSizeNs, new double[]{1});
		assertEquals(1, item.windows().length);
		assertEquals(secSid, item.secSid());
		assertEquals(TimeUnit.NANOSECONDS.toSeconds(OrderFlowAnalyzer.MIN_WINDOW_SIZE_NS), item.windows()[0].windowSizeInSec());
	}
	
	@Test
	public void testCreateMultipleWindows(){
		long secSid = 12345L;
		OrderFlowAnalyzer item = OrderFlowAnalyzer.of(secSid, windowSizeNs, new double[]{1, 5, 10});
		assertEquals(3, item.windows().length);
		assertEquals(TimeUnit.NANOSECONDS.toSeconds(OrderFlowAnalyzer.MIN_WINDOW_SIZE_NS), item.windows()[0].windowSizeInSec());
		assertEquals(TimeUnit.NANOSECONDS.toSeconds(OrderFlowAnalyzer.MIN_WINDOW_SIZE_NS) * 5, item.windows()[1].windowSizeInSec());
		assertEquals(TimeUnit.NANOSECONDS.toSeconds(OrderFlowAnalyzer.MIN_WINDOW_SIZE_NS) * 10, item.windows()[2].windowSizeInSec());		
	}
	
	@Test
	public void givenCreatedWhenReceiveTickThenNoOutputIfLessThanWindowSize(){
		long nanoOfDay = timerService.toNanoOfDay();
		analyser.init(nanoOfDay);
		timerService.advance(Duration.ofNanos(windowSizeNs));
		nanoOfDay = timerService.toNanoOfDay();
		analyser.handleClockTick(nanoOfDay);
		verify(chartDataHandler, never()).handleNewChartData(any());
	}
	
	@Test
	public void givenCreatedWhenReceiveTickAfterWindowSizeThenChartDataIsProduced(){
		timerService.advance(Duration.ofNanos(windowSizeNs + 1));
		long nanoOfDay = timerService.toNanoOfDay();
		analyser.handleClockTick(nanoOfDay);
		ArgumentCaptor<ChartData> argumentCaptor = ArgumentCaptor.forClass(ChartData.class);
		verify(chartDataHandler, times(1)).handleNewChartData(argumentCaptor.capture());
		
		ChartData captured = argumentCaptor.getValue();
		assertEquals(secSid, captured.secSid());
		assertEquals(1, captured.windowSizeInSec());
		assertEquals(Integer.MIN_VALUE, captured.high());
		assertEquals(Integer.MAX_VALUE, captured.low());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.open());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.close());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.pointOfControl());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.vwap());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.valueAreaLow());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.valueAreaHigh());
		assertEquals(nanoOfDay - 1, captured.dataTime());
	}
	
	@Test
	public void givenCreatedWhenReceiveMarketTradeAfterWindowSizeThenChartDataIsProduced(){
		timerService.advance(Duration.ofNanos(windowSizeNs));
		long nanoOfDay = timerService.toNanoOfDay();
		int price = 2500;
		analyser.handleMarketDataTrade(nanoOfDay, createTrade(nanoOfDay, price, 100));
		
		timerService.advance(Duration.ofNanos(1));
		nanoOfDay = timerService.toNanoOfDay();
		analyser.handleClockTick(nanoOfDay);
		
		ArgumentCaptor<ChartData> argumentCaptor = ArgumentCaptor.forClass(ChartData.class);
		verify(chartDataHandler, times(1)).handleNewChartData(argumentCaptor.capture());
		ChartData captured = argumentCaptor.getValue();
		assertEquals(1, captured.windowSizeInSec());
		assertEquals(price, captured.high());
		assertEquals(price, captured.low());
		assertEquals(price, captured.open());
		assertEquals(price, captured.close());
		assertEquals(price, captured.vwap());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.valueAreaHigh());
		assertEquals(ServiceConstant.NULL_CHART_PRICE, captured.valueAreaLow());
	}
	
	static class Trade {
		int price;
		int quantity;
		
		static Trade of(int price, int quantity){
			Trade trade =  new Trade();
			trade.price = price;
			trade.quantity = quantity;
			return trade;
		}
	}
	
	static class Running {
		long totalShares = 0;
		long totalNotional = 0;
		
		static Running of(){
			return new Running();
		}
		
		public Running addTrade(Trade trade){
			totalShares += trade.quantity;
			totalNotional += (long)(trade.price) * trade.quantity;
			return this;
		}
		public Running addRunning(Running running){
			totalShares += running.totalShares;
			totalNotional += running.totalNotional;
			return this;
		}
		public int vwap(){
			if (totalShares != 0){
				return (int)(totalNotional / totalShares); 				
			}
			return 0;
		}
	}
	
	@Test
	public void givenCreateWhenReceiveMultipleTradesThenChartDataIsProduced(){
		timerService.advance(Duration.ofNanos(windowSizeNs));
		long nanoOfDay = timerService.toNanoOfDay();
		
		Trade[] trades = new Trade[]{ Trade.of(2500,  100), Trade.of(3560, 200), Trade.of(1500, 100) };
		
		for (Trade trade : trades){
			analyser.handleMarketDataTrade(nanoOfDay, createTrade(nanoOfDay, trade));	
		}
		
		timerService.advance(Duration.ofNanos(1));
		nanoOfDay = timerService.toNanoOfDay();
		analyser.handleClockTick(nanoOfDay);

		ArgumentCaptor<ChartData> argumentCaptor = ArgumentCaptor.forClass(ChartData.class);
		verify(chartDataHandler, times(1)).handleNewChartData(argumentCaptor.capture());
		ChartData captured = argumentCaptor.getValue();

		// Then
		int expectedOpen = trades[0].price;
		int expectedClose = trades[trades.length - 1].price;
		int expectedHigh = Arrays.stream(trades).max((trade1, trade2) -> { return Integer.compare(trade1.price, trade2.price);}).get().price;
		int expectedLow = Arrays.stream(trades).min((trade1, trade2) -> { return Integer.compare(trade1.price, trade2.price);}).get().price;
				
		Running sumRunning = Arrays.stream(trades).reduce(Running.of(),
				(Running running, Trade trade) -> { return running.addTrade(trade); }, 
				(Running running1, Running running2) -> { return running1.addRunning(running2);});
		
		int expectedVwap = sumRunning.vwap();
		int expectedPoc = Arrays.stream(trades).max((trade1, trade2) -> { return Integer.compare(trade1.quantity, trade2.quantity);}).get().price;

		
		int totalQuantity = Arrays.stream(trades).mapToInt(t -> t.quantity).sum();
		long runningTotalQuantity = 0;
		long valThreshold = (long)(analyser.valThreshold() * totalQuantity);
		long vahThreshold = (long)(analyser.vahThreshold() * totalQuantity);
		long val = ServiceConstant.NULL_CHART_PRICE;
		long vah = ServiceConstant.NULL_CHART_PRICE;
		Iterator<Trade> iterator = Arrays.stream(trades).sorted((t1, t2) -> { return Integer.compare(t1.price, t2.price);}).iterator();
		while (iterator.hasNext()){
			Trade trade = iterator.next();
			runningTotalQuantity += trade.quantity;
			if (runningTotalQuantity <= valThreshold){
				val = trade.price;
				LOG.info("[runningTotalQuantity: {}, valThreshold:{}, val:{}]", runningTotalQuantity, valThreshold, val);
			}
			if (runningTotalQuantity <= vahThreshold){
				vah = trade.price;
				LOG.info("[runningTotalQuantity: {}, vahThreshold:{}, vah:{}]", runningTotalQuantity, vahThreshold, vah);
			}
		}
		
		assertEquals(expectedOpen, captured.open());
		assertEquals(expectedClose, captured.close());
		assertEquals(expectedHigh, captured.high());
		assertEquals(expectedLow, captured.low());
		assertEquals(expectedVwap, captured.vwap());
		assertEquals(expectedPoc, captured.pointOfControl());
		assertEquals(val, captured.valueAreaLow());
		assertEquals(vah, captured.valueAreaHigh());
	}

	public void givenCreateWhenReceiveTypicalTickThenChartDataIsProduced(){
		timerService.advance(Duration.ofNanos(windowSizeNs));
		long nanoOfDay = timerService.toNanoOfDay();
		int price = 2500;
		analyser.handleMarketDataTrade(nanoOfDay, createTrade(nanoOfDay, price, 100));
	}

	private MarketDataTradeSbeDecoder createTrade(long tradeTime, Trade trade){
		return createTrade(tradeTime, trade.price, trade.quantity);
	}
	
	private MarketDataTradeSbeDecoder createTrade(long tradeTime, int price, int quantity){
		tradeEncoder.wrap(buffer, 0)
			.isRecovery(BooleanType.FALSE)
			.numActualTrades(1)
			.price(price)
			.quantity(quantity)
			.secSid(secSid)
			.tradeType(TradeType.AUTOMATCH_NORMAL)
			.tradeTime(tradeTime);
		return tradeDecoder.wrap(buffer, 0, MarketDataTradeSbeDecoder.BLOCK_LENGTH, MarketDataTradeSbeDecoder.SCHEMA_VERSION);
	}
}
