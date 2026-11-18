-- An incident is one continuous outage of one monitor: it opens after N consecutive failed checks
-- and is resolved by the first successful check.

CREATE TABLE incidents (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monitor_id  BIGINT        NOT NULL REFERENCES monitors (id) ON DELETE CASCADE,

    started_at  TIMESTAMPTZ   NOT NULL,   -- time of the FIRST failed check of the streak
    resolved_at TIMESTAMPTZ,              -- NULL while the incident is open
    error       VARCHAR(1000),            -- the error that was seen when it opened

    CONSTRAINT chk_incidents_resolved_after_started CHECK (resolved_at IS NULL OR resolved_at >= started_at)
);

-- The deduplication rule, enforced by the database itself: a monitor can have at most ONE row
-- where resolved_at IS NULL. Resolved rows are not in the index, so history can grow freely.
-- This also makes "find the open incident of monitor X" a fast index lookup.
CREATE UNIQUE INDEX uq_incidents_one_open_per_monitor ON incidents (monitor_id) WHERE resolved_at IS NULL;
