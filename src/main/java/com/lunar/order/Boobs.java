package com.lunar.order;

import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.BoobsSender;

import org.agrona.MutableDirectBuffer;

public class Boobs implements SbeEncodable {
	private long secSid;
    private int bestBid;
    private int bestAsk;
    private int last;
    
    static public Boobs of() {
        return new Boobs();
    }
    
    static public Boobs of(final long secSid) {
        return new Boobs(secSid);
    }

    static public Boobs of(final long secSid, final int bestBid, final int bestAsk, final int last) {
        return new Boobs(secSid, bestBid, bestAsk, last);
    }
    
    public Boobs() {
        this(0, 0, 0, 0);
    }

    public Boobs(final long secSid) {
    	this(secSid, 0, 0, 0);
    }

    public Boobs(final long secSid, final int bestBid, final int bestAsk, final int last) {
    	this.secSid = secSid;
        this.bestAsk = bestAsk;
        this.bestBid = bestBid;
        this.last = last;
    }
    
    public long secSid() {
    	return secSid;
    }
    public Boobs secSid(final long secSid) {
    	this.secSid = secSid;
    	return this;
    }
    
    public int bestBid() {
        return bestBid;
    }
    public Boobs bestBid(final int bestBid) {
        this.bestBid = bestBid;
        return this;
    }
    
    public int bestAsk() {
        return bestAsk;
    }
    public Boobs bestAsk(final int bestAsk) {
        this.bestAsk = bestAsk;
        return this;
    }
    
    public int last() {
        return last;
    }
    public Boobs last(final int last) {
        this.last = last;
        return this;
    }

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return BoobsSender.encodeBoobsWithoutHeader(buffer, offset, encoder.boobsSbeEncoder(), secSid, bestBid, bestAsk, last);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.BOOBS;
	}

	@Override
	public short blockLength() {
		return BoobsSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
		return BoobsSbeDecoder.BLOCK_LENGTH;
	}

    @Override
    public int schemaId() {
        return BoobsSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return BoobsSbeEncoder.SCHEMA_VERSION;
    }
    
}
