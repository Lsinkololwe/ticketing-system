'use client';

/**
 * Right side of the top bar: search (opens the command palette), the pending-work bell and, when the
 * navigation drawer is not on screen (phones), the account menu.
 */
import { Avatar, Icon, Menu } from '@pml.tickets/shared/components/m3';
import { useConsoleUi } from './ConsoleUi';
import { useStaff } from './StaffContext';

export function TopTools() {
  const ui = useConsoleUi();
  const staff = useStaff();
  if (!ui) return null;
  return (
    <>
      <button type="button" className="adm-search m3-state" aria-label="Search everything (Control K)" onClick={ui.openPalette}>
        <Icon name="search" />
        <span className="adm-search__lb">Search</span>
        <kbd className="adm-search__kbd">Ctrl K</kbd>
      </button>
      <Menu
        align="end"
        label="Pending work"
        items={ui.bellItems}
        trigger={({ ref, ...p }) => (
          <button ref={ref} type="button" className="adm-bell m3-state" aria-label={`Notifications: ${ui.pendingTotal} items need attention`} {...p}>
            <Icon name="bell" />
            {ui.pendingTotal ? <span className="adm-bell__bdg">{ui.pendingTotal > 99 ? '99+' : ui.pendingTotal}</span> : null}
          </button>
        )}
      />
      <span className="adm-who">
        <Menu
          align="end"
          label="Account menu"
          items={[
            { id: 'profile', label: 'My profile', icon: 'user', onSelect: ui.openProfile },
            { id: 'signout', label: 'Sign out', icon: 'logout', danger: true, onSelect: ui.signOut },
          ]}
          trigger={({ ref, ...p }) => (
            <button ref={ref} type="button" className="adm-who__btn m3-state" aria-label="Account menu" {...p}>
              <Avatar name={staff.name} size="sm" tone="accent" />
            </button>
          )}
        />
      </span>
    </>
  );
}
