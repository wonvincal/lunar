package com.lunar.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/*
 * Wrapper around java.sql.Connection
 */
public interface DbConnection {
	public void closeQuery(final ResultSet rs);
	public ResultSet executeQuery(final String query);
	public ResultSet executeQuery(final String query, boolean canRetry);
    public boolean executeNonQuery(final String query);
    public boolean executeNonQuery(final String query, boolean canRetry);
    public PreparedStatement prepareStatement(final String query, int statement);
    public PreparedStatement prepareStatement(final String query);
}
