import pytest
from datetime import datetime, timezone
from lunar_core.models import MarketData, Order, Position

def test_market_data_creation(sample_market_data):
    """Test MarketData model creation and validation"""
    market_data = MarketData(**sample_market_data)
    
    assert market_data.symbol == "AAPL"
    assert isinstance(market_data.timestamp, datetime)
    assert market_data.open == 150.0
    assert market_data.high == 151.0
    assert market_data.low == 149.0
    assert market_data.close == 150.5
    assert market_data.volume == 1000000
    assert market_data.source == "test"

def test_market_data_validation():
    """Test MarketData model validation"""
    with pytest.raises(ValueError):
        MarketData(
            symbol="AAPL",
            timestamp=datetime.now(timezone.utc),
            open=150.0,
            high=148.0,  # Invalid: high < low
            low=149.0,
            close=150.0,
            volume=1000000,
            source="test"
        )

def test_order_creation(sample_order):
    """Test Order model creation and validation"""
    order = Order(**sample_order)
    
    assert order.symbol == "AAPL"
    assert order.quantity == 100
    assert order.side == "buy"
    assert order.order_type == "market"
    assert isinstance(order.timestamp, datetime)

def test_order_validation():
    """Test Order model validation"""
    with pytest.raises(ValueError):
        Order(
            symbol="AAPL",
            quantity=-100,  # Invalid: negative quantity
            side="buy",
            order_type="market",
            timestamp=datetime.now(timezone.utc)
        )

def test_position_creation(sample_position):
    """Test Position model creation and validation"""
    position = Position(**sample_position)
    
    assert position.symbol == "AAPL"
    assert position.quantity == 100
    assert position.avg_price == 150.0
    assert position.current_price == 150.5
    assert position.unrealized_pnl == 50.0

def test_position_calculation():
    """Test Position model calculations"""
    position = Position(
        symbol="AAPL",
        quantity=100,
        avg_price=150.0,
        current_price=160.0
    )
    
    assert position.unrealized_pnl == 1000.0  # (160 - 150) * 100 