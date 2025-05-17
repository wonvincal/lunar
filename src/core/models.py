"""Core data models for the lunar trading system."""
from datetime import datetime
from typing import Optional, Literal
from pydantic import BaseModel, Field, field_validator, model_validator

AssetType = Literal['option', 'stock', 'future', 'index']

class MarketData(BaseModel):
    """Market data model."""
    symbol: str
    timestamp: datetime
    open: float
    high: float
    low: float
    close: float
    volume: int
    source: str

    @model_validator(mode='after')
    def validate_prices(self) -> 'MarketData':
        """Validate price relationships."""
        if self.high < self.low:
            raise ValueError('high price must be greater than low price')
        return self

class Order(BaseModel):
    """Order model."""
    symbol: str
    quantity: float
    side: str  # buy or sell
    order_type: str  # market or limit
    timestamp: datetime
    price: Optional[float] = None
    status: str = "new"

    @field_validator('quantity')
    @classmethod
    def quantity_must_be_positive(cls, v: float) -> float:
        """Validate that quantity is positive."""
        if v <= 0:
            raise ValueError('quantity must be positive')
        return v

class Position(BaseModel):
    """Position model."""
    symbol: str
    quantity: float
    avg_price: float
    current_price: float
    unrealized_pnl: Optional[float] = None

    def __init__(self, **data):
        super().__init__(**data)
        if self.unrealized_pnl is None:
            self.unrealized_pnl = (self.current_price - self.avg_price) * self.quantity 

class Aggregation(BaseModel):
    """Base class for all market data aggregations."""
    timestamp: datetime
    symbol: str = Field(..., min_length=1, max_length=20)
    open: float = Field(..., gt=0)
    high: float = Field(..., gt=0)
    low: float = Field(..., gt=0)
    close: float = Field(..., gt=0)
    volume: int = Field(..., ge=0)

    @model_validator(mode='after')
    def validate_high_low(self):
        if self.high < self.low:
            raise ValueError('high must be greater than low')
        return self
    
class Snapshot(BaseModel):
    """Snapshot of market data at a specific point in time."""
    timestamp: datetime
    symbol: str = Field(..., min_length=1, max_length=20)
    bid: float = Field(..., gt=0)
    ask: float = Field(..., gt=0)    
    last_price: float = Field(..., gt=0)
    
    @model_validator(mode='after')
    def validate_bid_ask(self):
        if self.ask < self.bid:
            raise ValueError('ask must be greater than bid')
        return self 
    
class StockAggregation(Aggregation):
    """Stock market data aggregation model."""
    vwap: Optional[float] = Field(None, gt=0)

class FutureAggregation(Aggregation):
    """Future market data aggregation model."""
    open_interest: int = Field(..., ge=0)
    settlement_price: float = Field(..., gt=0)

class IndexAggregation(Aggregation):
    """Index market data aggregation model."""
    pass

class OptionContract(BaseModel):
    """Option reference data model."""
    symbol: str = Field(..., min_length=1, max_length=50)
    strike_price: float = Field(..., gt=0)
    expiration_date: datetime
    option_type: str = Field(..., pattern='^(CALL|PUT)$')
    underlying_symbol: str = Field(..., min_length=1, max_length=20)

class OptionSnapshot(Snapshot):
    """Option market data snapshot model."""
    open_interest: Optional[int] = Field(..., ge=0)
    implied_volatility: Optional[float] = Field(..., ge=0)
    delta: Optional[float] = Field(..., ge=-1, le=1)
    gamma: Optional[float] = Field(..., ge=0)
    theta: Optional[float] = Field(..., le=0)
    vega: Optional[float] = Field(..., ge=0)
    rho: Optional[float] = Field(..., ge=0)

class OptionAggregation(Aggregation):
    """Option market data aggregation model."""
    vwap: Optional[float] = Field(None, gt=0)
