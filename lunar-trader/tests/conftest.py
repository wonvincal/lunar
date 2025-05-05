"""Shared test fixtures for lunar_trader tests."""
import pytest
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

from lunar_trader.trading_engine import TradingEngine, MarketData
from lunar_core.order_management import Order, OrderType, OrderSide, OrderStatus
from lunar_core.risk_management import Position

@pytest.fixture
def mock_session():
    """Create a mock aiohttp session."""
    with patch('aiohttp.ClientSession') as mock:
        yield mock

@pytest.fixture
def mock_trading_engine(mock_session):
    """Create a mock trading engine."""
    engine = MagicMock(spec=TradingEngine)
    engine.api_key = "test_key"
    engine.base_url = "https://api.polygon.io"
    engine._session = mock_session
    engine.place_order = AsyncMock()
    engine.cancel_order = AsyncMock()
    engine.get_position = AsyncMock()
    engine.get_market_data = AsyncMock()
    return engine

@pytest.fixture
def sample_market_data():
    """Create sample market data."""
    return MarketData(
        symbol="AAPL",
        timestamp=datetime.now(),
        last_price=150.0,
        bid=149.9,
        ask=150.1,
        volume=1000
    )

@pytest.fixture
def sample_order():
    """Create a sample order."""
    return Order(
        order_id="test_order",
        symbol="AAPL",
        order_type=OrderType.LIMIT,
        side=OrderSide.BUY,
        quantity=100,
        price=150.0
    )

@pytest.fixture
def sample_position():
    """Create a sample position."""
    return Position(symbol="AAPL", quantity=100, average_price=150.0) 