# Lunar Trader

The real-time trading component of the Lunar trading system. This package handles live trading operations, market data processing, and strategy execution.

## Features

- Real-time market data processing
- Order management system
- Position tracking
- Strategy framework
- Risk management
- Asynchronous operation

## Installation

```bash
# Install using uv
uv pip install -e lunar-trader

# Install with development dependencies
uv pip install -e "lunar-trader[dev]"
```

## Usage

### Basic Trading Engine Setup

```python
from lunar_trader import TradingEngine

# Initialize trading engine
engine = TradingEngine(api_key="your_api_key")

# Start the engine
await engine.start()

# Place an order
order = await engine.place_order(
    symbol="AAPL",
    order_type=OrderType.MARKET,
    side=OrderSide.BUY,
    quantity=100
)

# Stop the engine
await engine.stop()
```

### Strategy Implementation

```python
from lunar_trader import TradingEngine, ExampleStrategy

# Initialize trading engine
engine = TradingEngine(api_key="your_api_key")

# Create and run strategy
strategy = ExampleStrategy(engine, symbol="AAPL")
await engine.start()

# The strategy will automatically handle market data and place orders
# based on its trading logic
```

## Development

### Project Structure

```
lunar-trader/
├── src/
│   └── lunar_trader/
│       ├── __init__.py
│       ├── trading_engine.py    # Core trading functionality
│       ├── strategy.py          # Strategy interface
│       └── example_strategy.py  # Example strategy implementation
├── tests/                       # Test suite
├── pyproject.toml              # Project configuration
└── README.md                   # This file
```

### Running Tests

```bash
# Install test dependencies
uv pip install -e "lunar-trader[dev]"

# Run tests
pytest tests/
```

## Dependencies

- lunar-core: Core trading functionality
- pandas: Data manipulation
- numpy: Numerical operations
- pydantic: Data validation
- aiohttp: Asynchronous HTTP client
- websockets: WebSocket client

## License

MIT License 