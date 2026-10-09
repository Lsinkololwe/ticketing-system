import { redirect } from 'next/navigation';

export default function InviteRedirect() {
  redirect('/team?tab=invites&invite=1');
}
