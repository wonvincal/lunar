import pytest
from datetime import datetime, timezone, timedelta
import pandas as pd
from lunar_backtester.data_fetcher import DataFetcher

class MockDataFetcher(DataFetcher):
    """Mock data fetcher for testing."""
    def __init__(self):
        self.data = {}
    
    async def fetch_data(self, symbol: str, start: datetime, end: datetime) -> pd.DataFrame:
        """Mock fetch data method."""
        if symbol not in self.data:
            dates = pd.date_range(start=start, end=end, freq='D')
            self.data[symbol] = pd.DataFrame({
                'open': pd.Series([100.0] * len(dates)),
                'high': pd.Series([102.0] * len(dates)),
                'low': pd.Series([98.0] * len(dates)),
                'close': pd.Series([101.0] * len(dates)),
                'volume': pd.Series([1000000] * len(dates))
            }, index=dates)
        
        # Filter data for the requested date range
        data = self.data[symbol]
        return data[start:end]

@pytest.fixture
def mock_data_fetcher():
    return MockDataFetcher()

@pytest.mark.asyncio
async def test_fetch_data(mock_data_fetcher, sample_market_data):
    """Test fetching data."""
    symbol = 'AAPL'
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    data = await mock_data_fetcher.fetch_data(symbol, start, end)
    
    assert isinstance(data, pd.DataFrame)
    assert not data.empty
    assert all(col in data.columns for col in ['open', 'high', 'low', 'close', 'volume'])
    assert data.index[0] == start
    assert data.index[-1] == end

@pytest.mark.asyncio
async def test_fetch_data_caching(mock_data_fetcher):
    """Test data caching."""
    symbol = 'AAPL'
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    # First fetch
    data1 = await mock_data_fetcher.fetch_data(symbol, start, end)
    # Second fetch should use cached data
    data2 = await mock_data_fetcher.fetch_data(symbol, start, end)
    
    assert data1.equals(data2)
    assert len(mock_data_fetcher.data) == 1

@pytest.mark.asyncio
async def test_fetch_data_date_range(mock_data_fetcher):
    """Test fetching data with different date ranges."""
    symbol = 'AAPL'
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    # Full range
    full_data = await mock_data_fetcher.fetch_data(symbol, start, end)
    # Partial range
    partial_data = await mock_data_fetcher.fetch_data(
        symbol,
        start + timedelta(days=2),
        end - timedelta(days=2)
    )
    
    assert len(partial_data) < len(full_data)
    assert partial_data.index[0] == start + timedelta(days=2)
    assert partial_data.index[-1] == end - timedelta(days=2) 