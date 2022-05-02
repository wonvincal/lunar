package com.lunar.position;

import com.lunar.message.io.sbe.Side;

public class PartialMatchedTradeInfo {
	private final TradeInfo trade;
	private long outstanding;
	
	public static PartialMatchedTradeInfo NULL_INSTANCE = new PartialMatchedTradeInfo(TradeInfo.of(-1, 0, 0, Side.NULL_VAL, 0, 0), 0);
	
	static PartialMatchedTradeInfo of(TradeInfo trade, long outstanding){
		return new PartialMatchedTradeInfo(trade, outstanding);
	}
	
	PartialMatchedTradeInfo(TradeInfo trade, long outstanding){
		this.trade = trade;
		this.outstanding = outstanding;
	}
	
	public boolean nothingHasBeenMatched(){
		return trade.quantity() == outstanding;
	}
	
	public long matchedQty(){
		return trade.quantity() - outstanding;
	}
	
	public long outstandingQty(){
		return outstanding;
	}
	
	public long quantity(){
		return trade.quantity();
	}
	
	public TradeInfo trade(){
		return trade;
	}

	public int tradeSid(){
		return trade.tradeSid();
	}

	public Side side(){
		return trade.side();
	}
	
	public PartialMatchedTradeInfo decOutstandingQty(long value){
		outstanding -= value;
		return this;
	}
	
	public PartialMatchedTradeInfo incOutstandingQty(long value){
		outstanding += value;
		return this;
	}

	public double price(){
		return trade.price();
	}
	
	
}