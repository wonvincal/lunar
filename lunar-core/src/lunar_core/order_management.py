from datetime import datetime
from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field

class OrderType(Enum):
    MARKET = "market"
    LIMIT = "limit"
    STOP = "stop"
    STOP_LIMIT = "stop_limit"

class OrderSide(Enum):
    BUY = "buy"
    SELL = "sell"

class OrderStatus(Enum):
    PENDING = "pending"
    FILLED = "filled"
    CANCELLED = "cancelled"
    REJECTED = "rejected"
    PARTIALLY_FILLED = "partially_filled"

class Order(BaseModel):
    """Represents a trading order."""
    order_id: str
    symbol: str
    order_type: OrderType
    side: OrderSide
    quantity: float
    price: Optional[float] = None
    stop_price: Optional[float] = None
    status: OrderStatus = OrderStatus.PENDING
    created_at: datetime = Field(default_factory=datetime.now)
    filled_at: Optional[datetime] = None
    filled_price: Optional[float] = None
    filled_quantity: float = 0.0

class OrderManager:
    """Manages order operations."""
    
    def __init__(self):
        self._orders: dict[str, Order] = {}
    
    def create_order(self, order: Order) -> Order:
        """Create a new order."""
        self._orders[order.order_id] = order
        return order
    
    def get_order(self, order_id: str) -> Optional[Order]:
        """Retrieve an order by ID."""
        return self._orders.get(order_id)
    
    def update_order_status(self, order_id: str, status: OrderStatus, 
                          filled_price: Optional[float] = None,
                          filled_quantity: Optional[float] = None) -> Optional[Order]:
        """Update the status of an order."""
        if order_id not in self._orders:
            return None
        
        order = self._orders[order_id]
        order.status = status
        
        if status == OrderStatus.FILLED or status == OrderStatus.PARTIALLY_FILLED:
            order.filled_at = datetime.now()
            if filled_price is not None:
                order.filled_price = filled_price
            if filled_quantity is not None:
                order.filled_quantity = filled_quantity
        
        return order
    
    def cancel_order(self, order_id: str) -> Optional[Order]:
        """Cancel an order."""
        return self.update_order_status(order_id, OrderStatus.CANCELLED) 