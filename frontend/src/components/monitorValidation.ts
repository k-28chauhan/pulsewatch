import type { HttpMethod, Monitor, MonitorRequest } from '../api/types';

/** Form state keeps every number as a string: that is what an <input> holds, and "" must stay distinguishable from 0. */
export interface MonitorFormValues {
  name: string;
  url: string;
  method: HttpMethod;
  expectedStatus: string;
  intervalSeconds: string;
  timeoutMs: string;
  latencyThresholdMs: string;
  failureThreshold: string;
}

export const DEFAULT_VALUES: MonitorFormValues = {
  name: '',
  url: '',
  method: 'GET',
  expectedStatus: '200',
  intervalSeconds: '60',
  timeoutMs: '5000',
  latencyThresholdMs: '',
  failureThreshold: '3',
};

export function valuesFromMonitor(monitor: Monitor): MonitorFormValues {
  return {
    name: monitor.name,
    url: monitor.url,
    method: monitor.method,
    expectedStatus: String(monitor.expectedStatus),
    intervalSeconds: String(monitor.intervalSeconds),
    timeoutMs: String(monitor.timeoutMs),
    latencyThresholdMs: monitor.latencyThresholdMs == null ? '' : String(monitor.latencyThresholdMs),
    failureThreshold: String(monitor.failureThreshold),
  };
}

const isInt = (text: string) => /^\d+$/.test(text.trim());

/**
 * The same rules the server enforces (MonitorRequest and @TimeoutShorterThanInterval), checked early for a faster
 * answer. The server stays the authority: its messages are shown too if something slips through.
 */
export function validateMonitor(values: MonitorFormValues): Partial<Record<keyof MonitorFormValues, string>> {
  const errors: Partial<Record<keyof MonitorFormValues, string>> = {};

  if (!values.name.trim()) errors.name = 'Give the monitor a name';
  else if (values.name.length > 100) errors.name = 'Use 100 characters or fewer';

  if (!values.url.trim()) errors.url = 'Enter the URL to check';
  else if (!/^https?:\/\/\S+$/i.test(values.url.trim())) errors.url = 'Must start with http:// or https://';
  else if (values.url.length > 2048) errors.url = 'Use 2048 characters or fewer';

  if (!isInt(values.intervalSeconds) || Number(values.intervalSeconds) < 10) errors.intervalSeconds = 'At least 10 seconds';

  if (!isInt(values.timeoutMs) || Number(values.timeoutMs) < 100 || Number(values.timeoutMs) > 60000) {
    errors.timeoutMs = 'Between 100 and 60000 ms';
  } else if (!errors.intervalSeconds && Number(values.timeoutMs) >= Number(values.intervalSeconds) * 1000) {
    errors.timeoutMs = `Must be shorter than the interval (${Number(values.intervalSeconds) * 1000} ms)`;
  }

  if (!isInt(values.expectedStatus) || Number(values.expectedStatus) < 100 || Number(values.expectedStatus) > 599) {
    errors.expectedStatus = 'A status code from 100 to 599';
  }

  if (values.latencyThresholdMs.trim() !== '' && (!isInt(values.latencyThresholdMs) || Number(values.latencyThresholdMs) < 1)) {
    errors.latencyThresholdMs = 'A positive number, or leave empty';
  }

  if (!isInt(values.failureThreshold) || Number(values.failureThreshold) < 1) errors.failureThreshold = 'At least 1';

  return errors;
}

export function toRequest(values: MonitorFormValues): MonitorRequest {
  return {
    name: values.name.trim(),
    url: values.url.trim(),
    method: values.method,
    expectedStatus: Number(values.expectedStatus),
    intervalSeconds: Number(values.intervalSeconds),
    timeoutMs: Number(values.timeoutMs),
    latencyThresholdMs: values.latencyThresholdMs.trim() === '' ? null : Number(values.latencyThresholdMs),
    failureThreshold: Number(values.failureThreshold),
  };
}
