package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.lunar.message.io.sbe.EntryType;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder.EntryEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MarketDataSenderTest {
	@Test
	public void testEncodeAndDecode(){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
		final int refSecSid = 1234;
		final int entryCount = 3;
		
		// Single entry
		AggregateOrderBookUpdateSbeEncoder sbe = new AggregateOrderBookUpdateSbeEncoder();
		EntryEncoder entryEncoder = sbe.wrap(buffer, 0)
				.secSid(refSecSid)
				.entryCount(entryCount);
		entryEncoder.next()
		.numOrders(1)
		.price(2)
		.priceLevel((byte)3)
		.quantity(4)
		.tickLevel(5);
		
		AggregateOrderBookUpdateSbeDecoder decoder = new AggregateOrderBookUpdateSbeDecoder();
		decoder.wrap(buffer, 0, AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH, AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION);
		assertEquals(refSecSid, decoder.secSid());
		EntryDecoder entryDecoder = decoder.entry();
		entryDecoder.next();
		assertEquals(1, entryDecoder.numOrders());
		assertEquals(2, entryDecoder.price());
		assertEquals(3, entryDecoder.priceLevel());
		assertEquals(4, entryDecoder.quantity());
		assertEquals(5, entryDecoder.tickLevel());
		
		// Multiple entries
		entryEncoder = sbe.wrap(buffer, 0)
				.secSid(refSecSid)
				.entryCount(entryCount);
		for (int i = 0; i < entryCount; i++){
			entryEncoder.next()
			.numOrders(1 * i)
			.price(2 * i)
			.priceLevel((byte)(3 * i))
			.quantity(4 * i)
			.tickLevel(5 * i);
		}

		decoder = new AggregateOrderBookUpdateSbeDecoder();
		decoder.wrap(buffer, 0, AggregateOrderBookUpdateSbeEncoder.BLOCK_LENGTH, AggregateOrderBookUpdateSbeEncoder.SCHEMA_VERSION);
		
		// make this call here to make sure that we are not getting any unexpected side effect
		@SuppressWarnings("unused")
		int length = MarketDataSender.expectedEncodedLength(decoder);
		
		assertEquals(refSecSid, decoder.secSid());
		entryDecoder = decoder.entry();
		for (int i = 0; i < entryCount; i++){
			entryDecoder.next();
			assertEquals(1 * i, entryDecoder.numOrders());
			assertEquals(2 * i, entryDecoder.price());
			assertEquals(3 * i, entryDecoder.priceLevel());
			assertEquals(4 * i, entryDecoder.quantity());
			assertEquals(5 * i, entryDecoder.tickLevel());
		}
	}
	
	@Test
	public void testEncodedLength(){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
		final int refSecSid = 1234;

		// zero entry
		AggregateOrderBookUpdateSbeEncoder sbe = new AggregateOrderBookUpdateSbeEncoder();
		sbe.wrap(buffer, 0)
		.secSid(refSecSid)
		.entryCount(0);
		int encodedLength = sbe.encodedLength();
		assertEquals(MessageHeaderEncoder.ENCODED_LENGTH + encodedLength, MarketDataSender.expectedEncodedLength(0));

		// one entry
		sbe = new AggregateOrderBookUpdateSbeEncoder();
		sbe.wrap(buffer, 0)
		.secSid(refSecSid)
		.entryCount(1)
		.next()
			.entryType(EntryType.BID)
			.price(1)
			.priceLevel((byte)2)
			.quantity(3)
			.tickLevel(4)
			.numOrders(5);
		encodedLength = sbe.encodedLength();
		assertEquals(MessageHeaderEncoder.ENCODED_LENGTH + encodedLength, MarketDataSender.expectedEncodedLength(1));
		
		// multiple entries
		sbe = new AggregateOrderBookUpdateSbeEncoder();
		sbe.wrap(buffer, 0)
		.secSid(refSecSid)
		.entryCount(2)
		.next()
			.entryType(EntryType.BID)
			.price(1)
			.priceLevel((byte)2)
			.quantity(3)
			.tickLevel(4)
			.numOrders(5)
		.next()
			.entryType(EntryType.BID)
			.price(11)
			.priceLevel((byte)21)
			.quantity(31)
			.tickLevel(41)
			.numOrders(51);
		encodedLength = sbe.encodedLength();
		assertEquals(MessageHeaderEncoder.ENCODED_LENGTH + encodedLength, MarketDataSender.expectedEncodedLength(2));
		
		// get encoded length from decoder
		AggregateOrderBookUpdateSbeDecoder decoder = new AggregateOrderBookUpdateSbeDecoder();
		decoder.wrap(buffer, 0, AggregateOrderBookUpdateSbeDecoder.BLOCK_LENGTH, AggregateOrderBookUpdateSbeDecoder.SCHEMA_VERSION);
		assertEquals(MessageHeaderEncoder.ENCODED_LENGTH + encodedLength, MarketDataSender.expectedEncodedLength(decoder));
	}
}
