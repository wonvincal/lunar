package com.lunar.message.binary;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;

public class MessageHeaderUtil {
	public static MessageHeaderDecoder headerOf(MutableDirectBuffer buffer, int offset, int blockLength, int version, int templateId, int seq, int payloadLength, byte senderSinkId, byte dstSinkId){
		MessageHeaderEncoder encoder = new MessageHeaderEncoder();
		encoder.wrap(buffer, offset)
			.senderSinkId(senderSinkId)
			.dstSinkId(dstSinkId)
			.blockLength(blockLength)
			.version(version)
			.templateId(templateId)
			.seq(seq)
			.payloadLength(payloadLength);
		MessageHeaderDecoder decoder = new MessageHeaderDecoder();
		return decoder.wrap(buffer, offset);
	}
}
