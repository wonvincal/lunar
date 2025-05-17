"""Strategy interface for trading strategies."""
from abc import ABC, abstractmethod
from typing import Dict, List, Optional
from datetime import datetime

from core.order_management import Order, OrderType, OrderSide
from core.risk_management import Position
from .trading_engine import TradingEngine, MarketData

class Strategy(ABC):
    """Abstract base class for trading strategies."""
    
    def __init__(self, trading_engine: TradingEngine):
        self.trading_engine = trading_engine
        self.positions: Dict[str, Position] = {}
        self.market_data: Dict[str, MarketData] = {}
    
    @abstractmethod
    async def on_market_data(self, market_data: MarketData) -> None:
        """Handle new market data."""
        pass
    
    @abstractmethod
    async def on_order_fill(self, order: Order) -> None:
        """Handle order fills."""
        pass
    
    @abstractmethod
    async def on_position_update(self, position: Position) -> None:
        """Handle position updates."""
        pass
    
    async def place_order(self, symbol: str, order_type: OrderType, side: OrderSide,
                         quantity: float, price: Optional[float] = None,
                         stop_price: Optional[float] = None) -> Order:
        """Place a new order."""
        order = Order(
            order_id=f"{symbol}_{datetime.now().timestamp()}",
            symbol=symbol,
            order_type=order_type,
            side=side,
            quantity=quantity,
            price=price,
            stop_price=stop_price
        )
        return await self.trading_engine.place_order(order)
    
    async def cancel_order(self, order_id: str) -> Optional[Order]:
        """Cancel an existing order."""
        return await self.trading_engine.cancel_order(order_id)
    
    async def get_position(self, symbol: str) -> Optional[Position]:
        """Get current position for a symbol."""
        return await self.trading_engine.get_position(symbol)
    
    async def get_market_data(self, symbol: str) -> Optional[MarketData]:
        """Get current market data for a symbol."""
        return await self.trading_engine.get_market_data(symbol)
    
    async def update_positions(self) -> None:
        """Update all positions."""
        for symbol in self.positions:
            position = await self.get_position(symbol)
            if position:
                self.positions[symbol] = position
                await self.on_position_update(position)
    
    async def update_market_data(self, symbol: str) -> None:
        """Update market data for a symbol."""
        market_data = await self.get_market_data(symbol)
        if market_data:
            self.market_data[symbol] = market_data
            await self.on_market_data(market_data) 