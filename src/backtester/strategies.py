from abc import ABC, abstractmethod
from datetime import datetime
from typing import Dict, Optional, List
import pandas as pd
import numpy as np

from core import MarketData, Order

class BaseStrategy(ABC):
    """Base class for all trading strategies."""
    
    def __init__(self, config: Dict):
        self.name: str = config['name']
        self.symbols: List[str] = config['symbols']
        self.parameters: Dict = config.get('parameters', {})
    
    @abstractmethod
    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        """Generate trading signals based on market data."""
        pass
    
    @abstractmethod
    def update(self, timestamp: datetime, data: MarketData) -> None:
        """Update strategy state with new market data."""
        pass

class MovingAverageCrossover(BaseStrategy):
    """Moving average crossover strategy."""
    
    def __init__(self, config: Dict):
        super().__init__(config)
        self.short_window = self.parameters.get('short_window', 5)
        self.long_window = self.parameters.get('long_window', 20)
        if self.short_window >= self.long_window:
            raise ValueError("Short window must be less than long window")
    
    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        """Generate trading signals based on moving average crossover."""
        # Calculate moving averages
        short_ma = data['close'].rolling(window=self.short_window).mean()
        long_ma = data['close'].rolling(window=self.long_window).mean()
        
        # Initialize signals DataFrame
        signals = pd.DataFrame(index=data.index)
        signals['signal'] = 0
        
        # Generate signals
        # Compare current and previous day's moving averages
        for i in range(1, len(data)):
            if short_ma.iloc[i] > long_ma.iloc[i] and short_ma.iloc[i-1] <= long_ma.iloc[i-1]:
                signals.loc[signals.index[i], 'signal'] = 1  # Buy signal
            elif short_ma.iloc[i] < long_ma.iloc[i] and short_ma.iloc[i-1] >= long_ma.iloc[i-1]:
                signals.loc[signals.index[i], 'signal'] = -1  # Sell signal
            else:
                signals.loc[signals.index[i], 'signal'] = 0  # No signal
        
        # Handle missing data
        signals['signal'] = signals['signal'].fillna(0)
        
        return signals
    
    def update(self, timestamp: datetime, data: MarketData) -> None:
        """Update strategy state with new market data."""
        # No state to update for this strategy
        pass

class OptionStrategy(BaseStrategy):
    """Base class for option trading strategies."""
    
    def __init__(self):
        super().__init__()
        self.parameters.update({
            'max_position_size': 100000.0,
            'max_loss_per_trade': 5000.0,
            'target_profit': 0.1,  # 10% target profit
            'stop_loss': 0.05,     # 5% stop loss
        })
    
    def generate_signals(self, data: MarketData) -> Optional[Order]:
        """Generate option trading signals."""
        # TODO: Implement option strategy logic
        return None
    
    def update(self, timestamp: datetime, data: MarketData) -> None:
        """Update option strategy state."""
        # TODO: Implement state update logic
        pass 