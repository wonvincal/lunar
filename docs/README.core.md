# Lunar Core

Core trading infrastructure for Lunar trading systems. This library provides the foundational components for both backtesting and live trading systems.

## Features

- Market data handling
- Order management system
- Risk management components
- Common utilities and helpers

## Installation

```bash
# Using uv
uv pip install lunar-core
```

## Usage

```python
from lunar_core.market_data import MarketDataHandler
from lunar_core.order_management import OrderManager
from lunar_core.risk_management import RiskManager

# Initialize components
market_data = MarketDataHandler()
order_manager = OrderManager()
risk_manager = RiskManager()
```

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