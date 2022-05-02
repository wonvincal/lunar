package com.lunar.marketdata.archive;

import com.lunar.core.TriggerInfo;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.archive.SingleSideMarketOrderBook;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.order.Tick;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.io.sbe.UpdateAction;

public final class MarketOrderBook implements com.lunar.marketdata.MarketOrderBook {
	private static final int BID_INDEX = 0;
	private static final int ASK_INDEX = 1;
	private static final SnapshotEntryProcessor[] AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS;
	private final SingleSideMarketOrderBook[] sides;
	private int seqNum;
	private long timestamp;
	private boolean isSnapshot;
	private TriggerInfo triggerInfo;
	
	static {
		AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS = new SnapshotEntryProcessor[4];
		AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS[UpdateAction.NEW.value()] = new SnapshotEntryProcessor() {
			@Override
			public void process(MarketOrderBook ob, EntryDecoder entry) {
				ob.sides[entry.entryType().value()].create(entry.tickLevel(), 
														   entry.price(), 
														   entry.priceLevel(), 
														   entry.quantity(), 
														   entry.numOrders());
			}
		};
		AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS[UpdateAction.CHANGE.value()] = new SnapshotEntryProcessor() {
			@Override
			public void process(MarketOrderBook ob, EntryDecoder entry) {
                ob.sides[entry.entryType().value()].create(entry.tickLevel(), 
                        entry.price(), 
                        entry.priceLevel(), 
                        entry.quantity(), 
                        entry.numOrders());
				ob.sides[entry.entryType().value()].update(entry.tickLevel(), 
														   entry.price(), 
														   entry.priceLevel(), 
														   entry.quantity(), 
														   entry.numOrders());
			}
		};
		AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS[UpdateAction.DELETE.value()] = new SnapshotEntryProcessor() {
			@Override
			public void process(MarketOrderBook ob, EntryDecoder entry) {
				ob.sides[entry.entryType().value()].delete(entry.tickLevel(), 
														   entry.price(), 
														   entry.priceLevel());
			}
		};
		AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS[UpdateAction.CLEAR.value()] = new SnapshotEntryProcessor() {
			@Override
			public void process(MarketOrderBook ob, EntryDecoder entry) {
				ob.sides[BID_INDEX].clear();
				ob.sides[ASK_INDEX].clear();
			}
		};
	}
	
	public static MarketOrderBook of(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
		return new MarketOrderBook(
				SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice),
				SingleSideMarketOrderBook.forAsk(bookDepth, spreadTable, nullTickLevel, nullPrice));		
	}
	
	public MarketOrderBook(SingleSideMarketOrderBook bid, SingleSideMarketOrderBook ask){
		this.sides = new SingleSideMarketOrderBook[2];
		this.sides[BID_INDEX] = bid;
		this.sides[ASK_INDEX] = ask;
		this.seqNum = 0;
		this.timestamp = 0;
		this.isSnapshot = false;
		this.triggerInfo = TriggerInfo.of();
	}
	
	public SingleSideMarketOrderBook bidSide(){
		return this.sides[BID_INDEX];
	}
	
	public SingleSideMarketOrderBook askSide(){
		return this.sides[ASK_INDEX];
	}
	
	public SingleSideMarketOrderBook side(int index){
		return this.sides[index];
	}
	
	public Tick bestBidOrNullIfEmpty(){
		return this.sides[BID_INDEX].bestOrNullIfEmpty();
	}

	public Tick bestAskOrNullIfEmpty(){
		return this.sides[ASK_INDEX].bestOrNullIfEmpty();
	}

	public boolean isEmpty(){
		return this.sides[BID_INDEX].isEmpty() && this.sides[ASK_INDEX].isEmpty();
	}
	
	public void process(EntryDecoder entry) {
		AGGREGATE_ORDERBOOK_UPDATE_ENTRY_PROCESSORS[entry.updateAction().value()].process(this, entry);
	}
	
	public MarketOrderBook channelSeqNum(final int seqNum) {
	    this.seqNum = seqNum;
	    return this;
	}
	
	public int channelSeqNum() {
	    return this.seqNum;
	}
	
	public MarketOrderBook transactNanoOfDay(final long timestamp) {
	    this.timestamp = timestamp;
	    return this;
	}
	
	public long transactNanoOfDay() {
	    return this.timestamp;
	}
	
	public MarketOrderBook isRecovery(final boolean isSnapshot) {
	    this.isSnapshot = isSnapshot;
	    return this;
	}
	
	public boolean isRecovery() {
	    return this.isSnapshot;
	}
	
	private static interface SnapshotEntryProcessor {
		void process(MarketOrderBook ob, EntryDecoder entry);
	}

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public TemplateType templateType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public short blockLength() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int expectedEncodedLength() {
		// TODO Auto-generated method stub
		return 0;
	}
	
    @Override
    public int schemaId() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int schemaVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

	@Override
	public long secSid() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public com.lunar.marketdata.MarketOrderBook secSid(long secSid) {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public void copyFrom(com.lunar.marketdata.MarketOrderBook other) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public TriggerInfo triggerInfo() {
        return this.triggerInfo;
    }

}
