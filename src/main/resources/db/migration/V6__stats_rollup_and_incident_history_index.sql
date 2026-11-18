-- One row per monitor per UTC hour: a compact summary of check_results that outlives the raw rows.
-- Counts and sums can be added together exactly, so uptime over 30 days stays exact.
-- Percentiles cannot be combined, so they are NOT stored here: latency percentiles come from raw rows only.

CREATE TABLE check_results_hourly (
    monitor_id     BIGINT      NOT NULL REFERENCES monitors (id) ON DELETE CASCADE,
    hour_start     TIMESTAMPTZ NOT NULL,           -- start of the UTC hour
    total_checks   INTEGER     NOT NULL,
    failed_checks  INTEGER     NOT NULL,
    latency_sum_ms BIGINT      NOT NULL,           -- sum over SUCCESSFUL checks (average = sum / (total - failed))
    latency_max_ms INTEGER,                        -- NULL if every check in the hour failed

    PRIMARY KEY (monitor_id, hour_start),          -- also the target of the nightly upsert (ON CONFLICT)
    CONSTRAINT chk_hourly_counts CHECK (failed_checks >= 0 AND failed_checks <= total_checks)
);

-- Incident history is "newest first, one page at a time, for one monitor".
CREATE INDEX idx_incidents_monitor_started ON incidents (monitor_id, started_at DESC);
