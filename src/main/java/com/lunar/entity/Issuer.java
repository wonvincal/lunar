package com.lunar.entity;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.IssuerSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.IssuerSender;

import org.agrona.MutableDirectBuffer;

public class Issuer extends Entity {
    public static long INVALID_ISSUER_SID = -1;
    public static Issuer INVALID = of(INVALID_ISSUER_SID, "", "");
    
    public static Issuer of(final long sid, final String code, final String name) {
        return new Issuer(sid, code, name);
    }
    
    
    private String code;
    private String name;
    
    public Issuer(final long sid, final String code, final String name) {
        super(sid);
        this.code = code;
        this.name = name;
    }
    
    public String code() {
        return code;
    }
    public Issuer code(final String code) {
        this.code = code;
        return this;
    }
    
    public String name() {
        return name;
    }
    public Issuer name(final String name) {
        this.name = name;
        return this;
    }

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return IssuerSender.encodeIssuerOnly(buffer, offset, stringBuffer, encoder.issuerSbeEncoder(), this);
    }
    @Override
    public TemplateType templateType(){
        return TemplateType.ISSUER;
    }

    @Override
    public short blockLength() {
        return IssuerSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int expectedEncodedLength() {
        return IssuerSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    public int schemaId() {
        return IssuerSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return IssuerSbeEncoder.SCHEMA_VERSION;
    } 
}
