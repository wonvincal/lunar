package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

/**
 * A useless class now, to be removed later
 * @author wongca
 *
 */
public class EntityDecoderManager {
    private final Decoder[] decoders;

    public static EntityDecoderManager of(Decoder[] decoders){
        return new EntityDecoderManager(decoders);
    }

    EntityDecoderManager(Decoder[] decoders){
        this.decoders = decoders;
    }

    public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header){
        decoders[header.templateId()].decode(buffer, offset, header);
    }

    public void decodeWithClientKey(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
        decoders[embeddedTemplateId].decodeWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, embeddedOffset, embeddedBlockLength, embeddedTemplateId);
    }

    public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey){
        return decoders[header.templateId()].dump(buffer, offset, header);
    }

    public String dumpWithClientKey(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
        return decoders[embeddedTemplateId].dumpWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, embeddedOffset, embeddedBlockLength, embeddedTemplateId);
    }
}
