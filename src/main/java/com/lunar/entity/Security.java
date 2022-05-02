package com.lunar.entity;

import java.time.LocalDate;
import java.util.Optional;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.SecuritySender;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

/**
 * TODO Should we allow string to have length that is
 * longer than the max specified in SBE?
 * @author Calvin
 *
 */
public class Security extends Entity {
	public static long INVALID_SECURITY_SID = -1;
	public static Security INVALID = new Security(INVALID_SECURITY_SID, SecurityType.NULL_VAL, "", -1, INVALID_SECURITY_SID, Optional.empty(), ServiceConstant.NULL_LISTED_DATE, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, -1, 0, false, SpreadTableBuilder.get(SecurityType.NULL_VAL));

	private final SecurityType secType;
	private Long undSecSid = INVALID_SECURITY_SID;
	private final int exchSid;
	private final String code;
	private final SpreadTable spreadTable;
	private PutOrCall putOrCall;
	private OptionStyle optionStyle;
	private int convRatio;
	private int strikePrice;
	private int barrier;
	private Optional<LocalDate> maturity;
	private LocalDate listedDate;
	private int issuerSid =(int)Issuer.INVALID_ISSUER_SID;
	private int lotSize;
	private boolean isAlgo;
	private MessageSinkRef omesSink = MessageSinkRef.NA_INSTANCE;
	private MessageSinkRef mdsSink = MessageSinkRef.NA_INSTANCE;
	private MessageSinkRef mdsssSink = MessageSinkRef.NA_INSTANCE;

	public static Security of(long sid, SecurityType secType, String code, int exchangeSid, boolean algo, SpreadTable spreadTable){
		return new Security(sid, secType, code, exchangeSid, 1, algo, spreadTable);
	}

	public static Security of(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid, Optional<LocalDate> maturity, LocalDate listedDate, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean algo, SpreadTable spreadTable) {
		return new Security(sid, secType, code, exchangeSid, undSecSid, maturity, listedDate, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable);
	}

	public static Security of(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid, Optional<LocalDate> maturity, LocalDate listedDate, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, int barrier, boolean algo, SpreadTable spreadTable) {
		return new Security(sid, secType, code, exchangeSid, undSecSid, maturity, listedDate, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable);
	}

	public Security(final long sid, final SecurityType secType, final String code, final int exchangeSid, final int lotSize, final boolean algo, final SpreadTable spreadTable) {
		this(sid, secType, code, exchangeSid, INVALID_SECURITY_SID, Optional.empty(), ServiceConstant.NULL_LISTED_DATE, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, (int)SidManager.INVALID_SID, lotSize, algo, spreadTable);
	}

	public Security(final long sid, final SecurityType secType, final String code, final int exchangeSid, final long undSecSid, final Optional<LocalDate> maturity, final LocalDate listedDate, final PutOrCall putOrCall, final OptionStyle optionStyle, final int strikePrice, final int convRatio, final int issuerSid, final int lotSize, final boolean isAlgo, final SpreadTable spreadTable){
		this(sid, secType, code, exchangeSid, undSecSid, maturity, listedDate, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, 0, isAlgo, spreadTable);
	}
	
	public Security(final long sid, final SecurityType secType, final String code, final int exchangeSid, final long undSecSid, final Optional<LocalDate> maturity, final LocalDate listedDate, final PutOrCall putOrCall, final OptionStyle optionStyle, final int strikePrice, final int convRatio, final int issuerSid, final int lotSize, final int barrier, final boolean isAlgo, final SpreadTable spreadTable) {
		super(sid);
		this.spreadTable = spreadTable;
		this.secType = secType;
		this.code = code;
		this.exchSid = exchangeSid;
		this.undSecSid = undSecSid;
		this.maturity = maturity;
		this.listedDate = listedDate;
		this.putOrCall = putOrCall;
		this.optionStyle = optionStyle;
		this.strikePrice = strikePrice;
		this.convRatio = convRatio;		
		this.issuerSid = issuerSid;
		this.lotSize = lotSize;
		this.barrier = barrier;
		this.isAlgo = isAlgo;
	}	

	public SpreadTable spreadTable(){
		return spreadTable;
	}
	
	public long underlyingSid(){
		return undSecSid;
	}
	
	public int exchangeSid(){
		return exchSid;
	}
	
	public int issuerSid() {
	    return issuerSid;
	}
	
	public SecurityType securityType(){
		return secType;
	}

	public String code(){
		return code;
	}
	
    public Optional<LocalDate> maturity(){
        return maturity;	
    }

    public LocalDate listedDate(){
        return listedDate;
    }
    
    public PutOrCall putOrCall(){
        return putOrCall;	
    }

	public OptionStyle optionStyle() {
		return optionStyle;
	}

	public int convRatio() {
		return convRatio;
	}
	
	public int strikePrice() {
		return strikePrice;
	}
	
	public int barrier() {
		return barrier;
	}
	
	public int lotSize() {
	    return lotSize;
	}
	
	public boolean isAlgo() {
	    return isAlgo;
	}
	
	public MessageSinkRef omesSink(){
		return omesSink;
	}

	public MessageSinkRef mdsSink(){
		return mdsSink;
	}
	
	public MessageSinkRef mdsssSink() {
	    return mdsssSink;
	}
	
	public Security omesSink(MessageSinkRef ref){
		this.omesSink = ref;
		return this;
	}

	public Security mdsSink(MessageSinkRef ref){
		this.mdsSink = ref;
		return this;
	}
	
	public Security mdsssSink(MessageSinkRef ref) {
	    this.mdsssSink = ref;
	    return this;
	}
	
	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return SecuritySender.encodeSecurityOnly(buffer, offset, stringBuffer, encoder.securitySbeEncoder(), this);
	}
	@Override
	public TemplateType templateType(){
		return TemplateType.SECURITY;
	}

	@Override
	public short blockLength() {
		return SecuritySbeEncoder.BLOCK_LENGTH;
	}
	
	@Override
	public int expectedEncodedLength() {
		return SecuritySbeEncoder.BLOCK_LENGTH;
	}
	
    @Override
    public int schemaId() {
        return SecuritySbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return SecuritySbeEncoder.SCHEMA_VERSION;
    }
    
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(Security.class);
	public static long EXCHANGE_MASK = 0x0000_0007_0000_0000l;
	public static long EXCHANGE_SPECIFIC_ID_MASK = 0x0000_0000_FFFF_FFFFl;
	
	public static long buildSid(byte exchangeSid, int exchangeSpecificId){
		if (exchangeSid > 0x7 || exchangeSid < 0){
			throw new IllegalArgumentException("Exchange sid must be less than or equal to 7 [exchangeSid:" + exchangeSid + "]");
		}
		long secSid = exchangeSid;
		secSid = (secSid << 32) | (EXCHANGE_SPECIFIC_ID_MASK & exchangeSpecificId);
		return secSid;
	}
	
	public static int getExchangeSpecificId(long secSid){
		return (int)(secSid & EXCHANGE_SPECIFIC_ID_MASK);
	}
	
	public static byte getExchangeId(long secSid){
		return (byte)((secSid & EXCHANGE_MASK) >> 32);
	}

}
