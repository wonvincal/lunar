from datetime import datetime
from typing import Dict, List, Optional, Union
import pandas as pd
from pydantic import BaseModel, Field

class MarketData(BaseModel):
    """Base class for market data."""
    timestamp: datetime
    symbol: str
    price: float
    volume: Optional[int] = None
    bid: Optional[float] = None
    ask: Optional[float] = None

class MarketDataHandler:
    """Handles market data operations."""
    
    def __init__(self):
        self._data: Dict[str, List[MarketData]] = {}
    
    def add_data(self, data: Union[MarketData, List[MarketData]]) -> None:
        """Add market data to the handler."""
        if isinstance(data, MarketData):
            data = [data]
        
        for item in data:
            if item.symbol not in self._data:
                self._data[item.symbol] = []
            self._data[item.symbol].append(item)
    
    def get_data(self, symbol: str, start_time: Optional[datetime] = None, 
                end_time: Optional[datetime] = None) -> pd.DataFrame:
        """Retrieve market data for a symbol within a time range."""
        if symbol not in self._data:
            return pd.DataFrame()
        
        data = self._data[symbol]
        if start_time:
            data = [d for d in data if d.timestamp >= start_time]
        if end_time:
            data = [d for d in data if d.timestamp <= end_time]
        
        return pd.DataFrame([d.dict() for d in data])
    
    def get_latest_price(self, symbol: str) -> Optional[float]:
        """Get the latest price for a symbol."""
        if symbol not in self._data or not self._data[symbol]:
            return None
        return self._data[symbol][-1].price 