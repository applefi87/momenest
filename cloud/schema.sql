-- D1 資料表：感測讀值 (時間戳由 Worker 端寫入，unix 秒)
CREATE TABLE IF NOT EXISTS readings (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    ts          INTEGER NOT NULL,          -- unix 秒 (伺服器時間)
    air_temp    REAL,                      -- NULL = 該次讀取失敗
    air_hum     REAL,
    water_temp  REAL,
    soil        INTEGER,                   -- ADC 原始值 0~4095
    water_level INTEGER
);

CREATE INDEX IF NOT EXISTS idx_readings_ts ON readings(ts);
