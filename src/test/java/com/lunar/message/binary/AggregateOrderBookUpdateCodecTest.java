package com.lunar.message.binary;

public class AggregateOrderBookUpdateCodecTest {
	/*
	@Test
	public void testEncodeAndDecodeMarketDataOrderBookSnapshotBegin(){
		final int expectedSenderSinkId = 3;
		final int expectedDstSinkId = 64;
		final int seq = 101;
		final long secSid = 10000101010l;

		MarketDataOrderBookSnapshotCodec codec = MarketDataOrderBookSnapshotCodec.of();
		MessageHeader messageHeader = new MessageHeader();
		Frame frame = new Frame(true, messageHeader.size() + MarketDataOrderBookSnapshotSbe.BLOCK_LENGTH * 2);

		codec.encodeMarketDataOrderBookSnapshotBegin(frame, 
													 messageHeader, 
													 expectedSenderSinkId, 
													 expectedDstSinkId, 
													 seq, 
													 secSid,
													 0);

		codec.addHandler(new Handler<MarketDataOrderBookSnapshotSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataOrderBookSnapshotSbeDecoder codec) {
				assertEquals(secSid, codec.secSid());
			}
		});
		CodecUtil.decode(messageHeader, frame, codec.decoder());
	}

	@Test
	public void testEncodeAndDecodeMarketDataOrderBookSnapshotAll(){
		final int numActions = 2;
		final int expectedSenderSinkId = 3;
		final int expectedDstSinkId = 64;
		final int seq = 101;
		final long secSid = 10000101010l;

		MarketDataOrderBookSnapshotCodec codec = MarketDataOrderBookSnapshotCodec.of();
		MessageHeader messageHeader = new MessageHeader();
		Frame frame = new Frame(true, messageHeader.size() + MarketDataOrderBookSnapshotSbe.BLOCK_LENGTH * 10);

		Entry entry = codec.encodeMarketDataOrderBookSnapshotBegin(frame, 
													 messageHeader, 
													 expectedSenderSinkId, 
													 expectedDstSinkId, 
													 seq, 
													 secSid,
													 numActions);
		
		final EntryType entryType = EntryType.BID;
		final int numOrders = 2;
		final int price = 105000;
		final byte priceLevel = 9;
		final int tickLevel = 120;
		final UpdateAction updateAction = UpdateAction.CHANGE;

		final EntryType entryType2 = EntryType.OFFER;
		final int numOrders2 = 3;
		final int price2 = 10000;
		final byte priceLevel2 = 19;
		final int tickLevel2 = 1201;
		final UpdateAction updateAction2 = UpdateAction.NEW;
		
		entry.next().entryType(entryType).numOrders(numOrders).price(price).priceLevel(priceLevel).tickLevel(tickLevel).updateAction(updateAction)
			 .next().entryType(entryType2).numOrders(numOrders2).price(price2).priceLevel(priceLevel2).tickLevel(tickLevel2).updateAction(updateAction2);

		
		codec.addHandler(new Handler<MarketDataOrderBookSnapshotSbe>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataOrderBookSnapshotSbe codec) {
				assertEquals(secSid, codec.secSid());
				
				Entry e = codec.entry();
				assertEquals(numActions, e.count());
				e.next();
				assertEquals(numOrders, e.numOrders());
				assertEquals(price, e.price());
				assertEquals(priceLevel, e.priceLevel());
				assertEquals(tickLevel, e.tickLevel());
				assertEquals(updateAction, e.updateAction());
				
				e.next(); 
				assertEquals(numOrders2, e.numOrders());
				assertEquals(price2, e.price());
				assertEquals(priceLevel2, e.priceLevel());
				assertEquals(tickLevel2, e.tickLevel());
				assertEquals(updateAction2, e.updateAction());
			}
		});
		CodecUtil.decode(messageHeader, frame, codec.decoder());
	}
*/
}
