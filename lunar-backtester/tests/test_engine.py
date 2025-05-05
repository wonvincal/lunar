import pytest
from datetime import datetime, timezone
import pandas as pd
from lunar_backtester.engine import BacktestEngine
from lunar_backtester.strategies import BaseStrategy
from lunar_core import MarketData

class MockDataFetcher:
    """Mock data fetcher for testing."""
    def __init__(self):
        self.data = {}
    
    async def fetch_data(self, symbol: str, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """Return mock market data for the given symbol and date range."""
        if symbol in self.data:
            mask = (self.data[symbol].index >= start_date) & (self.data[symbol].index <= end_date)
            return self.data[symbol][mask]
        return pd.DataFrame()

class MockStrategy(BaseStrategy):
    """Mock strategy for testing."""
    def __init__(self, config: dict):
        super().__init__(config)
        self.signals = []
    
    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        """Generate mock signals."""
        signals = pd.DataFrame(index=data.index)
        signals['signal'] = 0
        signals.loc[data.index[0], 'signal'] = 1  # Buy signal on first day
        signals.loc[data.index[-1], 'signal'] = -1  # Sell signal on last day
        self.signals.append(signals)
        return signals
    
    def update(self, timestamp: datetime, data: MarketData) -> None:
        """Update strategy state."""
        pass

@pytest.fixture
def mock_strategy(sample_strategy_config):
    return MockStrategy(sample_strategy_config)

@pytest.fixture
def mock_engine(sample_backtest_config):
    engine = BacktestEngine(initial_capital=sample_backtest_config['initial_capital'])
    engine.commission = sample_backtest_config['commission']
    engine.slippage = sample_backtest_config['slippage']
    engine.start_date = sample_backtest_config['start_date']
    engine.end_date = sample_backtest_config['end_date']
    return engine

@pytest.mark.asyncio
async def test_engine_initialization(mock_engine, sample_backtest_config):
    """Test engine initialization."""
    assert mock_engine.initial_capital == sample_backtest_config['initial_capital']
    assert mock_engine.commission == sample_backtest_config['commission']
    assert mock_engine.slippage == sample_backtest_config['slippage']
    assert mock_engine.start_date == sample_backtest_config['start_date']
    assert mock_engine.end_date == sample_backtest_config['end_date']

@pytest.mark.asyncio
async def test_data_loading(mock_engine, sample_market_data, tmp_path):
    """Test data loading functionality."""
    # Add symbol column to the data
    data = sample_market_data.assign(symbol='AAPL')
    
    # Save sample data to a temporary CSV file
    data_path = tmp_path / "test_data.csv"
    data.to_csv(data_path, index=False)
    
    # Load the data
    mock_engine.load_data(str(data_path))
    
    # Check that data is loaded
    assert mock_engine.is_data_loaded()
    assert 'AAPL' in mock_engine.get_available_symbols()

@pytest.mark.asyncio
async def test_data_range(mock_engine, sample_market_data):
    """Test getting data range for a symbol."""
    # Process the sample data directly
    mock_engine._process_dataframe(sample_market_data.assign(symbol='AAPL'))
    
    # Get the data range
    date_range = mock_engine.get_data_range('AAPL')
    
    assert date_range is not None
    start_date, end_date = date_range
    assert start_date == pd.to_datetime(sample_market_data['timestamp'].iloc[0])
    assert end_date == pd.to_datetime(sample_market_data['timestamp'].iloc[-1])

@pytest.mark.asyncio
async def test_available_symbols(mock_engine, sample_market_data):
    """Test getting available symbols."""
    # Process data for multiple symbols
    for symbol in ['AAPL', 'MSFT']:
        mock_engine._process_dataframe(sample_market_data.assign(symbol=symbol))
    
    # Get available symbols
    symbols = mock_engine.get_available_symbols()
    
    assert len(symbols) == 2
    assert 'AAPL' in symbols
    assert 'MSFT' in symbols 