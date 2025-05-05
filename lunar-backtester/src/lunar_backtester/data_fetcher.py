"""Data fetcher for Polygon.io API."""
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional, Union, Tuple
import pandas as pd
from pathlib import Path
import requests
import json
import logging
from concurrent.futures import ThreadPoolExecutor
import time
import gzip
import pyarrow as pa
import pyarrow.parquet as pq
from pydantic import ValidationError
from abc import ABC, abstractmethod

from .sql import (
    DatabaseManager,
    StockData,
    FutureData,
    IndexData,
    OptionData
)
from .data_importer import DataImporter

class DataFetcher(ABC):
    """Base class for data fetchers."""
    
    @abstractmethod
    async def fetch_data(self, symbol: str, start: datetime, end: datetime) -> pd.DataFrame:
        """Fetch data for a symbol between start and end dates."""
        pass

class PolygonDataFetcher:
    """Fetches data from Polygon.io API and stores it in multiple formats."""
    
    def __init__(self, api_key: str, db_manager: DatabaseManager,
                 base_path: Union[str, Path] = "data",
                 compression: str = 'gzip'):
        self.api_key = api_key
        self.db_manager = db_manager
        self.base_path = Path(base_path)
        self.data_importer = DataImporter(db_manager)
        self.compression = compression
        self._setup_logging()
        self._setup_directories()
        self._setup_metadata()
    
    def _setup_logging(self) -> None:
        """Setup logging configuration."""
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )
        self.logger = logging.getLogger(__name__)
    
    def _setup_directories(self) -> None:
        """Create necessary directories for data storage."""
        for asset_type in ['stocks', 'futures', 'indices', 'options']:
            for format in ['csv', 'parquet']:
                path = self.base_path / asset_type / format
                path.mkdir(parents=True, exist_ok=True)
    
    def _setup_metadata(self) -> None:
        """Setup metadata tracking for incremental updates."""
        self.metadata_path = self.base_path / 'metadata.json'
        if self.metadata_path.exists():
            with open(self.metadata_path, 'r') as f:
                self.metadata = json.load(f)
        else:
            self.metadata = {
                'last_update': {},
                'data_ranges': {}
            }
    
    def _save_metadata(self) -> None:
        """Save metadata to file."""
        with open(self.metadata_path, 'w') as f:
            json.dump(self.metadata, f, indent=2)
    
    def _update_metadata(self, asset_type: str, symbol: str,
                        start_date: datetime, end_date: datetime) -> None:
        """Update metadata for a symbol."""
        key = f"{asset_type}_{symbol}"
        self.metadata['last_update'][key] = datetime.now().isoformat()
        if key not in self.metadata['data_ranges']:
            self.metadata['data_ranges'][key] = {
                'start': start_date.isoformat(),
                'end': end_date.isoformat()
            }
        else:
            current = self.metadata['data_ranges'][key]
            current_start = datetime.fromisoformat(current['start'])
            current_end = datetime.fromisoformat(current['end'])
            if start_date < current_start:
                current['start'] = start_date.isoformat()
            if end_date > current_end:
                current['end'] = end_date.isoformat()
        self._save_metadata()
    
    def _validate_data(self, data: pd.DataFrame, asset_type: str) -> Tuple[bool, str]:
        """Validate data against Pydantic models."""
        try:
            if asset_type == 'stock':
                [StockData(**row) for _, row in data.iterrows()]
            elif asset_type == 'future':
                [FutureData(**row) for _, row in data.iterrows()]
            elif asset_type == 'index':
                [IndexData(**row) for _, row in data.iterrows()]
            elif asset_type == 'option':
                [OptionData(**row) for _, row in data.iterrows()]
            return True, ""
        except ValidationError as e:
            return False, str(e)
    
    def _clean_data(self, data: pd.DataFrame, asset_type: str) -> pd.DataFrame:
        """Clean and transform data."""
        # Remove duplicates
        data = data.drop_duplicates()
        
        # Handle missing values
        if asset_type == 'stock':
            data['volume'] = data['volume'].fillna(0)
            data['vwap'] = data['vwap'].fillna(data['close'])
        elif asset_type == 'option':
            data['open_interest'] = data['open_interest'].fillna(0)
            data['implied_volatility'] = data['implied_volatility'].fillna(0)
        
        # Ensure data types
        data['timestamp'] = pd.to_datetime(data['timestamp'])
        numeric_cols = data.select_dtypes(include=['float64', 'int64']).columns
        data[numeric_cols] = data[numeric_cols].fillna(0)
        
        return data
    
    def _make_request(self, endpoint: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Make a request to Polygon.io API."""
        base_url = "https://api.polygon.io"
        params['apiKey'] = self.api_key
        
        try:
            response = requests.get(f"{base_url}{endpoint}", params=params)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            self.logger.error(f"API request failed: {e}")
            raise
    
    def _save_data(self, data: pd.DataFrame, asset_type: str,
                  symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Save data in multiple formats with compression."""
        # Clean and validate data
        data = self._clean_data(data, asset_type)
        is_valid, error_msg = self._validate_data(data, asset_type)
        if not is_valid:
            self.logger.error(f"Data validation failed for {symbol}: {error_msg}")
            return
        
        # Generate filename based on date range
        date_str = f"{start_date.strftime('%Y%m%d')}_{end_date.strftime('%Y%m%d')}"
        filename = f"{symbol}_{date_str}"
        
        # Save to CSV with compression
        csv_path = self.base_path / asset_type / 'csv' / f"{filename}.csv.gz"
        with gzip.open(csv_path, 'wt') as f:
            data.to_csv(f, index=False)
        self.logger.info(f"Saved compressed CSV: {csv_path}")
        
        # Save to Parquet with compression
        parquet_path = self.base_path / asset_type / 'parquet' / f"{filename}.parquet"
        table = pa.Table.from_pandas(data)
        pq.write_table(
            table,
            parquet_path,
            compression=self.compression,
            version='2.6'
        )
        self.logger.info(f"Saved compressed Parquet: {parquet_path}")
        
        # Import to database
        self.data_importer.import_pandas(data, asset_type)
        self.logger.info(f"Imported to database: {symbol}")
        
        # Update metadata
        self._update_metadata(asset_type, symbol, start_date, end_date)
    
    def fetch_stock_data(self, symbol: str, start_date: datetime,
                        end_date: datetime) -> None:
        """Fetch stock data from Polygon.io."""
        endpoint = f"/v2/aggs/ticker/{symbol}/range/1/day/{start_date.strftime('%Y-%m-%d')}/{end_date.strftime('%Y-%m-%d')}"
        
        try:
            data = self._make_request(endpoint, {})
            if 'results' not in data:
                self.logger.warning(f"No data found for {symbol}")
                return
            
            # Convert to DataFrame
            df = pd.DataFrame(data['results'])
            df['timestamp'] = pd.to_datetime(df['t'], unit='ms')
            df['symbol'] = symbol
            df = df.rename(columns={
                'o': 'open',
                'h': 'high',
                'l': 'low',
                'c': 'close',
                'v': 'volume',
                'vw': 'vwap'
            })
            
            self._save_data(df, 'stock', symbol, start_date, end_date)
        except Exception as e:
            self.logger.error(f"Error fetching stock data for {symbol}: {e}")
    
    def fetch_option_data(self, symbol: str, start_date: datetime,
                         end_date: datetime) -> None:
        """Fetch option data from Polygon.io."""
        endpoint = f"/v3/reference/options/contracts"
        params = {
            'underlying_ticker': symbol,
            'expired': 'false',
            'limit': 1000
        }
        
        try:
            # First get the option contracts
            contracts = self._make_request(endpoint, params)
            if 'results' not in contracts:
                self.logger.warning(f"No option contracts found for {symbol}")
                return
            
            # Fetch data for each contract
            for contract in contracts['results']:
                contract_id = contract['ticker']
                endpoint = f"/v2/aggs/ticker/{contract_id}/range/1/day/{start_date.strftime('%Y-%m-%d')}/{end_date.strftime('%Y-%m-%d')}"
                
                try:
                    data = self._make_request(endpoint, {})
                    if 'results' not in data:
                        continue
                    
                    # Convert to DataFrame
                    df = pd.DataFrame(data['results'])
                    df['timestamp'] = pd.to_datetime(df['t'], unit='ms')
                    df['symbol'] = symbol
                    df['strike_price'] = contract['strike_price']
                    df['expiration_date'] = pd.to_datetime(contract['expiration_date'])
                    df['option_type'] = contract['contract_type']
                    
                    # Add Greeks if available
                    if 'greeks' in contract:
                        df['delta'] = contract['greeks'].get('delta', 0)
                        df['gamma'] = contract['greeks'].get('gamma', 0)
                        df['theta'] = contract['greeks'].get('theta', 0)
                        df['vega'] = contract['greeks'].get('vega', 0)
                        df['rho'] = contract['greeks'].get('rho', 0)
                    
                    self._save_data(df, 'option', contract_id, start_date, end_date)
                    
                    # Respect rate limits
                    time.sleep(0.1)
                except Exception as e:
                    self.logger.error(f"Error fetching option data for {contract_id}: {e}")
                    continue
        except Exception as e:
            self.logger.error(f"Error fetching option contracts for {symbol}: {e}")
    
    def fetch_future_data(self, symbol: str, start_date: datetime,
                         end_date: datetime) -> None:
        """Fetch future data from Polygon.io."""
        endpoint = f"/v2/aggs/ticker/{symbol}/range/1/day/{start_date.strftime('%Y-%m-%d')}/{end_date.strftime('%Y-%m-%d')}"
        
        try:
            data = self._make_request(endpoint, {})
            if 'results' not in data:
                self.logger.warning(f"No data found for {symbol}")
                return
            
            # Convert to DataFrame
            df = pd.DataFrame(data['results'])
            df['timestamp'] = pd.to_datetime(df['t'], unit='ms')
            df['symbol'] = symbol
            df = df.rename(columns={
                'o': 'open',
                'h': 'high',
                'l': 'low',
                'c': 'close',
                'v': 'volume'
            })
            
            # Add settlement price (you might need to fetch this separately)
            df['settlement_price'] = df['close']
            df['open_interest'] = 0  # You might need to fetch this separately
            
            self._save_data(df, 'future', symbol, start_date, end_date)
        except Exception as e:
            self.logger.error(f"Error fetching future data for {symbol}: {e}")
    
    def fetch_index_data(self, symbol: str, start_date: datetime,
                        end_date: datetime) -> None:
        """Fetch index data from Polygon.io."""
        endpoint = f"/v2/aggs/ticker/{symbol}/range/1/day/{start_date.strftime('%Y-%m-%d')}/{end_date.strftime('%Y-%m-%d')}"
        
        try:
            data = self._make_request(endpoint, {})
            if 'results' not in data:
                self.logger.warning(f"No data found for {symbol}")
                return
            
            # Convert to DataFrame
            df = pd.DataFrame(data['results'])
            df['timestamp'] = pd.to_datetime(df['t'], unit='ms')
            df['symbol'] = symbol
            df = df.rename(columns={
                'o': 'open',
                'h': 'high',
                'l': 'low',
                'c': 'close',
                'v': 'volume'
            })
            
            self._save_data(df, 'index', symbol, start_date, end_date)
        except Exception as e:
            self.logger.error(f"Error fetching index data for {symbol}: {e}")
    
    def fetch_dividends(self, symbol: str, start_date: datetime,
                       end_date: datetime) -> None:
        """Fetch dividend data from Polygon.io."""
        endpoint = f"/v3/reference/dividends"
        params = {
            'ticker': symbol,
            'ex_dividend_date.gte': start_date.strftime('%Y-%m-%d'),
            'ex_dividend_date.lte': end_date.strftime('%Y-%m-%d')
        }
        
        try:
            data = self._make_request(endpoint, params)
            if 'results' not in data:
                self.logger.warning(f"No dividend data found for {symbol}")
                return
            
            df = pd.DataFrame(data['results'])
            self._save_data(df, 'dividends', symbol, start_date, end_date)
        except Exception as e:
            self.logger.error(f"Error fetching dividend data for {symbol}: {e}")
    
    def fetch_splits(self, symbol: str, start_date: datetime,
                    end_date: datetime) -> None:
        """Fetch stock split data from Polygon.io."""
        endpoint = f"/v3/reference/splits"
        params = {
            'ticker': symbol,
            'execution_date.gte': start_date.strftime('%Y-%m-%d'),
            'execution_date.lte': end_date.strftime('%Y-%m-%d')
        }
        
        try:
            data = self._make_request(endpoint, params)
            if 'results' not in data:
                self.logger.warning(f"No split data found for {symbol}")
                return
            
            df = pd.DataFrame(data['results'])
            self._save_data(df, 'splits', symbol, start_date, end_date)
        except Exception as e:
            self.logger.error(f"Error fetching split data for {symbol}: {e}")
    
    def fetch_news(self, symbol: str, start_date: datetime,
                  end_date: datetime) -> None:
        """Fetch news data from Polygon.io."""
        endpoint = f"/v2/reference/news"
        params = {
            'ticker': symbol,
            'published_utc.gte': start_date.strftime('%Y-%m-%d'),
            'published_utc.lte': end_date.strftime('%Y-%m-%d')
        }
        
        try:
            data = self._make_request(endpoint, params)
            if 'results' not in data:
                self.logger.warning(f"No news data found for {symbol}")
                return
            
            df = pd.DataFrame(data['results'])
            self._save_data(df, 'news', symbol, start_date, end_date)
        except Exception as e:
            self.logger.error(f"Error fetching news data for {symbol}: {e}")
    
    def fetch_incremental(self, symbols: Dict[str, List[str]],
                         days_back: int = 1) -> None:
        """Fetch only new data since last update."""
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days_back)
        
        for asset_type, symbol_list in symbols.items():
            for symbol in symbol_list:
                key = f"{asset_type}_{symbol}"
                if key in self.metadata['last_update']:
                    last_update = datetime.fromisoformat(self.metadata['last_update'][key])
                    if (end_date - last_update).days < days_back:
                        self.logger.info(f"Skipping {symbol} - up to date")
                        continue
                
                if asset_type == 'stock':
                    self.fetch_stock_data(symbol, start_date, end_date)
                    self.fetch_dividends(symbol, start_date, end_date)
                    self.fetch_splits(symbol, start_date, end_date)
                    self.fetch_news(symbol, start_date, end_date)
                elif asset_type == 'option':
                    self.fetch_option_data(symbol, start_date, end_date)
                elif asset_type == 'future':
                    self.fetch_future_data(symbol, start_date, end_date)
                elif asset_type == 'index':
                    self.fetch_index_data(symbol, start_date, end_date)
    
    def fetch_all_data(self, symbols: Dict[str, List[str]],
                      start_date: datetime, end_date: datetime,
                      max_workers: int = 5) -> None:
        """Fetch data for multiple symbols in parallel."""
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = []
            
            for asset_type, symbol_list in symbols.items():
                for symbol in symbol_list:
                    if asset_type == 'stock':
                        futures.append(executor.submit(
                            self.fetch_stock_data, symbol, start_date, end_date))
                        futures.append(executor.submit(
                            self.fetch_dividends, symbol, start_date, end_date))
                        futures.append(executor.submit(
                            self.fetch_splits, symbol, start_date, end_date))
                        futures.append(executor.submit(
                            self.fetch_news, symbol, start_date, end_date))
                    elif asset_type == 'option':
                        futures.append(executor.submit(
                            self.fetch_option_data, symbol, start_date, end_date))
                    elif asset_type == 'future':
                        futures.append(executor.submit(
                            self.fetch_future_data, symbol, start_date, end_date))
                    elif asset_type == 'index':
                        futures.append(executor.submit(
                            self.fetch_index_data, symbol, start_date, end_date))
            
            # Wait for all futures to complete
            for future in futures:
                try:
                    future.result()
                except Exception as e:
                    self.logger.error(f"Error in parallel fetch: {e}") 