import pytest
from datetime import datetime, timezone
import pandas as pd
import json
from pathlib import Path
import tempfile
import shutil
from lunar_backtester.data_importer import DataImporter
from lunar_backtester.sql import DatabaseManager, StockData, FutureData, IndexData, OptionData

@pytest.fixture
def temp_dir():
    """Create a temporary directory for test files."""
    temp_dir = tempfile.mkdtemp()
    yield temp_dir
    shutil.rmtree(temp_dir)

@pytest.fixture
def db_manager(temp_dir):
    """Create a database manager with a temporary database."""
    db_path = Path(temp_dir) / "test.db"
    return DatabaseManager(str(db_path))

@pytest.fixture
def data_importer(db_manager):
    """Create a data importer with the test database manager."""
    return DataImporter(db_manager)

@pytest.fixture
def sample_stock_data():
    """Create sample stock data."""
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    return pd.DataFrame({
        'timestamp': dates,
        'symbol': ['AAPL'] * len(dates),
        'open': [100.0] * len(dates),
        'high': [102.0] * len(dates),
        'low': [98.0] * len(dates),
        'close': [101.0] * len(dates),
        'volume': [1000000] * len(dates),
        'vwap': [100.5] * len(dates)
    })

@pytest.fixture
def sample_future_data():
    """Create sample future data."""
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    return pd.DataFrame({
        'timestamp': dates,
        'symbol': ['ES'] * len(dates),
        'open': [4000.0] * len(dates),
        'high': [4010.0] * len(dates),
        'low': [3990.0] * len(dates),
        'close': [4005.0] * len(dates),
        'volume': [100000] * len(dates),
        'open_interest': [500000] * len(dates),
        'settlement_price': [4002.5] * len(dates)
    })

@pytest.fixture
def sample_index_data():
    """Create sample index data."""
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    return pd.DataFrame({
        'timestamp': dates,
        'symbol': ['SPX'] * len(dates),
        'open': [5000.0] * len(dates),
        'high': [5010.0] * len(dates),
        'low': [4990.0] * len(dates),
        'close': [5005.0] * len(dates),
        'volume': [1000000] * len(dates)
    })

@pytest.fixture
def sample_option_data():
    """Create sample option data."""
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    return pd.DataFrame({
        'timestamp': dates,
        'symbol': ['AAPL'] * len(dates),
        'strike_price': [150.0] * len(dates),
        'expiration_date': [datetime(2024, 1, 19, tzinfo=timezone.utc)] * len(dates),
        'option_type': ['call'] * len(dates),
        'last_price': [5.0] * len(dates),
        'bid': [4.9] * len(dates),
        'ask': [5.1] * len(dates),
        'volume': [1000] * len(dates),
        'open_interest': [5000] * len(dates),
        'implied_volatility': [0.3] * len(dates),
        'delta': [0.5] * len(dates),
        'gamma': [0.1] * len(dates),
        'theta': [-0.05] * len(dates),
        'vega': [0.2] * len(dates),
        'rho': [0.1] * len(dates)
    })

def test_import_csv(data_importer, temp_dir, sample_stock_data):
    """Test importing data from a CSV file."""
    # Save sample data to CSV
    csv_path = Path(temp_dir) / "test_stock.csv"
    sample_stock_data.to_csv(csv_path, index=False)
    
    # Import the CSV
    data_importer.import_csv(csv_path, 'stock')
    
    # Verify data was imported
    with data_importer.db_manager._get_connection() as conn:
        cursor = conn.execute("SELECT COUNT(*) FROM stocks")
        count = cursor.fetchone()[0]
        assert count == len(sample_stock_data)

def test_import_json(data_importer, temp_dir, sample_stock_data):
    """Test importing data from a JSON file."""
    # Convert DataFrame to list of dictionaries
    data_list = sample_stock_data.to_dict('records')
    
    # Save to JSON
    json_path = Path(temp_dir) / "test_stock.json"
    data_importer.export_json(data_list, json_path)
    
    # Import the JSON
    data_importer.import_json(json_path, 'stock')
    
    # Verify data was imported
    with data_importer.db_manager._get_connection() as conn:
        cursor = conn.execute("SELECT COUNT(*) FROM stocks")
        count = cursor.fetchone()[0]
        assert count == len(sample_stock_data)

