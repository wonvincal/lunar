package com.lunar.marketdata;

import com.lunar.core.TriggerInfo;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.MarketDataSender;
import com.lunar.order.Tick;

import org.agrona.MutableDirectBuffer;

public class OrderBookSnapshot implements MarketOrderBook {
    private final TriggerInfo triggerInfo;
	private int channelSeqNum;
	private long secSid;
	private long transactNanoOfDay;
	private boolean isRecovery;
	private OrderBookSideSnapshot bidSide;
	private OrderBookSideSnapshot askSide;
	private OrderBookSideSnapshot[] sides;
	private final StringBuilder stringBuilder;
	
	public OrderBookSnapshot(final int bookDepth, final SpreadTable spreadTable, final int nullTickLevel, final int nullPrice) {
		this(-1, bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

	public OrderBookSnapshot(long secSid, final int bookDepth, final SpreadTable spreadTable, final int nullTickLevel, final int nullPrice) {
		this.triggerInfo = TriggerInfo.of();
		this.channelSeqNum = 0;		
		this.secSid = secSid;
		this.transactNanoOfDay = 0;
		this.bidSide = new OrderBookBidSideSnapshot(bookDepth, spreadTable, nullTickLevel, nullPrice);
		this.askSide = new OrderBookAskSideSnapshot(bookDepth, spreadTable, nullTickLevel, nullPrice);
		this.sides = new OrderBookSideSnapshot[2];
		this.sides[BID_INDEX] = this.bidSide;
		this.sides[ASK_INDEX] = this.askSide;
		this.stringBuilder = new StringBuilder();
	}
	
	public OrderBookSide bidSide(){
		return bidSide;
	}
	
	public OrderBookSide askSide(){
		return askSide;
	}
	
	public OrderBookSide side(final int index){
		return this.sides[index];
	}
	
	public Tick bestBidOrNullIfEmpty(){
		return bidSide.bestOrNullIfEmpty();
	}

	public Tick bestAskOrNullIfEmpty(){
		return askSide.bestOrNullIfEmpty();
	}

	public boolean isEmpty(){
		return bidSide.isEmpty() && askSide.isEmpty();
	}
	
	public long secSid() {
		return this.secSid;
	}
	public MarketOrderBook secSid(final long secSid) {
		this.secSid = secSid;
		return this;
	}

	public TriggerInfo triggerInfo() {
		return this.triggerInfo;
	}
	
	public int channelSeqNum() {
	    return this.channelSeqNum;
	}	
	public MarketOrderBook channelSeqNum(final int seqNum) {
	    this.channelSeqNum = seqNum;
	    return this;
	}
	
	@Override
	public MarketOrderBook transactNanoOfDay(final long nanoOfDay) {
	    this.transactNanoOfDay = nanoOfDay;
	    return this;
	}
	
	@Override
	public long transactNanoOfDay() {
	    return this.transactNanoOfDay;
	}

	@Override
	public MarketOrderBook isRecovery(boolean isRecovery) {		
		this.isRecovery = isRecovery;
		return this;
	}

	@Override
	public boolean isRecovery() {
		return this.isRecovery;
	}
	
	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return MarketDataSender.encodeOrderBookSnapshotWithoutHeader(buffer, offset, encoder.orderBookSnapshotEncoder(), this);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.ORDERBOOK_SNAPSHOT;
	}

	@Override
	public short blockLength() {
		return OrderBookSnapshotSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
	    return OrderBookSnapshotSbeEncoder.BLOCK_LENGTH + 
            OrderBookSnapshotSbeEncoder.BidDepthEncoder.sbeHeaderSize() + 
            OrderBookSnapshotSbeEncoder.BidDepthEncoder.sbeBlockLength() * bidSide().numPriceLevels() +
            OrderBookSnapshotSbeEncoder.AskDepthEncoder.sbeHeaderSize() +
            OrderBookSnapshotSbeEncoder.AskDepthEncoder.sbeBlockLength() * askSide().numPriceLevels();
	}
	
    @Override
    public int schemaId() {
        return OrderBookSnapshotSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return OrderBookSnapshotSbeEncoder.SCHEMA_VERSION;
    }

	@Override
	public String toString() {
	    stringBuilder.setLength(0);;
	    stringBuilder.append("[");
	    stringBuilder.append(transactNanoOfDay);
	    stringBuilder.append("] ");
	    stringBuilder.append(secSid);
	    stringBuilder.append(": ");
	    stringBuilder.append(channelSeqNum);
	    stringBuilder.append(" Bid: [");
	    stringBuilder.append(bidSide);
	    stringBuilder.append("] Ask: [");
	    stringBuilder.append(askSide);
	    stringBuilder.append("]");
	    return stringBuilder.toString();
	}

	@Override
    public void copyFrom(final MarketOrderBook other) {
	    if (other instanceof OrderBookSnapshot) {
	        final OrderBookSnapshot otherSnapshot = (OrderBookSnapshot)other;
	        this.triggerInfo().copyFrom(otherSnapshot.triggerInfo());
	        this.channelSeqNum = otherSnapshot.channelSeqNum;
	        this.secSid = otherSnapshot.secSid;
            this.transactNanoOfDay = otherSnapshot.transactNanoOfDay;
	        this.isRecovery = otherSnapshot.isRecovery;
	        this.askSide().copyFrom(otherSnapshot.askSide());
	        this.bidSide().copyFrom(otherSnapshot.bidSide());
	        //copyTo.askSide().clear();
	        //final Iterator<Tick> askIterator = copyFrom.askSide().localPriceLevelsIterator();
	        //while (askIterator.hasNext()) {
	        //    final Tick tick = askIterator.next();
	        //    copyTo.askSide().create(tick.price(), tick.qty());
	        //}
	        //copyTo.bidSide().clear();
	        //final Iterator<Tick> bidIterator = copyFrom.bidSide().localPriceLevelsIterator();
	        //while (bidIterator.hasNext()) {
	        //    final Tick tick = bidIterator.next();
	        //    copyTo.bidSide().create(tick.price(), tick.qty());
	        //}
	    }
	}

}
