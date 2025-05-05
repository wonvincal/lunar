import pytest
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List
import pandas as pd
import numpy as np

@pytest.fixture
def sample_market_data() -> pd.DataFrame:
    """Create sample market data for testing."""
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    data = {
        'timestamp': dates,
        'price': np.linspace(101, 111, len(dates)),
        'volume': np.random.randint(1000000, 2000000, len(dates)),
        'open': np.linspace(100, 110, len(dates)),
        'high': np.linspace(102, 112, len(dates)),
        'low': np.linspace(98, 108, len(dates)),
        'close': np.linspace(101, 111, len(dates))
    }
    return pd.DataFrame(data)

@pytest.fixture
def sample_strategy_config() -> Dict[str, Any]:
    """Create sample strategy configuration."""
    return {
        'name': 'test_strategy',
        'symbols': ['AAPL', 'MSFT'],
        'parameters': {
            'lookback_period': 20,
            'threshold': 0.02
        }
    }

@pytest.fixture
def sample_backtest_config() -> Dict[str, Any]:
    """Create sample backtest configuration."""
    return {
        'start_date': datetime(2024, 1, 1, tzinfo=timezone.utc),
        'end_date': datetime(2024, 1, 10, tzinfo=timezone.utc),
        'initial_capital': 100000.0,
        'commission': 0.001,
        'slippage': 0.0005
    }

@pytest.fixture
def sample_trades() -> List[Dict[str, Any]]:
    """Create sample trades for testing."""
    return [
        {
            'timestamp': datetime(2024, 1, 2, tzinfo=timezone.utc),
            'symbol': 'AAPL',
            'side': 'buy',
            'quantity': 100,
            'price': 101.0,
            'commission': 0.1
        },
        {
            'timestamp': datetime(2024, 1, 5, tzinfo=timezone.utc),
            'symbol': 'AAPL',
            'side': 'sell',
            'quantity': 100,
            'price': 105.0,
            'commission': 0.1
        }
    ] 