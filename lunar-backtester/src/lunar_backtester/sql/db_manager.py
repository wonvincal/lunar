"""Database manager for the backtesting system."""
import sqlite3
from typing import List, Optional, Dict, Any, Literal, Union
import pandas as pd
from datetime import datetime
from contextlib import contextmanager
from pydantic import BaseModel

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
from .models import StockData, FutureData, IndexData, OptionData

AssetType = Literal['option', 'stock', 'future', 'index']

class DatabaseManager:
    """Manages database operations for the backtesting system."""
    
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._init_db()
    
    @contextmanager
    def _get_connection(self):
        """Context manager for database connections."""
        conn = sqlite3.connect(self.db_path)
        try:
            yield conn
        finally:
            conn.close()
    
    def _init_db(self) -> None:
        """Initialize the database."""
        with self._get_connection() as conn:
            conn.execute(CREATE_OPTIONS_TABLE)
            conn.execute(CREATE_STOCKS_TABLE)
            conn.execute(CREATE_FUTURES_TABLE)
            conn.execute(CREATE_INDICES_TABLE)
    
    def _validate_and_convert(self, data: Union[Dict[str, Any], BaseModel], model_class: type) -> Dict[str, Any]:
        """Validate and convert data to the appropriate model."""
        if isinstance(data, dict):
            data = model_class(**data)
        return data.model_dump()
    
    def _convert_timestamp(self, timestamp: Union[str, datetime, pd.Timestamp]) -> str:
        """Convert timestamp to ISO format string."""
        if isinstance(timestamp, pd.Timestamp):
            return timestamp.isoformat()
        elif isinstance(timestamp, datetime):
            return timestamp.isoformat()
        return timestamp
    
    def insert_option(self, option_data: Union[Dict[str, Any], OptionData]) -> None:
        """Insert a single option record."""
        data = self._validate_and_convert(option_data, OptionData)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        data['expiration_date'] = self._convert_timestamp(data['expiration_date'])
        with self._get_connection() as conn:
            conn.execute(INSERT_OPTION, (
                data['timestamp'],
                data['symbol'],
                data['strike_price'],
                data['expiration_date'],
                data['option_type'],
                data['last_price'],
                data['bid'],
                data['ask'],
                data['volume'],
                data['open_interest'],
                data['implied_volatility'],
                data['delta'],
                data['gamma'],
                data['theta'],
                data['vega'],
                data['rho']
            ))
    
    def insert_stock(self, stock_data: Union[Dict[str, Any], StockData]) -> None:
        """Insert a single stock record."""
        data = self._validate_and_convert(stock_data, StockData)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_STOCK, (
                data['timestamp'],
                data['symbol'],
                data['open'],
                data['high'],
                data['low'],
                data['close'],
                data['volume'],
                data.get('vwap')
            ))
    
    def insert_future(self, future_data: Union[Dict[str, Any], FutureData]) -> None:
        """Insert a single future record."""
        data = self._validate_and_convert(future_data, FutureData)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_FUTURE, (
                data['timestamp'],
                data['symbol'],
                data['open'],
                data['high'],
                data['low'],
                data['close'],
                data['volume'],
                data['open_interest'],
                data['settlement_price']
            ))
    
    def insert_index(self, index_data: Union[Dict[str, Any], IndexData]) -> None:
        """Insert a single index record."""
        data = self._validate_and_convert(index_data, IndexData)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_INDEX, (
                data['timestamp'],
                data['symbol'],
                data['open'],
                data['high'],
                data['low'],
                data['close'],
                data['volume']
            ))
    
    def bulk_insert_options(self, options_data: List[Union[Dict[str, Any], OptionData]]) -> None:
        """Bulk insert option records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_OPTION, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['strike_price'],
                    self._convert_timestamp(data['expiration_date']),
                    data['option_type'],
                    data['last_price'],
                    data['bid'],
                    data['ask'],
                    data['volume'],
                    data['open_interest'],
                    data['implied_volatility'],
                    data['delta'],
                    data['gamma'],
                    data['theta'],
                    data['vega'],
                    data['rho']
                ) for data in [self._validate_and_convert(d, OptionData) for d in options_data]
            ])
            conn.commit()
    
    def bulk_insert_stocks(self, stocks_data: List[Union[Dict[str, Any], StockData]]) -> None:
        """Bulk insert stock records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_STOCK, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['open'],
                    data['high'],
                    data['low'],
                    data['close'],
                    data['volume'],
                    data.get('vwap')
                ) for data in [self._validate_and_convert(d, StockData) for d in stocks_data]
            ])
            conn.commit()
    
    def bulk_insert_futures(self, futures_data: List[Union[Dict[str, Any], FutureData]]) -> None:
        """Bulk insert future records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_FUTURE, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['open'],
                    data['high'],
                    data['low'],
                    data['close'],
                    data['volume'],
                    data['open_interest'],
                    data['settlement_price']
                ) for data in [self._validate_and_convert(d, FutureData) for d in futures_data]
            ])
            conn.commit()
    
    def bulk_insert_indices(self, indices_data: List[Union[Dict[str, Any], IndexData]]) -> None:
        """Bulk insert index records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_INDEX, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['open'],
                    data['high'],
                    data['low'],
                    data['close'],
                    data['volume']
                ) for data in [self._validate_and_convert(d, IndexData) for d in indices_data]
            ])
            conn.commit()
    
    def get_stock_metrics(self, symbol: str, start_date: datetime,
                         end_date: datetime) -> pd.DataFrame:
        """Get detailed stock metrics for a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_STOCK_METRICS,
                conn,
                params=(symbol, start_date, end_date)
            )
    
    def get_future_metrics(self, symbol: str, start_date: datetime,
                          end_date: datetime) -> pd.DataFrame:
        """Get detailed future metrics for a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_FUTURE_METRICS,
                conn,
                params=(symbol, start_date, end_date)
            )
    
    def get_index_metrics(self, symbol: str, start_date: datetime,
                         end_date: datetime) -> pd.DataFrame:
        """Get detailed index metrics for a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_INDEX_METRICS,
                conn,
                params=(symbol, start_date, end_date)
            )
    
    def get_asset_volatility(self, asset_type: AssetType, symbol: str,
                            start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get volatility metrics for any asset type."""
        table_map = {
            'stock': 'stocks',
            'future': 'futures',
            'index': 'indices'
        }
        
        if asset_type not in table_map:
            raise ValueError(f"Unsupported asset type: {asset_type}")
        
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_ASSET_VOLATILITY.format(table=table_map[asset_type]),
                conn,
                params=(symbol, start_date, end_date)
            )
    
    def get_asset_correlation(self, asset_type: AssetType,
                             start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get correlation matrix for assets of the same type."""
        table_map = {
            'stock': 'stocks',
            'future': 'futures',
            'index': 'indices'
        }
        
        if asset_type not in table_map:
            raise ValueError(f"Unsupported asset type: {asset_type}")
        
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_ASSET_CORRELATION.format(table=table_map[asset_type]),
                conn,
                params=(start_date, end_date)
            )
    
    def cleanup_old_data(self, asset_type: AssetType, cutoff_date: datetime) -> None:
        """Remove data older than the cutoff date for a specific asset type."""
        table_map = {
            'option': 'options',
            'stock': 'stocks',
            'future': 'futures',
            'index': 'indices'
        }
        
        if asset_type not in table_map:
            raise ValueError(f"Unsupported asset type: {asset_type}")
        
        with self._get_connection() as conn:
            conn.execute(
                CLEANUP_OLD_DATA.format(table=table_map[asset_type]),
                (cutoff_date,)
            )
    
    def vacuum(self) -> None:
        """Vacuum the database to reclaim space."""
        with self._get_connection() as conn:
            conn.execute("VACUUM") 