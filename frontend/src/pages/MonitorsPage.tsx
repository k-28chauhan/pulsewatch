import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Search } from 'lucide-react';
import { useOverview } from '../api/hooks';
import type { MonitorOverview, MonitorStatus } from '../api/types';
import { ErrorBox, Skeleton } from '../components/States';
import { MonitorDrawer } from '../components/MonitorDrawer';
import { StatusBadge } from '../components/StatusBadge';
import { formatLatency, formatPercent, formatRelative, shortUrl } from '../lib/format';
import { useNow } from '../lib/useNow';

/** Trouble first: the list is sorted so that what needs attention is at the top. */
const SEVERITY: Record<MonitorStatus, number> = { DOWN: 0, DEGRADED: 1, PENDING: 2, UP: 3, PAUSED: 4 };

export function sortMonitors(monitors: MonitorOverview[]): MonitorOverview[] {
  return [...monitors].sort((a, b) => SEVERITY[a.status] - SEVERITY[b.status] || a.name.localeCompare(b.name));
}

export function MonitorsPage() {
  const { data, isPending, error, refetch } = useOverview();
  const [creating, setCreating] = useState(false);
  const [query, setQuery] = useState('');
  const now = useNow(15_000);

  const monitors = useMemo(() => sortMonitors(data ?? []), [data]);
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? monitors.filter((m) => m.name.toLowerCase().includes(q) || m.url.toLowerCase().includes(q)) : monitors;
  }, [monitors, query]);

  const counts = useMemo(() => {
    const result: Partial<Record<MonitorStatus, number>> = {};
    for (const m of monitors) result[m.status] = (result[m.status] ?? 0) + 1;
    return result;
  }, [monitors]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Monitors</h1>
          {data && data.length > 0 && (
            <div className="summary sub" aria-label="Summary">
              {(['DOWN', 'DEGRADED', 'UP', 'PENDING', 'PAUSED'] as MonitorStatus[]).filter((s) => counts[s]).map((s) => (
                <span key={s} className={`status status-${s.toLowerCase()}`}>
                  <span className={`dot dot-${s.toLowerCase()}`} aria-hidden="true" />
                  {counts[s]} {s === 'DEGRADED' ? 'failing' : s.toLowerCase()}
                </span>
              ))}
            </div>
          )}
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
          <Plus size={16} />
          New monitor
        </button>
      </div>

      {error && <ErrorBox message={error.message} onRetry={() => refetch()} />}

      {isPending && !error && (
        <div className="panel" aria-busy="true" aria-label="Loading monitors">
          {[0, 1, 2].map((i) => (
            <div key={i} className="row" style={{ gridTemplateColumns: '1fr' }}>
              <Skeleton width="38%" height={14} />
            </div>
          ))}
        </div>
      )}

      {data && data.length === 0 && (
        <div className="panel empty">
          <h2>No monitors yet</h2>
          <p>Add the URL of an API or website. PulseWatch checks it on a schedule and tells you the moment it stops responding.</p>
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
            <Plus size={16} />
            Add your first monitor
          </button>
        </div>
      )}

      {data && data.length > 0 && (
        <>
          {data.length > 5 && (
            <div className="toolbar">
              <div className="input-affix" style={{ flex: 1, maxWidth: 280 }}>
                <input className="input" placeholder="Filter by name or URL" aria-label="Filter monitors" value={query} onChange={(e) => setQuery(e.target.value)} style={{ height: 32, paddingLeft: 30 }} />
                <Search size={14} style={{ position: 'absolute', left: 10, top: 9, color: 'var(--text-muted)' }} />
              </div>
            </div>
          )}
          <div className="panel" role="table" aria-label="Monitors">
            <div className="list-head" role="row">
              <span role="columnheader">Status</span>
              <span role="columnheader">Monitor</span>
              <span role="columnheader" className="num">Uptime 24h</span>
              <span role="columnheader" className="num">Response</span>
              <span role="columnheader" className="num">Last check</span>
            </div>
            {visible.map((m) => (
              <Link key={m.id} to={`/monitors/${m.id}`} className={`row${m.status === 'PAUSED' ? ' row-paused' : ''}`} role="row">
                <StatusBadge status={m.status} />
                <span className="row-name">
                  <strong>{m.name}</strong>
                  <span className="mono">{shortUrl(m.url)}</span>
                </span>
                <span className="row-meta">
                  <span className="num row-muted" data-label="Uptime" title="Uptime in the last 24 hours">{formatPercent(m.uptime24h)}</span>
                  <span className="num row-muted" data-label="Response">{formatLatency(m.lastLatencyMs)}</span>
                  <span className="num row-muted" data-label="Checked">{formatRelative(m.lastCheckedAt, now)}</span>
                </span>
              </Link>
            ))}
            {visible.length === 0 && <div className="empty-inline">No monitor matches “{query}”.</div>}
          </div>
        </>
      )}

      {creating && <MonitorDrawer onClose={() => setCreating(false)} />}
    </>
  );
}
