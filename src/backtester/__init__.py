"""Lunar Backtester - A comprehensive options backtesting system."""

from .engine import BacktestEngine, BacktestResult
from .strategies import BaseStrategy, OptionStrategy
from .sql.db_manager import DatabaseManager

from .data_importer import DataImporter
from .data_fetcher import PolygonDataFetcher

__all__ = [
    'BacktestEngine',
    'BacktestResult',
    'BaseStrategy',
    'OptionStrategy',
    'DatabaseManager',
    'DataImporter',
    'PolygonDataFetcher'
]

__version__ = '0.1.0' 