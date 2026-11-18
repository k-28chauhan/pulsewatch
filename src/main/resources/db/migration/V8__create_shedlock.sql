-- ShedLock: one row per named scheduled job. The instance that manages to take the row (set lock_until into
-- the future) runs the job; the others skip that round. Plain TIMESTAMP (UTC), as ShedLock's own docs
-- prescribe for PostgreSQL, because it computes lock times in UTC on the database server.

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
