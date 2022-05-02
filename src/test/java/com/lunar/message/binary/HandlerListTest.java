package com.lunar.message.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.service.ServiceConstant;

public class HandlerListTest {
	private static final Logger LOG = LogManager.getLogger(HandlerListTest.class);
	
	@Test
	public void testAddIfNotExist(){
	    final Handler<Integer> NULL_INT_HANDLER = new Handler<Integer>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, Integer payload) {
                LOG.debug("Null int handler: {}", payload);
            }
	    };
	    
	    HandlerList<Integer> handlerList = new HandlerList<Integer>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, NULL_INT_HANDLER);
	    
	    Handler<Integer> orderHandler = (buffer, offset, header, payload) -> { LOG.debug("Inside orderHandler: {}", payload);};
	    Handler<Integer> orderHandler2 = (buffer, offset, header, payload) -> { LOG.debug("Inside orderHandler2: {}", payload);};
	    Handler<Integer> orderHandler3 = (buffer, offset, header, payload) -> { LOG.debug("Inside orderHandler3: {}", payload);};
	    
	    handlerList.add(orderHandler);
	    assertTrue(handlerList.prepend(orderHandler2));
	    assertEquals(orderHandler2, handlerList.get(0));
	    assertEquals(orderHandler, handlerList.get(1));
	    assertTrue(handlerList.prepend(orderHandler3));
	    assertEquals(orderHandler3, handlerList.get(0));
        assertEquals(orderHandler2, handlerList.get(1));
        assertEquals(orderHandler, handlerList.get(2));
        assertFalse(handlerList.prepend(orderHandler));
        assertFalse(handlerList.prepend(orderHandler));
        assertFalse(handlerList.prepend(orderHandler3));
	}
	
	@Test
	public void test(){
		HandlerList<OrderSbeDecoder> handlerList = new HandlerList<OrderSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, NULL_HANDLER);
		
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int senderSinkId = 1;
		int dstSinkId = 2;
		int seq = 1;
		OrderSbeEncoder encoder = new OrderSbeEncoder();
		int orderSid = 2000001;
		long secSid = 5000001;
		encoder.wrap(buffer, 0)
			.channelId(1)
			.channelSnapshotSeq(1)
			.orderSid(orderSid)
			.secSid(secSid);
		
		MessageHeaderDecoder header = MessageHeaderUtil.headerOf(buffer, 
				0, 
				OrderSbeEncoder.BLOCK_LENGTH, 
				OrderSbeEncoder.SCHEMA_VERSION, 
				OrderSbeEncoder.TEMPLATE_ID, 
				seq, 
				encoder.encodedLength(), 
				(byte)senderSinkId, 
				(byte)dstSinkId);
		
		OrderSbeDecoder codec = new OrderSbeDecoder();
		codec.wrap(buffer, 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		handlerList.handle(buffer, 0, header, codec);
		
		handlerList.add(orderHandler);
		assertTrue(handlerList.contains(orderHandler));
		handlerList.remove(orderHandler);
		assertFalse(handlerList.contains(orderHandler));
		
		// Handler created by method reference is always different
		handlerList.add(this::handleOrder);
		assertFalse(handlerList.contains(this::handleOrder));
	}
	
	static final Handler<OrderSbeDecoder> NULL_HANDLER = new Handler<OrderSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder order) {
			LOG.debug("Received order in null handler [orderSid:{}, secSid:{}]", order.orderSid(), order.secSid());
		}
	};
	
    private void handleOrder(final DirectBuffer buffer, final int offset, MessageHeaderDecoder header, final OrderSbeDecoder order){
    	LOG.debug("Received order [orderSid:{}, secSid:{}]", order.orderSid(), order.secSid());
    }

    private Handler<OrderSbeDecoder> orderHandler = new Handler<OrderSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder order) {
			LOG.debug("Received order in a standalone orderHandler [orderSid:{}, secSid:{}]", order.orderSid(), order.secSid());
		}
	};
    
}
