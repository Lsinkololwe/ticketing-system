import type { IconName } from '@pml.tickets/shared/components/m3';

/**
 * Deployment links. The organizer and admin apps live on their own origins, so their URLs come from
 * the environment. When unset the "Switch app" menu and the "Sell tickets" column are left out.
 */
const ORGANIZER = process.env.NEXT_PUBLIC_ORGANIZER_URL ?? '';
const ADMIN = process.env.NEXT_PUBLIC_ADMIN_URL ?? '';
const SUPPORT_EMAIL = process.env.NEXT_PUBLIC_SUPPORT_EMAIL ?? '';
const SUPPORT_PHONE = process.env.NEXT_PUBLIC_SUPPORT_PHONE ?? '';

export const SWITCH_APPS: Array<{ id: string; label: string; hint: string; href: string; icon: IconName }> = [
  ...(ORGANIZER ? [{ id: 'org', label: 'Organizer portal', hint: 'Manage events and payouts', href: ORGANIZER, icon: 'building' as IconName }] : []),
  ...(ADMIN ? [{ id: 'admin', label: 'Platform admin', hint: 'Approvals, finance and support', href: ADMIN, icon: 'shield' as IconName }] : []),
];

export interface FooterColumn {
  heading: string;
  links?: Array<{ label: string; href: string }>;
  text?: string;
}

/** Footer link columns. `categories` come from the catalog (never hard-coded). */
export function buildFooterColumns(categories: string[]): FooterColumn[] {
  const cols: FooterColumn[] = [
    {
      heading: 'Showstop',
      text: 'Tickets for concerts, theatre, comedy, sport and festivals across Zambia. Reserve online and pay with MTN, Airtel or Zamtel mobile money.',
    },
    {
      heading: 'Help',
      links: [
        { label: 'Need help with your booking?', href: '/help' },
        { label: 'Refund policies explained', href: '/help#refund-policies' },
        { label: 'Terms of use', href: '/terms' },
        { label: 'Privacy policy', href: '/privacy' },
      ],
    },
  ];
  if (categories.length) {
    cols.push({
      heading: 'Browse',
      links: categories.map((c) => ({ label: c, href: `/?category=${encodeURIComponent(c)}#events` })),
    });
  }
  const contact = [
    ...(SUPPORT_PHONE ? [{ label: SUPPORT_PHONE, href: `tel:${SUPPORT_PHONE.replace(/\s/g, '')}` }] : []),
    ...(SUPPORT_EMAIL ? [{ label: SUPPORT_EMAIL, href: `mailto:${SUPPORT_EMAIL}` }] : []),
  ];
  if (contact.length) cols.push({ heading: 'Get in touch', links: contact });
  if (ORGANIZER) {
    cols.push({
      heading: 'Sell tickets',
      links: [
        { label: 'Become an organizer', href: ORGANIZER },
        { label: 'Organizer portal', href: ORGANIZER },
      ],
    });
  }
  return cols;
}
