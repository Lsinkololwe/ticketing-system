import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }), usePathname: () => '/health' }));

const api = vi.hoisted(() => ({
  health: { services: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  alerts: { alerts: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  acknowledge: vi.fn(),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useServiceHealth: () => api.health,
  useSystemAlerts: () => api.alerts,
  useAcknowledgeAlert: () => ({ acknowledge: api.acknowledge, loading: false }),
}));

import { HealthPage } from '../HealthPage';

const svc = (name: string, status: 'UP' | 'DOWN', over: Record<string, unknown> = {}) => ({ name, status, latencyMillis: 12, checkedAt: '2026-10-05T08:00:00Z', detail: null, ...over });
const alert = (id: string, status: string) => ({ id, source: 'booking', key: id, severity: 'WARNING', title: `Alert ${id}`, message: null, status, occurrences: 1, raisedAt: '2026-10-05T07:00:00Z', lastSeenAt: null, acknowledgedAt: null, acknowledgedBy: null, resolvedAt: null });

beforeEach(() => {
  vi.clearAllMocks();
  api.health = { services: [svc('mongodb', 'UP'), svc('redis', 'DOWN', { detail: 'timeout' })], loading: false, error: undefined, refetch: vi.fn() };
  api.alerts = { alerts: [alert('a1', 'OPEN'), alert('a2', 'ACKNOWLEDGED')], loading: false, error: undefined, refetch: vi.fn() };
  api.acknowledge.mockResolvedValue({});
});

describe('HealthPage', () => {
  it('renders tiles, services and alerts from the backend', () => {
    renderConsole(<HealthPage />, { roles: ['SUPER_ADMIN'] });
    expect(screen.getByRole('heading', { level: 1, name: 'Health' })).toBeInTheDocument();
    const services = screen.getByRole('table', { name: 'Services' });
    expect(within(services).getByText('Mongodb')).toBeInTheDocument();
    expect(within(services).getByText('Healthy')).toBeInTheDocument();
    expect(within(services).getByText('Unhealthy')).toBeInTheDocument();
    expect(within(services).getByText('Timeout')).toBeInTheDocument();
    const alerts = screen.getByRole('table', { name: 'Alerts' });
    expect(within(alerts).getByText('Alert a1')).toBeInTheDocument();
    expect(within(alerts).getAllByRole('button', { name: 'Acknowledge' })).toHaveLength(1);
    expect(screen.queryByText(/Not available yet/)).not.toBeInTheDocument();
  });

  it('acknowledges one alert', async () => {
    renderConsole(<HealthPage />, { roles: ['SUPER_ADMIN'] });
    fireEvent.click(screen.getByRole('button', { name: 'Acknowledge' }));
    await waitFor(() => expect(api.acknowledge).toHaveBeenCalledWith('a1'));
  });

  it('shows empty, loading and error states', () => {
    api.health = { services: [], loading: false, error: undefined, refetch: vi.fn() };
    api.alerts = { alerts: [], loading: false, error: undefined, refetch: vi.fn() };
    const { unmount } = renderConsole(<HealthPage />, { roles: ['ADMIN'] });
    expect(screen.getByText('No services reported.')).toBeInTheDocument();
    expect(screen.getByText('No alerts. All quiet.')).toBeInTheDocument();
    unmount();
    api.health = { services: [], loading: false, error: new Error('down'), refetch: api.health.refetch };
    const r2 = renderConsole(<HealthPage />, { roles: ['ADMIN'] });
    expect(screen.getAllByRole('alert').length).toBeGreaterThan(0);
    r2.unmount();
    api.health = { services: [], loading: true, error: undefined, refetch: vi.fn() };
    renderConsole(<HealthPage />, { roles: ['ADMIN'] });
    expect(document.querySelectorAll('[aria-busy="true"]').length).toBeGreaterThan(0);
  });

  it('is blocked for finance (no health access)', () => {
    renderConsole(<HealthPage />, { roles: ['FINANCE'] });
    expect(screen.getByText(/You don't have access/)).toBeInTheDocument();
  });

  it('is open to the finance lead', () => {
    renderConsole(<HealthPage />, { roles: ['FINANCE_LEAD'] });
    expect(screen.getByRole('table', { name: 'Services' })).toBeInTheDocument();
  });
});
