/**
 * Application layout (server). Guards the onboarding flow: requires a session,
 * resolves the onboarding state once, and redirects anyone who does not belong
 * on this route. Presentation is the shared console shell.
 */
import type { ReactNode } from 'react';
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { bff } from '@/lib/bff';
import { getOnboardingState } from '@/lib/organization/server';
import { redirectFor, ROUTES } from '@/lib/onboarding/state';
import { ConsoleShell } from '@/components/console/ConsoleShell';

export default async function ApplicationLayout({ children }: { children: ReactNode }) {
  await bff.requireSession();
  const state = await getOnboardingState();
  const path = (await headers()).get('x-pml-path') ?? ROUTES.welcome;
  const target = redirectFor(state, path.split('?')[0]);
  if (target) redirect(target);
  return <ConsoleShell>{children}</ConsoleShell>;
}
