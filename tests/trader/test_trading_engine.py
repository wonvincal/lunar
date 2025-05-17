"""Tests for the TradingEngine class."""
import pytest
import asyncio
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

from trader.trading_engine import TradingEngine, MarketData
from core.order_management import Order, OrderType, OrderSide, OrderStatus
from core.risk_management import Position
from core.logging import TradeError, MarketDataError

@pytest.fixture
def mock_session():
    """Create a mock aiohttp session."""
    mock = AsyncMock()
    mock.close = AsyncMock()
    return mock

@pytest.fixture
def trading_engine(mock_session):
    """Create a TradingEngine instance with mocked session."""
    engine = TradingEngine(api_key="test_key")
    engine._session = mock_session
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

@pytest.mark.asyncio
async def test_trading_engine_initialization():
    """Test TradingEngine initialization."""
    engine = TradingEngine(api_key="test_key")
    assert engine.api_key == "test_key"
    assert engine.base_url == "https://api.polygon.io"
    assert engine.positions == {}
    assert engine.market_data == {}
    assert engine._session is None

@pytest.mark.asyncio
async def test_trading_engine_start(trading_engine):
    """Test starting the trading engine."""
    await trading_engine.start()
    assert trading_engine._session is not None

@pytest.mark.asyncio
async def test_trading_engine_stop(trading_engine):
    """Test stopping the trading engine."""
    await trading_engine.stop()
    assert trading_engine._session is None

@pytest.mark.asyncio
async def test_place_order_success(trading_engine, sample_order):
    """Test successful order placement."""
    with patch.object(trading_engine.order_manager, 'create_order', return_value=sample_order):
        placed_order = await trading_engine.place_order(sample_order)
        assert placed_order == sample_order
        assert placed_order.order_id == "test_order"
        assert placed_order.symbol == "AAPL"
        assert placed_order.order_type == OrderType.LIMIT
        assert placed_order.side == OrderSide.BUY
        assert placed_order.quantity == 100
        assert placed_order.price == 150.0

@pytest.mark.asyncio
async def test_place_order_validation_failure(trading_engine):
    """Test order placement with invalid parameters."""
    invalid_order = Order(
        order_id="test_order",
        symbol="AAPL",
        order_type=OrderType.LIMIT,
        side=OrderSide.BUY,
        quantity=0,  # Invalid quantity
        price=150.0
    )
    with pytest.raises(ValueError):
        await trading_engine.place_order(invalid_order)

@pytest.mark.asyncio
async def test_get_position(trading_engine):
    """Test getting position information."""
    position = Position(
        symbol="AAPL",
        quantity=100,
        average_price=150.0,
        current_price=155.0,
        unrealized_pnl=500.0  # (155.0 - 150.0) * 100
    )
    trading_engine.positions["AAPL"] = position
    
    retrieved_position = await trading_engine.get_position("AAPL")
    assert retrieved_position == position
    assert retrieved_position.quantity == 100
    assert retrieved_position.average_price == 150.0

@pytest.mark.asyncio
async def test_get_market_data(trading_engine, sample_market_data):
    """Test getting market data."""
    trading_engine.market_data["AAPL"] = sample_market_data
    
    retrieved_data = await trading_engine.get_market_data("AAPL")
    assert retrieved_data == sample_market_data
    assert retrieved_data.symbol == "AAPL"
    assert retrieved_data.last_price == 150.0

@pytest.mark.asyncio
async def test_cancel_order(trading_engine, sample_order):
    """Test order cancellation."""
    with patch.object(trading_engine.order_manager, 'cancel_order', return_value=sample_order):
        cancelled_order = await trading_engine.cancel_order("test_order")
        assert cancelled_order == sample_order
        assert cancelled_order.order_id == "test_order"

@pytest.mark.asyncio
async def test_get_order_status(trading_engine, sample_order):
    """Test getting order status."""
    with patch.object(trading_engine.order_manager, 'get_order', return_value=sample_order):
        order = await trading_engine.get_order_status("test_order")
        assert order == sample_order
        assert order.order_id == "test_order"
        assert order.symbol == "AAPL"

@pytest.mark.asyncio
async def test_market_data_subscription(trading_engine):
    """Test market data subscription."""
    # Create a task for market data subscription
    subscription_task = asyncio.create_task(trading_engine._subscribe_market_data())
    
    # Let it run for a short time
    await asyncio.sleep(0.1)
    
    # Cancel the task
    subscription_task.cancel()
    try:
        await subscription_task
    except asyncio.CancelledError:
        pass 