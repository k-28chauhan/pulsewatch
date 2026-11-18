-- A monitor = "please call this URL every N seconds and tell me if it breaks".
-- user_id is intentionally missing: the users table arrives in Phase 6.

CREATE TABLE monitors (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                 VARCHAR(100)  NOT NULL,
    url                  VARCHAR(2048) NOT NULL,
    method               VARCHAR(10)   NOT NULL DEFAULT 'GET',
    expected_status      INTEGER       NOT NULL DEFAULT 200,
    interval_seconds     INTEGER       NOT NULL DEFAULT 60,
    timeout_ms           INTEGER       NOT NULL DEFAULT 5000,
    latency_threshold_ms INTEGER,
    failure_threshold    INTEGER       NOT NULL DEFAULT 3,
    enabled              BOOLEAN       NOT NULL DEFAULT TRUE,
    next_check_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Last line of defence: the API validates too, but the database refuses bad rows no matter who writes them.
    CONSTRAINT chk_monitors_method            CHECK (method IN ('GET', 'HEAD', 'POST')),
    CONSTRAINT chk_monitors_expected_status   CHECK (expected_status BETWEEN 100 AND 599),
    CONSTRAINT chk_monitors_interval          CHECK (interval_seconds >= 10),
    CONSTRAINT chk_monitors_timeout           CHECK (timeout_ms BETWEEN 100 AND 60000),
    CONSTRAINT chk_monitors_failure_threshold CHECK (failure_threshold >= 1)
);

-- Phase 2's scheduler will ask: "which enabled monitors are due?"
-- This partial index makes that query cheap even with many monitors.
CREATE INDEX idx_monitors_due ON monitors (next_check_at) WHERE enabled;
