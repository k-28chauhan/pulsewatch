-- A check may run for up to timeout_ms; if that is not shorter than the interval, the next check
-- for the same monitor would start while the previous one is still running.
-- The API enforces this too (@TimeoutShorterThanInterval); this is the database's own guard.

ALTER TABLE monitors
    ADD CONSTRAINT chk_monitors_timeout_lt_interval CHECK (timeout_ms < interval_seconds * 1000);
