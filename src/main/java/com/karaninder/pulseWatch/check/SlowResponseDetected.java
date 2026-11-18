package com.karaninder.pulseWatch.check;

import java.time.Instant;

/** "A check succeeded but took longer than the monitor's latency threshold." */
public record SlowResponseDetected(Long monitorId, int latencyMs, int thresholdMs, Instant checkedAt) {
}
