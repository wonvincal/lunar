# Lunar Trading System

A comprehensive trading and backtesting system for various asset types including stocks, options, futures, and indices.

## Project Structure

```
lunar/
├── lunar-backtester/     # Backtesting engine and strategies
├── lunar-core/          # Core trading functionality
├── lunar-trader/        # Live trading engine and strategies
├── pyproject.toml       # Project configuration
├── README.md            # This file
└── LICENSE              # MIT License
```

## Features

### Lunar Backtester
- Multi-asset backtesting (stocks, options, futures, indices)
- Strategy development framework
- Performance analysis and reporting
- Data management and validation

### Lunar Core
- Real-time market data processing
- Order management system
- Risk management
- Portfolio tracking

### Lunar Trader
- Live trading engine
- Strategy implementation framework
- Real-time market data integration
- Order execution and management

## Installation

### Using uv (Recommended)
```bash
# Install uv if you haven't already
curl -LsSf https://astral.sh/uv/install.sh | sh

# Clone the repository
git clone git@github.com:wonvincal/lunar.git
cd lunar

# Install all packages in development mode
uv pip install -e lunar-core/ -e lunar-backtester/ -e lunar-trader/

# Install development dependencies
uv pip install -e ".[dev]"
```

### Using pip
```bash
# Clone the repository
git clone git@github.com:wonvincal/lunar.git
cd lunar

# Install all packages in development mode
pip install -e lunar-core/ -e lunar-backtester/ -e lunar-trader/

# Install development dependencies
pip install -e ".[dev]"
```

## Usage

### Backtesting
```python
from lunar_backtester import BacktestEngine, OptionStrategy
from datetime import datetime

# Initialize backtest engine
engine = BacktestEngine()

# Define strategy
strategy = OptionStrategy()

# Run backtest
result = engine.run(
    strategy=strategy,
    start_date=datetime(2023, 1, 1),
    end_date=datetime(2023, 12, 31)
)

# Analyze results
print(result.summary())
```

### Live Trading
```python
from lunar_trader import TradingEngine, Strategy
from lunar_core import MarketData, OrderManager

# Initialize trading engine
engine = TradingEngine()

# Define strategy
class MyStrategy(Strategy):
    def __init__(self):
        super().__init__()
        self.market_data = MarketData()
        self.order_manager = OrderManager()

    async def on_market_data(self, data):
        # Implement your trading logic here
        pass

# Run trading engine
strategy = MyStrategy()
engine.run(strategy)
```

### Data Management
```python
from lunar_backtester import DatabaseManager, PolygonDataFetcher
from datetime import datetime

# Initialize database manager
db_manager = DatabaseManager("backtest.db")

# Initialize data fetcher
fetcher = PolygonDataFetcher(
    api_key="your_polygon_api_key",
    db_manager=db_manager
)

# Fetch data
symbols = {
    'stock': ['AAPL', 'MSFT'],
    'option': ['SPY'],
    'future': ['ES'],
    'index': ['SPX']
}

start_date = datetime(2023, 1, 1)
end_date = datetime(2023, 12, 31)

fetcher.fetch_all_data(symbols, start_date, end_date)
```

## Development

### Setting Up Development Environment

#### Using uv (Recommended)
```bash
# Install uv if you haven't already
curl -LsSf https://astral.sh/uv/install.sh | sh

# Create and activate virtual environment
uv venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install all packages in development mode
uv pip install -e lunar-core/ -e lunar-backtester/ -e lunar-trader/

# Install development dependencies
uv pip install -e ".[dev]"
```

#### Using pip
```bash
# Create and activate virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install all packages in development mode
pip install -e lunar-core/ -e lunar-backtester/ -e lunar-trader/

# Install development dependencies
pip install -e ".[dev]"
```

### Running Tests
```bash
# Run tests for all packages
pytest lunar-core/tests/ lunar-backtester/tests/ lunar-trader/tests/

# Run tests with coverage
pytest --cov=lunar_core --cov=lunar_backtester --cov=lunar_trader
```

### Code Formatting
```bash
# Using uv
uv pip install black isort
black .
isort .

# Using pip
pip install black isort
black .
isort .
```

### Type Checking
```bash
# Using uv
uv pip install mypy
mypy .

# Using pip
pip install mypy
mypy .
```

### Dependency Management
```bash
# Using uv
uv pip compile pyproject.toml -o requirements.txt  # Generate requirements
uv pip sync requirements.txt  # Install exact versions

# Using pip
pip freeze > requirements.txt  # Generate requirements
pip install -r requirements.txt  # Install exact versions
```

### Performance Tips
- Use `uv` for faster package installation and dependency resolution
- Enable `uv`'s caching for even better performance:
  ```bash
  export UV_CACHE_DIR=/path/to/cache
  ```
- Use `uv pip install --no-cache` to force fresh installations
- Use `uv pip install --upgrade` to update packages

## License

MIT License - see LICENSE file for details 