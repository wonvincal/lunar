"""Database manager for the backtesting system."""
import sqlite3
from typing import List, Dict, Any, Literal, Union
import pandas as pd
from datetime import datetime
from contextlib import contextmanager
from pydantic import BaseModel

from .queries import (
    CREATE_OPTION_CONTRACT_TABLE,
    CREATE_OPTION_SNAPSHOT_TABLE,
    CREATE_STOCK_AGGREGATION_TABLE,
    CREATE_FUTURE_AGGREGATION_TABLE,
    CREATE_INDEX_AGGREGATION_TABLE,
    CREATE_OPTION_AGGREGATION_TABLE,
    INSERT_OPTION_CONTRACT,
    INSERT_OPTION_SNAPSHOT,
    INSERT_STOCK_AGGREGATION,
    INSERT_FUTURE_AGGREGATION,
    INSERT_INDEX_AGGREGATION,
    INSERT_OPTION_AGGREGATION,
    GET_OPTION_SNAPSHOTS_BY_DATE_RANGE,
    GET_STOCK_AGGREGATIONS_BY_DATE_RANGE,
    GET_FUTURE_AGGREGATIONS_BY_DATE_RANGE,
    GET_INDEX_AGGREGATIONS_BY_DATE_RANGE,
    GET_OPTION_AGGREGATIONS_BY_DATE_RANGE,
    GET_OPTION_CHAIN,
    GET_OPTION_METRICS,
    GET_STOCK_METRICS,
    GET_FUTURE_METRICS,
    GET_INDEX_METRICS,
    GET_ASSET_VOLATILITY,
    GET_ASSET_CORRELATION,
    CLEANUP_OLD_DATA
)
from core.models import (
    Aggregation,
    StockAggregation,
    FutureAggregation,
    IndexAggregation,
    OptionAggregation,
    OptionContract,
    Snapshot,
    OptionSnapshot
)

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
            conn.execute(CREATE_OPTION_CONTRACT_TABLE)
            conn.execute(CREATE_OPTION_SNAPSHOT_TABLE)
            conn.execute(CREATE_STOCK_AGGREGATION_TABLE)
            conn.execute(CREATE_FUTURE_AGGREGATION_TABLE)
            conn.execute(CREATE_INDEX_AGGREGATION_TABLE)
            conn.execute(CREATE_OPTION_AGGREGATION_TABLE)
    
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
    
    def insert_option_contract(self, contract_data: Union[Dict[str, Any], OptionContract]) -> None:
        """Insert a single option contract record."""
        data = self._validate_and_convert(contract_data, OptionContract)
        data['expiration_date'] = self._convert_timestamp(data['expiration_date'])
        with self._get_connection() as conn:
            conn.execute(INSERT_OPTION_CONTRACT, (
                data['symbol'],
                data['strike_price'],
                data['expiration_date'],
                data['option_type'],
                data['underlying_symbol']
            ))
            conn.commit()
    
    def insert_option_snapshot(self, snapshot_data: Union[Dict[str, Any], OptionSnapshot]) -> None:
        """Insert a single option snapshot record."""
        data = self._validate_and_convert(snapshot_data, OptionSnapshot)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_OPTION_SNAPSHOT, (
                data['timestamp'],
                data['symbol'],
                data['bid'],
                data['ask'],
                data['last_price'],
                data.get('open_interest'),
                data.get('implied_volatility'),
                data.get('delta'),
                data.get('gamma'),
                data.get('theta'),
                data.get('vega'),
                data.get('rho')
            ))
            conn.commit()
    
    def insert_stock_aggregation(self, aggregation_data: Union[Dict[str, Any], StockAggregation]) -> None:
        """Insert a single stock aggregation record."""
        data = self._validate_and_convert(aggregation_data, StockAggregation)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_STOCK_AGGREGATION, (
                data['timestamp'],
                data['symbol'],
                data['open'],
                data['high'],
                data['low'],
                data['close'],
                data['volume'],
                data.get('vwap')
            ))
            conn.commit()
    
    def insert_future_aggregation(self, aggregation_data: Union[Dict[str, Any], FutureAggregation]) -> None:
        """Insert a single future aggregation record."""
        data = self._validate_and_convert(aggregation_data, FutureAggregation)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_FUTURE_AGGREGATION, (
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
            conn.commit()
    
    def insert_index_aggregation(self, aggregation_data: Union[Dict[str, Any], IndexAggregation]) -> None:
        """Insert a single index aggregation record."""
        data = self._validate_and_convert(aggregation_data, IndexAggregation)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_INDEX_AGGREGATION, (
                data['timestamp'],
                data['symbol'],
                data['open'],
                data['high'],
                data['low'],
                data['close'],
                data['volume']
            ))
            conn.commit()
    
    def insert_option_aggregation(self, aggregation_data: Union[Dict[str, Any], OptionAggregation]) -> None:
        """Insert a single option aggregation record."""
        data = self._validate_and_convert(aggregation_data, OptionAggregation)
        data['timestamp'] = self._convert_timestamp(data['timestamp'])
        with self._get_connection() as conn:
            conn.execute(INSERT_OPTION_AGGREGATION, (
                data['timestamp'],
                data['symbol'],
                data['open'],
                data['high'],
                data['low'],
                data['close'],
                data['volume'],
                data.get('vwap')
            ))
            conn.commit()
    
    def get_option_snapshots_by_date_range(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get option snapshots for a symbol within a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_OPTION_SNAPSHOTS_BY_DATE_RANGE,
                conn,
                params=(symbol, start_date, end_date)
            )

    def get_stock_aggregations_by_date_range(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get stock aggregations for a symbol within a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_STOCK_AGGREGATIONS_BY_DATE_RANGE,
                conn,
                params=(symbol, start_date, end_date)
            )

    def get_future_aggregations_by_date_range(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get future aggregations for a symbol within a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_FUTURE_AGGREGATIONS_BY_DATE_RANGE,
                conn,
                params=(symbol, start_date, end_date)
            )

    def get_index_aggregations_by_date_range(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get index aggregations for a symbol within a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_INDEX_AGGREGATIONS_BY_DATE_RANGE,
                conn,
                params=(symbol, start_date, end_date)
            )

    def get_option_aggregations_by_date_range(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get option aggregations for a symbol within a date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_OPTION_AGGREGATIONS_BY_DATE_RANGE,
                conn,
                params=(symbol, start_date, end_date)
            )
        
    def get_option_chain(self, underlying_symbol: str, timestamp: datetime, expiration_date: datetime) -> pd.DataFrame:
        """Get option chain for a given underlying symbol, timestamp, and expiration date."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_OPTION_CHAIN,
                conn,
                params=(underlying_symbol, timestamp.isoformat(), expiration_date.isoformat())
            )

    def get_option_metrics(self, underlying_symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Get option metrics for a given underlying symbol and date range."""
        with self._get_connection() as conn:
            return pd.read_sql_query(
                GET_OPTION_METRICS,
                conn,
                params=(underlying_symbol, start_date, end_date)
            )

        
    def bulk_insert_option_contracts(self, contracts_data: List[Union[Dict[str, Any], OptionContract]]) -> None:
        """Bulk insert option contract records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_OPTION_CONTRACT, [
                (
                    data['symbol'],
                    data['strike_price'],
                    self._convert_timestamp(data['expiration_date']),
                    data['option_type'],
                    data['underlying_symbol']
                ) for data in [self._validate_and_convert(d, OptionContract) for d in contracts_data]
            ])
            conn.commit()
    
    def bulk_insert_option_snapshots(self, snapshots_data: List[Union[Dict[str, Any], OptionSnapshot]]) -> None:
        """Bulk insert option snapshot records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_OPTION_SNAPSHOT, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['bid'],
                    data['ask'],
                    data['last_price'],
                    data.get('open_interest'),
                    data.get('implied_volatility'),
                    data.get('delta'),
                    data.get('gamma'),
                    data.get('theta'),
                    data.get('vega'),
                    data.get('rho')
                ) for data in [self._validate_and_convert(d, OptionSnapshot) for d in snapshots_data]
            ])
            conn.commit()
    
    def bulk_insert_stock_aggregations(self, aggregations_data: List[Union[Dict[str, Any], StockAggregation]]) -> None:
        """Bulk insert stock aggregation records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_STOCK_AGGREGATION, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['open'],
                    data['high'],
                    data['low'],
                    data['close'],
                    data['volume'],
                    data.get('vwap')
                ) for data in [self._validate_and_convert(d, StockAggregation) for d in aggregations_data]
            ])
            conn.commit()
    
    def bulk_insert_future_aggregations(self, aggregations_data: List[Union[Dict[str, Any], FutureAggregation]]) -> None:
        """Bulk insert future aggregation records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_FUTURE_AGGREGATION, [
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
                ) for data in [self._validate_and_convert(d, FutureAggregation) for d in aggregations_data]
            ])
            conn.commit()
    
    def bulk_insert_index_aggregations(self, aggregations_data: List[Union[Dict[str, Any], IndexAggregation]]) -> None:
        """Bulk insert index aggregation records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_INDEX_AGGREGATION, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['open'],
                    data['high'],
                    data['low'],
                    data['close'],
                    data['volume']
                ) for data in [self._validate_and_convert(d, IndexAggregation) for d in aggregations_data]
            ])
            conn.commit()
    
    def bulk_insert_option_aggregations(self, aggregations_data: List[Union[Dict[str, Any], OptionAggregation]]) -> None:
        """Bulk insert option aggregation records."""
        with self._get_connection() as conn:
            conn.executemany(INSERT_OPTION_AGGREGATION, [
                (
                    self._convert_timestamp(data['timestamp']),
                    data['symbol'],
                    data['open'],
                    data['high'],
                    data['low'],
                    data['close'],
                    data['volume'],
                    data.get('vwap')
                ) for data in [self._validate_and_convert(d, OptionAggregation) for d in aggregations_data]
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
            'stock': 'stock_aggregation',
            'future': 'future_aggregation',
            'index': 'index_aggregation',
            'option': 'option_aggregation'
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
            'stock': 'stock_aggregation',
            'future': 'future_aggregation',
            'index': 'index_aggregation',
            'option': 'option_aggregation'
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
            'option': 'option_snapshot',
            'stock': 'stock_aggregation',
            'future': 'future_aggregation',
            'index': 'index_aggregation'
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