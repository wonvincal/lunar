package com.lunar.message.binary;

import com.lunar.message.io.sbe.MessageHeaderEncoder;

import org.agrona.MutableDirectBuffer;

/**
 * Encoders of all message types wrapping in one class.  This class links up each encoder with a header.
 * @author wongca
 *
 */
public final class MessageEncoder {
	public static int encodeHeader(Frame frame, MessageHeaderEncoder header, int blockLength, int templateId, int schemaId, int schemaVersion, int senderSinkId, int dstSinkId, int seq){
		return encodeHeader(frame.buffer(), frame.offset(), header, blockLength, templateId, schemaId, schemaVersion, senderSinkId, dstSinkId, seq);
	}
	
	public static int encodeHeader(MutableDirectBuffer buffer, int offset, MessageHeaderEncoder header, int blockLength, int templateId, int schemaId, int schemaVersion, int senderSinkId, int dstSinkId, int seq){
		return header.wrap(buffer, offset)
				.blockLength(blockLength)
				.templateId(templateId)
				.schemaId(schemaId)
				.version(schemaVersion)
				.senderSinkId((byte)senderSinkId)
				.dstSinkId((byte)dstSinkId)
				.seq(seq)
				.encodedLength();
	}

	public static int encodeHeader(MutableDirectBuffer buffer, int offset, MessageHeaderEncoder header, int blockLength, int templateId, int schemaId, int schemaVersion, int senderSinkId, int dstSinkId, int seq, int payloadLength){
		return header.wrap(buffer, offset)
				.blockLength(blockLength)
				.templateId(templateId)
				.schemaId(schemaId)
				.version(schemaVersion)
				.senderSinkId((byte)senderSinkId)
				.dstSinkId((byte)dstSinkId)
				.seq(seq)
				.payloadLength(payloadLength)
				.encodedLength();
	}

	public static void encodeDstSinkId(MutableDirectBuffer buffer, int offset, MessageHeaderEncoder header, int dstSinkId){
		header.wrap(buffer, offset).dstSinkId((byte)dstSinkId);
	}

}
