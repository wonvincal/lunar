from pydantic import ValidationError
import pytest
from datetime import datetime, timedelta, timezone
from core.models import Aggregation, FutureAggregation, MarketData, OptionAggregation, OptionContract, OptionSnapshot, Order, Position, Snapshot, StockAggregation

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

def test_valid_aggregation():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000
    }
    try:
        Aggregation(**data)
        assert True
    except ValidationError:
        pytest.fail("ValidationError raised for valid data")

def test_invalid_symbol_min_length():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "String should have at least 1 character" in str(excinfo.value)

def test_invalid_symbol_max_length():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "A" * 21,
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "String should have at most 20 characters" in str(excinfo.value)

def test_invalid_open_less_than_or_equal_to_zero():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 0.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "Input should be greater than 0" in str(excinfo.value)

def test_invalid_high_less_than_or_equal_to_zero():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 0.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "Input should be greater than 0" in str(excinfo.value)

def test_invalid_low_less_than_or_equal_to_zero():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 0.0,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "Input should be greater than 0" in str(excinfo.value)

def test_invalid_close_less_than_or_equal_to_zero():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 0.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "Input should be greater than 0" in str(excinfo.value)

def test_invalid_volume_less_than_zero():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": -1
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "Input should be greater than or equal to 0" in str(excinfo.value)

def test_invalid_high_less_than_low():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 149.0,
        "low": 150.0,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        Aggregation(**data)
    assert "high must be greater than low" in str(excinfo.value)

# Snapshot
def test_valid_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "GOOGL",
        "bid": 150.0,
        "ask": 150.5,
        "last_price": 150.25,
    }
    try:
        Snapshot(**data)
        assert True
    except ValidationError:
        pytest.fail("ValidationError raised for valid data")

def test_invalid_symbol_min_length_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "",
        "bid": 150.0,
        "ask": 150.5,
        "last_price": 150.25,
    }
    with pytest.raises(ValidationError) as excinfo:
        Snapshot(**data)
    assert "string" in str(excinfo.value).lower()
    assert "at least 1" in str(excinfo.value).lower()

def test_invalid_symbol_max_length_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "A" * 21,
        "bid": 150.0,
        "ask": 150.5,
        "last_price": 150.25,
    }
    with pytest.raises(ValidationError) as excinfo:
        Snapshot(**data)
    assert "string" in str(excinfo.value).lower()
    assert "at most 20" in str(excinfo.value).lower()

def test_invalid_bid_less_than_or_equal_to_zero_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "GOOGL",
        "bid": 0.0,
        "ask": 150.5,
        "last_price": 150.25,
    }
    with pytest.raises(ValidationError) as excinfo:
        Snapshot(**data)
    assert "greater than 0" in str(excinfo.value).lower()

def test_invalid_ask_less_than_or_equal_to_zero_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "GOOGL",
        "bid": 150.0,
        "ask": 0.0,
        "last_price": 150.25,
    }
    with pytest.raises(ValidationError) as excinfo:
        Snapshot(**data)
    assert "greater than 0" in str(excinfo.value).lower()

def test_invalid_last_price_less_than_or_equal_to_zero_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "GOOGL",
        "bid": 150.0,
        "ask": 150.5,
        "last_price": 0.0,
    }
    with pytest.raises(ValidationError) as excinfo:
        Snapshot(**data)
    assert "greater than 0" in str(excinfo.value).lower()

def test_invalid_ask_less_than_bid_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "GOOGL",
        "bid": 150.5,
        "ask": 150.0,
        "last_price": 150.25,
    }
    with pytest.raises(ValidationError) as excinfo:
        Snapshot(**data)
    assert "ask must be greater than bid" in str(excinfo.value).lower()

def test_valid_stock_aggregation():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000,
        "vwap": 100.5
    }
    """Test creation of StockAggregation with valid VWAP."""
    agg = StockAggregation(**data)
    assert agg.vwap == 100.5

def test_invalid_stock_aggregation():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000,
        "vwap": -10
    }
    """Test that VWAP must be greater than 0."""
    with pytest.raises(ValidationError) as excinfo:
        StockAggregation(**data) # VWAP should be positive
    assert "Input should be greater than 0" in str(excinfo.value)

def test_valid_future_aggregation():
    """Test valid FutureAggregation instance."""
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000,
        "open_interest": 500,
        "settlement_price": 1250.75
    }
    agg = FutureAggregation(**data)
    assert agg.open_interest == 500
    assert agg.settlement_price == 1250.75

def test_invalid_open_interest():
    """Test that open_interest must be non-negative."""
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000,
        "open_interest": -1,
        "settlement_price": 1250.75
    }
    with pytest.raises(ValidationError) as excinfo:
        FutureAggregation(**data)
    assert "Input should be greater than or equal to 0" in str(excinfo.value)

def test_invalid_settlement_price():
    """Test that settlement_price must be positive."""
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000,
        "open_interest": 500,
        "settlement_price": 0
    }
    with pytest.raises(ValidationError) as excinfo:
        FutureAggregation(**data)
    assert "Input should be greater than 0" in str(excinfo.value)


