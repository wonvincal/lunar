"""Tests for the Strategy class."""
import pytest
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

from trader.strategy import Strategy
from trader.trading_engine import TradingEngine, MarketData
from core.order_management import Order, OrderType, OrderSide, OrderStatus
from core.risk_management import Position

class TestStrategy(Strategy):
    """Concrete implementation of Strategy for testing."""
    
    async def on_market_data(self, market_data: MarketData) -> None:
        """Handle new market data."""
        self.last_market_data = market_data
    
    async def on_order_fill(self, order: Order) -> None:
        """Handle order fills."""
        self.last_order_fill = order
    
    async def on_position_update(self, position: Position) -> None:
        """Handle position updates."""
        self.last_position_update = position

@pytest.fixture
def mock_trading_engine():
    """Create a mock trading engine."""
    engine = MagicMock(spec=TradingEngine)
    engine.place_order = AsyncMock()
    engine.cancel_order = AsyncMock()
    engine.get_position = AsyncMock()
    engine.get_market_data = AsyncMock()
    return engine

@pytest.fixture
def strategy(mock_trading_engine):
    """Create a test strategy instance."""
    return TestStrategy(mock_trading_engine)

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
    return Position(
        symbol="AAPL",
        quantity=100,
        average_price=150.0,
        current_price=155.0,
        unrealized_pnl=500.0  # (155.0 - 150.0) * 100
    )

@pytest.mark.asyncio
async def test_strategy_initialization(mock_trading_engine):
    """Test strategy initialization."""
    strategy = TestStrategy(mock_trading_engine)
    assert strategy.trading_engine == mock_trading_engine
    assert strategy.positions == {}
    assert strategy.market_data == {}

@pytest.mark.asyncio
async def test_place_order(strategy, sample_order):
    """Test order placement through strategy."""
    strategy.trading_engine.place_order.return_value = sample_order
    
    placed_order = await strategy.place_order(
        symbol="AAPL",
        order_type=OrderType.LIMIT,
        side=OrderSide.BUY,
        quantity=100,
        price=150.0
    )
    
    assert placed_order == sample_order
    strategy.trading_engine.place_order.assert_called_once()

@pytest.mark.asyncio
async def test_cancel_order(strategy, sample_order):
    """Test order cancellation through strategy."""
    strategy.trading_engine.cancel_order.return_value = sample_order
    
    cancelled_order = await strategy.cancel_order("test_order")
    
    assert cancelled_order == sample_order
    strategy.trading_engine.cancel_order.assert_called_once_with("test_order")

@pytest.mark.asyncio
async def test_get_position(strategy, sample_position):
    """Test getting position through strategy."""
    strategy.trading_engine.get_position.return_value = sample_position
    
    position = await strategy.get_position("AAPL")
    
    assert position == sample_position
    strategy.trading_engine.get_position.assert_called_once_with("AAPL")

@pytest.mark.asyncio
async def test_get_market_data(strategy, sample_market_data):
    """Test getting market data through strategy."""
    strategy.trading_engine.get_market_data.return_value = sample_market_data
    
    market_data = await strategy.get_market_data("AAPL")
    
    assert market_data == sample_market_data
    strategy.trading_engine.get_market_data.assert_called_once_with("AAPL")

@pytest.mark.asyncio
async def test_update_positions(strategy, sample_position):
    """Test updating positions."""
    strategy.trading_engine.get_position.return_value = sample_position
    strategy.positions["AAPL"] = sample_position
    
    await strategy.update_positions()
    
    assert strategy.last_position_update == sample_position
    strategy.trading_engine.get_position.assert_called_once_with("AAPL")

@pytest.mark.asyncio
async def test_update_market_data(strategy, sample_market_data):
    """Test updating market data."""
    strategy.trading_engine.get_market_data.return_value = sample_market_data
    
    await strategy.update_market_data("AAPL")
    
    assert strategy.last_market_data == sample_market_data
    assert strategy.market_data["AAPL"] == sample_market_data
    strategy.trading_engine.get_market_data.assert_called_once_with("AAPL")

@pytest.mark.asyncio
async def test_on_market_data(strategy, sample_market_data):
    """Test market data callback."""
    await strategy.on_market_data(sample_market_data)
    assert strategy.last_market_data == sample_market_data

@pytest.mark.asyncio
async def test_on_order_fill(strategy, sample_order):
    """Test order fill callback."""
    await strategy.on_order_fill(sample_order)
    assert strategy.last_order_fill == sample_order

@pytest.mark.asyncio
async def test_on_position_update(strategy, sample_position):
    """Test position update callback."""
    await strategy.on_position_update(sample_position)
    assert strategy.last_position_update == sample_position 