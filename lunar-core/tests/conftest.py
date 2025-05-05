import pytest
from datetime import datetime, timezone
from typing import Dict, Any

@pytest.fixture
def sample_market_data() -> Dict[str, Any]:
    return {
        "symbol": "AAPL",
        "timestamp": datetime.now(timezone.utc),
        "open": 150.0,
        "high": 151.0,
        "low": 149.0,
        "close": 150.5,
        "volume": 1000000,
        "source": "test"
    }

@pytest.fixture
def sample_order() -> Dict[str, Any]:
    return {
        "symbol": "AAPL",
        "quantity": 100,
        "side": "buy",
        "order_type": "market",
        "timestamp": datetime.now(timezone.utc)
    }

@pytest.fixture
def sample_position() -> Dict[str, Any]:
    return {
        "symbol": "AAPL",
        "quantity": 100,
        "avg_price": 150.0,
        "current_price": 150.5,
        "unrealized_pnl": 50.0
    } 