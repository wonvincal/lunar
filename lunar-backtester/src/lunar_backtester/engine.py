from datetime import datetime
from typing import Dict, List, Optional, Union
import pandas as pd
from pydantic import BaseModel, ConfigDict
import os

from lunar_core import MarketDataHandler, OrderManager, RiskManager, MarketData

class BacktestResult(BaseModel):
    """Results from a backtest run."""
    model_config = ConfigDict(arbitrary_types_allowed=True)
    
    initial_capital: float
    final_capital: float
    total_return: float
    trades: List[Dict]
    positions: List[Dict]
    equity_curve: pd.DataFrame
    metrics: Dict[str, float]

class BacktestEngine:
    """Main backtesting engine."""
    
    def __init__(self, initial_capital: float = 100000.0):
        self.initial_capital = initial_capital
        self.current_capital = initial_capital
        self.market_data = MarketDataHandler()
        self.order_manager = OrderManager()
        self.risk_manager = RiskManager()
        self._results: Optional[BacktestResult] = None
        self._data_loaded = False
    
    def load_data(self, data_path: str, data_format: str = 'csv') -> None:
        """Load historical market data from a file.
        
        Args:
            data_path: Path to the data file
            data_format: Format of the data file ('csv', 'parquet', 'json')
        """
        if not os.path.exists(data_path):
            raise FileNotFoundError(f"Data file not found: {data_path}")
        
        if data_format == 'csv':
            self._load_csv_data(data_path)
        elif data_format == 'parquet':
            self._load_parquet_data(data_path)
        elif data_format == 'json':
            self._load_json_data(data_path)
        else:
            raise ValueError(f"Unsupported data format: {data_format}")
        
        self._data_loaded = True
    
    def _load_csv_data(self, data_path: str) -> None:
        """Load data from a CSV file."""
        df = pd.read_csv(data_path)
        self._process_dataframe(df)
    
    def _load_parquet_data(self, data_path: str) -> None:
        """Load data from a Parquet file."""
        df = pd.read_parquet(data_path)
        self._process_dataframe(df)
    
    def _load_json_data(self, data_path: str) -> None:
        """Load data from a JSON file."""
        df = pd.read_json(data_path)
        self._process_dataframe(df)
    
    def _process_dataframe(self, df: pd.DataFrame) -> None:
        """Process DataFrame into MarketData objects."""
        required_columns = {'timestamp', 'symbol', 'price'}
        if not required_columns.issubset(df.columns):
            raise ValueError(f"DataFrame must contain columns: {required_columns}")
        
        # Convert timestamp to datetime if it's not already
        if not pd.api.types.is_datetime64_any_dtype(df['timestamp']):
            df['timestamp'] = pd.to_datetime(df['timestamp'])
        
        # Sort by timestamp
        df = df.sort_values('timestamp')
        
        # Convert to MarketData objects
        market_data_list = []
        for _, row in df.iterrows():
            market_data = MarketData(
                timestamp=row['timestamp'],
                symbol=row['symbol'],
                price=row['price'],
                volume=row.get('volume'),
                bid=row.get('bid'),
                ask=row.get('ask')
            )
            market_data_list.append(market_data)
        
        # Add to MarketDataHandler
        self.market_data.add_data(market_data_list)
    
    def is_data_loaded(self) -> bool:
        """Check if data has been loaded."""
        return self._data_loaded
    
    def get_available_symbols(self) -> List[str]:
        """Get list of available symbols in the loaded data."""
        return list(self.market_data._data.keys())
    
    def get_data_range(self, symbol: str) -> Optional[tuple[datetime, datetime]]:
        """Get the date range for a specific symbol."""
        if symbol not in self.market_data._data:
            return None
        
        data = self.market_data._data[symbol]
        if not data:
            return None
        
        return (data[0].timestamp, data[-1].timestamp)
    
    def run_backtest(self, strategy, start_date: Optional[datetime] = None,
                    end_date: Optional[datetime] = None) -> BacktestResult:
        """Run a backtest with the given strategy."""
        # TODO: Implement backtest execution
        pass
    
    def calculate_metrics(self) -> Dict[str, float]:
        """Calculate performance metrics."""
        # TODO: Implement metric calculation
        return {}
    
    def plot_performance(self) -> None:
        """Plot performance metrics."""
        # TODO: Implement visualization
        pass 