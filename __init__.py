"""Lunar Trading System - A comprehensive trading and backtesting system."""

from lunar_backtester import (
    BacktestEngine,
    BacktestResult,
    BaseStrategy,
    OptionStrategy,
    DatabaseManager,
    AssetType,
    StockData,
    FutureData,
    IndexData,
    OptionData,
    DataImporter,
    PolygonDataFetcher
)

from lunar_trader import (
    TradingEngine,
    Strategy
)

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
    'PolygonDataFetcher',
    'TradingEngine',
    'Strategy'
]

__version__ = '0.1.0' 