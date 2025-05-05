"""Broker interface for the lunar trading system."""
from abc import ABC, abstractmethod
from typing import Optional, Dict, Any
from datetime import datetime

from .models import Order, Position

class Broker(ABC):
    """Abstract base class for brokers."""

    @abstractmethod
    async def place_order(self, order: Order) -> str:
        """Place an order and return the order ID."""
        pass

    @abstractmethod
    async def get_position(self, symbol: str) -> Optional[Position]:
        """Get current position for a symbol."""
        pass

    @abstractmethod
    async def get_market_data(self, symbol: str) -> Optional[Dict[str, Any]]:
        """Get current market data for a symbol."""
        pass 