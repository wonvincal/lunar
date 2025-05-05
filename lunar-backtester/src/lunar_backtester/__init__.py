"""Lunar Backtester - A comprehensive options backtesting system."""

from .engine import BacktestEngine, BacktestResult
from .strategies import BaseStrategy, OptionStrategy
from .sql import (
    DatabaseManager,
    AssetType,
    StockData,
    FutureData,
    IndexData,
    OptionData
)
from .data_importer import DataImporter
from .data_fetcher import PolygonDataFetcher

__all__ = [
    'BacktestEngine',
    'BacktestResult',
    'BaseStrategy',
    'OptionStrategy',
    'DatabaseManager',
    'AssetType',
    'StockData',
    'FutureData',
    'IndexData',
    'OptionData',
    'DataImporter',
    'PolygonDataFetcher'
]

__version__ = '0.1.0' 