# Lunar Backtester

Option backtesting system for Lunar trading systems. This system allows you to test trading strategies using historical market data.

## Features

- Historical option data management
- Strategy simulation
- Performance analysis
- Visualization tools
- Risk metrics calculation

## Installation

```bash
# Using uv
uv pip install lunar-backtester
```

## Usage

```python
from lunar_backtester import BacktestEngine
from lunar_backtester.strategies import OptionStrategy

# Initialize backtest engine
engine = BacktestEngine()

# Load historical data
engine.load_data("path/to/data.csv")
engine.load_data_from_database()

# Define and run strategy
strategy = OptionStrategy()
results = engine.run_backtest(strategy)

# Analyze results
print(results.summary())
results.plot_performance()
```

# Initialize data fetcher
fetcher = DataFetcher()

# What is the use of database for backtester
# 1. External analysis of data using database query directly
# 

## Development

1. Clone the repository
2. Install development dependencies:
   ```bash
   uv pip install -e ".[dev]"
   ```
3. Run tests:
   ```bash
   pytest
   ```

## License

MIT 