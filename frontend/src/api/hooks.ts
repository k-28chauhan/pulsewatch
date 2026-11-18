import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { del, get, post, put } from './client';
import type {
  AuthResponse,
  ErrorRateBucket,
  Incident,
  LatencyBucket,
  Monitor,
  MonitorOverview,
  MonitorRequest,
  Page,
  StatsResponse,
  Window,
} from './types';

/** The whole app refreshes itself this often. */
export const REFRESH_MS = 30_000;

// Query keys in one place: invalidating "everything about monitor 5" is then a one-liner.
export const keys = {
  overview: ['overview'] as const,
  monitor: (id: number) => ['monitor', id] as const,
  stats: (id: number) => ['monitor', id, 'stats'] as const,
  errorRate: (id: number, window: Window) => ['monitor', id, 'error-rate', window] as const,
  latency: (id: number, window: Window) => ['monitor', id, 'latency', window] as const,
  incidents: (id: number) => ['monitor', id, 'incidents'] as const,
};

export const useOverview = () =>
  useQuery({
    queryKey: keys.overview,
    queryFn: () => get<MonitorOverview[]>('/api/monitors/overview'),
    refetchInterval: REFRESH_MS,
  });

export const useMonitor = (id: number) =>
  useQuery({ queryKey: keys.monitor(id), queryFn: () => get<Monitor>(`/api/monitors/${id}`) });

export const useStats = (id: number) =>
  useQuery({
    queryKey: keys.stats(id),
    queryFn: () => get<StatsResponse>(`/api/monitors/${id}/stats`),
    refetchInterval: REFRESH_MS,
  });

// placeholderData keeps the previous chart on screen while a new window loads: no flash, no layout jump.
export const useErrorRate = (id: number, window: Window) =>
  useQuery({
    queryKey: keys.errorRate(id, window),
    queryFn: () => get<ErrorRateBucket[]>(`/api/monitors/${id}/error-rate?window=${window}`),
    refetchInterval: REFRESH_MS,
    placeholderData: keepPreviousData,
  });

/** Only 24h and 7d exist on the server (raw rows are kept 7 days), so 30d is simply not requested. */
export const useLatency = (id: number, window: Window) =>
  useQuery({
    queryKey: keys.latency(id, window),
    queryFn: () => get<LatencyBucket[]>(`/api/monitors/${id}/latency?window=${window}`),
    refetchInterval: REFRESH_MS,
    placeholderData: keepPreviousData,
    enabled: window !== '30d',
  });

export const useIncidents = (id: number) =>
  useInfiniteQuery({
    queryKey: keys.incidents(id),
    queryFn: ({ pageParam }) => get<Page<Incident>>(`/api/monitors/${id}/incidents?page=${pageParam}&size=10`),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    refetchInterval: REFRESH_MS,
  });

function useInvalidateMonitors() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ['overview'] }).then(() => client.invalidateQueries({ queryKey: ['monitor'] }));
}

export const useCreateMonitor = () => {
  const invalidate = useInvalidateMonitors();
  return useMutation({ mutationFn: (body: MonitorRequest) => post<Monitor>('/api/monitors', body), onSuccess: invalidate });
};

export const useUpdateMonitor = (id: number) => {
  const invalidate = useInvalidateMonitors();
  return useMutation({ mutationFn: (body: MonitorRequest) => put<Monitor>(`/api/monitors/${id}`, body), onSuccess: invalidate });
};

export const useSetPaused = (id: number) => {
  const invalidate = useInvalidateMonitors();
  return useMutation({
    mutationFn: (paused: boolean) => post<Monitor>(`/api/monitors/${id}/${paused ? 'pause' : 'resume'}`),
    onSuccess: invalidate,
  });
};

export const useDeleteMonitor = (id: number) => {
  const invalidate = useInvalidateMonitors();
  return useMutation({ mutationFn: () => del<void>(`/api/monitors/${id}`), onSuccess: invalidate });
};

export const authRequest = (mode: 'login' | 'register', email: string, password: string) =>
  post<AuthResponse>(`/api/auth/${mode}`, { email, password });
