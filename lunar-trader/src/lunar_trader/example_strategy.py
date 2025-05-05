"""Example trading strategy."""
from typing import Dict, Optional
from datetime import datetime

from lunar_core.order_management import Order, OrderType, OrderSide
from lunar_core.risk_management import Position
from .strategy import Strategy
from .trading_engine import MarketData

class ExampleStrategy(Strategy):
    """Example strategy that implements a simple moving average crossover."""
    
    def __init__(self, trading_engine, symbol: str, short_window: int = 5, long_window: int = 20):
        super().__init__(trading_engine)
        self.symbol = symbol
        self.short_window = short_window
        self.long_window = long_window
        self.prices: Dict[datetime, float] = {}
        self.position: Optional[Position] = None
    
    async def on_market_data(self, market_data: MarketData) -> None:
        """Handle new market data."""
        if market_data.symbol != self.symbol:
            return
        
        # Update price history
        self.prices[market_data.timestamp] = market_data.last_price
        
        # Keep only the necessary price history
        if len(self.prices) > self.long_window:
            oldest_timestamp = min(self.prices.keys())
            del self.prices[oldest_timestamp]
        
        # Calculate moving averages
        if len(self.prices) >= self.long_window:
            prices = list(self.prices.values())
            short_ma = sum(prices[-self.short_window:]) / self.short_window
            long_ma = sum(prices[-self.long_window:]) / self.long_window
            
            # Get current position
            self.position = await self.get_position(self.symbol)
            
            # Trading logic
            if short_ma > long_ma and (not self.position or self.position.quantity <= 0):
                # Buy signal
                await self.place_order(
                    symbol=self.symbol,
                    order_type=OrderType.MARKET,
                    side=OrderSide.BUY,
                    quantity=100  # Example quantity
                )
            elif short_ma < long_ma and self.position and self.position.quantity > 0:
                # Sell signal
                await self.place_order(
                    symbol=self.symbol,
                    order_type=OrderType.MARKET,
                    side=OrderSide.SELL,
                    quantity=self.position.quantity
                )
    
    async def on_order_fill(self, order: Order) -> None:
        """Handle order fills."""
        if order.symbol != self.symbol:
            return
        
        print(f"Order filled: {order.order_id}")
        print(f"Symbol: {order.symbol}")
        print(f"Side: {order.side}")
        print(f"Quantity: {order.quantity}")
        print(f"Price: {order.filled_price}")
    
    async def on_position_update(self, position: Position) -> None:
        """Handle position updates."""
        if position.symbol != self.symbol:
            return
        
        self.position = position
        print(f"Position updated: {position.symbol}")
        print(f"Quantity: {position.quantity}")
        print(f"Average Price: {position.average_price}")
        print(f"Current Price: {position.current_price}")
        print(f"Unrealized PnL: {position.unrealized_pnl}") 