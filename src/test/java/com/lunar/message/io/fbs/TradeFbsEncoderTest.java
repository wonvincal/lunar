package com.lunar.message.io.fbs;


import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.OrderSender;
import com.lunar.order.Trade;
import com.lunar.service.ServiceConstant;

public class TradeFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(TradeFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
	private final TradeSbeEncoder sbeEncoder = new TradeSbeEncoder();
	private final TradeSbeDecoder sbeDecoder = new TradeSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    int tradeSid = 100001;
		int orderSid = 200001;
		int orderId = 300001;
		long secSid = 500001;
		int leavesQty = 0;
		int cumulativeQty = 2000;
		String executionId = "400001";
		int executionPrice = 520000;
		int executionQty = 800;
		Trade trade = Trade.of(tradeSid, 
	    		orderSid, 
	    		orderId, 
	    		secSid, 
	    		Side.BUY, 
	    		leavesQty, 
	    		cumulativeQty, 
	    		executionId, 
	    		executionPrice, 
	    		executionQty, OrderStatus.PARTIALLY_FILLED, TradeStatus.NEW, 0, 1);
	    OrderSender.encodeTradeOnly(buffer, 0, sbeEncoder, 123, 1, trade.sid(), trade.orderSid(), trade.orderId(), OrderStatus.NEW, 
	    		trade.tradeStatus(), 
	    		trade.side(), 
	    		OrderSender.prepareExecutionId(trade.executionId(), stringBuffer),
	    		trade.executionPrice(), 
	    		trade.executionQty(), 
	    		888);
		sbeDecoder.wrap(buffer, 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
		
		int offset = TradeFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		TradeFbs tradeFbs = TradeFbs.getRootAsTradeFbs(builder.dataBuffer());
		assertEquals(123, tradeFbs.channelId());
		assertEquals(trade.executionPrice(), tradeFbs.executionPrice());
		assertEquals(trade.executionQty(), tradeFbs.executionQty());
		assertEquals(trade.side().value(), tradeFbs.side());
		assertEquals(trade.tradeStatus().value(), tradeFbs.tradeStatus());
		assertEquals(trade.executionQty(), tradeFbs.executionQty());
		assertEquals(trade.executionId(), tradeFbs.executionId());
		assertEquals(trade.executionPrice(), tradeFbs.executionPrice());
	}
	
}

