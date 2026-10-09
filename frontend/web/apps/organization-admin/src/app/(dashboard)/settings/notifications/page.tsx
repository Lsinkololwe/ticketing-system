import { redirect } from 'next/navigation';

export default function SettingsNotificationsRedirect() {
  redirect('/settings?tab=notif');
}
