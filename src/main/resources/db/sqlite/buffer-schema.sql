-- SQLite schema for the edge agent local buffer.
-- Applied on startup if tables do not exist.

CREATE TABLE IF NOT EXISTS sync_state (
  entity_type     TEXT PRIMARY KEY,
  last_watermark  TIMESTAMP,
  last_run_at     TIMESTAMP,
  last_status     TEXT,
  records_sent    INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS outbox (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type   TEXT NOT NULL,
  source_uuid   TEXT NOT NULL,
  watermark     TIMESTAMP NOT NULL,
  payload_json  TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'PENDING',
  attempts      INTEGER DEFAULT 0,
  last_error    TEXT,
  created_at    TEXT DEFAULT (datetime('now')),
  sent_at       TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_entity ON outbox(status, entity_type, id);
