'use client';

/**
 * Logout page. Reached when a client request discovers the session ended (or from a bookmark).
 * It POSTs to the BFF, which destroys the server session, revokes tokens and ends the Keycloak
 * session before returning to the landing page.
 */

import { useEffect, useState } from 'react';
import { AuthLayout } from '@pml.tickets/shared/layouts';
import { signOut } from '@/lib/session';

export default function LogoutPage() {
  const [status, setStatus] = useState('Signing out...');
  useEffect(() => {
    setStatus('Ending your session...');
    signOut();
  }, []);
  return (
    <AuthLayout product="MyTicketZM" console="Organizer" title="Signing out">
      <p role="status">{status}</p>
    </AuthLayout>
  );
}
