package com.lunar.entity;

import java.sql.ResultSet;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.database.DbConnection;

public class DatabaseEntityLoader<T extends Entity> implements EntityLoader<T> {
	private static final Logger LOG = LogManager.getLogger(DatabaseEntityLoader.class);

	private final DbConnection conn;
	private final EntityConverter<T> converter;
	private final String query;

	public DatabaseEntityLoader(DbConnection conn, String query, EntityConverter<T> converter) {
		this.conn = conn;
		this.converter = converter;
		this.query = query;
	}

	@Override
	public void loadInto(Loadable<T> loadable) throws Exception {
		LOG.info("Loading entity with query: {}", query);
		final ResultSet rs = this.conn.executeQuery(query);
		try {
			if (rs != null) {
				while (rs.next()) {
					loadable.load(converter.toEntity(rs));
				}
			}
		}
		finally {
			this.conn.closeQuery(rs);
		}
	}
}
