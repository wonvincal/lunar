import pandas as pd
from pathlib import Path
import pyarrow as pa
import pyarrow.parquet as pq
import logging
from typing import Union
from datetime import datetime

class ParquetPersister:
    def __init__(self, base_path: Union[str, Path], compression: str, logger: logging.Logger):
        self.base_path = Path(base_path)
        self.compression = compression
        self.logger = logger

    def persist(self, data: pd.DataFrame, asset_type: str, symbol: str, start_date: datetime, end_date: datetime) -> None:
        """Persist data to Parquet with compression."""
        date_str = f"{start_date.strftime('%Y%m%d')}_{end_date.strftime('%Y%m%d')}"
        filename = f"{symbol}_{date_str}"
        parquet_path = self.base_path / asset_type / 'parquet' / f"{filename}.parquet"
        table = pa.Table.from_pandas(data)
        pq.write_table(
            table,
            parquet_path,
            compression=self.compression,
            version='2.6'
        )
        self.logger.info(f"Persisted compressed Parquet: {parquet_path}")