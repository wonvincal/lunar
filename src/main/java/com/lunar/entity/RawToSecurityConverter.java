package com.lunar.entity;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.service.ServiceConstant;

public class RawToSecurityConverter implements EntityConverter<Security> {
	private final SidManager<String> sidManager;
	private final SidManager<String> issuerSidManager;
	
	public RawToSecurityConverter(final SidManager<String> sidManager, final SidManager<String> issuerSidManager) {
		this.sidManager = sidManager;
		this.issuerSidManager = issuerSidManager;
	}	
	
	public Security toEntity(String csv) {
		throw new UnsupportedOperationException();
	}
	
	public Security toEntity(ByteBuffer buffer){
		throw new UnsupportedOperationException();
	}

	@Override
	public Security toEntity(final ResultSet rs) throws SQLException {
		int idx = 1;
		final String symbol = rs.getString(idx++);
		final SecurityType secType = convertSecurityType(rs.getString(idx++));
		final int exchangeSid = Exchange.getSidFromCode(rs.getString(idx++));

		final long sid = Security.buildSid((byte)exchangeSid, convertSidFromSymbol(symbol));
        sidManager.setSidForKey(sid, symbol);
        
        final Date sqlListedDate = rs.getDate(idx++);
        final LocalDate listedDate = sqlListedDate == null ? ServiceConstant.NULL_LISTED_DATE : sqlListedDate.toLocalDate();
        final int lotSize = rs.getInt(idx++);
		final Boolean algo = rs.getBoolean(idx++);
		
		if (secType.equals(SecurityType.WARRANT) || secType.equals(SecurityType.CBBC) || secType.equals(SecurityType.FUTURES)) {
			final int issuerSid = (int)issuerSidManager.getSidForKey(rs.getString(idx++));
			final long undSecSid = sidManager.getSidForKey(rs.getString(idx++));
			final PutOrCall putOrCall = convertPutOrCall(rs.getString(idx++));
			final OptionStyle optionStyle = convertOptionStyle(rs.getString(idx++));
			final LocalDate maturity = rs.getDate(idx++).toLocalDate();
			final int strikePrice = rs.getInt(idx++);
			final int convRatio = rs.getInt(idx++);
			if (secType.equals(SecurityType.CBBC)) {
				final int barrier = rs.getInt(idx++);
				return Security.of(sid, secType, symbol, exchangeSid, undSecSid, Optional.of(maturity), listedDate, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, barrier, algo, SpreadTableBuilder.get(secType));
			}
			return Security.of(sid, secType, symbol, exchangeSid, undSecSid, Optional.of(maturity), listedDate, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, SpreadTableBuilder.get(secType));
		}
		return Security.of(sid, secType, symbol, exchangeSid, algo, SpreadTableBuilder.get(secType));
	}
	
	private OptionStyle convertOptionStyle(final String text) {
		switch (text) {
		case "Asian":
			return OptionStyle.ASIAN;
		case "Euro":
			return OptionStyle.EUROPEAN;
		}
		return OptionStyle.NULL_VAL;
	}
	
	private PutOrCall convertPutOrCall(final String text) {
		switch (text) {
		case "Call":
			return PutOrCall.CALL;
		case "Put":
			return PutOrCall.PUT;
		}
		return PutOrCall.NULL_VAL;
	}
	
	private SecurityType convertSecurityType(final String secType) {
		switch (secType) {
		case "Equity":
			return SecurityType.STOCK;
		case "ETF":
			return SecurityType.STOCK;
		case "Index":
			return SecurityType.INDEX;
		case "Futures":
			return SecurityType.FUTURES;
		case "Warrant":
			return SecurityType.WARRANT;
		case "Cbbc":
			return SecurityType.CBBC;
		}
		return SecurityType.NULL_VAL;
	}
	
	private int convertSidFromSymbol(final String symbol) {
		try {
			int sid = Integer.parseInt(symbol);
			if (sid < sidManager.getInitialSid()) {
				return sid;
			}
		}
		catch (final Exception exception) {
			
		}
		return (int)sidManager.getNextSid();
	}
	
}
