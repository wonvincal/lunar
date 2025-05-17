from datetime import datetime, timedelta
from typing import List, Dict, Any, Union, Optional
import pandas as pd
from pathlib import Path
import json
import logging
import gzip
import pyarrow as pa
import pyarrow.parquet as pq
from pydantic import ValidationError
from abc import ABC, abstractmethod
import time
from concurrent.futures import ThreadPoolExecutor

from .sql.db_manager import DatabaseManager

from core import (
    StockAggregation,
    FutureAggregation,
    IndexAggregation,
    OptionAggregation,
    OptionContract
)

from .api_client_polygon import PolygonAPIClient
from .csv_persister import CSVPersister
from .parquet_persister import ParquetPersister

class DataPersistence:
    """Orchestrates persisting fetched data to non-database formats."""

    def __init__(self, base_path: Union[str, Path], compression: str, logger: logging.Logger):
        self.csv_persister = CSVPersister(base_path, compression, logger)
        self.parquet_persister = ParquetPersister(base_path, compression, logger)
        self.base_path = Path(base_path)
        self.logger = logger
        self._setup_directories()

    def _setup_directories(self) -> None:
        """Create necessary directories for data storage."""
        for asset_type in ['stock', 'future', 'index', 'option', 'dividends', 'splits', 'news']:
            for format in ['csv', 'parquet']:
                path = self.base_path / asset_type / format
                path.mkdir(parents=True, exist_ok=True)

    def persist_data(self, data: pd.DataFrame, asset_type: str,
                     symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Persist data using the individual persisters."""
        self.csv_persister.persist(data, asset_type, symbol, start_date, end_date)
        self.parquet_persister.persist(data, asset_type, symbol, start_date, end_date)

class DataFetcher(ABC):
    """Base class for data fetchers."""

    @abstractmethod
    async def fetch_data(self, symbol: str, start: datetime, end: datetime) -> pd.DataFrame:
        """Fetch data for a symbol between start and end dates."""
        pass

class PolygonDataFetcher:
    """Fetches data from Polygon.io API and persists it to non-database formats."""

    def __init__(self, api_key: str, db_manager: DatabaseManager, # Keep for potential metadata storage or other uses
                 base_path: Union[str, Path] = "data",
                 compression: str = 'gzip',
                 max_retries: int = 5,
                 base_delay: float = 1.0,
                 pool_connections: int = 10,
                 pool_maxsize: int = 10):
        self.api_key = api_key
        self.db_manager = db_manager
        self.base_path = Path(base_path)
        self.compression = compression
        self.api_client = PolygonAPIClient(api_key, max_retries, base_delay)
        self._setup_logging()
        self.logger = logging.getLogger(__name__)

        self.data_persistence = DataPersistence(base_path, compression, self.logger)
        self._setup_metadata()
        self._api_method_map = {
            'stock': self._fetch_stock_aggregates,
            'future': self._fetch_aggregates,
            'index': self._fetch_aggregates,
            'option': self._fetch_option_aggregates,
            'dividends': self._fetch_dividends_data,
            'splits': self._fetch_splits_data,
            'news': self._fetch_news_data,
        }

    def _setup_logging(self) -> None:
        """Setup logging configuration."""
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )

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

    def _validate_data(self, data: pd.DataFrame, asset_type: str) -> tuple[bool, str]:
        """Validate data against Pydantic models."""
        try:
            if asset_type == 'stock':
                [StockAggregation(**row) for _, row in data.iterrows()]
            elif asset_type == 'future':
                [FutureAggregation(**row) for _, row in data.iterrows()]
            elif asset_type == 'index':
                [IndexAggregation(**row) for _, row in data.iterrows()]
            elif asset_type == 'option':
                [OptionAggregation(**row) for _, row in data.iterrows()]
            elif asset_type in ['dividends', 'splits', 'news']:
                return True, "" # No Pydantic models for these yet
            return True, ""
        except ValidationError as e:
            return False, str(e)

    def _clean_data(self, data: pd.DataFrame, asset_type: str) -> pd.DataFrame:
        """Clean and transform data."""
        data = data.drop_duplicates()
        data['timestamp'] = pd.to_datetime(data['timestamp'])
        numeric_cols = data.select_dtypes(include=['float64', 'int64']).columns
        data[numeric_cols] = data[numeric_cols].fillna(0)
        if asset_type == 'stock':
            data['volume'] = data['volume'].fillna(0)
            data['vwap'] = data['vwap'].fillna(data['close'])
        elif asset_type == 'option':
            data['volume'] = data['volume'].fillna(0)
            data['vwap'] = data['vwap'].fillna(data['close'])
        return data

    async def _fetch_aggregates(self, symbol: str, start_date: datetime, end_date: datetime) -> List[Dict[str, Any]]:
        """Generic fetch for aggregate data."""
        return self.api_client.get_aggregates(symbol, 1, "day", start_date, end_date)

    async def _fetch_stock_aggregates(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Fetch and format stock aggregate data."""
        aggs = await self._fetch_aggregates(symbol, start_date, end_date)
        return pd.DataFrame([{
            'timestamp': pd.to_datetime(agg['t'], unit='ms'),
            'open': agg['o'],
            'high': agg['h'],
            'low': agg['l'],
            'close': agg['c'],
            'volume': agg['v'],
            'vwap': agg.get('vw'),
            'symbol': symbol
        } for agg in aggs] if aggs else [])

    def fetch_option_contracts(
        self,
        underlying_ticker: str,
        as_of: Optional[datetime] = None,
        expiration_date: Optional[datetime] = None,
        expired: bool = False,
        limit: int = 1000
    ) -> pd.DataFrame:
        """
        Fetches a list of option contracts for a given underlying ticker.

        Args:
            underlying_ticker: The ticker symbol of the underlying asset.
            as_of: Specify a date to get contracts as of that date.
            expiration_date: Specify a specific expiration date to filter by.
            expired: Whether to include expired contracts. Defaults to False.
            limit: The maximum number of results to return. Defaults to 1000.

        Returns:
            A Pandas DataFrame containing the option contract details.
        """
        try:
            contracts = self.api_client.list_options_contracts(
                underlying_ticker=underlying_ticker,
                as_of=as_of.strftime('%Y-%m-%d') if as_of else None,
                expiration_date=expiration_date.strftime('%Y-%m-%d') if expiration_date else None,
                expired=expired,
                limit=limit
            )
            if contracts:
                df = pd.DataFrame(contracts)
                return df
            else:
                self.logger.warning(f"No option contracts found for {underlying_ticker} with the given criteria.")
                return pd.DataFrame()
        except Exception as e:
            self.logger.error(f"Error fetching option contracts for {underlying_ticker}: {e}")
            return pd.DataFrame()
        
    async def _fetch_option_aggregates(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Fetch option aggregate data."""
        contracts = self.api_client.list_options_contracts(underlying_ticker=symbol, expired=False, limit=None)
        option_data = []
        for contract in contracts:
            try:
                self.logger.info(f"Fetching aggregates for {contract['ticker']}")
                time.sleep(1.0)
                aggs = self.api_client.get_aggregates(contract['ticker'], 1, "day", start_date, end_date)
                if aggs:
                    option_data.extend([{
                        'timestamp': pd.to_datetime(agg['t'], unit='ms'),
                        'open': agg['o'],
                        'high': agg['h'],
                        'low': agg['l'],
                        'close': agg['c'],
                        'volume': agg['v'],
                        'vwap': agg.get('vw'),
                        'transactions': agg.get('n'),
                        'symbol': contract['ticker']
                    } for agg in aggs])
            except Exception as e:
                self.logger.error(f"Error fetching option data for {contract['ticker']}: {e}")
        return pd.DataFrame(option_data)

    async def _fetch_dividends_data(self, symbol: str) -> List[Dict[str, Any]]:
        """Fetch dividend data."""
        return self.api_client.list_dividends(ticker=symbol)

    async def _fetch_splits_data(self, symbol: str) -> List[Dict[str, Any]]:
        """Fetch split data."""
        return self.api_client.list_splits(ticker=symbol)

    async def _fetch_news_data(self, symbol: str, limit: int = 1000) -> List[Dict[str, Any]]:
        """Fetch news data."""
        return self.api_client.list_ticker_news(ticker=symbol, limit=limit)

    async def fetch_stock_data(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist stock data."""
        print(f"Fetching stock data for {symbol} from {start_date} to {end_date}")
        try:
            df = await self._fetch_stock_aggregates(symbol, start_date, end_date)
            if not df.empty:
                print(f"Stock data found for {symbol}")
                df = self._clean_data(df, 'stock')
                is_valid, error_msg = self._validate_data(df, 'stock')
                if is_valid:
                    self._persist_data(df, 'stock', symbol, start_date, end_date)
                else:
                    self.logger.error(f"Validation failed for stock data of {symbol}: {error_msg}")
            else:
                print(f"No stock data found for {symbol}")
                self.logger.warning(f"No stock data found for {symbol}")
            time.sleep(0.5)
        except Exception as e:
            self.logger.error(f"Error fetching stock data for {symbol}: {e}")

    async def fetch_option_data(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist option data."""
        try:
            df = await self._fetch_option_aggregates(symbol, start_date, end_date)
            if not df.empty:
                df = self._clean_data(df, 'option')
                # Validation for options?
                self._persist_data(df, 'option', symbol, start_date, end_date)
            else:
                self.logger.warning(f"No option data found for {symbol}")
        except Exception as e:
            self.logger.error(f"Error fetching option data for {symbol}: {e}")

    async def fetch_future_data(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist future data."""
        try:
            aggs = await self._fetch_aggregates(symbol, start_date, end_date)
            if aggs:
                df = pd.DataFrame([{
                    'timestamp': pd.to_datetime(agg['t'], unit='ms'),
                    'open': agg['o'],
                    'high': agg['h'],
                    'low': agg['l'],
                    'close': agg['c'],
                    'volume': agg['v'],
                    'vwap': agg.get('vw'),
                    'symbol': symbol
                } for agg in aggs])
                df = self._clean_data(df, 'future')
                is_valid, error_msg = self._validate_data(df, 'future')
                if is_valid:
                    self._persist_data(df, 'future', symbol, start_date, end_date)
                else:
                    self.logger.error(f"Validation failed for future data of {symbol}: {error_msg}")
            else:
                self.logger.warning(f"No future data found for {symbol}")
        except Exception as e:
            self.logger.error(f"Error fetching future data for {symbol}: {e}")

    async def fetch_index_data(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist index data."""
        try:
            aggs = await self._fetch_aggregates(symbol, start_date, end_date)
            if aggs:
                df = pd.DataFrame([{
                    'timestamp': pd.to_datetime(agg['t'], unit='ms'),
                    'open': agg['o'],
                    'high': agg['h'],
                    'low': agg['l'],
                    'close': agg['c'],
                    'volume': agg['v'],
                    'vwap': agg.get('vw'),
                    'symbol': symbol
                } for agg in aggs])
                df = self._clean_data(df, 'index')
                is_valid, error_msg = self._validate_data(df, 'index')
                if is_valid:
                    self._persist_data(df, 'index', symbol, start_date, end_date)
                else:
                    self.logger.error(f"Validation failed for index data of {symbol}: {error_msg}")
            else:
                self.logger.warning(f"No index data found for {symbol}")
        except Exception as e:
            self.logger.error(f"Error fetching index data for {symbol}: {e}")

    async def fetch_dividends(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist dividend data."""
        try:
            dividends = await self._fetch_dividends_data(symbol)
            filtered_dividends = [
                div for div in dividends
                if start_date <= pd.to_datetime(div['exDate']) <= end_date
            ]
            if filtered_dividends:
                df = pd.DataFrame([{
                    'timestamp': pd.to_datetime(div['exDate']),
                    'ex_date': pd.to_datetime(div['exDate']),
                    'payment_date': pd.to_datetime(div.get('paymentDate')),
                    'amount': div['amount'],
                    'symbol': symbol,
                    'type': 'dividend'
                } for div in filtered_dividends])
                self._persist_data(df, 'dividends', symbol, start_date, end_date)
            else:
                self.logger.warning(f"No dividend data found for {symbol} in date range")
        except Exception as e:
            self.logger.error(f"Error fetching dividend data for {symbol}: {e}")

    async def fetch_splits(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist stock split data."""
        try:
            splits = await self._fetch_splits_data(symbol)
            filtered_splits = [
                split for split in splits
                if start_date <= pd.to_datetime(split['executionDate']) <= end_date
            ]
            if filtered_splits:
                df = pd.DataFrame([{
                    'timestamp': pd.to_datetime(split['executionDate']),
                    'execution_date': pd.to_datetime(split['executionDate']),
                    'split_from': split['splitFrom'],
                    'split_to': split['splitTo'],
                    'symbol': symbol,
                    'type': 'split'
                } for split in filtered_splits])
                self._persist_data(df, 'splits', symbol, start_date, end_date)
            else:
                self.logger.warning(f"No split data found for {symbol} in date range")
        except Exception as e:
            self.logger.error(f"Error fetching split data for {symbol}: {e}")

    async def fetch_news(self, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Fetch and persist news data."""
        try:
            news = await self._fetch_news_data(symbol, limit=1000)
            news_list = []
            for article in news:
                published_dt = pd.to_datetime(article['published_utc'])
                if start_date <= published_dt <= end_date:
                    news_dict = {
                        'timestamp': published_dt,
                        'published_utc': published_dt,
                        'title': article['title'],
                        'article_url': article['article_url'],
                        'description': article['description'],
                        'symbol': symbol,
                        'type': 'news'
                    }
                    news_list.append(news_dict)
            if news_list:
                df = pd.DataFrame(news_list)
                self._persist_data(df, 'news', symbol, start_date, end_date)
            else:
                self.logger.warning(f"No news data found for {symbol} in date range")
        except Exception as e:
            self.logger.error(f"Error fetching news data for {symbol}: {e}")

    def _persist_data(self, data: pd.DataFrame, asset_type: str,
                      symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Persist data using the DataPersistence instance and update metadata."""
        self.data_persistence.persist_data(data, asset_type, symbol, start_date, end_date)
        self._update_metadata(asset_type, symbol, start_date, end_date)

    def fetch_incremental(self, symbols: Dict[str, List[str]], days_back: int = 1) -> None:
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
                    df = self.fetch_stock_data(symbol, start_date, end_date)
                    df = self.fetch_dividends(symbol, start_date, end_date)
                    df = self.fetch_splits(symbol, start_date, end_date)
                    df = self.fetch_news(symbol, start_date, end_date)
                elif asset_type == 'option':
                    df = self.fetch_option_data(symbol, start_date, end_date)
                elif asset_type == 'future':
                    df = self.fetch_future_data(symbol, start_date, end_date)
                elif asset_type == 'index':
                    df = self.fetch_index_data(symbol, start_date, end_date)

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

            for future in futures:
                try:
                    future.result()
                except Exception as e:
                    self.logger.error(f"Error in parallel fetch: {e}")