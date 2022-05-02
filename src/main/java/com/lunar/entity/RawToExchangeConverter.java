package com.lunar.entity;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RawToExchangeConverter implements EntityConverter<Exchange> {
	private static final String COMMA = ",";
	private static final int SID_INDEX = 0;
	private static final int NAME_INDEX = 1;
	
	public Exchange toEntity(String csv){
		String[] items = csv.split(COMMA);
		long sid = Long.parseLong(items[SID_INDEX]);
		String name = items[NAME_INDEX];
		return Exchange.of(sid, name, null, null);
	}
	
	public Exchange toEntity(ByteBuffer buffer){
		throw new UnsupportedOperationException();
	}

	@Override
	public Exchange toEntity(ResultSet rs) throws SQLException {
		throw new UnsupportedOperationException();
	}
}
