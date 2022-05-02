package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.Security;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;
import com.lunar.util.DateUtil;
import com.lunar.util.StringUtil;

import org.agrona.MutableDirectBuffer;

public class SecuritySender {
	static final Logger LOG = LogManager.getLogger(SecuritySender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = SecuritySbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final SecuritySbeEncoder sbe = new SecuritySbeEncoder();

	public static SecuritySender of(MessageSender msgSender){
		return new SecuritySender(msgSender);
	}
	
	SecuritySender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public void sendSecurity(MessageSinkRef sinks[], Security security) {
		int size = encodeSecurity(msgSender,
				0,
				msgSender.buffer(),
				0,
				sbe,
				security);
        for (MessageSinkRef sink : sinks){
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }
    }
	
	/**
	 * Send security.
	 * @param sender
	 * @param sink
	 * @param security
	 */
	public void sendSecurity(MessageSinkRef sink, Security security){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeSecurity(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					security);
			bufferClaim.commit();
			return;
		}
		int size = encodeSecurity(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				security);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + SecuritySbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + SecuritySbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + SecuritySbeEncoder.BLOCK_LENGTH;
	}
	
	public static int encodeSecurityOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			final MutableDirectBuffer stringBuffer,
			SecuritySbeEncoder sbe,
			Security security){
		sbe.wrap(dstBuffer, offset)
		.lastUpdateSeq(security.lastUpdateSeq())
		.sid(security.sid())
		.securityType(security.securityType())
		.exchangeSid(security.exchangeSid())
		.undSid(security.underlyingSid())
		.maturity(DateUtil.toSbeMaturityDate(security.maturity()))
		.listingDate(DateUtil.toSbeListedDate(security.listedDate()))
		.conversionRatio(security.convRatio())
		.issuerSid(security.issuerSid())
		.strikePrice(security.strikePrice())
		.lotSize(security.lotSize())
		.isAlgo(security.isAlgo() ? BooleanType.TRUE : BooleanType.FALSE)
		.putOrCall(security.putOrCall())
		.style(security.optionStyle())
		.spreadTableCode(security.spreadTable().id())
		.omesSinkId(security.omesSink().sinkId())
		.mdsSinkId(security.mdsSink().sinkId())
		.mdsssSinkId(security.mdsssSink().sinkId());
		
		sbe.putCode(StringUtil.copyAndPadSpace(security.code(), 
				SecuritySbeEncoder.codeCharacterEncoding(), 
				stringBuffer, 
				SecuritySbeEncoder.codeLength()).byteArray(), 0);
		return sbe.encodedLength();		
	}
	
	public static int encodeSecurity(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			SecuritySbeEncoder sbe,
			Security security){
		int payloadLength = encodeSecurityOnly(buffer, 
				offset + sender.headerSize(), 
				sender.stringBuffer(), 
				sbe, 
				security);
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				SecuritySbeEncoder.BLOCK_LENGTH, 
				SecuritySbeEncoder.TEMPLATE_ID, 
				SecuritySbeEncoder.SCHEMA_ID, 
				SecuritySbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength; 
	}
}
