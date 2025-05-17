import pytest
import sqlite3
import os
from datetime import datetime
import pandas as pd
from backtester.sql.db_manager import DatabaseManager
from core.models import OptionContract, OptionSnapshot, StockAggregation, FutureAggregation, IndexAggregation, OptionAggregation

@pytest.fixture
def db_path(tmp_path):
    """Creates a temporary database file path."""
    return str(tmp_path / "test_database.db")

@pytest.fixture
def db_manager(tmp_path):
    """Creates a DatabaseManager instance with a temporary database."""
    db_path = str(tmp_path / "test_database.db")
    manager = DatabaseManager(db_path)
    return manager

@pytest.fixture
def option_contract_data():
    return OptionContract(
        symbol="AAPL240126C170",
        strike_price=170.0,
        expiration_date=datetime(2024, 1, 26),
        option_type="CALL",
        underlying_symbol="AAPL",
    )

@pytest.fixture
def option_snapshot_data():
    return OptionSnapshot(
        timestamp=datetime(2024, 1, 15, 10, 0, 0),
        symbol="AAPL240126C170",
        bid=10.50,
        ask=10.75,
        last_price=10.60,
        open_interest=100,
        implied_volatility=0.20,
        delta=0.55,
        gamma=0.02,
        theta=-0.01,
        vega=0.03,
        rho=0.005,
    )

@pytest.fixture
def stock_aggregation_data():
    return StockAggregation(
        timestamp=datetime(2024, 1, 15),
        symbol="AAPL",
        open=170.00,
        high=170.50,
        low=169.75,
        close=170.25,
        volume=1000000,
        vwap=170.10,
    )

@pytest.fixture
def future_aggregation_data():
    return FutureAggregation(
        timestamp=datetime(2024, 1, 15),
        symbol="ESM24",
        open=4800.00,
        high=4810.00,
        low=4795.00,
        close=4805.00,
        volume=50000,
        open_interest=200000,
        settlement_price=4806.00,
    )

@pytest.fixture
def index_aggregation_data():
    return IndexAggregation(
        timestamp=datetime(2024, 1, 15),
        symbol="SPX",
        open=4700.00,
        high=4710.00,
        low=4690.00,
        close=4705.00,
        volume=1500000,
    )

@pytest.fixture
def option_aggregation_data():
    return OptionAggregation(
        timestamp=datetime(2024, 1, 15),
        symbol="AAPL240126C170",
        open=10.00,
        high=10.80,
        low=9.50,
        close=10.50,
        volume=500,
        vwap=10.20,
    )

@pytest.fixture
def date_range():
    start_date = datetime(2024, 1, 10)
    end_date = datetime(2024, 1, 20)
    return start_date, end_date

