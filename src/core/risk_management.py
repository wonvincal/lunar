"""Risk management module for the Lunar trading system."""
from datetime import datetime
from typing import Dict, Optional
from pydantic import BaseModel, Field

class Position(BaseModel):
    """Represents a trading position."""
    symbol: str
    quantity: float
    average_price: float
    current_price: float
    unrealized_pnl: float
    realized_pnl: float = 0.0
    last_updated: datetime = Field(default_factory=datetime.now)

class RiskManager:
    """Manages risk for trading operations."""
    
    def __init__(self, max_position_size: float = 100000.0,
                 max_loss_per_trade: float = 0.02,
                 max_daily_loss: float = 0.05):
        self.max_position_size = max_position_size
        self.max_loss_per_trade = max_loss_per_trade
        self.max_daily_loss = max_daily_loss
        self.positions: Dict[str, Position] = {}
        self.daily_pnl: float = 0.0
    
    def update_position(self, position: Position) -> None:
        """Update a position."""
        self.positions[position.symbol] = position
        self._update_daily_pnl()
    
    def _update_daily_pnl(self) -> None:
        """Update daily PnL."""
        self.daily_pnl = sum(
            position.realized_pnl + position.unrealized_pnl
            for position in self.positions.values()
        )
    
    def can_open_position(self, symbol: str, quantity: float,
                         price: float) -> bool:
        """Check if a new position can be opened."""
        position_value = quantity * price
        if position_value > self.max_position_size:
            return False
        
        # Check if adding this position would exceed daily loss limit
        potential_loss = position_value * self.max_loss_per_trade
        if self.daily_pnl - potential_loss < -self.max_daily_loss:
            return False
        
        return True
    
    def can_increase_position(self, symbol: str, additional_quantity: float,
                            price: float) -> bool:
        """Check if an existing position can be increased."""
        if symbol not in self.positions:
            return self.can_open_position(symbol, additional_quantity, price)
        
        position = self.positions[symbol]
        new_quantity = position.quantity + additional_quantity
        new_value = new_quantity * price
        
        if new_value > self.max_position_size:
            return False
        
        # Check if increasing this position would exceed daily loss limit
        potential_loss = additional_quantity * price * self.max_loss_per_trade
        if self.daily_pnl - potential_loss < -self.max_daily_loss:
            return False
        
        return True

    def check_position_limit(self, symbol: str, quantity: float, price: float) -> bool:
        """Check if a new position would exceed limits."""
        position_value = abs(quantity * price)
        if position_value > self.max_position_size:
            return False
        
        if symbol in self.positions:
            current_position = self.positions[symbol]
            new_position_value = abs((current_position.quantity + quantity) * price)
            if new_position_value > self.max_position_size:
                return False
        
        return True
    
    def check_daily_loss_limit(self) -> bool:
        """Check if daily loss limit has been reached."""
        total_pnl = self.daily_pnl + sum(
            position.unrealized_pnl for position in self.positions.values()
        )
        return total_pnl >= -self.max_daily_loss
    
    def get_position(self, symbol: str) -> Optional[Position]:
        """Get position for a symbol."""
        return self.positions.get(symbol)
    
    def get_all_positions(self) -> Dict[str, Position]:
        """Get all positions."""
        return self.positions.copy() 