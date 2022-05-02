package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class ValidationOrderBookTest {
	@Mock
	private MessageSink selfSink;

	private TestHelper helper;
	private ValidationOrderBook orderBook;
	private final int expectedOutstandingOrders = 32;
	private final long secSid = 100101;
	private Security security;
	private MessageSinkRef self;
	private MutableDirectBuffer buffer;
	
	@Before
	public void setup(){
		orderBook = ValidationOrderBook.of(expectedOutstandingOrders, secSid);
		security = Security.of(12345678L, SecurityType.WARRANT, "61727", 101, false, SpreadTableBuilder.get(SecurityType.WARRANT));
		self = MessageSinkRef.of(selfSink);
		helper = TestHelper.of();
		buffer = helper.createDirectBuffer();
	}
	
	@Test
	public void givenEmptyOrderBookWhenProcessNewOrderRequestThenOKAndUpdateOrderBook(){
		// given
		final int portSid = 55555;
		final int clientKey = 12345;
		NewOrderRequest origRequest = NewOrderRequest.of(
				clientKey,
				self, 
				security, 
				OrderType.LIMIT_ORDER,
				1000000, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.TRUE, 
				650, 
				650, 
				portSid);
		
		NewOrderRequestSbeEncoder sbe = new NewOrderRequestSbeEncoder();
		OrderSender.encodeNewOrderOnly(buffer, 0, sbe, origRequest);
		
		NewOrderRequestSbeDecoder request = new NewOrderRequestSbeDecoder();
		request.wrap(buffer, 0, NewOrderRequestSbeDecoder.BLOCK_LENGTH, NewOrderRequestSbeDecoder.SCHEMA_VERSION);
		
		// when
		OrderRejectType result = orderBook.isNewBuyOrderOk(request.limitPrice(), request.quantity());
		orderBook.newBuyOrder(request.limitPrice());
		
		// then
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
		assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
		ValidationOrderBook.OrderBookLevel level = orderBook.bidOrderBookLevelByPrice().get(origRequest.limitPrice());
		assertEquals(1, level.numOrders());
		assertEquals(origRequest.limitPrice(), level.price());
		assertEquals(origRequest.side(), level.side());
		
		assertEquals(0, orderBook.position().volatileLongPosition());		
	}

	@Test
	public void givenEmptyOrderBookWithNoPositionWhenProcessNewSellOrderRequestThenReject(){
		// given
		@SuppressWarnings("unused")
		final Side side = Side.SELL;
		final int price = 650;
		final int quantity = 1000000;
		
		// when
		OrderRejectType result = orderBook.isNewSellOrderOk(price, quantity);
		
		// then
		assertEquals(OrderRejectType.INSUFFICIENT_LONG_POSITION, result);
	}
	
	
	@Test
	public void givenOneExistingBidWhenSubmitNonCrossingAskThenOK(){
		// given
		orderBook.position().longPosition(1000000);
		
		@SuppressWarnings("unused")
		final Side side = Side.BUY;
		final int price = 650;
		final int quantity = 1000000;
		OrderRejectType result = orderBook.isNewBuyOrderOk(price, quantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
		orderBook.newBuyOrder(price);
		
		// when
		@SuppressWarnings("unused")
		final Side sellSide = Side.SELL;
		final int sellPrice = 651;
		final int sellQuantity = 500000;
		result = orderBook.isNewSellOrderOk(sellPrice, sellQuantity);
		
		// then
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
	}
	
	@Test
	public void givenOneExistingBidWhenSubmitCrossingAskThenReject(){
		// given
		orderBook.position().longPosition(1000000);
		
		@SuppressWarnings("unused")
		final Side side = Side.BUY;
		final int price = 650;
		final int quantity = 1000000;
		OrderRejectType result = orderBook.isNewBuyOrderOk(price, quantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
		orderBook.newBuyOrder(price);
		
		// when
		@SuppressWarnings("unused")
		final Side sellSide = Side.SELL;
		final int sellPrice = 650;
		final int sellQuantity = 500000;

		// then
		result = orderBook.isNewSellOrderOk(sellPrice, sellQuantity);
		assertEquals(OrderRejectType.CROSSED, result);
	}

	@Ignore
	@Test
	public void givenCheckDisabledOneExistingBidWhenSubmitCrossingAskThenNotReject(){
		// given
		orderBook.position().longPosition(1000000);
		
		@SuppressWarnings("unused")
		final Side side = Side.BUY;
		final int price = 650;
		final int quantity = 1000000;
		OrderRejectType result = orderBook.isNewBuyOrderOk(price, quantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
		orderBook.newBuyOrder(price);
		
		// when
		@SuppressWarnings("unused")
		final Side sellSide = Side.SELL;
		final int sellPrice = 650;
		final int sellQuantity = 500000;

		// then
		result = orderBook.isNewSellOrderOk(sellPrice, sellQuantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
	}
	
	@Test
	public void givenOneExistingAskWhenSubmitNonCrossingBidThenOK(){
		// given
		orderBook.position().longPosition(1000000);
		
		// when
		@SuppressWarnings("unused")
		final Side side = Side.SELL;
		final int price = 650;
		final int quantity = 1000000;

		OrderRejectType result = orderBook.isNewSellOrderOk(price, quantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
		orderBook.newSellOrder(price, quantity);
		
		// when
		@SuppressWarnings("unused")
		final Side buySide = Side.BUY;
		final int buyPrice = 649;
		final int buyQuantity = 500000;
		result = orderBook.isNewBuyOrderOk(buyPrice, buyQuantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
	}
	
	@Test
	public void givenOneExistingAskWhenSubmitCrossingBidThenReject(){
		// given
		orderBook.position().longPosition(1000000);
		
		// when
		@SuppressWarnings("unused")
		final Side side = Side.SELL;
		final int price = 650;
		final int quantity = 1000000;

		OrderRejectType result = orderBook.isNewSellOrderOk(price, quantity);
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
		orderBook.newSellOrder(price, quantity);
		
		// when
		@SuppressWarnings("unused")
		final Side buySide = Side.BUY;
		final int buyPrice = 650;
		final int buyQuantity = 500000;
		result = orderBook.isNewBuyOrderOk(buyPrice, buyQuantity);
		assertEquals(OrderRejectType.CROSSED, result);
	}
	
	private OrderEntryPair setupOrderBookWithTightBidAskTotalTwoLevels(){
		final long initialPosition = 10_000_000;
		orderBook.position().longPosition(initialPosition);
		
		final int quantity = 500_000;
		
		final OrderEntry ask = addEntry(Side.SELL, 650, quantity);
		final OrderEntry bid = addEntry(Side.BUY, 645, quantity);
		
		assertEquals(ask.price(), orderBook.bestAsk());
		assertEquals(bid.price(), orderBook.bestBid());
		assertEquals(initialPosition - quantity, orderBook.position().volatileLongPosition());
		return OrderEntryPair.of(bid, ask);
	}
	
	private OrderEntry addEntry(Side side, int price, int quantity){
		OrderEntry entry = OrderEntry.of(side, price, quantity);
		if (side == Side.BUY){
			OrderRejectType result = orderBook.isNewBuyOrderOk(entry.price(), entry.quantity());
			assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
			orderBook.newBuyOrder(entry.price());
		}
		else{
			OrderRejectType result = orderBook.isNewSellOrderOk(entry.price(), entry.quantity());			
			assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, result);
			orderBook.newSellOrder(entry.price(), entry.quantity());		
		}
		return entry;
	}
	
	@SuppressWarnings("unused")
	private void setupOrderBookWithTightBidAskMoreThanTwoLevels(){
		final long initialPosition = 10_000_000;
		orderBook.position().longPosition(initialPosition);
		
		final int quantity = 500_000;
		final OrderEntry ask1 = addEntry(Side.SELL, 650, quantity);
		final OrderEntry ask2 = addEntry(Side.SELL, 651, quantity);
		final OrderEntry ask3 = addEntry(Side.SELL, 652, quantity);
		final OrderEntry ask4 = addEntry(Side.SELL, 653, quantity);
		final OrderEntry bid1 = addEntry(Side.BUY, 649, quantity);
		final OrderEntry bid2 = addEntry(Side.BUY, 648, quantity);
		final OrderEntry bid3 = addEntry(Side.BUY, 647, quantity);
		final OrderEntry bid4 = addEntry(Side.BUY, 646, quantity);

		assertEquals(ask1.price(), orderBook.bestAsk());
		assertEquals(bid1.price(), orderBook.bestBid());
		assertEquals(ask4.price(), orderBook.maxAsk());
		assertEquals(bid4.price(), orderBook.minBid());
		assertEquals(initialPosition - 4 * quantity, orderBook.position().volatileLongPosition());
	}

	private static class OrderEntryPair{
		private OrderEntry bid;
		private OrderEntry ask;
		
		public static OrderEntryPair of (OrderEntry bid, OrderEntry ask){
			return new OrderEntryPair(bid, ask);
		}
		OrderEntryPair(OrderEntry bid, OrderEntry ask){
			this.bid = bid;
			this.ask = ask;
		}
		@SuppressWarnings("unused")
		public OrderEntry bid(){ return bid;}
		public OrderEntry ask(){ return ask;}
	}
	
	private static class OrderEntry {
		private Side side;
		private int price;
		private int quantity;
		static OrderEntry of(Side side, int price, int quantity){
			return new OrderEntry(side, price, quantity);
		}
		OrderEntry(Side side, int price, int quantity){
			this.side = side;
			this.price = price;
			this.quantity = quantity;
		}
		@SuppressWarnings("unused")
		public Side side(){return side;}
		public int price(){return price;}
		public int quantity(){return quantity;}
	}
	
	@Test
	public void givenExistingBidAndAskWhenCancelAskOrderThenPositionIncBySellQuantity(){
		// given
		OrderEntryPair pair = setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		// when
		orderBook.sellOrderCancelled(pair.ask().price(), pair.ask().quantity());
		
		// then
		assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
		assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
		assertEquals(origPosition + pair.ask().quantity(), orderBook.position().volatileLongPosition());
	}
	
	// Tight Order Book (Sell Order) -----------------------------------
	@Test
	public void givenTightOrderBookWithTwoLevelsWhenNewSellThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		
		// when
		int price = orderBook.bestAsk();
		int quantity = 100;
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewSellOrderOk(price, quantity));
		orderBook.newSellOrder(price, quantity);
		assertEquals(origPosition - quantity, orderBook.position().volatileLongPosition());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellOrderCancelledThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		final int leavesQuantity = 500;
		orderBook.sellOrderCancelled(orderBook.bestAsk(), leavesQuantity);
		assertEquals(origPosition + leavesQuantity, orderBook.position().volatileLongPosition());
	}
	
	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellOrderRejectedThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		final int leavesQuantity = 500;
		orderBook.sellOrderRejected(orderBook.bestAsk(), leavesQuantity);
		assertEquals(origPosition + leavesQuantity, orderBook.position().volatileLongPosition());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellOrderExpiredThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		final int leavesQuantity = 500;
		orderBook.sellOrderExpired(orderBook.bestAsk(), leavesQuantity);
		assertEquals(origPosition + leavesQuantity, orderBook.position().volatileLongPosition());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellOrderFilledThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		orderBook.sellOrderFilled(orderBook.bestAsk());
		assertEquals(origPosition, orderBook.position().volatileLongPosition());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellTradeThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		final int execQuantity = 500;
		orderBook.sellTrade(orderBook.bestAsk(), execQuantity);
		assertEquals(origPosition, orderBook.position().volatileLongPosition());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellTradeCancelledThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();

		final int execQuantity = 5000;
		orderBook.sellTradeCancelled(orderBook.bestAsk(), execQuantity);
		assertEquals(origPosition + execQuantity, orderBook.position().volatileLongPosition());
	}

	// Tight Order Book (Buy Order) -----------------------------------
	@Test
	public void givenTightOrderBookWithTwoLevelsWhenNewBuyThenOK(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		int origSize = orderBook.bidOrderBookLevelByPrice().size();
		
		// when
		int price = orderBook.bestBid();
		int quantity = 100;
		assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewBuyOrderOk(price, quantity));
		orderBook.newBuyOrder(price);
		assertEquals(origPosition, orderBook.position().volatileLongPosition());
		assertEquals(origSize, orderBook.bidOrderBookLevelByPrice().size());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenBuyOrderCancelledThenNoChangeToPosition(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		int origSize = orderBook.bidOrderBookLevelByPrice().size();

		// when
		orderBook.buyOrderCancelled(orderBook.bestBid());
		assertEquals(origPosition, orderBook.position().volatileLongPosition());
		assertEquals(origSize - 1, orderBook.bidOrderBookLevelByPrice().size());		
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenBuyOrderRejectedThenNoChangeToPosition(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		int origSize = orderBook.bidOrderBookLevelByPrice().size();

		orderBook.buyOrderRejected(orderBook.bestBid()); 
		assertEquals(origPosition, orderBook.position().volatileLongPosition());
		assertEquals(origSize - 1, orderBook.bidOrderBookLevelByPrice().size());		
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenBuyOrderExpiredThenNoChangeToPosition(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		int origSize = orderBook.bidOrderBookLevelByPrice().size();

		orderBook.buyOrderExpired(orderBook.bestBid()); 
		assertEquals(origPosition, orderBook.position().volatileLongPosition());
		assertEquals(origSize - 1, orderBook.bidOrderBookLevelByPrice().size());		
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenBuyTradeThenIncPosition(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		int origSize = orderBook.bidOrderBookLevelByPrice().size();
		
		final int execQuantity = 500;
		orderBook.buyTrade(orderBook.bestBid(), execQuantity);
		assertEquals(origPosition + execQuantity, orderBook.position().volatileLongPosition());
		assertEquals(origSize, orderBook.bidOrderBookLevelByPrice().size());
	}

	@Test
	public void givenTightOrderBookWithTwoLevelsWhenSellTradeCancelledThenDecPosition(){
		// given
		setupOrderBookWithTightBidAskTotalTwoLevels();		
		long origPosition = orderBook.position().volatileLongPosition();
		int origSize = orderBook.bidOrderBookLevelByPrice().size();

		final int execQuantity = 5000;
		orderBook.buyTradeCancelled(orderBook.bestBid(), execQuantity);
		assertEquals(origPosition - execQuantity, orderBook.position().volatileLongPosition());
		assertEquals(origSize, orderBook.bidOrderBookLevelByPrice().size());
	}
	
	@Test
	public void givenNewPositionWhenBuyRejectSellRejectThenOK(){
        // Given
        orderBook.position().longPosition(1000000);

        // Order Reject
        // When - create order buy at 1000
        int price = 1000;
        orderBook.newBuyOrder(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
        
        // When - reject order
        orderBook.buyOrderRejected(price);
        assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
        assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
        
        int quantity = 100;
        assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewSellOrderOk(price, quantity));
        orderBook.newSellOrder(price, quantity);
        assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
        assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
        assertEquals(1, orderBook.askOrderBookLevelByPrice().get(price).numOrders());
        
        orderBook.sellOrderRejected(price, quantity);
        assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
        assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
        assertNull(orderBook.askOrderBookLevelByPrice().get(price));
	}
	
	@Test
	public void givenWhenBuyRejectSellRejectThenOK(){
        // Given
        orderBook.position().longPosition(1000000);

        // Order filled
        // When - create order buy at 1000
        int price = 1000;
        orderBook.newBuyOrder(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
        assertNull(orderBook.askOrderBookLevelByPrice().get(price));
        
        int execQty = 400;
        orderBook.buyTrade(price, execQty);
        orderBook.buyOrderFilled(price);
        assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
        assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
        assertNull(orderBook.askOrderBookLevelByPrice().get(price));
        
        int quantity = 100;
        int newPrice = price - 1;
        assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewSellOrderOk(price, quantity));
        orderBook.newSellOrder(newPrice, quantity);
        assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
        assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
        assertEquals(1, orderBook.askOrderBookLevelByPrice().get(newPrice).numOrders());
	}
	
	@Test
	public void testNormalUseOfPosition(){
        // Given
        orderBook.position().longPosition(1000000);
	    
        int price = 110000;
        orderBook.newBuyOrder(price);        
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());

        int price2 = 111000;
        orderBook.newBuyOrder(price2);        
        assertEquals(2, orderBook.bidOrderBookLevelByPrice().size());

        int price3 = 100000;
        orderBook.newBuyOrder(price3);        
        assertEquals(3, orderBook.bidOrderBookLevelByPrice().size());

        int quantity = 100;
        orderBook.buyTrade(price2, quantity);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        assertEquals(price2, orderBook.bestBid());
        assertEquals(3, orderBook.bidOrderBookLevelByPrice().size());

        assertEquals(OrderRejectType.CROSSED, orderBook.isNewSellOrderOk(price2, quantity));

        orderBook.buyOrderFilled(price2);
        assertEquals(price, orderBook.bestBid());
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price2));

        assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewSellOrderOk(price2, quantity));

        orderBook.buyTrade(price3, quantity);
        orderBook.buyOrderFilled(price3);
        assertEquals(price, orderBook.bestBid());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price3));

        assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewSellOrderOk(price2, quantity));
	}	
	
	@Test
	public void givenWhenBuyPartialFilledFOKThenOK(){
        // Given
        orderBook.position().longPosition(1000000);
	    
        int price = 1000;
        orderBook.newBuyOrder(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
        assertNull(orderBook.askOrderBookLevelByPrice().get(price));

        orderBook.newBuyOrder(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(2, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        
        orderBook.newBuyOrder(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(3, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());
        
        int execQty = 500;
        orderBook.buyTrade(price, execQty);
        orderBook.buyTrade(price, execQty);
        orderBook.buyTrade(price, execQty);
        orderBook.buyOrderFilled(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(2, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());

        int quantity = 250;
        assertEquals(OrderRejectType.CROSSED, orderBook.isNewSellOrderOk(price, quantity));
        
        orderBook.buyTrade(price, execQty);
        orderBook.buyOrderFilled(price);
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
        assertEquals(1, orderBook.bidOrderBookLevelByPrice().get(price).numOrders());

        assertEquals(OrderRejectType.CROSSED, orderBook.isNewSellOrderOk(price, quantity));

        orderBook.buyTrade(price, execQty);
        orderBook.buyOrderFilled(price);
        assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
        assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
        
        assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewSellOrderOk(price, quantity));
	}

	@Test
	public void givenWhenSellPartialFilledFOKThenOK(){
	    // Given
	    orderBook.position().longPosition(1000000);

	    int price = 1000;
	    int quantity = 2000;
	    orderBook.newSellOrder(price, quantity);
	    assertEquals(0, orderBook.bidOrderBookLevelByPrice().size());
	    assertNull(orderBook.bidOrderBookLevelByPrice().get(price));
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().get(price).numOrders());

	    orderBook.newSellOrder(price, quantity);
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    assertEquals(2, orderBook.askOrderBookLevelByPrice().get(price).numOrders());

	    orderBook.newSellOrder(price, quantity);
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    assertEquals(3, orderBook.askOrderBookLevelByPrice().get(price).numOrders());

	    int execQty = 1000;
	    orderBook.sellTrade(price, execQty);
	    orderBook.sellTrade(price, execQty);
	    orderBook.sellTrade(price, execQty);
	    orderBook.sellTrade(price, execQty);
	    orderBook.sellOrderFilled(price);
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    assertEquals(2, orderBook.askOrderBookLevelByPrice().get(price).numOrders());

	    int buyQuantity = 2000;
	    assertEquals(OrderRejectType.CROSSED, orderBook.isNewBuyOrderOk(price, buyQuantity));

	    orderBook.sellTrade(price, buyQuantity);
	    orderBook.sellOrderFilled(price);
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().get(price).numOrders());

	    assertEquals(OrderRejectType.CROSSED, orderBook.isNewBuyOrderOk(price, buyQuantity));

        orderBook.sellTrade(price, buyQuantity);
        orderBook.sellOrderFilled(price);
	    assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
	    assertNull(orderBook.askOrderBookLevelByPrice().get(price));

	    assertEquals(OrderRejectType.VALID_AND_NOT_REJECT, orderBook.isNewBuyOrderOk(price, buyQuantity));
	}
}

