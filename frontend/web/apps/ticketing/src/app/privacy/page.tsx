import type { Metadata } from 'next';
import { LegalPage } from '@/components/LegalPage';

export const metadata: Metadata = { title: 'Privacy Policy (draft) | Showstop' };

export default function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy Policy"
      updated="October 2026"
      sections={[
        {
          heading: 'What we collect',
          body: [
            'To sign you in we keep the WhatsApp number or email address you verify. We store it encrypted and show it back to you in a masked form. We also keep your ticket and order history and basic technical data needed to keep the service secure.',
          ],
        },
        {
          heading: 'How we use it',
          body: [
            'We use your contact to send one-time sign-in codes, tickets, and notices about your orders or account changes. We do not sell your contact details.',
          ],
        },
        {
          heading: 'Sharing',
          body: [
            'Organizers see only what they need to deliver your ticket. Service providers such as message delivery and payment partners process data on our behalf.',
          ],
        },
        {
          heading: 'Your choices',
          body: [
            'You can add, change or remove a sign-in contact in your profile, as long as one verified contact remains. You can ask us to delete your account; some records may be kept where the law requires.',
          ],
        },
        {
          heading: 'Retention and contact',
          body: ['Retention periods and our data protection contact will be set out in the reviewed version of this policy.'],
        },
      ]}
    />
  );
}
