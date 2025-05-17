"""Data importer for the backtesting system."""
from datetime import datetime
from typing import List, Dict, Any, Optional, Union
import pandas as pd
from pathlib import Path
import json
import csv
from pydantic import ValidationError
from .sql.db_manager import DatabaseManager
from core.models import (
    StockAggregation,
    FutureAggregation,
    IndexAggregation,
    OptionAggregation,
    OptionContract
)

class DataImportError(Exception):
    """Base class for data import errors."""
    pass

class TimestampJSONEncoder(json.JSONEncoder):
    """JSON encoder that handles pandas Timestamp objects."""
    def default(self, obj):
        if isinstance(obj, pd.Timestamp):
            return obj.isoformat()
        return super().default(obj)

class DataImporter:
    """Handles importing data from various sources into the database."""
    
    def __init__(self, db_manager: DatabaseManager):
        self.db_manager = db_manager
    
    def import_csv(self, file_path: Union[str, Path], asset_type: str) -> None:
        """Import data from a CSV file."""
        file_path = Path(file_path)
        if not file_path.exists():
            raise FileNotFoundError(f"File not found: {file_path}")
        
        try:
            df = pd.read_csv(file_path)
            if 'timestamp' in df.columns:
                df['timestamp'] = pd.to_datetime(df['timestamp'])
            if 'expiration_date' in df.columns:
                df['expiration_date'] = pd.to_datetime(df['expiration_date'])
            self._import_dataframe(df, asset_type)
        except ValueError as e:
            # Re-raise ValueError directly
            raise
        except Exception as e:
            raise DataImportError(f"Error importing CSV file: {e}")
    
    def import_json(self, file_path: Union[str, Path], asset_type: str) -> None:
        """Import data from a JSON file."""
        file_path = Path(file_path)
        if not file_path.exists():
            raise FileNotFoundError(f"File not found: {file_path}")
        
        try:
            with open(file_path, 'r') as f:
                data = json.load(f)
            
            # Convert string timestamps to datetime objects
            for item in data:
                if 'timestamp' in item:
                    item['timestamp'] = pd.to_datetime(item['timestamp'])
                if 'expiration_date' in item:
                    item['expiration_date'] = pd.to_datetime(item['expiration_date'])
            
            self._import_dict_list(data, asset_type)
        except Exception as e:
            raise DataImportError(f"Error importing JSON file: {e}")
    
    def export_json(self, data: List[Dict[str, Any]], file_path: Union[str, Path]) -> None:
        """Export data to a JSON file."""
        file_path = Path(file_path)
        try:
            with open(file_path, 'w') as f:
                json.dump(data, f, cls=TimestampJSONEncoder)
        except Exception as e:
            raise DataImportError(f"Error exporting to JSON file: {e}")
    
    def import_pandas(self, df: pd.DataFrame, asset_type: str) -> None:
        """Import data from a pandas DataFrame."""
        try:
            if 'timestamp' in df.columns:
                df['timestamp'] = pd.to_datetime(df['timestamp'])
            if 'expiration_date' in df.columns:
                df['expiration_date'] = pd.to_datetime(df['expiration_date'])
            self._import_dataframe(df, asset_type)
        except Exception as e:
            raise DataImportError(f"Error importing DataFrame: {e}")
    
    def _import_dataframe(self, df: pd.DataFrame, asset_type: str) -> None:
        """Import data from a pandas DataFrame."""
        try:
            # Convert DataFrame to list of dictionaries
            data = df.to_dict('records')
            self._import_dict_list(data, asset_type)
        except ValueError:
            # Re-raise ValueError directly
            raise
        except Exception as e:
            raise DataImportError(f"Error converting DataFrame: {e}")
    
    def _import_dict_list(self, data: List[Dict[str, Any]], asset_type: str) -> None:
        """Import data from a list of dictionaries."""
        if not data:
            raise DataImportError("No data to import")
        
        valid_asset_types = {'stock', 'future', 'index', 'option'}
        if asset_type not in valid_asset_types:
            raise ValueError(f"Invalid asset type: {asset_type}. Must be one of {valid_asset_types}")
        
        try:
            if asset_type == 'stock':
                self._import_stocks(data)
            elif asset_type == 'future':
                self._import_futures(data)
            elif asset_type == 'index':
                self._import_indices(data)
            elif asset_type == 'option':
                self._import_options(data)
        except Exception as e:
            raise DataImportError(f"Error importing {asset_type} data: {e}")
    
    def _import_stocks(self, data: List[Dict[str, Any]]) -> None:
        """Import stock data."""
        validated_data = []
        errors = []
        
        for item in data:
            try:
                # Convert numeric fields
                numeric_fields = ['open', 'high', 'low', 'close', 'volume', 'vwap']
                for field in numeric_fields:
                    if field in item and item[field] is not None:
                        item[field] = float(item[field])
                
                # Validate and convert to StockAggregation
                stock_data = StockAggregation(**item)
                validated_data.append(stock_data)
            except ValidationError as e:
                errors.append(f"Validation error: {e}")
            except Exception as e:
                errors.append(f"Error: {e}")
        
        if not validated_data:
            raise DataImportError(f"No valid stock data to import. Errors: {'; '.join(errors)}")
        
        self.db_manager.bulk_insert_stocks(validated_data)
    
    def _import_futures(self, data: List[Dict[str, Any]]) -> None:
        """Import future data."""
        validated_data = []
        errors = []
        
        for item in data:
            try:
                # Convert numeric fields
                numeric_fields = ['open', 'high', 'low', 'close', 'volume', 'open_interest', 'settlement_price']
                for field in numeric_fields:
                    if field in item and item[field] is not None:
                        item[field] = float(item[field])
                
                # Validate and convert to FutureAggregation
                future_data = FutureAggregation(**item)
                validated_data.append(future_data)
            except ValidationError as e:
                errors.append(f"Validation error: {e}")
            except Exception as e:
                errors.append(f"Error: {e}")
        
        if not validated_data:
            raise DataImportError(f"No valid future data to import. Errors: {'; '.join(errors)}")
        
        self.db_manager.bulk_insert_futures(validated_data)
    
    def _import_indices(self, data: List[Dict[str, Any]]) -> None:
        """Import index data."""
        validated_data = []
        errors = []
        
        for item in data:
            try:
                # Convert numeric fields
                numeric_fields = ['open', 'high', 'low', 'close', 'volume']
                for field in numeric_fields:
                    if field in item and item[field] is not None:
                        item[field] = float(item[field])
                
                # Validate and convert to IndexAggregation
                index_data = IndexAggregation(**item)
                validated_data.append(index_data)
            except ValidationError as e:
                errors.append(f"Validation error: {e}")
            except Exception as e:
                errors.append(f"Error: {e}")
        
        if not validated_data:
            raise DataImportError(f"No valid index data to import. Errors: {'; '.join(errors)}")
        
        self.db_manager.bulk_insert_indices(validated_data)
    
    def _import_options(self, data: List[Dict[str, Any]]) -> None:
        """Import option data."""
        validated_data = []
        errors = []
        
        for item in data:
            try:
                # Convert numeric fields
                numeric_fields = [
                    'strike_price', 'last_price', 'bid', 'ask', 'volume',
                    'open_interest', 'implied_volatility', 'delta', 'gamma',
                    'theta', 'vega', 'rho'
                ]
                for field in numeric_fields:
                    if field in item and item[field] is not None:
                        item[field] = float(item[field])
                
                # Validate and convert to OptionAggregation
                option_data = OptionAggregation(**item)
                validated_data.append(option_data)
            except ValidationError as e:
                errors.append(f"Validation error: {e}")
            except Exception as e:
                errors.append(f"Error: {e}")
        
        if not validated_data:
            raise DataImportError(f"No valid option data to import. Errors: {'; '.join(errors)}")
        
        self.db_manager.bulk_insert_options(validated_data)
    
    def import_directory(self, directory: Union[str, Path],
                        file_pattern: str = "*.csv") -> None:
        """Import all files matching the pattern in a directory."""
        directory = Path(directory)
        if not directory.exists():
            raise FileNotFoundError(f"Directory not found: {directory}")
        
        errors = []
        imported_files = 0
        
        for file_path in directory.rglob(file_pattern):
            try:
                # Determine asset type from filename or directory structure
                asset_type = self._determine_asset_type(file_path)
                if asset_type:
                    print(f"Importing {file_path} as {asset_type}")
                    self.import_csv(file_path, asset_type)
                    imported_files += 1
            except Exception as e:
                errors.append(f"Error importing {file_path}: {e}")
        
        if not imported_files:
            raise DataImportError(f"No files were imported successfully. Errors: {'; '.join(errors)}")
    
    def _determine_asset_type(self, file_path: Path) -> Optional[str]:
        """Determine asset type from file path."""
        # First try to determine from parent directory name
        parent_dir = file_path.parent.name.lower()
        if parent_dir in {'stocks', 'futures', 'indices', 'options'}:
            return parent_dir[:-1]  # Remove 's' from the end
        
        # Then try from file name
        file_name = file_path.stem.lower()
        if any(name in file_name for name in ['stock', 'equity']):
            return 'stock'
        elif any(name in file_name for name in ['future', 'fut']):
            return 'future'
        elif any(name in file_name for name in ['index', 'idx']):
            return 'index'
        elif any(name in file_name for name in ['option', 'opt']):
            return 'option'
        
        return None 