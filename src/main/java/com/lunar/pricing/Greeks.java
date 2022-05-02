package com.lunar.pricing;

import org.agrona.MutableDirectBuffer;

import com.lunar.core.SbeDecodable;
import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.GreeksSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.PricingSender;

public class Greeks implements SbeEncodable, SbeDecodable<GreeksSbeDecoder> {
	public static final int NUM_DP_DELTA = 5;
	public static final int NUM_DP_GAMMA = 5;
	public static final int NUM_DP_REFSPOT = 3;
    public static final int NULL_VALUE = 0;
    private long secSid;
    private int delta;    
    private int vega;
    private int gamma;
    private int impliedVol;
    private int bidImpliedVol;
    private int askImpliedVol;
    private int refSpot;
    private int bid;
    private int ask;
    
    static public Greeks of(final long secSid) {
    	final Greeks greeks = new Greeks();
    	greeks.secSid(secSid);
    	return greeks;
    }
    
    public Greeks() {
        
    }
    
    public Greeks secSid(final long secSid) {
        this.secSid = secSid;
        return this;
    }
    
    public long secSid() {
        return this.secSid;
    }
    
    public Greeks delta(final int delta) {
        this.delta = delta;
        return this;
    }
    
    public int delta() {
        return delta;
    }

    public Greeks gamma(final int gamma) {
        this.gamma = gamma;
        return this;
    }
    
    public int gamma() {
        return gamma;
    }
    
    public Greeks vega(final int vega) {
        this.vega = vega;
        return this;
    }
    
    public int vega() {
        return vega;
    }
    
    public Greeks impliedVol(final int impliedVol) {
        this.impliedVol = impliedVol;
        return this;
    }
    
    public int impliedVol() {
        return impliedVol;
    }

    public Greeks bidImpliedVol(final int impliedVol) {
        this.bidImpliedVol = impliedVol;
        return this;
    }
    
    public int bidImpliedVol() {
        return bidImpliedVol;
    }

    public Greeks askImpliedVol(final int impliedVol) {
        this.askImpliedVol = impliedVol;
        return this;
    }
    
    public int askImpliedVol() {
        return askImpliedVol;
    }

    public Greeks refSpot(final int refSpot) {
        this.refSpot = refSpot;
        return this;
    }
    
    public int refSpot() {
        return refSpot;
    }
    
    public int bid() {
        return bid;
    }
    
    public Greeks bid(final int bid) {
        this.bid = bid;
        return this;
    }
    
    public int ask() {
        return ask;
    }
    
    public Greeks ask(final int ask) {
        this.ask = ask;
        return this;
    }

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return PricingSender.encodeGreeksWithoutHeader(stringBuffer, offset, encoder.greeksSbeEncoder(), this);
    }

    @Override
    public TemplateType templateType() {
        return TemplateType.GREEKS;
    }

    @Override
    public short blockLength() {
        return GreeksSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    public int expectedEncodedLength() {
        return GreeksSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int schemaId() {
        return GreeksSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return GreeksSbeEncoder.SCHEMA_VERSION;
    }
    
    
    public void copyFrom(final Greeks other) {
        this.secSid = other.secSid;
        this.delta = other.delta;
        this.vega = other.vega;
        this.gamma = other.gamma;
        this.impliedVol = other.impliedVol;
        this.refSpot = other.refSpot;
        this.bid = other.bid;
        this.ask = other.ask;
    }
    
    public void clear(){
        this.delta = NULL_VALUE;
        this.vega = NULL_VALUE;
        this.gamma = NULL_VALUE;
        this.impliedVol = NULL_VALUE;
        this.refSpot = NULL_VALUE;
        this.bid = NULL_VALUE;
        this.ask = NULL_VALUE;
    }

    @Override
    public void decodeFrom(final GreeksSbeDecoder decoder) {
        this.secSid(decoder.secSid());
        this.refSpot(decoder.refSpot());
        this.ask(decoder.ask());
        this.bid(decoder.bid());
        this.delta(decoder.delta());
        this.gamma(decoder.gamma());
        this.vega(decoder.vega());
        this.impliedVol(decoder.impliedVol());
        this.bidImpliedVol(decoder.bidImpliedVol());
        this.askImpliedVol(decoder.askImpliedVol());
    }

}
