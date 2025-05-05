"""Core data models for the lunar trading system."""
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field, field_validator, model_validator

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