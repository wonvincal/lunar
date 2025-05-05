"""Data models for the backtesting system."""
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field, field_validator, model_validator

class BaseAssetData(BaseModel):
    """Base class for all asset data."""
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

class StockData(BaseAssetData):
    """Stock data model."""
    vwap: Optional[float] = Field(None, gt=0)

class FutureData(BaseAssetData):
    """Future data model."""
    open_interest: int = Field(..., ge=0)
    settlement_price: float = Field(..., gt=0)

class IndexData(BaseAssetData):
    """Index data model."""
    pass

class OptionData(BaseModel):
    """Option data model."""
    timestamp: datetime
    symbol: str = Field(..., min_length=1, max_length=20)
    strike_price: float = Field(..., gt=0)
    expiration_date: datetime
    option_type: str = Field(..., pattern='^(CALL|PUT)$')
    last_price: float = Field(..., gt=0)
    bid: float = Field(..., gt=0)
    ask: float = Field(..., gt=0)
    volume: int = Field(..., ge=0)
    open_interest: int = Field(..., ge=0)
    implied_volatility: float = Field(..., ge=0)
    delta: float = Field(..., ge=-1, le=1)
    gamma: float = Field(..., ge=0)
    theta: float = Field(..., le=0)
    vega: float = Field(..., ge=0)
    rho: float = Field(..., ge=0)

    @model_validator(mode='after')
    def validate_bid_ask(self):
        if self.ask < self.bid:
            raise ValueError('ask must be greater than bid')
        return self 