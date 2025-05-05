import pytest
import pandas as pd
import numpy as np
from datetime import datetime, timezone
from lunar_core import MarketData
from lunar_backtester.strategies import BaseStrategy, MovingAverageCrossover

class TestStrategy(BaseStrategy):
    """Concrete strategy class for testing."""
    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        """Generate test signals."""
        signals = pd.DataFrame(index=data.index)
        signals['signal'] = 0
        return signals
    
    def update(self, timestamp: datetime, data: MarketData) -> None:
        """Update strategy state."""
        pass

@pytest.fixture
def sample_data():
    """Create sample data for testing strategies."""
    dates = pd.date_range(start='2024-01-01', end='2024-01-30', freq='D')
    data = pd.DataFrame({
        'open': np.linspace(100, 110, len(dates)),
        'high': np.linspace(102, 112, len(dates)),
        'low': np.linspace(98, 108, len(dates)),
        'close': np.linspace(101, 111, len(dates)),
        'volume': np.random.randint(1000000, 2000000, len(dates))
    }, index=dates)
    return data

@pytest.fixture
def ma_crossover_strategy():
    """Create a moving average crossover strategy."""
    config = {
        'name': 'ma_crossover',
        'symbols': ['AAPL'],
        'parameters': {
            'short_window': 5,
            'long_window': 20
        }
    }
    return MovingAverageCrossover(config)

def test_base_strategy_initialization(sample_strategy_config):
    """Test base strategy initialization."""
    strategy = TestStrategy(sample_strategy_config)
    assert strategy.name == sample_strategy_config['name']
    assert strategy.symbols == sample_strategy_config['symbols']
    assert strategy.parameters == sample_strategy_config['parameters']

def test_ma_crossover_signal_generation(ma_crossover_strategy, sample_data):
    """Test moving average crossover signal generation."""
    signals = ma_crossover_strategy.generate_signals(sample_data)
    
    assert isinstance(signals, pd.DataFrame)
    assert 'signal' in signals.columns
    assert len(signals) == len(sample_data)
    assert signals['signal'].isin([-1, 0, 1]).all()  # Signals should be -1, 0, or 1

def test_ma_crossover_parameters(ma_crossover_strategy):
    """Test moving average crossover parameters."""
    assert ma_crossover_strategy.short_window == 5
    assert ma_crossover_strategy.long_window == 20
    assert ma_crossover_strategy.short_window < ma_crossover_strategy.long_window

def test_ma_crossover_calculation(ma_crossover_strategy, sample_data):
    """Test moving average crossover calculation."""
    signals = ma_crossover_strategy.generate_signals(sample_data)
    
    # Calculate moving averages manually
    short_ma = sample_data['close'].rolling(window=5).mean()
    long_ma = sample_data['close'].rolling(window=20).mean()
    
    # Check that signals are generated correctly
    for i in range(20, len(signals)):  # Start after long window
        if short_ma[i] > long_ma[i] and short_ma[i-1] <= long_ma[i-1]:
            assert signals['signal'][i] == 1  # Buy signal
        elif short_ma[i] < long_ma[i] and short_ma[i-1] >= long_ma[i-1]:
            assert signals['signal'][i] == -1  # Sell signal
        else:
            assert signals['signal'][i] == 0  # No signal

def test_strategy_with_missing_data(ma_crossover_strategy):
    """Test strategy with missing data."""
    # Create data with missing values
    dates = pd.date_range(start='2024-01-01', end='2024-01-30', freq='D')
    data = pd.DataFrame({
        'open': np.linspace(100, 110, len(dates)),
        'high': np.linspace(102, 112, len(dates)),
        'low': np.linspace(98, 108, len(dates)),
        'close': np.linspace(101, 111, len(dates)),
        'volume': np.random.randint(1000000, 2000000, len(dates))
    }, index=dates)
    
    # Add some missing values
    data.loc[data.index[5:10], 'close'] = np.nan
    
    signals = ma_crossover_strategy.generate_signals(data)
    
    assert isinstance(signals, pd.DataFrame)
    assert len(signals) == len(data)
    assert signals['signal'].isin([-1, 0, 1]).all() 