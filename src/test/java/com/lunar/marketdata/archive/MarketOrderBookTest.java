package com.lunar.marketdata.archive;

public class MarketOrderBookTest {
	/*
	private static SpreadTable spreadTable = SpreadTableBuilder.get();
	final int bookDepth = 10;
	final int nullTickLevel = -1;
	final int nullPrice = Integer.MIN_VALUE;

	@Test
	public void testCreate(){
		MarketOrderBook ob = MarketOrderBook.of(bookDepth, spreadTable, nullTickLevel, nullPrice);
		assertTrue("order book is expected to be empty", ob.isEmpty());
	}
	
	@Test
	public void givenOneOutstandingWhenUpdateToSameLevelThenOKWithMessageCodec(){
		MarketOrderBook ob = MarketOrderBook.of(bookDepth, spreadTable, nullTickLevel, nullPrice);
		int price = 100;
		int tickLevel = spreadTable.priceToTick(price);
		byte priceLevel = 1;
		int numOrders = 1;
		long qty = 1000;
		long secSid = 123123;
		int dstSinkId = 2;
		int senderSinkId = 1;
		int seq = 0;

		TestHelper helper = TestHelper.of();
		helper.codecForTest().encoder().encodeMarketDataOrderBookSnapshotBegin(helper.frameForTest(), senderSinkId, dstSinkId, seq, secSid, 2)
			.next()
				.entryType(EntryType.BID)
				.price(price)
				.priceLevel(priceLevel)
				.quantity(qty)
				.numOrders(numOrders)
				.tickLevel(tickLevel)
				.updateAction(UpdateAction.NEW)
			.next()
				.entryType(EntryType.BID)
				.price(price)
				.priceLevel(priceLevel)
				.quantity(qty * 3)
				.numOrders(numOrders + 1)
				.tickLevel(tickLevel)
				.updateAction(UpdateAction.CHANGE);

		helper.codecForTest().decoder().addMarketDataOrderBookSnapshotHandler(new Handler<MarketDataOrderBookSnapshotSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoderMarketDataOrderBookSnapshotSbeDecoder snapshot) {
				EntryDecoder entry = snapshot.entry().next();
				ob.process(entry);
				assertTrue(compare(ob, nullTickLevel, new Tick[] { Tick.of(tickLevel, price, qty, numOrders)}, new Tick[0]));
				assertFalse(ob.isEmpty());
				
				int bestTickLevel = ob.bestBidOrNullIfEmpty().tickLevel();
				entry = entry.next();
				ob.process(entry);
				assertTrue(compare(ob, nullTickLevel, new Tick[] { Tick.of(tickLevel, price, qty * 3, numOrders + 1) }, new Tick[0]));
				
				assertFalse("order book is expected not to be empty", ob.isEmpty());
				assertEquals(bestTickLevel, ob.bestBidOrNullIfEmpty().tickLevel());
			}
		});
		helper.codecForTest().decoder().receive(helper.frameForTest());
	}
	
	public static boolean compare(MarketOrderBook ob, int nullTickLevel, Tick[] expectedBidTicks, Tick[] expectedAskTicks){
		return SingleSidedMarketOrderBookTest.compare(ob.bidSide(), nullTickLevel, expectedBidTicks) &&
			   SingleSidedMarketOrderBookTest.compare(ob.askSide(), nullTickLevel, expectedAskTicks);
	}
	
	@Test
	public void testSpecForBidWithMessageCodec(){
		MarketOrderBook ob = MarketOrderBook.of(bookDepth, spreadTable, nullTickLevel, nullPrice);
		TestHelper helper = TestHelper.of(302);
		int senderSinkId = 1;
		int dstSinkId = 2;
		int seq = 0;
		long secSid = 123123;
		
		helper.codecForTest().encoder().encodeMarketDataOrderBookSnapshotBegin(helper.frameForTest(), senderSinkId, dstSinkId, seq, secSid, 9)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9730)).price(9730).priceLevel((byte)1).quantity(700).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9720)).price(9720).priceLevel((byte)2).quantity(350).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9710)).price(9710).priceLevel((byte)3).quantity(150).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9700)).price(9700).priceLevel((byte)4).quantity(250).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9690)).price(9690).priceLevel((byte)5).quantity(100).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9680)).price(9680).priceLevel((byte)6).quantity(150).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9670)).price(9670).priceLevel((byte)7).quantity(50).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9660)).price(9660).priceLevel((byte)8).quantity(200).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9650)).price(9650).priceLevel((byte)9).quantity(100).numOrders(1).updateAction(UpdateAction.NEW);

		Handler<MarketDataOrderBookSnapshotSbeDecoder> handler = new Handler<MarketDataOrderBookSnapshotSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataOrderBookSnapshotSbeDecoder snapshot) {
				EntryDecoder entries = snapshot.entry();
				while (entries.hasNext()){
					ob.process(entries.next());
				}
				
				assertTrue(compare(ob, nullTickLevel, new Tick[] {
						Tick.of(spreadTable.priceToTick(9730), 9730, 1, 700, 1),
						Tick.of(spreadTable.priceToTick(9720), 9720, 2, 350, 1),
						Tick.of(spreadTable.priceToTick(9710), 9710, 3, 150, 1),
						Tick.of(spreadTable.priceToTick(9700), 9700, 4, 250, 1),
						Tick.of(spreadTable.priceToTick(9690), 9690, 5, 100, 1),
						Tick.of(spreadTable.priceToTick(9680), 9680, 6, 150, 1),
						Tick.of(spreadTable.priceToTick(9670), 9670, 7, 50, 1),
						Tick.of(spreadTable.priceToTick(9660), 9660, 8, 200, 1),
						Tick.of(spreadTable.priceToTick(9650), 9650, 9, 100, 1)}, new Tick[0]));			
				}
		};
		helper.codecForTest().decoder().addMarketDataOrderBookSnapshotHandler(handler);
		helper.codecForTest().decoder().receive(helper.frameForTest());
		helper.codecForTest().decoder().removeMarketDataOrderBookSnapshotHandler(handler);
		
		helper.codecForTest().encoder().encodeMarketDataOrderBookSnapshotBegin(helper.frameForTest(), senderSinkId, dstSinkId, seq, secSid, 5)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9740)).price(9740).priceLevel((byte)1).quantity(50).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9750)).price(9750).priceLevel((byte)1).quantity(250).numOrders(1).updateAction(UpdateAction.NEW)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9660)).price(9660).priceLevel((byte)10).quantity(150).numOrders(1).updateAction(UpdateAction.CHANGE)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9750)).price(9750).priceLevel((byte)1).updateAction(UpdateAction.DELETE)
			.next().entryType(EntryType.BID).tickLevel(spreadTable.priceToTick(9650)).price(9650).priceLevel((byte)10).quantity(100).numOrders(1).updateAction(UpdateAction.NEW);

		handler = new Handler<MarketDataOrderBookSnapshotSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataOrderBookSnapshotSbeDecoder snapshot) {
				EntryDecoder entries = snapshot.entry();
				while (entries.hasNext()){
					ob.process(entries.next());
				}
				
				assertTrue(compare(ob, nullTickLevel, new Tick[] {
						Tick.of(spreadTable.priceToTick(9740), 9740, 1, 50, 1),
						Tick.of(spreadTable.priceToTick(9730), 9730, 2, 700, 1),
						Tick.of(spreadTable.priceToTick(9720), 9720, 3, 350, 1),
						Tick.of(spreadTable.priceToTick(9710), 9710, 4, 150, 1),
						Tick.of(spreadTable.priceToTick(9700), 9700, 5, 250, 1),
						Tick.of(spreadTable.priceToTick(9690), 9690, 6, 100, 1),
						Tick.of(spreadTable.priceToTick(9680), 9680, 7, 150, 1),
						Tick.of(spreadTable.priceToTick(9670), 9670, 8, 50, 1),
						Tick.of(spreadTable.priceToTick(9660), 9660, 9, 150, 1),
						Tick.of(spreadTable.priceToTick(9650), 9650, 10, 100, 1)}, new Tick[0]
						));
			}
		};
		helper.codecForTest().decoder().addMarketDataOrderBookSnapshotHandler(handler);
		helper.codecForTest().decoder().receive(helper.frameForTest());
		helper.codecForTest().decoder().removeMarketDataOrderBookSnapshotHandler(handler);
		
		helper.codecForTest().encoder().encodeMarketDataOrderBookSnapshotBegin(helper.frameForTest(), senderSinkId, dstSinkId, seq, secSid, 1)
		.next().entryType(EntryType.BID).updateAction(UpdateAction.CLEAR);

		handler = new Handler<MarketDataOrderBookSnapshotSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataOrderBookSnapshotSbeDecoder snapshot) {
				EntryDecoder entries = snapshot.entry();
				while (entries.hasNext()){
					ob.process(entries.next());
				}
				assertTrue(ob.isEmpty());
			}
		};
		helper.codecForTest().decoder().addMarketDataOrderBookSnapshotHandler(handler);
		helper.codecForTest().decoder().receive(helper.frameForTest());
	}
	*/
}