def test_missing_fields():
    """Test that required fields must be provided."""
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000
    }
    with pytest.raises(ValidationError) as excinfo:
        FutureAggregation(**data)
    assert "missing" in str(excinfo.value)
    assert "settlement_price" in str(excinfo.value)
    assert "open_interest" in str(excinfo.value)

def test_invalid_option_aggregation():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL",
        "open": 150.0,
        "high": 155.0,
        "low": 149.5,
        "close": 154.0,
        "volume": 1000000,
        "vwap": -10
    }
    """Test that VWAP must be greater than 0."""
    with pytest.raises(ValidationError) as excinfo:
        OptionAggregation(**data) # VWAP should be positive
    assert "Input should be greater than 0" in str(excinfo.value)

def test_valid_option_contract():
    """Test valid OptionContract instance."""
    option = OptionContract(
        symbol="AAPL230615C150",
        strike_price=150.0,
        expiration_date=datetime.now() + timedelta(days=30),
        option_type="CALL",
        underlying_symbol="AAPL"
    )
    assert option.symbol == "AAPL230615C150"
    assert option.strike_price == 150.0
    assert option.option_type == "CALL"
    assert option.underlying_symbol == "AAPL"

@pytest.mark.parametrize("symbol, strike_price, expiration_date, option_type, underlying_symbol, expected_error", [
    ("", 150.0, datetime.now(), "CALL", "AAPL", "String should have at least 1 character"),  # Too short symbol
    ("A" * 51, 150.0, datetime.now(), "CALL", "AAPL", "String should have at most 50 characters"),  # Too long symbol
    ("AAPL230615C150", 0, datetime.now(), "CALL", "AAPL", "Input should be greater than 0"),  # Invalid strike_price
    ("AAPL230615C150", 150.0, datetime.now(), "INVALID", "AAPL", "String should match pattern"),  # Invalid option_type
    ("AAPL230615C150", 150.0, datetime.now(), "CALL", "", "String should have at least 1 character"),  # Too short underlying_symbol
    ("AAPL230615C150", 150.0, datetime.now(), "CALL", "A" * 21, "String should have at most 20 characters")  # Too long underlying_symbol
])
def test_invalid_option_contract(symbol, strike_price, expiration_date, option_type, underlying_symbol, expected_error):
    """Test various invalid OptionContract cases."""
    with pytest.raises(ValidationError) as error:
        OptionContract(symbol=symbol, strike_price=strike_price, expiration_date=expiration_date, option_type=option_type, underlying_symbol=underlying_symbol)
    
    assert expected_error in str(error.value)

def test_valid_option_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    try:
        OptionSnapshot(**data)
        assert True
    except ValidationError:
        pytest.fail("ValidationError raised for valid option snapshot data")

def test_option_snapshot_inherits_snapshot_validations():
    now = datetime.now()
    invalid_data = {
        "timestamp": now,
        "symbol": "",  # Invalid symbol length
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**invalid_data)
    assert "string" in str(excinfo.value).lower()
    assert "at least 1" in str(excinfo.value).lower()

    invalid_bid_ask = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.1,
        "ask": 1.0,  # Invalid bid > ask
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**invalid_bid_ask)
    assert "ask must be greater than bid" in str(excinfo.value).lower()

def test_invalid_open_interest_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": -1,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**data)
    assert "greater than or equal to 0" in str(excinfo.value).lower()

def test_invalid_implied_volatility_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": -0.1,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**data)
    assert "greater than or equal to 0" in str(excinfo.value).lower()

def test_invalid_delta_snapshot():
    now = datetime.now()
    invalid_delta_lower = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": -1.1,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**invalid_delta_lower)
    assert "greater than or equal to -1" in str(excinfo.value).lower()

    invalid_delta_upper = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 1.1,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**invalid_delta_upper)
    assert "less than or equal to 1" in str(excinfo.value).lower()

def test_invalid_gamma_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": -0.01,
        "theta": -0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**data)
    assert "greater than or equal to 0" in str(excinfo.value).lower()

def test_invalid_theta_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": 0.01,
        "vega": 0.05,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**data)
    assert "less than or equal to 0" in str(excinfo.value).lower()

def test_invalid_vega_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": -0.01,
        "rho": 0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**data)
    assert "greater than or equal to 0" in str(excinfo.value).lower()

def test_invalid_rho_snapshot():
    now = datetime.now()
    data = {
        "timestamp": now,
        "symbol": "AAPL250620C160",
        "bid": 1.0,
        "ask": 1.1,
        "last_price": 1.05,
        "open_interest": 100,
        "implied_volatility": 0.25,
        "delta": 0.5,
        "gamma": 0.02,
        "theta": -0.01,
        "vega": 0.05,
        "rho": -0.01,
    }
    with pytest.raises(ValidationError) as excinfo:
        OptionSnapshot(**data)
    assert "greater than or equal to 0" in str(excinfo.value).lower()