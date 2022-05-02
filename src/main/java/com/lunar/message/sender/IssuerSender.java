package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.Issuer;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.IssuerSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;
import com.lunar.util.StringUtil;

import org.agrona.MutableDirectBuffer;

public class IssuerSender {
	static final Logger LOG = LogManager.getLogger(IssuerSender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = IssuerSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final IssuerSbeEncoder sbe = new IssuerSbeEncoder();

	public static IssuerSender of(MessageSender msgSender){
		return new IssuerSender(msgSender);
	}
	
	IssuerSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public void sendIssuer(MessageSinkRef sinks[], Issuer issuer) {
		int size = encodeIssuer(msgSender,
				0,
				msgSender.buffer(),
				0,
				sbe,
				issuer);
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
	public void sendIssuer(MessageSinkRef sink, Issuer issuer){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeIssuer(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					issuer);
			bufferClaim.commit();
			return;
		}
		int size = encodeIssuer(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				issuer);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + IssuerSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + IssuerSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + IssuerSbeEncoder.BLOCK_LENGTH;
	}
	
	public static int encodeIssuerOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			final MutableDirectBuffer stringBuffer,
			IssuerSbeEncoder sbe,
			Issuer issuer){
		sbe.wrap(dstBuffer, offset)
		.lastUpdateSeq(issuer.lastUpdateSeq())
		.sid((int)issuer.sid());
		sbe.putCode(StringUtil.copyAndPadSpace(issuer.code(), 
				IssuerSbeEncoder.codeCharacterEncoding(), 
				stringBuffer, 
				IssuerSbeEncoder.codeLength()).byteArray(), 0);
        sbe.putName(StringUtil.copyAndPadSpace(issuer.name(), 
                IssuerSbeEncoder.nameCharacterEncoding(), 
                stringBuffer, 
                IssuerSbeEncoder.nameLength()).byteArray(), 0);
		return sbe.encodedLength();		
	}
	
	public static int encodeIssuer(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			IssuerSbeEncoder sbe,
			Issuer issuer){
		
		int payloadLength = encodeIssuerOnly(buffer, 
				offset + sender.headerSize(), 
				sender.stringBuffer(), 
				sbe, 
				issuer);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				IssuerSbeEncoder.BLOCK_LENGTH, 
				IssuerSbeEncoder.TEMPLATE_ID, 
				IssuerSbeEncoder.SCHEMA_ID,
				IssuerSbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength;
	}
}
