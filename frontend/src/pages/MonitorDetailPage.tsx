import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft, Pause, Pencil, Play, Trash2, TriangleAlert } from 'lucide-react';
import { ApiError } from '../api/client';
import {
  useDeleteMonitor,
  useErrorRate,
  useIncidents,
  useLatency,
  useMonitor,
  useOverview,
  useSetPaused,
  useStats,
} from '../api/hooks';
import type { Incident, StatsResponse, UptimeStats, Window } from '../api/types';
import { LatencyChart, LatencyLegend } from '../charts/LatencyChart';
import { StatusStrip, StatusStripLegend } from '../charts/StatusStrip';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { MonitorDrawer } from '../components/MonitorDrawer';
import { SegmentedControl } from '../components/SegmentedControl';
import { ErrorBox, Skeleton } from '../components/States';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime, formatDuration, formatLatency, formatPercent, formatRelative } from '../lib/format';
import { useNow } from '../lib/useNow';

const WINDOWS: { value: Window; label: string }[] = [
  { value: '24h', label: '24 hours' },
  { value: '7d', label: '7 days' },
  { value: '30d', label: '30 days' },
];

function uptimeFor(stats: StatsResponse, window: Window): UptimeStats {
  return window === '24h' ? stats.uptime24h : window === '7d' ? stats.uptime7d : stats.uptime30d;
}