def test_database_manager_init(db_path):
    """Tests the __init__ method of DatabaseManager."""
    manager = DatabaseManager(db_path)
    assert manager.db_path == db_path
    assert os.path.exists(db_path)
    with sqlite3.connect(db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
        tables = {row[0] for row in cursor.fetchall()}
        assert "option_contract" in tables
        assert "option_snapshot" in tables
        assert "stock_aggregation" in tables
        assert "future_aggregation" in tables
        assert "index_aggregation" in tables
        assert "option_aggregation" in tables

def test_insert_option_contract(db_manager, option_contract_data):
    """Tests the insert_option_contract method."""
    db_manager.insert_option_contract(option_contract_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM option_contract WHERE symbol = ?", (option_contract_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] == option_contract_data.symbol
        assert row[1] == option_contract_data.strike_price
        assert row[2] == option_contract_data.expiration_date.isoformat()
        assert row[3] == option_contract_data.option_type
        assert row[4] == option_contract_data.underlying_symbol

def test_insert_option_snapshot(db_manager, option_snapshot_data):
    """Tests the insert_option_snapshot method."""
    db_manager.insert_option_snapshot(option_snapshot_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM option_snapshot WHERE symbol = ?", (option_snapshot_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] == option_snapshot_data.timestamp.isoformat()
        assert row[1] == option_snapshot_data.symbol
        assert row[2] == option_snapshot_data.bid
        # ... add assertions for other fields

def test_insert_stock_aggregation(db_manager, stock_aggregation_data):
    """Tests the insert_stock_aggregation method."""
    db_manager.insert_stock_aggregation(stock_aggregation_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM stock_aggregation WHERE symbol = ?", (stock_aggregation_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] == stock_aggregation_data.timestamp.isoformat()
        assert row[1] == stock_aggregation_data.symbol
        assert row[2] == stock_aggregation_data.open
        # ... add assertions for other fields

def test_insert_future_aggregation(db_manager, future_aggregation_data):
    """Tests the insert_future_aggregation method."""
    db_manager.insert_future_aggregation(future_aggregation_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM future_aggregation WHERE symbol = ?", (future_aggregation_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] == future_aggregation_data.timestamp.isoformat()
        assert row[1] == future_aggregation_data.symbol
        assert row[2] == future_aggregation_data.open
        # ... add assertions for other fields

def test_insert_index_aggregation(db_manager, index_aggregation_data):
    """Tests the insert_index_aggregation method."""
    db_manager.insert_index_aggregation(index_aggregation_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM index_aggregation WHERE symbol = ?", (index_aggregation_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] == index_aggregation_data.timestamp.isoformat()
        assert row[1] == index_aggregation_data.symbol
        assert row[2] == index_aggregation_data.open
        # ... add assertions for other fields

def test_insert_option_aggregation(db_manager, option_aggregation_data):
    """Tests the insert_option_aggregation method."""
    db_manager.insert_option_aggregation(option_aggregation_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM option_aggregation WHERE symbol = ?", (option_aggregation_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] == option_aggregation_data.timestamp.isoformat()
        assert row[1] == option_aggregation_data.symbol
        assert row[2] == option_aggregation_data.open
        # ... add assertions for other fields

def test_bulk_insert_option_contracts(db_manager):
    """Tests bulk_insert_option_contracts."""
    contracts = [
        OptionContract(symbol="AAPL240126C170", strike_price=170.0, expiration_date=datetime(2024, 1, 26), option_type="CALL", underlying_symbol="AAPL"),
        OptionContract(symbol="AAPL240126P165", strike_price=165.0, expiration_date=datetime(2024, 1, 26), option_type="PUT", underlying_symbol="AAPL"),
    ]
    db_manager.bulk_insert_option_contracts(contracts)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM option_contract")
        count = cursor.fetchone()[0]
        assert count == len(contracts)

def test_get_option_snapshots_by_date_range(db_manager, option_snapshot_data, option_contract_data, date_range):
    """Tests get_option_snapshots_by_date_range."""
    db_manager.insert_option_contract(option_contract_data)
    snapshot1 = option_snapshot_data.copy(update={"timestamp": datetime(2024, 1, 12)})
    snapshot2 = option_snapshot_data.copy(update={"timestamp": datetime(2024, 1, 16)})
    db_manager.insert_option_snapshot(snapshot1)
    db_manager.insert_option_snapshot(snapshot2)
    start_date, end_date = date_range
    results_df = db_manager.get_option_snapshots_by_date_range(option_snapshot_data.symbol, start_date, end_date)
    assert isinstance(results_df, pd.DataFrame)
    assert not results_df.empty
    assert len(results_df) == 2 # Only snapshot2 should be in the range

def test_get_stock_aggregations_by_date_range(db_manager, stock_aggregation_data, date_range):
    """Tests get_stock_aggregations_by_date_range."""
    agg1 = stock_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 11)})
    agg2 = stock_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 17)})
    db_manager.insert_stock_aggregation(agg1)
    db_manager.insert_stock_aggregation(agg2)
    start_date, end_date = date_range
    results_df = db_manager.get_stock_aggregations_by_date_range(stock_aggregation_data.symbol, start_date, end_date)
    assert isinstance(results_df, pd.DataFrame)
    assert not results_df.empty
    assert len(results_df) == 2 # Only agg2 should be in the range

# Add similar tests for other get_by_date_range methods
def test_get_future_aggregations_by_date_range(db_manager, future_aggregation_data, date_range):
    agg1 = future_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 11)})
    agg2 = future_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 17)})
    db_manager.insert_future_aggregation(agg1)
    db_manager.insert_future_aggregation(agg2)
    start_date, end_date = date_range
    results_df = db_manager.get_future_aggregations_by_date_range(future_aggregation_data.symbol, start_date, end_date)
    assert isinstance(results_df, pd.DataFrame)
    assert not results_df.empty
    assert len(results_df) == 2

