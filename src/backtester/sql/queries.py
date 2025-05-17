"""SQL queries for the backtesting system."""

# Table creation
CREATE_OPTION_CONTRACT_TABLE = '''
CREATE TABLE IF NOT EXISTS option_contract (
    symbol TEXT PRIMARY KEY,
    strike_price REAL,
    expiration_date DATETIME,
    option_type TEXT,
    underlying_symbol TEXT,
    FOREIGN KEY (underlying_symbol) REFERENCES stock(symbol)
)
'''

CREATE_OPTION_SNAPSHOT_TABLE = '''
CREATE TABLE IF NOT EXISTS option_snapshot (
    timestamp DATETIME,
    symbol TEXT,
    bid REAL,
    ask REAL,
    last_price REAL,
    open_interest INTEGER,
    implied_volatility REAL,
    delta REAL,
    gamma REAL,
    theta REAL,
    vega REAL,
    rho REAL,
    PRIMARY KEY (timestamp, symbol),
    FOREIGN KEY (symbol) REFERENCES option_contract(symbol)
)
'''

CREATE_STOCK_AGGREGATION_TABLE = '''
CREATE TABLE IF NOT EXISTS stock_aggregation (
    timestamp DATETIME,
    symbol TEXT,
    open REAL,
    high REAL,
    low REAL,
    close REAL,
    volume INTEGER,
    vwap REAL,
    PRIMARY KEY (timestamp, symbol)
)
'''

CREATE_FUTURE_AGGREGATION_TABLE = '''
CREATE TABLE IF NOT EXISTS future_aggregation (
    timestamp DATETIME,
    symbol TEXT,
    open REAL,
    high REAL,
    low REAL,
    close REAL,
    volume INTEGER,
    open_interest INTEGER,
    settlement_price REAL,
    PRIMARY KEY (timestamp, symbol)
)
'''

CREATE_INDEX_AGGREGATION_TABLE = '''
CREATE TABLE IF NOT EXISTS index_aggregation (
    timestamp DATETIME,
    symbol TEXT,
    open REAL,
    high REAL,
    low REAL,
    close REAL,
    volume INTEGER,
    PRIMARY KEY (timestamp, symbol)
)
'''

CREATE_OPTION_AGGREGATION_TABLE = '''
CREATE TABLE IF NOT EXISTS option_aggregation (
    timestamp DATETIME,
    symbol TEXT,
    open REAL,
    high REAL,
    low REAL,
    close REAL,
    volume INTEGER,
    vwap REAL,
    PRIMARY KEY (timestamp, symbol)
)
'''

# Data insertion
INSERT_OPTION_CONTRACT = '''
INSERT OR REPLACE INTO option_contract VALUES (
    ?, ?, ?, ?, ?
)
'''