export function MonitorDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const navigate = useNavigate();
  const now = useNow();

  const [window, setWindow] = useState<Window>('24h');
  const [editing, setEditing] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const monitor = useMonitor(id);
  const overview = useOverview();
  const stats = useStats(id);
  const errorRate = useErrorRate(id, window);
  const latency = useLatency(id, window);
  const incidents = useIncidents(id);
  const setPaused = useSetPaused(id);
  const remove = useDeleteMonitor(id);

  if (!Number.isInteger(id)) return <Navigate to="/" replace />;

  if (monitor.error instanceof ApiError && monitor.error.status === 404) {
    return (
      <div className="panel empty">
        <h2>Monitor not found</h2>
        <p>It may have been deleted, or it belongs to another account.</p>
        <Link to="/" className="btn">Back to monitors</Link>
      </div>
    );
  }

  const m = monitor.data;
  const summary = overview.data?.find((o) => o.id === id);
  const status = summary?.status;
  const uptime = stats.data ? uptimeFor(stats.data, window) : undefined;
  const percentiles = stats.data && window !== '30d' ? (window === '24h' ? stats.data.latency24h : stats.data.latency7d) : undefined;
  const updatedAgo = stats.dataUpdatedAt ? formatRelative(new Date(stats.dataUpdatedAt).toISOString(), now) : null;

  return (
    <>
      <Link to="/" className="back">
        <ChevronLeft size={15} />
        Monitors
      </Link>

      <div className="detail-head">
        <div style={{ minWidth: 0 }}>
          <div className="detail-title">
            {m ? <h1>{m.name}</h1> : <Skeleton width={220} height={26} />}
            {status && <StatusBadge status={status} />}
          </div>
          {m ? <div className="detail-url mono">{m.method} {m.url}</div> : <Skeleton width={320} height={14} style={{ marginTop: 8 }} />}
        </div>
        {m && (
          <div className="detail-actions">
            <button type="button" className="btn" onClick={() => setPaused.mutate(m.enabled)} disabled={setPaused.isPending}>
              {m.enabled ? <Pause size={14} /> : <Play size={14} />}
              {m.enabled ? 'Pause' : 'Resume'}
            </button>
            <button type="button" className="btn" onClick={() => setEditing(true)}>
              <Pencil size={14} />
              Edit
            </button>
            <button type="button" className="btn btn-ghost btn-icon" onClick={() => setConfirmingDelete(true)} aria-label="Delete monitor">
              <Trash2 size={15} />
            </button>
          </div>
        )}
      </div>

      {monitor.error && <ErrorBox message={monitor.error.message} onRetry={() => monitor.refetch()} />}

      {stats.data?.openIncident && (
        <div className="banner" role="alert">
          <TriangleAlert size={18} />
          <div>
            <strong>Down for {formatDuration(stats.data.openIncident.durationSeconds)}</strong>
            <div className="detail">Since {formatDateTime(stats.data.openIncident.startedAt)}{stats.data.openIncident.error ? `: ${stats.data.openIncident.error}` : ''}</div>
          </div>
        </div>
      )}

      <div className="filter-row">
        <SegmentedControl label="Time range" value={window} onChange={setWindow} options={WINDOWS} />
        <span className="spacer" />
        {updatedAgo && <span className="updated">Updated {updatedAgo}</span>}
      </div>

      <div className="panel figures" aria-busy={stats.isPending}>
        <div className="figure figure-hero">
          <div className="figure-label">Uptime</div>
          <div className="figure-value">{stats.isPending ? <Skeleton width={120} height={44} /> : formatPercent(uptime?.uptimePercent)}</div>
          <div className="figure-note">{uptime && uptime.totalChecks > 0 ? `${uptime.failedChecks} failed of ${uptime.totalChecks} checks` : stats.isPending ? '' : 'No checks in this period'}</div>
        </div>
        <div className="figure">
          <div className="figure-label">Average response</div>
          <div className="figure-value tnum">{stats.isPending ? <Skeleton width={70} height={24} /> : formatLatency(uptime?.avgLatencyMs)}</div>
        </div>
        <div className="figure">
          <div className="figure-label">Slowest 5% (p95)</div>
          <div className="figure-value tnum">{stats.isPending ? <Skeleton width={70} height={24} /> : formatLatency(percentiles?.p95)}</div>
          {window === '30d' && <div className="figure-note">Kept for 7 days</div>}
        </div>
        <div className="figure">
          <div className="figure-label">Slowest check</div>
          <div className="figure-value tnum">{stats.isPending ? <Skeleton width={70} height={24} /> : formatLatency(percentiles?.max)}</div>
          {window === '30d' && <div className="figure-note">Kept for 7 days</div>}
        </div>
      </div>

      <div className="panel chart-card">
        <div className="chart-head">
          <h2>Response time</h2>
          {window !== '30d' && <LatencyLegend />}
        </div>
        {window === '30d' ? (
          <div className="chart-note">Response times are kept for 7 days. Choose 24 hours or 7 days to see them.</div>
        ) : latency.error ? (
          <ErrorBox message={latency.error.message} onRetry={() => latency.refetch()} />
        ) : latency.data ? (
          <div className={latency.isFetching ? 'refetching' : undefined}>
            <LatencyChart points={latency.data} window={window} />
          </div>
        ) : (
          <Skeleton height={220} />
        )}
      </div>

      <div className="panel chart-card">
        <div className="chart-head">
          <h2>Check results</h2>
          <StatusStripLegend />
        </div>
        {errorRate.error ? (
          <ErrorBox message={errorRate.error.message} onRetry={() => errorRate.refetch()} />
        ) : errorRate.data ? (
          <div className={errorRate.isFetching ? 'refetching' : undefined}>
            <StatusStrip buckets={errorRate.data} window={window} />
          </div>
        ) : (
          <Skeleton height={40} />
        )}
      </div>

      <div className="section-title">
        <h2>Incidents</h2>
        {incidents.data && <span className="sub">{incidents.data.pages[0]?.totalElements ?? 0} total</span>}
      </div>
      <div className="panel">
        {incidents.isPending && <div className="empty-inline"><Skeleton width="40%" /></div>}
        {incidents.error && <ErrorBox message={incidents.error.message} onRetry={() => incidents.refetch()} />}
        {incidents.data && incidents.data.pages[0]?.totalElements === 0 && <div className="empty-inline">No incidents. Everything has been running smoothly.</div>}
        {incidents.data?.pages.flatMap((page) => page.content).map((incident) => <IncidentRow key={incident.id} incident={incident} />)}
        {incidents.hasNextPage && (
          <div style={{ padding: 12, textAlign: 'center', borderTop: '1px solid var(--border)' }}>
            <button type="button" className="btn" onClick={() => incidents.fetchNextPage()} disabled={incidents.isFetchingNextPage}>
              {incidents.isFetchingNextPage ? 'Loading…' : 'Show more'}
            </button>
          </div>
        )}
      </div>

      {m && (
        <>
          <div className="section-title"><h2>Settings</h2></div>
          <dl className="panel settings" style={{ margin: 0 }}>
            <div><dt>Checks</dt><dd>every {formatDuration(m.intervalSeconds)}</dd></div>
            <div><dt>Timeout</dt><dd className="tnum">{formatLatency(m.timeoutMs)}</dd></div>
            <div><dt>Expects</dt><dd className="tnum">HTTP {m.expectedStatus}</dd></div>
            <div><dt>Alerts after</dt><dd>{m.failureThreshold} failed {m.failureThreshold === 1 ? 'check' : 'checks'}{m.latencyThresholdMs ? `, or over ${formatLatency(m.latencyThresholdMs)}` : ''}</dd></div>
          </dl>
        </>
      )}

      {editing && m && <MonitorDrawer monitor={m} onClose={() => setEditing(false)} />}
      {confirmingDelete && m && (
        <ConfirmDialog
          title={`Delete “${m.name}”?`}
          message="Its check history and incidents are deleted with it. This cannot be undone."
          confirmLabel="Delete monitor"
          busy={remove.isPending}
          error={remove.error?.message}
          onCancel={() => setConfirmingDelete(false)}
          onConfirm={() => remove.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })}
        />
      )}
    </>
  );
}

function IncidentRow({ incident }: { incident: Incident }) {
  return (
    <div className="incident-row">
      <span className={`status ${incident.open ? 'status-down' : ''}`}>
        <span className={`dot ${incident.open ? 'dot-down' : 'dot-up'}`} aria-hidden="true" />
        {incident.open ? 'Ongoing' : 'Resolved'}
      </span>
      <span className="tnum">{formatDateTime(incident.startedAt)}</span>
      <span className="tnum muted">{formatDuration(incident.durationSeconds)}</span>
      <span className="err" title={incident.error ?? undefined}>{incident.error ?? 'No error recorded'}</span>
    </div>
  );
}
