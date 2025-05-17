"""Data management interface for the lunar trading system."""
from abc import ABC, abstractmethod
from typing import List, Optional
from datetime import datetime

from .models import MarketData

class DataManager(ABC):
    """Abstract base class for data managers."""

    @abstractmethod
    async def get_historical_data(self, symbol: str, start: datetime, end: datetime) -> List[MarketData]:
        """Get historical market data for a symbol between start and end dates."""
        pass

    @abstractmethod
    async def get_latest_data(self, symbol: str) -> Optional[MarketData]:
        """Get the latest market data for a symbol."""
        pass 