#!/usr/bin/env python3
"""Script to fetch data from Polygon.io and store it."""
import os
import sys
from datetime import datetime, timedelta
from pathlib import Path
import logging
from dotenv import load_dotenv

from backtester.data_fetcher import PolygonDataFetcher
from backtester.sql.db_manager import DatabaseManager

def setup_logging():
    """Setup logging configuration."""
    # Create logs directory if it doesn't exist
    log_dir = Path("logs")
    log_dir.mkdir(exist_ok=True)
    
    # Setup logging with both file and console handlers
    log_file = log_dir / f"data_fetch_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler(log_file)
        ]
    )
    return logging.getLogger(__name__)

def main():
    """Main function to fetch data."""
    logger = setup_logging()
    
    # Load environment variables
    load_dotenv()
    api_key = os.getenv('POLYGON_API_KEY')
    if not api_key:
        logger.error("POLYGON_API_KEY not found in environment variables")
        sys.exit(1)
    
    # Initialize database manager
    db_manager = DatabaseManager("backtest.db")
    
    # Initialize data fetcher
    fetcher = PolygonDataFetcher(
        api_key=api_key,
        db_manager=db_manager,
        base_path="data",
        pool_connections=5,    # Reduced pool size
        pool_maxsize=5,        # Reduced max connections
        max_retries=5,         # Maximum retry attempts
        base_delay=2.0         # Increased base delay
    )
    
    # Define symbols to fetch
    symbols = {
        'stock': [],  # Apple stock
        'option': ['AAPL'], # Apple options
        'future': [],       # No futures
        'index': []    # S&P 500 ETF as reference
    }
    
    # Define date range
    end_date = datetime.now()
    start_date = end_date - timedelta(days=30)  # Fetch last 30 days of data
    
    try:
        logger.info(f"Starting data fetch for {len(symbols['stock'])} stocks, "
                   f"{len(symbols['option'])} options, "
                   f"{len(symbols['future'])} futures, and "
                   f"{len(symbols['index'])} indices")
        
        # Fetch all data with reduced workers to avoid rate limits
        fetcher.fetch_all_data(
            symbols=symbols,
            start_date=start_date,
            end_date=end_date,
            max_workers=1  # Single worker to avoid overwhelming the API
        )
        
        logger.info("Data fetch completed successfully")
        
    except Exception as e:
        logger.error(f"Error during data fetch: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 