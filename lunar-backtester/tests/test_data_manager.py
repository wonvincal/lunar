import pytest
from datetime import datetime, timezone, timedelta
import pandas as pd
from lunar_backtester.data_manager import DataManager

class MockDataManager(DataManager):
    """Mock data manager for testing."""
    def __init__(self):
        self.data = {}
        self.cache = {}
    
    async def get_data(self, symbol: str, start: datetime, end: datetime) -> pd.DataFrame:
        """Get data for a symbol."""
        key = f"{symbol}_{start}_{end}"
        if key not in self.cache:
            if symbol not in self.data:
                dates = pd.date_range(start=start, end=end, freq='D')
                self.data[symbol] = pd.DataFrame({
                    'open': pd.Series([100.0] * len(dates)),
                    'high': pd.Series([102.0] * len(dates)),
                    'low': pd.Series([98.0] * len(dates)),
                    'close': pd.Series([101.0] * len(dates)),
                    'volume': pd.Series([1000000] * len(dates))
                }, index=dates)
            self.cache[key] = self.data[symbol]
        return self.cache[key]

@pytest.fixture
def mock_data_manager():
    return MockDataManager()

@pytest.mark.asyncio
async def test_get_data(mock_data_manager):
    """Test getting data."""
    symbol = 'AAPL'
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    data = await mock_data_manager.get_data(symbol, start, end)
    
    assert isinstance(data, pd.DataFrame)
    assert not data.empty
    assert all(col in data.columns for col in ['open', 'high', 'low', 'close', 'volume'])
    assert data.index[0] == start
    assert data.index[-1] == end

@pytest.mark.asyncio
async def test_data_caching(mock_data_manager):
    """Test data caching."""
    symbol = 'AAPL'
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    # First request
    data1 = await mock_data_manager.get_data(symbol, start, end)
    # Second request should use cache
    data2 = await mock_data_manager.get_data(symbol, start, end)
    
    assert data1.equals(data2)
    assert len(mock_data_manager.cache) == 1

@pytest.mark.asyncio
async def test_multiple_symbols(mock_data_manager):
    """Test handling multiple symbols."""
    symbols = ['AAPL', 'MSFT']
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    for symbol in symbols:
        data = await mock_data_manager.get_data(symbol, start, end)
        assert isinstance(data, pd.DataFrame)
        assert not data.empty
        assert data.index[0] == start
        assert data.index[-1] == end
    
    assert len(mock_data_manager.data) == len(symbols)
    assert len(mock_data_manager.cache) == len(symbols)

@pytest.mark.asyncio
async def test_data_consistency(mock_data_manager):
    """Test data consistency across different date ranges."""
    symbol = 'AAPL'
    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    end = datetime(2024, 1, 10, tzinfo=timezone.utc)
    
    # Get full range
    full_data = await mock_data_manager.get_data(symbol, start, end)
    # Get partial range
    partial_data = await mock_data_manager.get_data(
        symbol,
        start + timedelta(days=2),
        end - timedelta(days=2)
    )
    
    # Check that partial data is consistent with full data
    assert partial_data.equals(full_data.loc[partial_data.index]) 