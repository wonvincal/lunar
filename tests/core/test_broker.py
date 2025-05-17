import pytest
from datetime import datetime, timezone
from core.broker import Broker
from core.models import Order, Position

class MockBroker(Broker):
    """Mock broker implementation for testing"""
    def __init__(self):
        self.orders = []
        self.positions = {}
        self.market_data = {}
    
    async def place_order(self, order: Order) -> str:
        self.orders.append(order)
        return f"order_{len(self.orders)}"
    
    async def get_position(self, symbol: str) -> Position:
        return self.positions.get(symbol)
    
    async def get_market_data(self, symbol: str) -> dict:
        return self.market_data.get(symbol)

@pytest.fixture
def mock_broker():
    return MockBroker()

@pytest.mark.asyncio
async def test_place_order(mock_broker, sample_order):
    """Test placing an order through the broker"""
    order = Order(**sample_order)
    order_id = await mock_broker.place_order(order)
    
    assert order_id == "order_1"
    assert len(mock_broker.orders) == 1
    assert mock_broker.orders[0].symbol == "AAPL"
    assert mock_broker.orders[0].quantity == 100

@pytest.mark.asyncio
async def test_get_position(mock_broker, sample_position):
    """Test getting a position from the broker"""
    position = Position(**sample_position)
    mock_broker.positions["AAPL"] = position
    
    retrieved_position = await mock_broker.get_position("AAPL")
    assert retrieved_position is not None
    assert retrieved_position.symbol == "AAPL"
    assert retrieved_position.quantity == 100
    assert retrieved_position.avg_price == 150.0

@pytest.mark.asyncio
async def test_get_market_data(mock_broker, sample_market_data):
    """Test getting market data from the broker"""
    mock_broker.market_data["AAPL"] = sample_market_data
    
    market_data = await mock_broker.get_market_data("AAPL")
    assert market_data is not None
    assert market_data["symbol"] == "AAPL"
    assert market_data["open"] == 150.0
    assert market_data["close"] == 150.5 