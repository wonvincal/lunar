package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class SecurityFbsEncoder {
	static final Logger LOG = LogManager.getLogger(SecurityFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, SecuritySbeDecoder security){
		int limit = security.limit();
		stringBuffer.clear();
		stringBuffer.limit(security.getCode(stringBuffer.array(), 0));
		int codeOffset = builder.createString(stringBuffer);

		SecurityFbs.startSecurityFbs(builder);
		SecurityFbs.addSid(builder, security.sid());
		SecurityFbs.addExchangeSid(builder, security.exchangeSid());
		SecurityFbs.addSecurityType(builder, security.securityType().value());
		SecurityFbs.addSpreadTableCode(builder, security.spreadTableCode());
		SecurityFbs.addLotSize(builder, security.lotSize());
		SecurityFbs.addPreviousClosingPrice(builder, security.previousClosingPrice());
		SecurityFbs.addListingDate(builder, security.listingDate());
		SecurityFbs.addConversionRatio(builder, security.conversionRatio());
		SecurityFbs.addStrikePrice(builder, security.strikePrice());
		SecurityFbs.addPutOrCall(builder, security.putOrCall().value());
		SecurityFbs.addStyle(builder, security.style().value());
		SecurityFbs.addUndSid(builder, security.undSid());
		SecurityFbs.addMaturity(builder, (security.maturity() == SecuritySbeDecoder.maturityNullValue()) ? 0 : security.maturity());
		SecurityFbs.addCode(builder, codeOffset);
		SecurityFbs.addIssuerSid(builder, security.issuerSid());
		SecurityFbs.addIsAlgo(builder, security.isAlgo() == BooleanType.TRUE);
		security.limit(limit);
		return SecurityFbs.endSecurityFbs(builder);
	}
}

