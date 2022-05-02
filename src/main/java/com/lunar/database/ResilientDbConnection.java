package com.lunar.database;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * Wrapper around java.sql.Connection that will attempt to re-connect to the database if connection is lost
 */
public class ResilientDbConnection implements DbConnection {
	private static final Logger LOG = LogManager.getLogger(ResilientDbConnection.class);

	private final Driver m_driver;
	private String m_url;
	private Connection m_connection = null;
	
	public ResilientDbConnection(final String url) throws SQLException  {
		m_driver = DriverManager.getDriver(url);
		m_url = url;
	}
	
	private boolean reestablishConnection() {
		if (m_connection != null) {
			try {
				m_connection.close();
			} catch (final SQLException e) {
				// eat it, we are here becuz something is already not rite
			}
			m_connection = null;
		}
		return establishConnection();
	}
	
	private boolean establishConnection() {
		if (m_connection != null)
			return true;
		try {
			LOG.info("Connecting to database at {}...", m_url);
			m_connection = m_driver.connect(m_url, null);
			return true;
		}
		catch (final SQLException e) {
			LOG.error("Cannot open connection...", e);
			m_connection = null;
		}
		return false;
	}
	
	public void closeQuery(final ResultSet rs) {
		try {
			final Statement stmt = rs.getStatement();
			rs.close();
			stmt.close();
		}
		catch (final SQLException e) {
			LOG.warn("Error encountered while closing ResultSet or Statement...", e);
		}
	}

	public PreparedStatement prepareStatement(final String query, final int statement){
		try {
			if (establishConnection()){
				return m_connection.prepareStatement(query, statement);
			}
		} 
		catch (SQLException e) {
			LOG.error("Cannot create prepared statement: {}", query, e);
		}
		return null;
	}
	
	public PreparedStatement prepareStatement(final String query){
		try {
			if (establishConnection()){
				return m_connection.prepareStatement(query);
			}
		} 
		catch (SQLException e) {
			LOG.error("Cannot create prepared statement: {}", query, e);
		}
		return null;
	}
	
	public ResultSet executeQuery(final String query) {
		return executeQuery(query, true);
	}

	public ResultSet executeQuery(final String query, boolean canRetry) {
		Statement stmt = null;
		try {
			if (establishConnection()) {
				stmt = m_connection.createStatement();
				return stmt.executeQuery(query);
			}
		}
		catch (final SQLException e) {
			try {
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
			}
			catch (final SQLException e2) {
				// eat the exception
			}
			if (canRetry) {
				try {
					// most likely the query is correct and exception is due to faulty connection
					if (!m_connection.isValid(0) || m_connection.isClosed()) {
						LOG.warn("Connection is invalid. Attempt to re-establish connection...", e);
						if (reestablishConnection()) {
							return executeQuery(query, false);
						}
					}
				}
				catch (final SQLException e2) {
					// eat the exception
				}
			}
			LOG.error("Cannot execute query: {}", query, e);
		}
		return null;
	}
	
    public boolean executeNonQuery(final String query) {
        return executeNonQuery(query, true);
    }
	
    public boolean executeNonQuery(final String query, boolean canRetry) {
        Statement stmt = null;
        try {
            if (establishConnection()) {
                stmt = m_connection.createStatement();
                return stmt.execute(query);
            }
        }
        catch (final SQLException e) {
            try {
                if (stmt != null) {
                    stmt.close();
                    stmt = null;
                }
            }
            catch (final SQLException e2) {
                // eat the exception
            }
            if (canRetry) {
                try {
                    // most likely the query is correct and exception is due to faulty connection
                    if (!m_connection.isValid(0) || m_connection.isClosed()) {
                        LOG.warn("Connection is invalid. Attempt to re-establish connection...", e);
                        if (reestablishConnection()) {
                            return executeNonQuery(query, false);
                        }
                    }
                }
                catch (final SQLException e2) {
                    // eat the exception
                }
            }
            LOG.error("Cannot execute non-query: {}", query, e);
        }
        return false;
    }
}
