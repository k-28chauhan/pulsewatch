-- One row per health check that ran. This table grows fastest, so it is kept narrow.

CREATE TABLE check_results (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- ON DELETE CASCADE: deleting a monitor also deletes its history, so no orphan rows.
    monitor_id    BIGINT        NOT NULL REFERENCES monitors (id) ON DELETE CASCADE,

    checked_at    TIMESTAMPTZ   NOT NULL,
    status_code   INTEGER,                      -- NULL when no HTTP response arrived (timeout, DNS, refused)
    latency_ms    INTEGER       NOT NULL,
    success       BOOLEAN       NOT NULL,
    error         VARCHAR(1000),                -- NULL on success
    attempt_count INTEGER       NOT NULL DEFAULT 1  -- Phase 3 (retries) will use this
);

-- Nearly every query is "results for ONE monitor, newest first" (detail page, uptime, incident logic).
CREATE INDEX idx_check_results_monitor_time ON check_results (monitor_id, checked_at DESC);