def test_get_index_aggregations_by_date_range(db_manager, index_aggregation_data, date_range):
    agg1 = index_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 11)})
    agg2 = index_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 17)})
    db_manager.insert_index_aggregation(agg1)
    db_manager.insert_index_aggregation(agg2)
    start_date, end_date = date_range
    results_df = db_manager.get_index_aggregations_by_date_range(index_aggregation_data.symbol, start_date, end_date)
    assert isinstance(results_df, pd.DataFrame)
    assert not results_df.empty
    assert len(results_df) == 2

def test_get_option_aggregations_by_date_range(db_manager, option_aggregation_data, date_range):
    agg1 = option_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 11)})
    agg2 = option_aggregation_data.copy(update={"timestamp": datetime(2024, 1, 17)})
    db_manager.insert_option_aggregation(agg1)
    db_manager.insert_option_aggregation(agg2)
    start_date, end_date = date_range
    results_df = db_manager.get_option_aggregations_by_date_range(option_aggregation_data.symbol, start_date, end_date)
    assert isinstance(results_df, pd.DataFrame)
    assert not results_df.empty
    assert len(results_df) == 2

def test_get_option_chain(db_manager, option_snapshot_data, option_contract_data):
    """Tests get_option_chain."""
    contract1 = option_contract_data.copy(update={"strike_price": 165.0, "symbol": "AAPL240126C165"})
    contract2 = option_contract_data.copy(update={"strike_price": 170.0, "symbol": "AAPL240126C170"})
    snapshot1 = option_snapshot_data.copy(update={"symbol": "AAPL240126C165", "timestamp": datetime(2024, 1, 15)})
    snapshot2 = option_snapshot_data.copy(update={"symbol": "AAPL240126C170", "timestamp": datetime(2024, 1, 15)})
    db_manager.insert_option_contract(contract1)
    db_manager.insert_option_contract(contract2)
    db_manager.insert_option_snapshot(snapshot1)
    db_manager.insert_option_snapshot(snapshot2)
    results_df = db_manager.get_option_chain(
        underlying_symbol=option_contract_data.underlying_symbol,
        timestamp=snapshot1.timestamp,
        expiration_date=option_contract_data.expiration_date,
    )
    assert isinstance(results_df, pd.DataFrame)
    assert not results_df.empty
    assert len(results_df) == 2
    assert all(results_df['underlying_symbol'] == option_contract_data.underlying_symbol)
    assert all(results_df['timestamp'] == snapshot1.timestamp.isoformat())
    assert all(results_df['expiration_date'] == option_contract_data.expiration_date.isoformat())
    assert set(results_df['strike_price']) == {165.0, 170.0}

def test_insert_option_snapshot_null_values(db_manager):
    """Tests the insert_option_snapshot method with NULL values."""
    snapshot_data = OptionSnapshot(
        timestamp=datetime(2024, 1, 15, 10, 0, 0),
        symbol="AAPL240126C170",
        bid=10.50,
        ask=10.75,
        last_price=10.60,
        open_interest=None,  # Explicitly set to None
        implied_volatility=None, # Explicitly set to None
        delta=0.55,
        gamma=0.02,
        theta=-0.01,
        vega=0.03,
        rho=0.005,
    )
    db_manager.insert_option_snapshot(snapshot_data)
    with sqlite3.connect(db_manager.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT open_interest, implied_volatility FROM option_snapshot WHERE symbol = ?", (snapshot_data.symbol,))
        row = cursor.fetchone()
        assert row is not None
        assert row[0] is None  # Check for NULL
        assert row[1] is None  # Check for NULL