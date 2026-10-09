'use client';

import { Card, CardHeader } from '@pml.tickets/shared/components/m3';
import type { EventRole, OrgRole } from '@/lib/api/team';
import { Banner } from '@pml.tickets/shared/components/m3';
import { CAPABILITIES, EVENT_CAPABILITIES } from '@/lib/team/roles';
import { useTeamRoles } from '@/lib/team/useTeamRoles';
import { statusLabel } from '@/components/console/Status';

const Yes = () => <span className="m3-pos" aria-label="Allowed">✓</span>;
const No = () => <span className="m3-muted" aria-label="Not allowed">—</span>;

/** Read-only roles and permissions reference. */
export function RolesTab() {
  const roles = useTeamRoles();
  const ROLE_KEYS = roles.orgRoles.map((r) => r.value);
  const EVENT_ROLES = roles.eventRoles.map((r) => r.value);
  return (
    <div className="m3-stack">
      {roles.unavailable ? <Banner tone="warning">Not available yet: the role list could not be loaded.</Banner> : null}
      <Card>
        <CardHeader
          title="Organization roles"
          subtitle="What each role can do. This table is read-only and fixed by the platform. A tick with a setting name means the owner can switch it on or off in Settings."
        />
        <div className="m3-table-wrap" role="region" aria-label="Organization roles" tabIndex={0}>
          <table className="m3-table" aria-label="Organization roles">
            <thead>
              <tr>
                <th scope="col">Action</th>
                <th scope="col">Permission</th>
                {ROLE_KEYS.map((r) => <th scope="col" key={r}>{r}</th>)}
              </tr>
            </thead>
            <tbody>
              {CAPABILITIES.map((c) => (
                <tr key={c.key}>
                  <th scope="row">{c.label}</th>
                  <td className="m3-mono">{c.permission}</td>
                  {ROLE_KEYS.map((r) =>
                    c.always.includes(r as OrgRole) ? (
                      <td key={r}><Yes /></td>
                    ) : c.conditional?.roles.includes(r as OrgRole) ? (
                      <td key={r}><span aria-label={`Allowed when ${c.conditional.setting} is on`}>✓ <small>if {c.conditional.setting}</small></span></td>
                    ) : (
                      <td key={r}><No /></td>
                    )
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="m3-muted">
          {ROLE_KEYS.map((r) => (
            <span key={r}><b>{r}</b>: {roles.describe(r)}<br /></span>
          ))}
        </p>
      </Card>
      <Card>
        <CardHeader title="Event roles" subtitle="Granted for one event at a time, optionally with an expiry." />
        <div className="m3-table-wrap" role="region" aria-label="Event roles" tabIndex={0}>
          <table className="m3-table" aria-label="Event roles">
            <thead>
              <tr>
                <th scope="col">Action</th>
                {EVENT_ROLES.map((r) => <th scope="col" key={r}>{statusLabel(r)}</th>)}
              </tr>
            </thead>
            <tbody>
              {EVENT_CAPABILITIES.map(([action, roles]) => (
                <tr key={action}>
                  <th scope="row">{action}</th>
                  {EVENT_ROLES.map((r) => <td key={r}>{roles.includes(r as EventRole) ? <Yes /> : <No />}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
