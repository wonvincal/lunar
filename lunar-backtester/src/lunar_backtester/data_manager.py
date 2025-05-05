from datetime import datetime, timedelta
from typing import Dict, List, Optional, Union
import pandas as pd
import requests
from pydantic import BaseModel, Field
import os

from .sql.db_manager import DatabaseManager

class OptionData(BaseModel):
    """Represents option data from Polygon.io."""
    timestamp: datetime
    symbol: str
    strike_price: float
    expiration_date: datetime
    option_type: str  # 'call' or 'put'
    last_price: float
    bid: float
    ask: float
    volume: int
    open_interest: int
    implied_volatility: float
    delta: float
    gamma: float
    theta: float
    vega: float
    rho: float

class DataManager:
    """Manages option data from Polygon.io and local storage."""
    
    def __init__(self, api_key: str, db_path: str = "option_data.db"):
        self.api_key = api_key
        self.db_path = db_path
        self.base_url = "https://api.polygon.io"
        self.db_manager = DatabaseManager(db_path)
    
    def fetch_option_chain(self, symbol: str, date: datetime) -> List[OptionData]:
        """Fetch option chain data from Polygon.io."""
        url = f"{self.base_url}/v3/snapshot/options/{symbol}"
        params = {
            "apiKey": self.api_key,
            "strike_price.gte": 0,
            "expiration_date.gte": date.strftime("%Y-%m-%d")
        }
        
        response = requests.get(url, params=params)
        response.raise_for_status()
        
        data = response.json()
        options = []
        
        for result in data.get("results", []):
            option = OptionData(
                timestamp=datetime.fromisoformat(result["updated"]),
                symbol=symbol,
                strike_price=result["strike_price"],
                expiration_date=datetime.fromisoformat(result["expiration_date"]),
                option_type=result["type"],
                last_price=result["last_price"],
                bid=result["bid"],
                ask=result["ask"],
                volume=result["volume"],
                open_interest=result["open_interest"],
                implied_volatility=result["implied_volatility"],
                delta=result["greeks"]["delta"],
                gamma=result["greeks"]["gamma"],
                theta=result["greeks"]["theta"],
                vega=result["greeks"]["vega"],
                rho=result["greeks"]["rho"]
            )
            options.append(option)
        
        return options
    
    def store_options(self, options: List[OptionData]) -> None:
        """Store option data in the database."""
        options_data = [option.dict() for option in options]
        self.db_manager.insert_options(options_data)
    
    def get_options(self, symbol: str, start_date: datetime,
                   end_date: datetime) -> pd.DataFrame:
        """Retrieve option data from the database."""
        return self.db_manager.get_options_by_date_range(symbol, start_date, end_date)
    
    def get_option_chain(self, symbol: str, timestamp: datetime,
                        expiration_date: datetime) -> pd.DataFrame:
        """Get the option chain for a specific timestamp and expiration date."""
        return self.db_manager.get_option_chain(symbol, timestamp, expiration_date)
    
    def get_option_metrics(self, symbol: str, start_date: datetime,
                          end_date: datetime) -> pd.DataFrame:
        """Get aggregated option metrics for a date range."""
        return self.db_manager.get_option_metrics(symbol, start_date, end_date)
    
    def export_to_csv(self, symbol: str, start_date: datetime,
                     end_date: datetime, output_path: str) -> None:
        """Export option data to CSV file."""
        df = self.get_options(symbol, start_date, end_date)
        df.to_csv(output_path, index=False)
    
    def update_data(self, symbol: str, days: int = 1) -> None:
        """Update option data for the specified number of days."""
        end_date = datetime.now()
        start_date = end_date - timedelta(days=days)
        
        options = self.fetch_option_chain(symbol, start_date)
        self.store_options(options)
    
    def cleanup_old_data(self, cutoff_date: datetime) -> None:
        """Remove data older than the cutoff date."""
        self.db_manager.cleanup_old_data(cutoff_date)
        self.db_manager.vacuum() 