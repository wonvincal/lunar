import pytest
from unittest.mock import MagicMock, patch
import pandas as pd
from datetime import datetime
import asyncio
from backtester.data_fetcher import PolygonDataFetcher

@pytest.fixture
def mock_db_manager():
    """Fixture for a mock DatabaseManager."""
    return MagicMock()

@pytest.fixture
def mock_polygon_client():
    """Fixture for a mock PolygonAPIClient."""
    return MagicMock()

@pytest.fixture
def mock_data_persistence():
    """Fixture for a mock DataPersistence."""
    return MagicMock()

@pytest.fixture
def data_fetcher(mock_db_manager, mock_polygon_client, mock_data_persistence):
    """Fixture for a PolygonDataFetcher with mocked dependencies."""
    fetcher = PolygonDataFetcher(
        api_key="test_api_key",
        db_manager=mock_db_manager,
        base_path="test_data",
        compression="gzip",
        max_retries=3,
        base_delay=1.0,
    )
    # Replace the api_client and data_persistence with mocks
    fetcher.api_client = mock_polygon_client
    fetcher.data_persistence = mock_data_persistence
    fetcher.logger = MagicMock()  # Mock the logger
    return fetcher

@patch("time.sleep", return_value=None)
def test_fetch_stock_data_success(
    mock_sleep, data_fetcher
):
    """Tests fetch_stock_data with a successful API response."""
    mock_aggs = [
        {
            't': 1672531200000,
            'o': 100.0,
            'h': 101.0,
            'l': 99.0,
            'c': 100.5,
            'v': 1000000,
            'vw': 100.25,
        },
        {
            't': 1672617600000,
            'o': 102.0,
            'h': 103.0,
            'l': 101.0,
            'c': 102.5,
            'v': 1200000,
            'vw': 102.25,
        },
    ]
    data_fetcher.api_client.get_aggregates.return_value = mock_aggs
    start_date = datetime(2023, 1, 1)
    end_date = datetime(2023, 1, 2)
    symbol = "AAPL"

    asyncio.run(data_fetcher.fetch_stock_data(symbol, start_date, end_date))

    # Assert that client.get_aggregates was called correctly
    data_fetcher.api_client.get_aggregates.assert_called_once_with(
        symbol, 1, "day", start_date, end_date
    )

    # Assert that _persist_data was called with the correct DataFrame
    expected_df = pd.DataFrame(
        {
            "timestamp": [
                pd.to_datetime(1672531200000, unit="ms"),
                pd.to_datetime(1672617600000, unit="ms"),
            ],
            "open": [100.0, 102.0],
            "high": [101.0, 103.0],
            "low": [99.0, 101.0],
            "close": [100.5, 102.5],
            "volume": [1000000, 1200000],
            "vwap": [100.25, 102.25],
            "symbol": ["AAPL", "AAPL"],
        }
    )
    data_fetcher.data_persistence.persist_data.assert_called_once()
    args, kwargs = data_fetcher.data_persistence.persist_data.call_args
    actual_df = args[0]
    actual_asset_type = args[1]
    actual_symbol = args[2]
    actual_start_date = args[3]
    actual_end_date = args[4]

    pd.testing.assert_frame_equal(actual_df, expected_df)
    assert actual_asset_type == 'stock'
    assert actual_symbol == symbol
    assert actual_start_date == start_date
    assert actual_end_date == end_date

@patch("time.sleep", return_value=None)
def test_fetch_stock_data_empty_response(mock_sleep, data_fetcher):
    """Tests fetch_stock_data with an empty API response."""
    data_fetcher.api_client.get_aggregates.return_value = []  # Mock empty response
    start_date = datetime(2023, 1, 1)
    end_date = datetime(2023, 1, 2)
    symbol = "AAPL"

    asyncio.run(data_fetcher.fetch_stock_data(symbol, start_date, end_date))

    # Assert that a warning was logged
    data_fetcher.logger.warning.assert_called_once_with("No stock data found for AAPL")
    data_fetcher.data_persistence.persist_data.assert_not_called()

async def test_fetch_option_contracts_success(data_fetcher):
    """Tests fetch_option_contracts with a successful API response."""
    mock_contracts_data = [
        {"ticker": "AAPL251219C00170000", "contract_type": "C", "strike_price": 170.0, "expiration_date": "2025-12-19"},
        {"ticker": "AAPL251219P00160000", "contract_type": "P", "strike_price": 160.0, "expiration_date": "2025-12-19"},
    ]
    data_fetcher.api_client.list_options_contracts.return_value = mock_contracts_data
    underlying_ticker = "AAPL"
    as_of_date = datetime(2025, 5, 10)
    expiry_date = datetime(2025, 12, 19)

    df = data_fetcher.fetch_option_contracts(
        underlying_ticker=underlying_ticker,
        as_of=as_of_date,
        expiration_date=expiry_date
    )

    # Assert that the API client method was called correctly
    data_fetcher.api_client.list_options_contracts.assert_called_once_with(
        underlying_ticker=underlying_ticker,
        as_of=as_of_date.strftime('%Y-%m-%d'),
        expiration_date=expiry_date.strftime('%Y-%m-%d'),
        expired=False,
        limit=1000
    )

    # Assert that the returned DataFrame is correct
    expected_df = pd.DataFrame(mock_contracts_data)
    pd.testing.assert_frame_equal(df, expected_df)

def test_fetch_option_contracts_empty_response(data_fetcher):
    """Tests fetch_option_contracts with an empty API response."""
    data_fetcher.api_client.list_options_contracts.return_value = []
    underlying_ticker = "GOOG"
    as_of_date = datetime(2025, 5, 10)
    expiry_date = datetime(2025, 6, 30)

    df = data_fetcher.fetch_option_contracts(
        underlying_ticker=underlying_ticker,
        as_of=as_of_date,
        expiration_date=expiry_date
    )

    # Assert that the API client method was called
    data_fetcher.api_client.list_options_contracts.assert_called_once()

    # Assert that an empty DataFrame is returned
    assert df.empty
    data_fetcher.logger.warning.assert_called_once_with(
        f"No option contracts found for {underlying_ticker} with the given criteria."
    )

def test_fetch_option_contracts_api_error(data_fetcher):
    """Tests fetch_option_contracts when the API raises an exception."""
    mock_error_message = "API Error occurred"
    data_fetcher.api_client.list_options_contracts.side_effect = Exception(mock_error_message)
    underlying_ticker = "MSFT"
    as_of_date = datetime(2025, 5, 1)
    expiry_date = datetime(2025, 7, 15)

    df = data_fetcher.fetch_option_contracts(
        underlying_ticker=underlying_ticker,
        as_of=as_of_date,
        expiration_date=expiry_date
    )

    # Assert that the API client method was called
    data_fetcher.api_client.list_options_contracts.assert_called_once()

    # Assert that an empty DataFrame is returned on error
    assert df.empty
    data_fetcher.logger.error.assert_called_once()