def test_import_pandas(data_importer, sample_stock_data):
    """Test importing data from a pandas DataFrame."""
    # Import the DataFrame
    data_importer.import_pandas(sample_stock_data, 'stock')
    
    # Verify data was imported
    with data_importer.db_manager._get_connection() as conn:
        cursor = conn.execute("SELECT COUNT(*) FROM stocks")
        count = cursor.fetchone()[0]
        assert count == len(sample_stock_data)

def test_import_directory(data_importer, temp_dir, sample_stock_data, sample_future_data):
    """Test importing data from a directory."""
    # Create subdirectories for different asset types
    stocks_dir = Path(temp_dir) / "stocks"
    futures_dir = Path(temp_dir) / "futures"
    stocks_dir.mkdir()
    futures_dir.mkdir()
    
    # Save sample data to CSV files
    sample_stock_data.to_csv(stocks_dir / "AAPL.csv", index=False)
    sample_future_data.to_csv(futures_dir / "ES.csv", index=False)
    
    # Import from directory
    data_importer.import_directory(temp_dir)
    
    # Verify data was imported
    with data_importer.db_manager._get_connection() as conn:
        cursor = conn.execute("SELECT COUNT(*) FROM stocks")
        stock_count = cursor.fetchone()[0]
        cursor = conn.execute("SELECT COUNT(*) FROM futures")
        future_count = cursor.fetchone()[0]
        
        assert stock_count == len(sample_stock_data)
        assert future_count == len(sample_future_data)

def test_import_invalid_file(data_importer, temp_dir):
    """Test handling of invalid file."""
    # Create an empty file
    invalid_path = Path(temp_dir) / "invalid.csv"
    invalid_path.touch()
    
    # Try to import
    with pytest.raises(Exception):
        data_importer.import_csv(invalid_path, 'stock')

def test_import_invalid_asset_type(data_importer, temp_dir, sample_stock_data):
    """Test handling of invalid asset type."""
    # Save sample data to CSV
    csv_path = Path(temp_dir) / "test.csv"
    sample_stock_data.to_csv(csv_path, index=False)
    
    # Try to import with invalid asset type
    with pytest.raises(ValueError):
        data_importer.import_csv(csv_path, 'invalid')

def test_import_missing_required_fields(data_importer, temp_dir):
    """Test handling of data with missing required fields."""
    # Create data with missing required fields
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    invalid_data = pd.DataFrame({
        'timestamp': dates,
        'symbol': ['AAPL'] * len(dates)
        # Missing required fields: open, high, low, close, volume
    })
    
    # Save to CSV
    csv_path = Path(temp_dir) / "invalid.csv"
    invalid_data.to_csv(csv_path, index=False)
    
    # Try to import
    with pytest.raises(Exception):
        data_importer.import_csv(csv_path, 'stock')

def test_import_invalid_data_types(data_importer, temp_dir):
    """Test handling of data with invalid data types."""
    # Create data with invalid data types
    dates = pd.date_range(start='2024-01-01', end='2024-01-10', freq='D')
    invalid_data = pd.DataFrame({
        'timestamp': dates,
        'symbol': ['AAPL'] * len(dates),
        'open': ['invalid'] * len(dates),  # Should be float
        'high': ['invalid'] * len(dates),  # Should be float
        'low': ['invalid'] * len(dates),   # Should be float
        'close': ['invalid'] * len(dates), # Should be float
        'volume': ['invalid'] * len(dates) # Should be int
    })
    
    # Save to CSV
    csv_path = Path(temp_dir) / "invalid.csv"
    invalid_data.to_csv(csv_path, index=False)
    
    # Try to import
    with pytest.raises(Exception):
        data_importer.import_csv(csv_path, 'stock') 