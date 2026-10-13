'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { tabsFor } from '@/config/navigation';
import { useStaff } from '@/components/console/StaffContext';

/** Opens the first tab this role may use: payments for most staff, the audit log for finance. */
export default function TransactionsIndex() {
  const staff = useStaff();
  const router = useRouter();
  const first = tabsFor(staff.roles, 'transactions')[0];
  useEffect(() => {
    router.replace(first ? `/transactions/${first.id}` : '/dashboard');
  }, [first, router]);
  return null;
}
