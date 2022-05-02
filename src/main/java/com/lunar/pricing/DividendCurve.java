package com.lunar.pricing;

import java.time.LocalDate;

import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.DividendCurveSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.DividendCurveSbeEncoder.PointsEncoder;
import com.lunar.message.sender.PricingSender;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.agrona.MutableDirectBuffer;

public class DividendCurve implements SbeEncodable {
    private static final int INITIAL_CAPACITY = 10;
    
	private long secSid;
	private int numPoints;
	private LongArrayList dates;
	private IntArrayList amounts;

    public DividendCurve(final long secSid) {
        this(secSid, INITIAL_CAPACITY);
    }

	public DividendCurve(final long secSid, final int initialCapacity) {
		this.secSid = secSid;
		this.numPoints = 0;
        amounts = IntArrayList.wrap(new int[initialCapacity]);
        dates = LongArrayList.wrap(new long[initialCapacity]);
        amounts.size(0);
        dates.size(0);
	}

	public long secSid() {
		return secSid;
	}
	
	public int[] amounts() {
		return amounts.elements();
	}
	
	public long[] dates() {
		return dates.elements();
	}
	
	public void addPoint(final LocalDate date, final int amount) {
	    addPoint(date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth(), amount);
	}

    public void addPoint(final long date, final int amount) {
        dates.add(date);
        amounts.add(amount);
        numPoints++;
    }
	
	public int numPoints() {
	    return numPoints;
	}

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
	    return PricingSender.encodeDividendCurveWithoutHeader(buffer, offset, stringBuffer, encoder.dividendCurveSbeEncoder(), this);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.DIVIDEND_CURVE;
	}

	@Override
	public short blockLength() {
		return DividendCurveSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
	    return DividendCurveSbeEncoder.BLOCK_LENGTH + 
	            PointsEncoder.sbeHeaderSize() + 
                PointsEncoder.sbeBlockLength() * numPoints();
	}

    @Override
    public int schemaId() {
        return DividendCurveSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return DividendCurveSbeEncoder.SCHEMA_VERSION;
    }

}
