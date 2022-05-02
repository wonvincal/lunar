package com.lunar.entity;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface EntityConverter<T> {
	T toEntity(String csv);
	T toEntity(ByteBuffer buffer);
	T toEntity(ResultSet rs) throws SQLException;
}
