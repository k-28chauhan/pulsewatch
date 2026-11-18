// These types mirror the JSON records of the Spring Boot API (the *Response records on the server).

export type MonitorStatus = 'UP' | 'DEGRADED' | 'DOWN' | 'PAUSED' | 'PENDING';
export type HttpMethod = 'GET' | 'HEAD' | 'POST';
export type Window = '24h' | '7d' | '30d';

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
}

export interface MonitorOverview {
  id: number;
  name: string;
  url: string;
  status: MonitorStatus;
  enabled: boolean;
  intervalSeconds: number;
  lastCheckedAt: string | null;
  lastLatencyMs: number | null;
  uptime24h: number | null;
}

export interface Monitor {
  id: number;
  name: string;
  url: string;
  method: HttpMethod;
  expectedStatus: number;
  intervalSeconds: number;
  timeoutMs: number;
  latencyThresholdMs: number | null;
  failureThreshold: number;
  enabled: boolean;
  nextCheckAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface MonitorRequest {
  name: string;
  url: string;
  method: HttpMethod;
  expectedStatus: number;
  intervalSeconds: number;
  timeoutMs: number;
  latencyThresholdMs: number | null;
  failureThreshold: number;
}

export interface UptimeStats {
  uptimePercent: number | null;
  totalChecks: number;
  failedChecks: number;
  avgLatencyMs: number | null;
}

export interface LatencyStats {
  p50: number | null;
  p95: number | null;
  p99: number | null;
  max: number | null;
}

export interface ErrorRateBucket {
  bucketStart: string;
  totalChecks: number;
  failedChecks: number;
  errorRate: number;
}

export interface LatencyBucket {
  bucketStart: string;
  p50: number | null;
  p95: number | null;
  checks: number;
}

export interface Incident {
  id: number;
  monitorId: number;
  startedAt: string;
  resolvedAt: string | null;
  open: boolean;
  durationSeconds: number;
  error: string | null;
}

export interface StatsResponse {
  monitorId: number;
  generatedAt: string;
  uptime24h: UptimeStats;
  uptime7d: UptimeStats;
  uptime30d: UptimeStats;
  latency24h: LatencyStats;
  latency7d: LatencyStats;
  errorRate24h: ErrorRateBucket[];
  openIncident: Incident | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
