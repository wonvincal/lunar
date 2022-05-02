package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class IssuerFbsEncoder {
	static final Logger LOG = LogManager.getLogger(IssuerFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, IssuerSbeDecoder issuer){
		int limit = issuer.limit();
		stringBuffer.clear();
		stringBuffer.limit(issuer.getCode(stringBuffer.array(), 0));
		int codeOffset = builder.createString(stringBuffer);

		IssuerFbs.startIssuerFbs(builder);
		IssuerFbs.addSid(builder, issuer.sid());
		IssuerFbs.addCode(builder, codeOffset);
		issuer.limit(limit);
		return IssuerFbs.endIssuerFbs(builder);
	}
}

