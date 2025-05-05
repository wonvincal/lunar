"""SQL module for the backtesting system."""

from .db_manager import DatabaseManager, AssetType
from .models import StockData, FutureData, IndexData, OptionData
from .queries import (
    CREATE_OPTIONS_TABLE,
    CREATE_STOCKS_TABLE,
    CREATE_FUTURES_TABLE,
    CREATE_INDICES_TABLE,
    INSERT_OPTION,
    INSERT_STOCK,
    INSERT_FUTURE,
    INSERT_INDEX,
    GET_OPTIONS_BY_DATE_RANGE,
    GET_STOCKS_BY_DATE_RANGE,
    GET_FUTURES_BY_DATE_RANGE,
    GET_INDICES_BY_DATE_RANGE,
    GET_OPTION_CHAIN,
    GET_OPTION_METRICS,
    GET_STOCK_METRICS,
    GET_FUTURE_METRICS,
    GET_INDEX_METRICS,
    GET_ASSET_VOLATILITY,
    GET_ASSET_CORRELATION,
    CLEANUP_OLD_DATA
)

__all__ = [
    'DatabaseManager',
    'AssetType',
    'StockData',
    'FutureData',
    'IndexData',
    'OptionData',
    'CREATE_OPTIONS_TABLE',
    'CREATE_STOCKS_TABLE',
    'CREATE_FUTURES_TABLE',
    'CREATE_INDICES_TABLE',
    'INSERT_OPTION',
    'INSERT_STOCK',
    'INSERT_FUTURE',
    'INSERT_INDEX',
    'GET_OPTIONS_BY_DATE_RANGE',
    'GET_STOCKS_BY_DATE_RANGE',
    'GET_FUTURES_BY_DATE_RANGE',
    'GET_INDICES_BY_DATE_RANGE',
    'GET_OPTION_CHAIN',
    'GET_OPTION_METRICS',
    'GET_STOCK_METRICS',
    'GET_FUTURE_METRICS',
    'GET_INDEX_METRICS',
    'GET_ASSET_VOLATILITY',
    'GET_ASSET_CORRELATION',
    'CLEANUP_OLD_DATA'
] 