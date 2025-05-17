import pandas as pd
from pathlib import Path
import gzip
import logging
from typing import Union
from datetime import datetime

class CSVPersister:
    def __init__(self, base_path: Union[str, Path], compression: str, logger: logging.Logger):
        self.base_path = Path(base_path)
        self.compression = compression
        self.logger = logger

    def persist(self, data: pd.DataFrame, asset_type: str, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Persist data to CSV with compression."""
        date_str = f"{start_date.strftime('%Y%m%d')}_{end_date.strftime('%Y%m%d')}"
        filename = f"{symbol}_{date_str}"
        csv_path = self.base_path / asset_type / 'csv' / f"{filename}.csv.gz"
        with gzip.open(csv_path, 'wt') as f:
            data.to_csv(f, index=False)
        self.logger.info(f"Persisted compressed CSV: {csv_path}")