INSERT_OPTION_SNAPSHOT = '''
INSERT OR REPLACE INTO option_snapshot VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_STOCK_AGGREGATION = '''
INSERT OR REPLACE INTO stock_aggregation VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_FUTURE_AGGREGATION = '''
INSERT OR REPLACE INTO future_aggregation VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_INDEX_AGGREGATION = '''
INSERT OR REPLACE INTO index_aggregation VALUES (
    ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_OPTION_AGGREGATION = '''
INSERT OR REPLACE INTO option_aggregation VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?
)
'''

# Data retrieval
GET_OPTION_SNAPSHOTS_BY_DATE_RANGE = '''
SELECT s.*, c.strike_price, c.expiration_date, c.option_type, c.underlying_symbol
FROM option_snapshot s
JOIN option_contract c ON s.symbol = c.symbol
WHERE s.symbol = ? AND s.timestamp BETWEEN ? AND ?
ORDER BY s.timestamp, c.strike_price
'''

GET_STOCK_AGGREGATIONS_BY_DATE_RANGE = '''
SELECT * FROM stock_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

GET_FUTURE_AGGREGATIONS_BY_DATE_RANGE = '''
SELECT * FROM future_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

GET_INDEX_AGGREGATIONS_BY_DATE_RANGE = '''
SELECT * FROM index_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

GET_OPTION_AGGREGATIONS_BY_DATE_RANGE = '''
SELECT * FROM option_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

# Data analysis
GET_OPTION_CHAIN = '''
SELECT s.*, c.strike_price, c.expiration_date, c.option_type, c.underlying_symbol
FROM option_snapshot s
JOIN option_contract c ON s.symbol = c.symbol
WHERE c.underlying_symbol = ? AND DATE(s.timestamp) = DATE(?) AND c.expiration_date = ?
ORDER BY c.strike_price
'''

GET_OPTION_METRICS = '''
SELECT 
    c.underlying_symbol,
    c.option_type,
    AVG(s.implied_volatility) as avg_iv,
    AVG(s.delta) as avg_delta,
    AVG(s.gamma) as avg_gamma,
    AVG(s.theta) as avg_theta,
    AVG(s.vega) as avg_vega,
    AVG(s.rho) as avg_rho
FROM option_snapshot s
JOIN option_contract c ON s.symbol = c.symbol
WHERE c.underlying_symbol = ? AND s.timestamp BETWEEN ? AND ?
GROUP BY c.underlying_symbol, c.option_type
'''

GET_ASSET_METRICS = '''
SELECT 
    symbol,
    AVG(close) as avg_price,
    MAX(high) as max_price,
    MIN(low) as min_price,
    SUM(volume) as total_volume,
    AVG(close - open) as avg_daily_change
FROM {table}
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol
'''

# Additional metrics queries
GET_STOCK_METRICS = '''
SELECT 
    symbol,
    AVG(close) as avg_price,
    MAX(high) as max_price,
    MIN(low) as min_price,
    SUM(volume) as total_volume,
    AVG(close - open) as avg_daily_change,
    AVG(vwap) as avg_vwap,
    AVG(volume) as avg_volume,
    MAX(volume) as max_volume,
    MIN(volume) as min_volume
FROM stock_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol
'''

GET_FUTURE_METRICS = '''
SELECT 
    symbol,
    AVG(close) as avg_price,
    MAX(high) as max_price,
    MIN(low) as min_price,
    SUM(volume) as total_volume,
    AVG(close - open) as avg_daily_change,
    AVG(open_interest) as avg_open_interest,
    MAX(open_interest) as max_open_interest,
    MIN(open_interest) as min_open_interest,
    AVG(settlement_price - close) as avg_settlement_diff
FROM future_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol
'''

GET_INDEX_METRICS = '''
SELECT 
    symbol,
    AVG(close) as avg_price,
    MAX(high) as max_price,
    MIN(low) as min_price,
    SUM(volume) as total_volume,
    AVG(close - open) as avg_daily_change,
    AVG(volume) as avg_volume,
    MAX(volume) as max_volume,
    MIN(volume) as min_volume
FROM index_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol
'''

GET_OPTION_METRICS = '''
SELECT 
    symbol,
    AVG(close) as avg_price,
    MAX(high) as max_price,
    MIN(low) as min_price,
    SUM(volume) as total_volume,
    AVG(close - open) as avg_daily_change,
    AVG(vwap) as avg_vwap,
    AVG(volume) as avg_volume,
    MAX(volume) as max_volume,
    MIN(volume) as min_volume
FROM option_aggregation
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol
'''

# Volatility and correlation queries
GET_ASSET_VOLATILITY = '''
SELECT 
    symbol,
    AVG((high - low) / low) as avg_daily_volatility,
    MAX((high - low) / low) as max_daily_volatility,
    MIN((high - low) / low) as min_daily_volatility,
    AVG(ABS(close - open) / open) as avg_daily_return
FROM {table}
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol
'''

GET_ASSET_CORRELATION = '''
WITH daily_returns AS (
    SELECT 
        symbol,
        timestamp,
        (close - LAG(close) OVER (PARTITION BY symbol ORDER BY timestamp)) / LAG(close) OVER (PARTITION BY symbol ORDER BY timestamp) as daily_return
    FROM {table}
    WHERE timestamp BETWEEN ? AND ?
)
SELECT 
    a.symbol as symbol1,
    b.symbol as symbol2,
    CORR(a.daily_return, b.daily_return) as correlation
FROM daily_returns a
JOIN daily_returns b ON a.timestamp = b.timestamp AND a.symbol < b.symbol
GROUP BY a.symbol, b.symbol
'''

# Data maintenance
CLEANUP_OLD_DATA = '''
DELETE FROM {table}
WHERE timestamp < ?
''' 