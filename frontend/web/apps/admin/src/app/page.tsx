/** Root: signed-in staff go to the dashboard, everyone else to sign-in (the proxy already guards both). */
import { redirect } from 'next/navigation';
import { bff } from '@/lib/bff';

export default async function Home() {
  redirect((await bff.getSession()) ? '/dashboard' : '/login');
}
