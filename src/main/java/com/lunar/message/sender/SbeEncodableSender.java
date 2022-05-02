package com.lunar.message.sender;

import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;

public class SbeEncodableSender {
    private final MessageSender msgSender;
    private final EntityEncoder encoder;
    
    public static SbeEncodableSender of(MessageSender msgSender){
        return new SbeEncodableSender(msgSender);
    }
    
    SbeEncodableSender(final MessageSender msgSender){
        this.msgSender = msgSender;
        this.encoder = EntityEncoder.of(); 
    }
    
    public int encodeSbeEncodableWithHeader(final int dstSinkId, final int offset, final SbeEncodable sbeEncodable) {
        final int payloadLength = sbeEncodable.encode(encoder, msgSender.buffer(), offset + msgSender.headerSize(), msgSender.stringBuffer());
        msgSender.encodeHeader(dstSinkId,
                msgSender.buffer(),
                offset,
                sbeEncodable.blockLength(),
                sbeEncodable.templateType().value(),
                sbeEncodable.schemaId(),
                sbeEncodable.schemaVersion(),
                payloadLength);
        return msgSender.headerSize() + payloadLength;                
    }
    
    public void sendSbeEncodable(final MessageSinkRef[] sinks, final SbeEncodable sbeEncodable) {
        final int size = encodeSbeEncodableWithHeader(0, 0, sbeEncodable);
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }        
    }
    
    public void sendSbeEncodable(final MessageSinkRef persistSink, final MessageSinkRef[] sinks, final SbeEncodable sbeEncodable) {
        final int size = encodeSbeEncodableWithHeader(persistSink.sinkId(), 0, sbeEncodable);
        msgSender.trySend(persistSink, msgSender.buffer(), 0, size);
        for (final MessageSinkRef sink : sinks) {
            if (sink == null)
                break;
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.send(sink, msgSender.buffer(), 0, size);
        }        
    }
    
    public long sendSbeEncodable(final MessageSinkRef sink, final SbeEncodable sbeEncodable) {
        final MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(MessageHeaderEncoder.ENCODED_LENGTH + sbeEncodable.expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK) {
            final int payloadLength = sbeEncodable.encode(encoder, bufferClaim.buffer(), bufferClaim.offset() + msgSender.headerSize(), msgSender.stringBuffer());
            msgSender.encodeHeader(sink.sinkId(),
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    sbeEncodable.blockLength(),
                    sbeEncodable.templateType().value(),
                    sbeEncodable.schemaId(),
                    sbeEncodable.schemaVersion(),
                    payloadLength);            
            bufferClaim.commit();
            return MessageSink.OK;
        }
        final int size = encodeSbeEncodableWithHeader(sink.sinkId(), 0, sbeEncodable);
        return msgSender.send(sink, msgSender.buffer(), 0, size);
    }

    public long trySendSbeEncodable(final MessageSinkRef sink, final SbeEncodable sbeEncodable) {
        final MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(MessageHeaderEncoder.ENCODED_LENGTH + sbeEncodable.expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK) {
            final int payloadLength = sbeEncodable.encode(encoder, bufferClaim.buffer(), bufferClaim.offset() + msgSender.headerSize(), msgSender.stringBuffer());
            msgSender.encodeHeader(sink.sinkId(),
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    sbeEncodable.blockLength(),
                    sbeEncodable.templateType().value(),
                    sbeEncodable.schemaId(),
                    sbeEncodable.schemaVersion(),
                    payloadLength);            
            bufferClaim.commit();
            return MessageSink.OK;
        }
        final int size = encodeSbeEncodableWithHeader(sink.sinkId(), 0, sbeEncodable);
        return msgSender.trySend(sink, msgSender.buffer(), 0, size);
    }
}
