package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class SecuritySenderTest {
	static final Logger LOG = LogManager.getLogger(SecuritySenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final SecuritySbeEncoder sbeEncoder = new SecuritySbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeDecode(){
		final int refSystemId = 1;
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.peekNextSeq();
		final long refSid = 12345;
		final String refCode = "MAX!";
		final SecurityType refType = SecurityType.STOCK;
		final int refExchangeSid = 2;
		final long refUndSecSid = 56789;
		final int refLastUpdateSeq = 5;
		final PutOrCall refPutOrCall = PutOrCall.CALL;
		final OptionStyle refStyle = OptionStyle.ASIAN;
		final int refStrikePrice = 123456;
		final int refConvRatio = 1000;
		final int refIssuerSid = 1;
		final int refLotSize = 1000;
		final boolean refIsAlgo = true;
		final SpreadTable refSpreadTable = SpreadTableBuilder.get(refType);
		final LocalDate refMaturity = LocalDate.of(2016, 3, 31);
		Security security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, refSpreadTable)
				.omesSink(MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.OrderManagementAndExecutionService, "test-omes"))
                .mdsssSink(MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.MarketDataSnapshotService, "test-mdsss"))
				.mdsSink(MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.MarketDataService, "test-mds"));
		security.lastUpdateSeq(refLastUpdateSeq);
		SecuritySender.encodeSecurity(sender, refDstSinkId, buffer, 0, sbeEncoder, security);
				
		MessageReceiver receiver = MessageReceiver.of();
		receiver.securityHandlerList().add(new Handler<SecuritySbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, MessageHeaderDecoder header,
							   SecuritySbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertSecurity(refLastUpdateSeq,
						refType,
						refUndSecSid,
						refExchangeSid,
						refCode,
						refPutOrCall,
						refConvRatio,
						refSpreadTable,
						codec);
				
				assertSecurity(security, codec);
			}
		});
		receiver.receive(buffer, 0);
	}
	
	public static void assertSecurity(int expectedLastUpdateSeq,
			SecurityType expectedSecurityType,
			long expectedUndSid,
			int expectedExchangeSid,
			String expectedCode,
			PutOrCall expectedPutOrCall,
			float expectedConvRatio,
			SpreadTable expectedSpreadTable,
			SecuritySbeDecoder actual){
		assertEquals(expectedLastUpdateSeq, actual.lastUpdateSeq());
		assertEquals(expectedSecurityType, actual.securityType());
		assertEquals(expectedUndSid, actual.undSid());
		assertEquals(expectedExchangeSid, actual.exchangeSid());
		assertEquals(expectedPutOrCall, actual.putOrCall());
		assertEquals(expectedConvRatio, actual.conversionRatio(), 0.001);
		assertEquals(expectedSpreadTable.id(), actual.spreadTableCode());
		ByteBuffer b = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
		actual.getCode(b.array(), 0);
		String string = new String(b.array(), 0, SecuritySbeDecoder.codeLength()).trim();
		assertEquals(expectedCode, string);
	}

	public static void assertSecurity(Security expected,
			SecuritySbeDecoder actual){
		assertEquals(expected.lastUpdateSeq(), actual.lastUpdateSeq());
		assertEquals(expected.securityType(), actual.securityType());
		assertEquals(expected.underlyingSid(), actual.undSid());
		assertEquals(expected.exchangeSid(), actual.exchangeSid());
		ByteBuffer b = ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE);
		actual.getCode(b.array(), 0);
		String string = new String(b.array(), 0, SecuritySbeDecoder.codeLength()).trim();
		assertEquals(expected.code(), string);
	}

}
