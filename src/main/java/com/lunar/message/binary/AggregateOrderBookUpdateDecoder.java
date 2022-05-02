package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.archive.MarketDataPrice;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.Side;
import com.lunar.service.ServiceConstant;

public class AggregateOrderBookUpdateDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(AggregateOrderBookUpdateDecoder.class);
	private final AggregateOrderBookUpdateSbeDecoder sbe = new AggregateOrderBookUpdateSbeDecoder();
	private final HandlerList<AggregateOrderBookUpdateSbeDecoder> handlerList;

	AggregateOrderBookUpdateDecoder(Handler<AggregateOrderBookUpdateSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<AggregateOrderBookUpdateSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}
	
	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(AggregateOrderBookUpdateSbeDecoder sbe){
		int origLimit = sbe.limit();
		EntryDecoder entries = sbe.entry();
		StringBuilder sb = new StringBuilder(String.format("secSid:%d, parametersCount:%d",
		sbe.secSid(),
		sbe.entry().count()));
		for (EntryDecoder entry : entries){
			sb.append(", quantity:").append(entry.quantity())
			  .append(", price:").append(entry.price());
		}
		sbe.limit(origLimit);					
		return sb.toString();
	}
	
	public HandlerList<AggregateOrderBookUpdateSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static AggregateOrderBookUpdateDecoder of(){
		return new AggregateOrderBookUpdateDecoder(NULL_HANDLER);
	}

	static final Handler<AggregateOrderBookUpdateSbeDecoder> NULL_HANDLER = new Handler<AggregateOrderBookUpdateSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder codec) {
			LOG.warn("message sent to imn null handler");
		}
	};

	public static void decodeToMarketDataPrice(AggregateOrderBookUpdateSbeDecoder sbe, MarketDataPrice price){
		int origLimit = sbe.limit();
		EntryDecoder entries = sbe.entry();
		boolean hasBestBid = false;
		int bestBid = Integer.MAX_VALUE;
		boolean hasBestAsk = false;
		int bestAsk = Integer.MIN_VALUE;
		for (EntryDecoder entry : entries){
			if (entry.side() == Side.BUY && entry.price() < bestBid){
				bestBid = entry.price();
				hasBestBid = true;
			}
			else if (entry.side() == Side.SELL && entry.price() > bestAsk){
				bestAsk = entry.price();
				hasBestAsk = true;
			}
		}
		sbe.limit(origLimit);					
		price.clear();
		if (hasBestAsk){
			price.bestAsk(bestAsk);
		}
		if (hasBestBid){
			price.bestBid(bestBid);
		}
	}
}
