package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeEncoder.EntryEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.EntryType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeEncoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeEncoder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.UpdateAction;
import com.lunar.message.sender.PositionSender;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sender.SecuritySender;
import com.lunar.position.FeeAndCommissionSchedule;
import com.lunar.position.SecurityPositionDetails;
import com.lunar.service.ServiceConstant;

public class MessageFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(MessageFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final UnsafeBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	private MessageFbsEncoder fbsEncoder = MessageFbsEncoder.of();
	private MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

	@Test
	public void testResponse(){
		// Given
		final int clientKey = 123456;
		final byte resultType = ResultTypeFbs.FAILED;
		final boolean isLast = false;
		
		// FBS - Encoding
		ByteBuffer byteBuffer = fbsEncoder.encodeResponse(clientKey, isLast, resultType);
		// FBS - Decoding
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		switch (fbsDecoder.init(byteBuffer)){
		case MessagePayloadFbs.ResponseFbs:
			ResponseFbs responseFbs = fbsDecoder.asResponseFbs();
			ResponseFbsEncoderTest.assertResponse(clientKey, isLast, resultType, responseFbs);
			break;
		default:
			throw new AssertionError();
		}

	}

	@Test
	public void testAggregateOrderBookUpdate(){
		AggregateOrderBookUpdateSbeEncoder sbeEncoder = new AggregateOrderBookUpdateSbeEncoder();
		AggregateOrderBookUpdateSbeDecoder sbeDecoder = new AggregateOrderBookUpdateSbeDecoder();
		
		// Given
		int refSeqNum = 1001;
		long refSecSid = 40000;
		BooleanType refIsSnapshot = BooleanType.FALSE;
		int refEntryCount = 4;
		long refQuantity = 10000;
		UpdateAction refAction = UpdateAction.NEW;
		EntryEncoder entryGroup = sbeEncoder.wrap(buffer, 0)
				.isSnapshot(refIsSnapshot)
				.seqNum(refSeqNum)
				.secSid(refSecSid)
				.entryCount(refEntryCount);
		for (int i = 0; i < refEntryCount; i++){
			int price = 150050 + i * 10;
			Side side = Side.BUY;
			EntryType entryType = EntryType.BID;
			if (i > 2){
				entryType = EntryType.OFFER;
				side = Side.SELL;
			}
			entryGroup.next().entryType(entryType)
			.side(side)
			.numOrders(1)
			.price(price)
			.tickLevel(price)
			.quantity(refQuantity)
			.transactTime(LocalTime.now().toNanoOfDay())
			.updateAction(refAction);
		}
		
		sbeDecoder.wrap(buffer, 0, AggregateOrderBookUpdateSbeDecoder.BLOCK_LENGTH, AggregateOrderBookUpdateSbeDecoder.SCHEMA_VERSION);

		ByteBuffer suppliedBuffer = ByteBuffer.allocate(1024);
		ByteBuffer byteBuffer = fbsEncoder.encodeAggregateOrderBookUpdate(suppliedBuffer, headerDecoder, sbeDecoder);
		
		// FBS - Decoding
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		switch (fbsDecoder.init(byteBuffer)){
		case MessagePayloadFbs.AggregateOrderBookUpdateFbs:
			AggregateOrderBookUpdateFbs fbs = fbsDecoder.asAggregateOrderBookUpdateFbs();
			assertEquals(refSeqNum, fbs.seqNum());
			assertEquals(refSecSid, fbs.secSid());
			assertEquals((refIsSnapshot == BooleanType.TRUE) ? true : false, fbs.isSnapshot());
			assertEquals(refEntryCount, fbs.entriesLength());
			for (int i = 0; i < fbs.entriesLength(); i++){
				AggregateOrderBookUpdateEntryFbs entry = fbs.entries(i);
				assertEquals(refQuantity, entry.quantity());
				assertEquals(refAction.value(), entry.updateAction());
				if (i > 2){
					assertEquals(EntryType.OFFER.value(), entry.entryType());
					assertEquals(Side.SELL.value(), entry.side());
				}
				else{
					assertEquals(EntryType.BID.value(), entry.entryType());
					assertEquals(Side.BUY.value(), entry.side());					
				}
			}
			break;
		default:
			throw new AssertionError();
		}
	}
	
	@Test
	public void testOrderBookSnapshotUpdate(){
		OrderBookSnapshotSbeEncoder sbeEncoder = new OrderBookSnapshotSbeEncoder();
		OrderBookSnapshotSbeDecoder sbeDecoder = new OrderBookSnapshotSbeDecoder();
		
		// Given
		int refSeqNum = 1001;
		long refSecSid = 40000;
		int refAskCount = 4;
		int refBidCount = 5;
		int refQuantity = 10000;
		long refTimestamp = 12345;
		
		sbeEncoder.wrap(buffer, 0).secSid(refSecSid).seqNum(refSeqNum).transactTime(refTimestamp);
		OrderBookSnapshotSbeEncoder.AskDepthEncoder askDepthEncoder = sbeEncoder.askDepthCount(refAskCount);
		for (int i = 0; i < refAskCount; i++) {
			askDepthEncoder = askDepthEncoder.next().price(10000 + i).quantity(refQuantity).tickLevel(i);
		}
		OrderBookSnapshotSbeEncoder.BidDepthEncoder bidDepthEncoder = sbeEncoder.bidDepthCount(refBidCount);
		for (int i = 0; i < refBidCount; i++) {
			bidDepthEncoder = bidDepthEncoder.next().price(20000 + i).quantity(refQuantity).tickLevel(i);
		}
		
		sbeDecoder.wrap(buffer, 0, OrderBookSnapshotSbeEncoder.BLOCK_LENGTH, OrderBookSnapshotSbeEncoder.SCHEMA_VERSION);

		ByteBuffer suppliedBuffer = ByteBuffer.allocate(1024);
		ByteBuffer byteBuffer = fbsEncoder.encodeOrderBookSnapshot(suppliedBuffer, headerDecoder, sbeDecoder);
		
		// FBS - Decoding
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		switch (fbsDecoder.init(byteBuffer)){
		case MessagePayloadFbs.OrderBookSnapshotFbs:
			OrderBookSnapshotFbs fbs = fbsDecoder.asOrderBookSnapshotFbs();
			assertEquals(refSeqNum, fbs.seqNum());
			assertEquals(refSecSid, fbs.secSid());
			assertEquals(refTimestamp, fbs.transactTime());
			assertEquals(refAskCount, fbs.askDepthLength());
			for (int i = 0; i < fbs.askDepthLength(); i++){
				OrderBookSnapshotEntryFbs entry = fbs.askDepth(i);
				assertEquals(refQuantity, entry.quantity());
				assertEquals(10000 + i, entry.price());
				assertEquals(i, entry.tickLevel());
			}
			assertEquals(refBidCount, fbs.bidDepthLength());
			for (int i = 0; i < fbs.bidDepthLength(); i++){
				OrderBookSnapshotEntryFbs entry = fbs.bidDepth(i);
				assertEquals(refQuantity, entry.quantity());
				assertEquals(20000 + i, entry.price());
				assertEquals(i, entry.tickLevel());
			}
			break;
		default:
			throw new AssertionError();
		}
	}

	@Test
	public void testRequest(){
		RequestSbeEncoder sbeEncoder = new RequestSbeEncoder();
		RequestSbeDecoder sbeDecoder = new RequestSbeDecoder();

		// Given
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;
		Request request = Request.of(anySinkId,
									 refRequestType,
									 refParameters).clientKey(refClientKey);
		RequestSender.encodeRequestOnly(buffer, 0, sbeEncoder, request, null, null, null);
		sbeDecoder.wrap(buffer, 0, RequestSbeDecoder.BLOCK_LENGTH, RequestSbeDecoder.SCHEMA_VERSION);
		
		// FBS - Encoding
		ByteBuffer byteBuffer = fbsEncoder.encodeRequest(sbeDecoder);

		// FBS - Decoding
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		switch (fbsDecoder.init(byteBuffer)){
		case MessagePayloadFbs.RequestFbs:
			RequestFbs requestFbs = fbsDecoder.asRequestFbs();
			RequestFbsEncoderTest.assertRequest(request, requestFbs);
			break;
		default:
			throw new AssertionError();
		}
	}
	
	@Test
	public void testSecurity(){
		SecuritySbeEncoder sbeEncoder = new SecuritySbeEncoder();
		SecuritySbeDecoder sbeDecoder = new SecuritySbeDecoder();
		
		long refSid = 12345;
		String refCode = "MAX!";
		SecurityType refType = SecurityType.STOCK;
		int refExchangeSid = 2;
		long refUndSecSid = 56789;
		PutOrCall refPutOrCall = PutOrCall.CALL;
		OptionStyle refStyle = OptionStyle.ASIAN;
		int refStrikePrice = 123456;
		int refConvRatio = 1000;
		int refIssuerSid = 1;
		LocalDate refMaturity = LocalDate.of(2016, 3, 31);
		int refLotSize = 10000;
		boolean refIsAlgo = true;
		
		Security security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, SpreadTableBuilder.get(refType));
		SecuritySender.encodeSecurityOnly(buffer, 0, stringBuffer, sbeEncoder, security);
		sbeDecoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);
		
		// FBS - Encoding
		ByteBuffer byteBuffer = fbsEncoder.encodeSecurity(sbeDecoder);
		
		// FBS - Decoding
		// Find out the payload(message) type
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		switch (fbsDecoder.init(byteBuffer)){
		case MessagePayloadFbs.SecurityFbs:
			SecurityFbs securityFbs = fbsDecoder.asSecurityFbs();
			SecurityFbsEncoderTest.assertSecurity(security, securityFbs);
			break;
		default:
			throw new AssertionError();
		}
	}
	
	@Test
	public void testPosition(){
		PositionSbeEncoder sbeEncoder = new PositionSbeEncoder();
		PositionSbeDecoder sbeDecoder = new PositionSbeDecoder();
		
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		long entitySid = 11111;
		EntityType entityType = EntityType.SECURITY;
		PutOrCall putOrCall = PutOrCall.NULL_VAL;
		long buyQty = 200;
		double buyNotional = 200000;
		long openPosition = 150;
		long openCallPosition = 0;
		long openPutPosition = 0;
		long sellQty = 50;
		double sellNotional = 50000;
		long osBuyQty = 0;
		double osBuyNotional = 0;
		long osSellQty = 0;
		double osSellNotional = 0;
		double fees = 0;
		double commission = 0;
		double totalPnl = 0 ;
		double avgBuyPrice = 0;
		double avgSellPrice = 0;
		double mtmBuyPrice = 0;
		double mtmSellPrice = 0;
		double capUsed = 0;
		double maxCapUsed = 0;
		int tradeCount = 111;
		SecurityPositionDetails details = SecurityPositionDetails.of(schedule, 
				entitySid, 
				entityType, 
				putOrCall,
				openPosition, 
				openCallPosition,
				openPutPosition,
				buyQty, 
				buyNotional, 
				sellQty, 
				sellNotional, 
				osBuyQty, 
				osBuyNotional, 
				osSellQty, 
				osSellNotional,
				capUsed,
				maxCapUsed,
				fees, 
				commission, 
				totalPnl, 
				avgBuyPrice, 
				avgSellPrice, 
				mtmBuyPrice, 
				mtmSellPrice, 
				tradeCount);
		PositionSender.encodeSecurityPositionOnly(buffer, 0, stringBuffer, sbeEncoder, details);
		sbeDecoder.wrap(buffer, 0, PositionSbeDecoder.BLOCK_LENGTH, PositionSbeDecoder.SCHEMA_VERSION);
		
		// FBS - Encoding
		ByteBuffer suppliedBuffer = ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE);
		ByteBuffer byteBuffer = fbsEncoder.encodePosition(suppliedBuffer, headerDecoder, sbeDecoder);
		
		// FBS - Decoding
		// Find out the payload(message) type
		AtomicInteger expected = new AtomicInteger(1);
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		switch (fbsDecoder.init(byteBuffer)){
		case MessagePayloadFbs.PositionFbs:
			PositionFbs positionFbs = fbsDecoder.asPositionFbs();
			if (positionFbs.tradeCount() == tradeCount){
				expected.decrementAndGet();
			}
			PositionFbsEncoderTest.assertPosition(details, positionFbs);
			break;
		default:
			throw new AssertionError();
		}
		assertEquals(0, expected.get());
	}
}
