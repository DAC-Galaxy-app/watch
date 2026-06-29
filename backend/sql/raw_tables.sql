CREATE TABLE IF NOT EXISTS collection_session (
    session_id UUID PRIMARY KEY,
    participant_id TEXT,
    watch_model TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    mode TEXT CHECK (mode IN ('continuous', 'on_demand'))
);

CREATE TABLE IF NOT EXISTS raw_biosignal (
    id TEXT PRIMARY KEY,
    session_id UUID,
    tracker_type TEXT NOT NULL,
    ts TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    values JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_raw_biosignal_tracker_ts
    ON raw_biosignal(tracker_type, ts);

CREATE INDEX IF NOT EXISTS idx_raw_biosignal_session_ts
    ON raw_biosignal(session_id, ts);
