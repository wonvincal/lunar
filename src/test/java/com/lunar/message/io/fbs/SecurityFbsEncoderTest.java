package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.sender.SecuritySender;
import com.lunar.service.ServiceConstant;
import com.lunar.util.DateUtil;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SecurityFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(SecurityFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final SecuritySbeEncoder sbeEncoder = new SecuritySbeEncoder();
	private final SecuritySbeDecoder sbeDecoder = new SecuritySbeDecoder();
	private final UnsafeBuffer sbeStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
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
		boolean refIsAlgo = false;
		SpreadTable refSpreadTable = SpreadTableBuilder.get(refType);
		
		Security security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, refSpreadTable);
		SecuritySender.encodeSecurityOnly(buffer, 0, sbeStringBuffer, sbeEncoder, security);
		sbeDecoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);
		
		int offset = SecurityFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		SecurityFbs securityFbs = SecurityFbs.getRootAsSecurityFbs(builder.dataBuffer());
		assertSecurity(security, securityFbs);

		builder.init(builder.dataBuffer());
		refSid = 12145;
		refCode = "MAX2!";
		refType = SecurityType.WARRANT;
		refExchangeSid = 1;
		refUndSecSid = 56189;
		refPutOrCall = PutOrCall.PUT;
		refStyle = OptionStyle.AMERICAN;
		refStrikePrice = 123156;
		refConvRatio = 1010;
		refMaturity = LocalDate.of(2016, 5, 31);
		refIssuerSid = 2;
		refLotSize = 10000;
		refIsAlgo = true;
		refSpreadTable = SpreadTableBuilder.get(refType);
		
		security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, refSpreadTable);
		SecuritySender.encodeSecurityOnly(buffer, 0, sbeStringBuffer, sbeEncoder, security);
		sbeDecoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);

		offset = SecurityFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		securityFbs = SecurityFbs.getRootAsSecurityFbs(builder.dataBuffer());
		assertSecurity(security, securityFbs);
	}
	
	@Test
	public void testEmptyMaturity(){
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
		int refLotSize = 12345;
		boolean refIsAlgo = false;
		SpreadTable refSpreadTable = SpreadTableBuilder.get(refType); 
		
		Security security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, refSpreadTable);
		SecuritySender.encodeSecurityOnly(buffer, 0, sbeStringBuffer, sbeEncoder, security);
		sbeDecoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);
		
		int offset = SecurityFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		SecurityFbs securityFbs = SecurityFbs.getRootAsSecurityFbs(builder.dataBuffer());
		assertSecurity(security, securityFbs);

		builder.init(builder.dataBuffer());
		refSid = 12145;
		refCode = "MAX2!";
		refType = SecurityType.WARRANT;
		refExchangeSid = 1;
		refUndSecSid = 56189;
		refPutOrCall = PutOrCall.PUT;
		refStyle = OptionStyle.AMERICAN;
		refStrikePrice = 123156;
		refConvRatio = 1010;
		refIssuerSid = 1;
		refLotSize = 54321;
		refIsAlgo = true;
		refSpreadTable = SpreadTableBuilder.get(refType);

		security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.empty(), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, refSpreadTable);
		SecuritySender.encodeSecurityOnly(buffer, 0, sbeStringBuffer, sbeEncoder, security);
		sbeDecoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);

		offset = SecurityFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		securityFbs = SecurityFbs.getRootAsSecurityFbs(builder.dataBuffer());
		assertSecurity(security, securityFbs);
	}

	public static void assertSecurity(Security security, SecurityFbs securityFbs){
		assertEquals(security.sid(), securityFbs.sid());
		assertEquals(security.convRatio(), securityFbs.conversionRatio());
		assertEquals(security.securityType().value(), securityFbs.securityType());
		assertEquals(security.exchangeSid(), securityFbs.exchangeSid());
		assertEquals(security.underlyingSid(), securityFbs.undSid());
		assertEquals(security.putOrCall().value(), securityFbs.putOrCall());
		assertEquals(security.optionStyle().value(), securityFbs.style());
		assertEquals(security.code(), securityFbs.code().trim());
		assertEquals(security.issuerSid(), securityFbs.issuerSid());
		assertEquals(security.lotSize(), securityFbs.lotSize());
		assertEquals(security.isAlgo(), securityFbs.isAlgo());
		assertEquals(security.spreadTable().id(), securityFbs.spreadTableCode());
		if (security.maturity().isPresent()){
			assertEquals(DateUtil.toIntDate(security.maturity()), securityFbs.maturity());
		}
		else{
			assertEquals(0, securityFbs.maturity());			
		}
	}
}
