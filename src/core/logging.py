"""Logging configuration for the Lunar trading system."""
import logging
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional

class TradeFormatter(logging.Formatter):
    """Custom formatter for trading logs."""
    
    def format(self, record: logging.LogRecord) -> str:
        """Format the log record with timestamp and extra fields."""
        # Add timestamp if not present
        if not hasattr(record, 'timestamp'):
            record.timestamp = datetime.now().isoformat()
        
        # Add trade ID if present
        trade_id = getattr(record, 'trade_id', None)
        trade_str = f"[Trade: {trade_id}] " if trade_id else ""
        
        # Add symbol if present
        symbol = getattr(record, 'symbol', None)
        symbol_str = f"[{symbol}] " if symbol else ""
        
        return (f"{record.timestamp} [{record.levelname}] "
                f"{trade_str}{symbol_str}{record.getMessage()}")

def setup_logging(log_dir: Optional[Path] = None,
                 console_level: int = logging.INFO,
                 file_level: int = logging.DEBUG) -> None:
    """Set up logging configuration.
    
    Args:
        log_dir: Directory to store log files. If None, only console logging is enabled.
        console_level: Logging level for console output.
        file_level: Logging level for file output.
    """
    # Create root logger
    logger = logging.getLogger('lunar')
    logger.setLevel(logging.DEBUG)
    
    # Remove existing handlers
    logger.handlers.clear()
    
    # Create formatters
    console_formatter = TradeFormatter()
    file_formatter = TradeFormatter()
    
    # Console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(console_level)
    console_handler.setFormatter(console_formatter)
    logger.addHandler(console_handler)
    
    # File handlers if log_dir is provided
    if log_dir:
        log_dir = Path(log_dir)
        log_dir.mkdir(parents=True, exist_ok=True)
        
        # Trading log
        trade_handler = logging.FileHandler(
            log_dir / f"trading_{datetime.now().strftime('%Y%m%d')}.log"
        )
        trade_handler.setLevel(file_level)
        trade_handler.setFormatter(file_formatter)
        logger.addHandler(trade_handler)
        
        # Error log
        error_handler = logging.FileHandler(
            log_dir / f"error_{datetime.now().strftime('%Y%m%d')}.log"
        )
        error_handler.setLevel(logging.ERROR)
        error_handler.setFormatter(file_formatter)
        logger.addHandler(error_handler)

def get_logger(name: str) -> logging.Logger:
    """Get a logger with the given name.
    
    Args:
        name: Name of the logger, typically __name__ of the module.
    
    Returns:
        Logger instance.
    """
    return logging.getLogger(f"lunar.{name}")

class LoggedError(Exception):
    """Base class for exceptions that should be logged."""
    
    def __init__(self, message: str, **kwargs):
        super().__init__(message)
        self.log_data = kwargs

class TradeError(LoggedError):
    """Error related to trading operations."""
    pass

class MarketDataError(LoggedError):
    """Error related to market data operations."""
    pass

class RiskError(LoggedError):
    """Error related to risk management."""
    pass 