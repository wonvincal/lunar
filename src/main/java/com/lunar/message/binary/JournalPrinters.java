package com.lunar.message.binary;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;

import com.lunar.core.StringConstant;
import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.GenericTrackerSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder.AskDepthDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder.BidDepthDecoder;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.util.StringUtil;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

public class JournalPrinters {
	public static JournalPrinter PERF_DATA_TO_CSV = new JournalPrinter(1); // Prepare for passing in DATA_DATE
	
	public static JournalPrinter MARKET_DATA_TO_CSV = new JournalPrinter(4);
	public static final int MARKET_DATA_TO_CSV_MARKET_DEPTH = 5; 
	public static class MarketDataToCsvUserSuppliedType {
		static int DATA_DATE = 0;
		static int ORDER_BOOK_ID = 1;
		static int MARKET_DEPTH_MAP = 2;
		static int BEST_BID_OF_ORDER_BOOK = 3;
	}
	
	static Handler<OrderBookSnapshotSbeDecoder> MARKET_DATA_TO_CSV_ORDER_BOOK_SNAPSHOT_FORMATTER = new Handler<OrderBookSnapshotSbeDecoder>() {
		
		private final StringBuilder builder = new StringBuilder();
		
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder payload) {};

		@Override
		public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder payload) {
			long secSid = payload.secSid();
			long jniReceivedTime = payload.transactTime();
			TriggerInfoDecoder triggerInfo = payload.triggerInfo();
			String dataDate = userSupplied[MarketDataToCsvUserSuppliedType.DATA_DATE].toString();
			outputStream.print(dataDate);
			outputStream.append(StringConstant.SPACE_CHAR);
			StringUtil.printMicroOfDay(outputStream, jniReceivedTime);
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(payload.secSid());
			outputStream.print(",MarketDepth,,,,,");

			builder.setLength(0);

			// Get Ask first
			AskDepthDecoder askDepths = payload.askDepth();
			int actualDepth = askDepths.count();
			for (final AskDepthDecoder askDepth : askDepths){
				if (askDepth.price() != 0){
					builder.append(askDepth.price())
					.append(StringConstant.COMMA_CHAR)
					.append(askDepth.quantity())
					.append(StringConstant.COMMA_CHAR);
				}
				else {
					builder.append(",,");					
				}
			}
			for (int i = 0; i < (MARKET_DATA_TO_CSV_MARKET_DEPTH - actualDepth); i++){
				builder.append(",,");
			}
			String askMarketDepth = builder.toString();
			
			// Get Bid
			builder.setLength(0);
			
			int bestBid = 0;
			BidDepthDecoder bidDepths = payload.bidDepth();
			actualDepth = bidDepths.count();
			int index = 0;
			for (final BidDepthDecoder bidDepth : bidDepths){
				if (index == 0){
					bestBid = bidDepth.price();
				}
				if (bidDepth.price() != 0){
					builder.append(bidDepth.price())
					.append(StringConstant.COMMA_CHAR)
					.append(bidDepth.quantity())
					.append(StringConstant.COMMA_CHAR);
				}
				else {
					builder.append(",,");
				}
				index++;
			}
			for (int i = 0; i < (MARKET_DATA_TO_CSV_MARKET_DEPTH - actualDepth); i++){
				builder.append(",,");
			}
			String bidMarketDepth = builder.toString();
			
			Long2IntLinkedOpenHashMap bestBids = (Long2IntLinkedOpenHashMap)userSupplied[MarketDataToCsvUserSuppliedType.BEST_BID_OF_ORDER_BOOK];
			bestBids.put(secSid, bestBid);

			String marketDepth = bidMarketDepth + askMarketDepth;
			
			@SuppressWarnings("unchecked")
			Long2ObjectArrayMap<String> marketDepths = (Long2ObjectArrayMap<String>)userSupplied[MarketDataToCsvUserSuppliedType.MARKET_DEPTH_MAP];
			marketDepths.put(secSid, marketDepth);
			outputStream.print(marketDepth);
			outputStream.print(",,,");
			outputStream.print(triggerInfo.triggerSeqNum());
			outputStream.append(StringConstant.COMMA_CHAR);
			AtomicInteger orderBookId = (AtomicInteger)userSupplied[MarketDataToCsvUserSuppliedType.ORDER_BOOK_ID];
			outputStream.print(orderBookId.getAndIncrement());
			outputStream.print(",,,,,,,,,,,");
			outputStream.print(dataDate);
			outputStream.append(StringConstant.SPACE_CHAR);
			StringUtil.prettyPrintNanoOfDay(outputStream, triggerInfo.nanoOfDay());
			outputStream.println();
			return true;
		}
	};

	static Handler<MarketDataTradeSbeDecoder> MARKET_DATA_TO_CSV_MARKET_DATA_TRADE_FORMATTER = new Handler<MarketDataTradeSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder payload) {};

		@Override
		public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder payload) {
			long jniReceivedTime = payload.tradeTime();
			TriggerInfoDecoder triggerInfo = payload.triggerInfo();
			String dataDate = userSupplied[MarketDataToCsvUserSuppliedType.DATA_DATE].toString();
			outputStream.print(dataDate);
			outputStream.append(StringConstant.SPACE_CHAR);
			StringUtil.printMicroOfDay(outputStream, jniReceivedTime);
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(payload.secSid());
			outputStream.print(",Trade,,");
			outputStream.print(payload.price());
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(payload.quantity());
			outputStream.print(",0,");
			
			boolean found = false;
			Object object = userSupplied[MarketDataToCsvUserSuppliedType.MARKET_DEPTH_MAP];
			if (object != null){
				@SuppressWarnings("unchecked")
				Long2ObjectArrayMap<String> marketDepths = (Long2ObjectArrayMap<String>)object;
				String depth = marketDepths.get(payload.secSid());
				if (depth != marketDepths.defaultReturnValue()){
					outputStream.print(depth);
					found = true;
				}
			}
			if (!found){
				outputStream.print(",,,,,,,,,,,,,,,,,,,,");
			}
			outputStream.print(",,");
			Long2IntLinkedOpenHashMap bestBids = (Long2IntLinkedOpenHashMap)userSupplied[MarketDataToCsvUserSuppliedType.BEST_BID_OF_ORDER_BOOK];
			int bestBid = bestBids.get(payload.secSid());
			outputStream.append(payload.price() > bestBid ? 'A' : 'B');
			outputStream.append(StringConstant.COMMA_CHAR);
			
			outputStream.print(triggerInfo.triggerSeqNum());
			outputStream.append(StringConstant.COMMA_CHAR);
			AtomicInteger orderBookId = (AtomicInteger)userSupplied[MarketDataToCsvUserSuppliedType.ORDER_BOOK_ID];
			outputStream.print(orderBookId.get());
			outputStream.print(",,,,,,,,,,,");
			outputStream.print(dataDate);
			outputStream.append(StringConstant.SPACE_CHAR);
			StringUtil.prettyPrintNanoOfDay(outputStream, triggerInfo.nanoOfDay());
			outputStream.println();
			return true;
		}
	};

	static Handler<GenericTrackerSbeDecoder> GENERIC_TRACKER_FORMATTER = new Handler<GenericTrackerSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, GenericTrackerSbeDecoder payload) {}
		
		@Override
		public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header, GenericTrackerSbeDecoder payload) {
			TriggerInfoDecoder triggerInfo = payload.triggerInfo();
			outputStream.print(payload.sendBy());
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(payload.stepId().value());
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(payload.stepId().name());
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(payload.timestamp());
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(triggerInfo.triggeredBy());
			outputStream.append(StringConstant.COMMA_CHAR);
			outputStream.print(triggerInfo.triggerSeqNum());
			outputStream.append(StringConstant.COMMA_CHAR);
			StringUtil.prettyPrintNanoOfDay(outputStream, triggerInfo.nanoOfDay());
			outputStream.println();
			return true;
		}
	};
	
	static {
		MARKET_DATA_TO_CSV.headerWriter((s) -> s.println("DateTime,RIC,Type,Id,Price,Volume,Qualifiers,L1-BidPrice,L1-BidSize,L2-BidPrice,L2-BidSize,L3-BidPrice,L3-BidSize,L4-BidPrice,L4-BidSize,L5-BidPrice,L5-BidSize,L1-AskPrice,L1-AskSize,L2-AskPrice,L2-AskSize,L3-AskPrice,L3-AskSize,L4-AskPrice,L4-AskSize,L5-AskPrice,L5-AskSize,Bid_BrokerQ,Ask_BrokerQ,Side,SeqNum,OrderBookId,L1-BidNum,L2-BidNum,L3-BidNum,L4-BidNum,L5-BidNum,L1-AskNum,L2-AskNum,L3-AskNum,L4-AskNum,L5-AskNum,SourceTime"))
		.orderBookSnapshotFormatter(MARKET_DATA_TO_CSV_ORDER_BOOK_SNAPSHOT_FORMATTER)
		.marketDataTradeFormatter(MARKET_DATA_TO_CSV_MARKET_DATA_TRADE_FORMATTER)
		.addUserSupplied(MarketDataToCsvUserSuppliedType.MARKET_DEPTH_MAP, new Long2ObjectArrayMap<String>())
		.addUserSupplied(MarketDataToCsvUserSuppliedType.ORDER_BOOK_ID, new AtomicInteger())
		.addUserSupplied(MarketDataToCsvUserSuppliedType.BEST_BID_OF_ORDER_BOOK, new Long2IntLinkedOpenHashMap());
		
		PERF_DATA_TO_CSV.headerWriter((s) -> s.println("sendBy,stepId,stepName,timestamp,triggeredBy,triggeredSeqNum,triggeredNanoOfDay"))
		.genericTrackerFormatter(GENERIC_TRACKER_FORMATTER);
	}
}
