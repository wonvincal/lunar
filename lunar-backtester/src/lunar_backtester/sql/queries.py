"""SQL queries for the backtesting system."""

# Table creation
CREATE_OPTIONS_TABLE = '''
CREATE TABLE IF NOT EXISTS options (
    timestamp DATETIME,
    symbol TEXT,
    strike_price REAL,
    expiration_date DATETIME,
    option_type TEXT,
    last_price REAL,
    bid REAL,
    ask REAL,
    volume INTEGER,
    open_interest INTEGER,
    implied_volatility REAL,
    delta REAL,
    gamma REAL,
    theta REAL,
    vega REAL,
    rho REAL,
    PRIMARY KEY (timestamp, symbol, strike_price, expiration_date, option_type)
)
'''

CREATE_STOCKS_TABLE = '''
CREATE TABLE IF NOT EXISTS stocks (
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

CREATE_FUTURES_TABLE = '''
CREATE TABLE IF NOT EXISTS futures (
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

CREATE_INDICES_TABLE = '''
CREATE TABLE IF NOT EXISTS indices (
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

# Data insertion
INSERT_OPTION = '''
INSERT OR REPLACE INTO options VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_STOCK = '''
INSERT OR REPLACE INTO stocks VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_FUTURE = '''
INSERT OR REPLACE INTO futures VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?, ?
)
'''

INSERT_INDEX = '''
INSERT OR REPLACE INTO indices VALUES (
    ?, ?, ?, ?, ?, ?, ?
)
'''

# Data retrieval
GET_OPTIONS_BY_DATE_RANGE = '''
SELECT * FROM options
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp, strike_price
'''

GET_STOCKS_BY_DATE_RANGE = '''
SELECT * FROM stocks
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

GET_FUTURES_BY_DATE_RANGE = '''
SELECT * FROM futures
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

GET_INDICES_BY_DATE_RANGE = '''
SELECT * FROM indices
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
ORDER BY timestamp
'''

# Data analysis
GET_OPTION_CHAIN = '''
SELECT * FROM options
WHERE symbol = ? AND timestamp = ? AND expiration_date = ?
ORDER BY strike_price
'''

GET_OPTION_METRICS = '''
SELECT 
    symbol,
    option_type,
    AVG(implied_volatility) as avg_iv,
    AVG(delta) as avg_delta,
    AVG(gamma) as avg_gamma,
    AVG(theta) as avg_theta,
    AVG(vega) as avg_vega,
    AVG(rho) as avg_rho
FROM options
WHERE symbol = ? AND timestamp BETWEEN ? AND ?
GROUP BY symbol, option_type
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
FROM stocks
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
FROM futures
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
FROM indices
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