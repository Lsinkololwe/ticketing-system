'use client';

import { ModuleFrame } from '@/components/console/ModuleFrame';
import { useStaff } from '@/components/console/StaffContext';
import { primaryRole } from '@/lib/pending';
import { AdminDashboard } from './AdminDashboard';
import { FinanceDashboard } from './FinanceDashboard';
import { LeadDashboard } from './LeadDashboard';
import { SuperDashboard } from './SuperDashboard';

export function greeting(hour: number): string {
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
}

export function DashboardPage() {
  const staff = useStaff();
  const role = primaryRole(staff.roles);
  const now = new Date();
  const first = staff.name.split(/\s+/)[0] || 'there';
  const date = now.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return (
    <ModuleFrame module="dashboard" title={`${greeting(now.getHours())}, ${first}`} subtitle={date}>
      <div className="adm-stack">
        {role === 'SUPER_ADMIN' ? <SuperDashboard /> : null}
        {role === 'ADMIN' ? <AdminDashboard /> : null}
        {role === 'FINANCE_LEAD' ? <LeadDashboard /> : null}
        {role === 'FINANCE' ? <FinanceDashboard /> : null}
      </div>
    </ModuleFrame>
  );
}
