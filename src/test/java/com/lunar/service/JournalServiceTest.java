package com.lunar.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import com.lunar.journal.JournalService;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.sender.MarketDataSender;
import com.lunar.message.sender.MessageSender;
import com.lunar.util.PathUtil;
import com.lunar.util.TestHelper;

public class JournalServiceTest {

	@Test
	public void testStartStop(){
		int sinkId = 1;
		String serviceName = "test-service";
		String journalPrefix = "mds";
		String journalFolder = "journal";
		String journalArchiveFolder = "journal_archive";
		
		Path journalPath = PathUtil.createWritableFolderIfNotExist("journal", journalFolder);
		Path archivePath = PathUtil.createWritableFolderIfNotExist("archive", journalArchiveFolder);
		TestHelper of = TestHelper.of();
		JournalService journalService = JournalService.of(sinkId, serviceName, journalPrefix, journalPath, archivePath, of.realTimerService(), of.systemClock());
		assertTrue(journalService.isStopped());
		journalService.onStart();
		assertFalse(journalService.isStopped());
		journalService.onShutdown();
		assertTrue(journalService.isStopped());
	}
	
	@Test
	public void testStartWriteStop() throws Exception{
		int sinkId = 1;
		String serviceName = "test-service";
		String journalPrefix = "mds";
		String journalFolder = "journal";
		String journalArchiveFolder = "journal_archive";
		
		Path journalPath = PathUtil.createWritableFolderIfNotExist("journal", journalFolder);
		Path archivePath = PathUtil.createWritableFolderIfNotExist("archive", journalArchiveFolder);
		TestHelper of = TestHelper.of();
		JournalService journalService = JournalService.of(sinkId, serviceName, journalPrefix, journalPath, archivePath, of.realTimerService(), of.systemClock());
		assertTrue(journalService.isStopped());
		journalService.onStart();
		
		// Create order book
		final SpreadTable spreadTable = SpreadTableBuilder.get(SecurityType.WARRANT);
		final int BOOK_DEPTH = 10;
		final long secSid = 11234567890l;
		MarketOrderBook orderBook = MarketOrderBook.of(secSid, BOOK_DEPTH, spreadTable, Integer.MIN_VALUE, Integer.MIN_VALUE);
		
		UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		MessageHeaderEncoder header = new MessageHeaderEncoder();
		OrderBookSnapshotSbeEncoder encoder = new OrderBookSnapshotSbeEncoder();

		write(orderBook, header, encoder, buffer);
		journalService.onEvent(buffer, 0, false);
		
		assertFalse(journalService.isStopped());
		journalService.onShutdown();
		assertTrue(journalService.isStopped());
	}

	private static void write(MarketOrderBook orderBook, MessageHeaderEncoder header, OrderBookSnapshotSbeEncoder encoder, MutableDirectBuffer buffer){
		long timestamp = LocalTime.now().toNanoOfDay();
		orderBook.transactNanoOfDay(timestamp);

		int payloadLength = MarketDataSender.encodeOrderBookSnapshotWithoutHeader(buffer, com.lunar.message.io.sbe.MessageHeaderEncoder.ENCODED_LENGTH, encoder, orderBook);
		byte senderSinkId = 1;
		int sinkId = 2;
		AtomicInteger seq = new AtomicInteger();
		MessageSender.encodeHeader(header, senderSinkId, sinkId, buffer, 0,
				OrderBookSnapshotSbeEncoder.BLOCK_LENGTH,
				OrderBookSnapshotSbeEncoder.TEMPLATE_ID,
				OrderBookSnapshotSbeEncoder.SCHEMA_ID,
				OrderBookSnapshotSbeEncoder.SCHEMA_VERSION,
				seq.getAndIncrement(), 
				payloadLength);
	}
}
