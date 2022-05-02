package com.lunar.entity;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RawToIssuerConverter implements EntityConverter<Issuer> {
	private static final String COMMA = ",";
	private static final int SID_INDEX = 0;
	private static final int NAME_INDEX = 1;
	private static final int CODE_INDEX = 2;
	private final SidManager<String> sidManager;
	
	public RawToIssuerConverter(final SidManager<String> sidManager) {
		this.sidManager = sidManager;
	}	
	
	@Override
	public Issuer toEntity(final String csv){
		final String[] items = csv.split(COMMA);
		final long sid = Integer.parseInt(items[SID_INDEX]);
		final String name = items[NAME_INDEX];
		final String code = items[CODE_INDEX];
		sidManager.setSidForKey(sid, code);
		return Issuer.of(sid, code, name);
	}
	
	@Override
	public Issuer toEntity(final ByteBuffer buffer){
		throw new UnsupportedOperationException();
	}

	@Override
	public Issuer toEntity(final ResultSet rs) throws SQLException {
	    final long sid = rs.getInt(SID_INDEX + 1);
        final String name = rs.getString(NAME_INDEX + 1);
        //final String code = rs.getString(CODE_INDEX + 1);
        final String code = name;
        sidManager.setSidForKey(sid, code);
        return Issuer.of(sid, code, name);
	}
}
