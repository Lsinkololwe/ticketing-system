import { ROLE_LABELS, type StaffRole } from '@/config/navigation';

/** Staff role as a coloured chip with a dot (the prototype's `.rolechip`). Colours come from the --m3-role-* tokens. */
export function RoleChip({ role, prefix }: { role: StaffRole; prefix?: string }) {
  return (
    <span className="adm-rolechip" data-role={role}>
      <i aria-hidden="true" />
      {prefix}
      {ROLE_LABELS[role]}
    </span>
  );
}
