package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import com.lunar.message.io.sbe.MessageHeaderDecoder;

public class CodecUtil {
	public static <T extends Decoder> void decode(MessageHeaderDecoder messageHeader, Frame frame, T decoder){
		final DirectBuffer buffer = frame.buffer();
		messageHeader.wrap(buffer, frame.offset());
		decoder.decode(buffer, frame.offset() + messageHeader.encodedLength(), messageHeader);		
	}
    public static void uint8Put(final MutableDirectBuffer buffer, final int index, final short value)
    {
        buffer.putByte(index, (byte)value);
    }
    public static short uint8Get(final DirectBuffer buffer, final int index)
    {
        return (short)(buffer.getByte(index) & 0xFF);
    }
}
