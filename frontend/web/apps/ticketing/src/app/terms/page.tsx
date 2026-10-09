import type { Metadata } from 'next';
import { LegalPage } from '@/components/LegalPage';

export const metadata: Metadata = { title: 'Terms of Use (draft) | Showstop' };

export default function TermsPage() {
  return (
    <LegalPage
      title="Terms of Use"
      updated="October 2026"
      sections={[
        {
          heading: 'Using Showstop',
          body: [
            'Showstop lets you find events in Zambia and buy tickets. By creating an account or buying a ticket you agree to these terms.',
            'You sign in with a one-time code sent to a WhatsApp number or an email address that you own. Keep access to at least one of them so you can sign in.',
          ],
        },
        {
          heading: 'Your account',
          body: [
            'You are responsible for activity on your account. Tell us promptly if you think someone else has access to your WhatsApp number or email.',
            'Each WhatsApp number or email can belong to one account at a time.',
          ],
        },
        {
          heading: 'Tickets and payments',
          body: [
            'Tickets are issued by the event organizer through Showstop. Prices are shown in Zambian Kwacha. Refund and transfer rules are set per event and shown before you pay.',
          ],
        },
        {
          heading: 'Acceptable use',
          body: ['Do not resell tickets in ways the organizer forbids, abuse the sign-in service, or interfere with the platform.'],
        },
        {
          heading: 'Changes and contact',
          body: ['We may update these terms. Material changes will be shown to you and, where required, need your agreement. Contact details: to be confirmed in the reviewed version.'],
        },
      ]}
    />
  );
}
