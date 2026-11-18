import { useState, type FormEvent } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { ApiError } from '../api/client';
import { useCreateMonitor, useUpdateMonitor } from '../api/hooks';
import type { Monitor } from '../api/types';
import { Drawer } from './Drawer';
import { SegmentedControl } from './SegmentedControl';
import {
  DEFAULT_VALUES,
  toRequest,
  validateMonitor,
  valuesFromMonitor,
  type MonitorFormValues,
} from './monitorValidation';

interface Props {
  /** Present when editing; absent when creating. */
  monitor?: Monitor;
  onClose: () => void;
}

const INTERVALS = [
  { seconds: 10, label: 'Every 10 seconds' },
  { seconds: 30, label: 'Every 30 seconds' },
  { seconds: 60, label: 'Every minute' },
  { seconds: 300, label: 'Every 5 minutes' },
  { seconds: 900, label: 'Every 15 minutes' },
  { seconds: 3600, label: 'Every hour' },
];

export function MonitorDrawer({ monitor, onClose }: Props) {
  const editing = monitor !== undefined;
  const create = useCreateMonitor();
  const update = useUpdateMonitor(monitor?.id ?? 0);
  const mutation = editing ? update : create;

  const [values, setValues] = useState<MonitorFormValues>(monitor ? valuesFromMonitor(monitor) : DEFAULT_VALUES);
  const [errors, setErrors] = useState<Partial<Record<keyof MonitorFormValues, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const set = <K extends keyof MonitorFormValues>(key: K, value: MonitorFormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined, ...(key === 'intervalSeconds' ? { timeoutMs: undefined } : {}) }));
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const found = validateMonitor(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      if (found.expectedStatus || found.latencyThresholdMs || found.failureThreshold) setShowAdvanced(true);
      return;
    }
    try {
      await mutation.mutateAsync(toRequest(values));
      onClose();
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors as Partial<Record<keyof MonitorFormValues, string>>);
        setFormError(Object.keys(error.fieldErrors).length > 0 ? null : error.message);
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    }
  }

  const intervalOptions = INTERVALS.some((o) => String(o.seconds) === values.intervalSeconds)
    ? INTERVALS
    : [...INTERVALS, { seconds: Number(values.intervalSeconds), label: `Every ${values.intervalSeconds} seconds` }];

  return (
    <Drawer title={editing ? 'Edit monitor' : 'New monitor'} onClose={onClose}>
      <form onSubmit={submit} noValidate style={{ display: 'contents' }}>
        <div className="drawer-body">
          <div className="field">
            <label htmlFor="mon-name">Name</label>
            <input id="mon-name" className="input" value={values.name} onChange={(e) => set('name', e.target.value)} placeholder="Production API" aria-invalid={!!errors.name} aria-describedby={errors.name ? 'mon-name-err' : undefined} maxLength={100} />
            {errors.name && <span id="mon-name-err" className="field-error">{errors.name}</span>}
          </div>

          <div className="field">
            <label htmlFor="mon-url">URL</label>
            <input id="mon-url" className="input mono" value={values.url} onChange={(e) => set('url', e.target.value)} placeholder="https://api.example.com/health" inputMode="url" autoCapitalize="off" spellCheck={false} aria-invalid={!!errors.url} aria-describedby={errors.url ? 'mon-url-err' : undefined} />
            {errors.url && <span id="mon-url-err" className="field-error">{errors.url}</span>}
          </div>

          <div className="field-row">
            <div className="field">
              <label htmlFor="mon-interval">Check</label>
              <select id="mon-interval" className="input" value={values.intervalSeconds} onChange={(e) => set('intervalSeconds', e.target.value)}>
                {intervalOptions.map((o) => (
                  <option key={o.seconds} value={o.seconds}>{o.label}</option>
                ))}
              </select>
              {errors.intervalSeconds && <span className="field-error">{errors.intervalSeconds}</span>}
            </div>
            <div className="field">
              <label htmlFor="mon-timeout">Timeout</label>
              <div className="input-affix">
                <input id="mon-timeout" className="input tnum" value={values.timeoutMs} onChange={(e) => set('timeoutMs', e.target.value)} inputMode="numeric" aria-invalid={!!errors.timeoutMs} aria-describedby={errors.timeoutMs ? 'mon-timeout-err' : undefined} />
                <span>ms</span>
              </div>
              {errors.timeoutMs && <span id="mon-timeout-err" className="field-error">{errors.timeoutMs}</span>}
            </div>
          </div>

          <button type="button" className="disclosure" onClick={() => setShowAdvanced((v) => !v)} aria-expanded={showAdvanced}>
            {showAdvanced ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
            Advanced
          </button>

          {showAdvanced && (
            <>
              <div className="field">
                <span className="field-label" id="mon-method-label">HTTP method</span>
                <SegmentedControl label="HTTP method" value={values.method} onChange={(m) => set('method', m)} options={[{ value: 'GET', label: 'GET' }, { value: 'HEAD', label: 'HEAD' }, { value: 'POST', label: 'POST' }]} />
              </div>
              <div className="field-row">
                <div className="field">
                  <label htmlFor="mon-status">Expected status</label>
                  <input id="mon-status" className="input tnum" value={values.expectedStatus} onChange={(e) => set('expectedStatus', e.target.value)} inputMode="numeric" aria-invalid={!!errors.expectedStatus} />
                  {errors.expectedStatus && <span className="field-error">{errors.expectedStatus}</span>}
                </div>
                <div className="field">
                  <label htmlFor="mon-failures">Alert after</label>
                  <div className="input-affix">
                    <input id="mon-failures" className="input tnum" value={values.failureThreshold} onChange={(e) => set('failureThreshold', e.target.value)} inputMode="numeric" aria-invalid={!!errors.failureThreshold} />
                    <span>fails</span>
                  </div>
                  {errors.failureThreshold && <span className="field-error">{errors.failureThreshold}</span>}
                </div>
              </div>
              <div className="field">
                <label htmlFor="mon-latency">Slow response threshold</label>
                <div className="input-affix">
                  <input id="mon-latency" className="input tnum" value={values.latencyThresholdMs} onChange={(e) => set('latencyThresholdMs', e.target.value)} inputMode="numeric" placeholder="Off" aria-invalid={!!errors.latencyThresholdMs} />
                  <span>ms</span>
                </div>
                {errors.latencyThresholdMs ? <span className="field-error">{errors.latencyThresholdMs}</span> : <span className="field-hint">Email me when a successful check takes longer than this.</span>}
              </div>
            </>
          )}

          {formError && <p className="field-error" role="alert">{formError}</p>}
        </div>
        <div className="drawer-foot">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create monitor'}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
