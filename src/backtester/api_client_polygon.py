from datetime import datetime, date
from typing import Any, Dict, List, Optional, Union # Added Optional import
import time
import random
import logging
from polygon import RESTClient

class PolygonAPIClient:
    """Encapsulates interaction with the Polygon.io API."""

    def __init__(self, api_key: str, max_retries: int = 5, base_delay: float = 1.0):
        self.api_key = api_key
        self.client = RESTClient(api_key=self.api_key)
        self.max_retries = max_retries
        self.base_delay = base_delay
        self.logger = logging.getLogger(__name__)

    def _handle_rate_limit(self, retry_count: int) -> None:
        """Handle rate limit with exponential backoff."""
        if retry_count >= self.max_retries:
            raise Exception("Max retries exceeded due to rate limits.")
        delay = min(300, self.base_delay * (2 ** retry_count) + random.uniform(0, 1))
        self.logger.warning(f"Rate limit hit, waiting {delay:.2f} seconds...")
        time.sleep(delay)

    def _fetch_with_retry(self, fetch_func, *args, **kwargs) -> Any:
        """Execute a fetch function with retry logic."""
        retry_count = 0
        while True:
            try:
                return fetch_func(*args, **kwargs)
            except Exception as e:
                if "429" in str(e) and retry_count < self.max_retries:
                    self._handle_rate_limit(retry_count)
                    retry_count += 1
                    continue
                raise

    def get_aggregates(self, symbol: str, multiplier: int, timespan: str, from_: datetime, to: datetime) -> List[Dict[str, Any]]:
        """Get aggregate (bar) data."""
        results = self._fetch_with_retry(
            self.client.get_aggs,
            symbol,
            multiplier,
            timespan,
            from_,
            to
        )
        return results if results else []

    def list_options_contracts(self, underlying_ticker: str, expiration_date: Optional[Union[str, date]] = None, as_of: Optional[Union[str, date]] = None, expired: bool = False, limit: Optional[int] = None) -> List[Dict[str, Any]]:
        """List option contracts."""
        results = self._fetch_with_retry(
            self.client.list_options_contracts,
            underlying_ticker=underlying_ticker,
            expiration_date=expiration_date,
            as_of=as_of,
            expired=expired,
            limit=limit
        )
        return [contract.__dict__ for contract in results] if results else [] # Convert Polygon object to dict

    def list_dividends(self, ticker: str) -> List[Dict[str, Any]]:
        """List dividends for a ticker."""
        results = self._fetch_with_retry(
            self.client.list_dividends,
            ticker=ticker
        )
        return [dividend.__dict__ for dividend in results] if results else [] # Convert Polygon object to dict

    def list_splits(self, ticker: str) -> List[Dict[str, Any]]:
        """List splits for a ticker."""
        results = self._fetch_with_retry(
            self.client.list_splits,
            ticker=ticker
        )
        return [split.__dict__ for split in results] if results else [] # Convert Polygon object to dict

    def list_ticker_news(self, ticker: str, limit: Optional[int] = None) -> List[Dict[str, Any]]:
        """List news for a ticker."""
        results = self._fetch_with_retry(
            self.client.list_ticker_news,
            ticker=ticker,
            limit=limit
        )
        return [news_item.__dict__ for news_item in results] if results else [] # Convert Polygon object to dict