import pytest
from datetime import datetime, timezone, timedelta
from lunar_core.data import DataManager
from lunar_core.models import MarketData

class MockDataManager(DataManager):
    """Mock data manager for testing"""
    def __init__(self):
        self.data = {}
    
    async def get_historical_data(self, symbol: str, start: datetime, end: datetime) -> list[MarketData]:
        return self.data.get(symbol, [])
    
    async def get_latest_data(self, symbol: str) -> MarketData:
        return self.data.get(symbol, [])[-1] if self.data.get(symbol) else None

@pytest.fixture
def mock_data_manager():
    return MockDataManager()

@pytest.fixture
def sample_historical_data():
    """Create sample historical data for testing"""
    base_time = datetime.now(timezone.utc)
    return [
        MarketData(
            symbol="AAPL",
            timestamp=base_time - timedelta(days=i),
            open=150.0 + i,
            high=151.0 + i,
            low=149.0 + i,
            close=150.5 + i,
            volume=1000000,
            source="test"
        )
        for i in range(5)
    ]

@pytest.mark.asyncio
async def test_get_historical_data(mock_data_manager, sample_historical_data):
    """Test retrieving historical data"""
    mock_data_manager.data["AAPL"] = sample_historical_data
    
    start = datetime.now(timezone.utc) - timedelta(days=5)
    end = datetime.now(timezone.utc)
    
    data = await mock_data_manager.get_historical_data("AAPL", start, end)
    assert len(data) == 5
    assert all(isinstance(d, MarketData) for d in data)
    assert all(d.symbol == "AAPL" for d in data)

@pytest.mark.asyncio
async def test_get_latest_data(mock_data_manager, sample_historical_data):
    """Test retrieving latest data"""
    mock_data_manager.data["AAPL"] = sample_historical_data
    
    latest_data = await mock_data_manager.get_latest_data("AAPL")
    assert latest_data is not None
    assert latest_data.symbol == "AAPL"
    assert latest_data.close == 154.5  # Last data point in sample_historical_data

@pytest.mark.asyncio
async def test_empty_data(mock_data_manager):
    """Test behavior with empty data"""
    start = datetime.now(timezone.utc) - timedelta(days=5)
    end = datetime.now(timezone.utc)
    
    data = await mock_data_manager.get_historical_data("AAPL", start, end)
    assert len(data) == 0
    
    latest_data = await mock_data_manager.get_latest_data("AAPL")
    assert latest_data is None 