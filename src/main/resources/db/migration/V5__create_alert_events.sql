-- Audit log of every alert delivery attempt: one row per (alert, channel).
-- It answers "who was told what, and when?" and powers the latency-alert cooldown.

CREATE TABLE alert_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monitor_id  BIGINT        NOT NULL REFERENCES monitors (id) ON DELETE CASCADE,
    incident_id BIGINT        REFERENCES incidents (id) ON DELETE CASCADE,   -- NULL for latency alerts
    type        VARCHAR(30)   NOT NULL,
    channel     VARCHAR(20)   NOT NULL,
    status      VARCHAR(10)   NOT NULL,
    error       VARCHAR(1000),                                               -- why a delivery FAILED
    created_at  TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_alert_events_type    CHECK (type    IN ('INCIDENT_OPENED', 'INCIDENT_RESOLVED', 'LATENCY_EXCEEDED')),
    CONSTRAINT chk_alert_events_channel CHECK (channel IN ('EMAIL', 'WEBHOOK')),
    CONSTRAINT chk_alert_events_status  CHECK (status  IN ('SENT', 'FAILED'))
);

-- The cooldown asks: "was a SENT alert of this type sent for this monitor recently?"
CREATE INDEX idx_alert_events_cooldown ON alert_events (monitor_id, type, created_at DESC);
