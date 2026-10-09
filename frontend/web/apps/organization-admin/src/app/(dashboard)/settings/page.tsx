'use client';

import { Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Banner, Skeleton, useSnackbar } from '@pml.tickets/shared/components/m3';
import { SettingsView, parseTab, type SettingsTab } from '@/components/settings/SettingsView';
import { OrgProfileSection } from '@/components/settings/OrgProfileSection';
import { OrgSettingsSection } from '@/components/settings/OrgSettingsSection';
import { NotificationPrefsSection } from '@/components/settings/NotificationPrefsSection';
import { PlatformRulesSection } from '@/components/settings/PlatformRulesSection';
import { MyProfileSection } from '@/components/settings/MyProfileSection';
import { DangerSection } from '@/components/settings/DangerSection';
import { useOrganizationDeletion, useSaveOrganization, useSettingsMe, useSettingsNotificationPrefs, useSettingsOrganization, useSlugCheck } from '@/lib/api/settings';
import { useOrgContext } from '@/lib/api/org-context';
import { usePlatformRulesView } from '@/lib/api/platform';

function SettingsContent() {
  const router = useRouter();
  const snackbar = useSnackbar();
  const params = useSearchParams();
  const { organization, loading, error } = useSettingsOrganization();
  const { saveProfile, saveFlags } = useSaveOrganization();
  const slug = useSlugCheck();
  const { me, saveName } = useSettingsMe();
  const { prefs, savePrefs } = useSettingsNotificationPrefs();
  const { capabilities, loading: ctxLoading } = useOrgContext();
  const { requestDeletion, cancelDeletion } = useOrganizationDeletion();
  const rules = usePlatformRulesView();
  const isOwner = capabilities.isOwner;
  const canEdit = capabilities.canEditOrganization;
  const tab = parseTab(params?.get('tab'), isOwner);

  // Sections run inside a <Form>: throw on failure so the kit maps the server error onto the form.
  const run = async (fn: () => Promise<unknown>, ok = 'Changes saved') => {
    await fn();
    snackbar.show(ok);
  };

  if (error && !organization) return <Banner tone="error" urgent>Could not load settings. {error.message}</Banner>;
  if ((loading || ctxLoading) && !organization) return <div className="m3-stack" role="status" aria-label="Loading" data-testid="loading"><Skeleton /><Skeleton /></div>;

  const panels: Record<SettingsTab, React.ReactNode> = {
    profile: organization ? (
      <OrgProfileSection
        organization={organization}
        canEdit={canEdit}
        slugAvailable={slug.available}
        onCheckSlug={(s) => void slug.check(s)}
        onSave={(d) =>
          run(() =>
            saveProfile(organization.id, {
              name: d.name.trim(),
              description: d.description,
              logoUrl: d.logoUrl,
              bannerUrl: d.bannerUrl,
              tagline: d.tagline,
              website: d.website,
              socialLinks: { facebook: d.facebook, instagram: d.instagram, twitter: d.twitter, linkedin: d.linkedin, youtube: d.youtube, tiktok: d.tiktok },
              // The server takes the identity fields only while the application is editable.
              ...(['DRAFT', 'CHANGES_REQUESTED'].includes(organization.status)
                ? {
                    taxId: d.taxId,
                    businessRegistrationNumber: d.businessRegistrationNumber,
                    businessPhone: d.businessPhone,
                    businessEmail: d.businessEmail,
                    businessAddress: { addressLine1: d.addressLine1, city: d.city, province: d.province, country: d.country },
                  }
                : {}),
            })
          )
        }
      />
    ) : null,
    org: organization?.settings ? (
      <OrgSettingsSection settings={organization.settings} isOwner={isOwner} onSave={(f) => run(() => saveFlags(organization.id, f))} />
    ) : (
      <Banner tone="info">Organization settings are not available yet.</Banner>
    ),
    notif: prefs ? <NotificationPrefsSection prefs={prefs} onSave={(p) => run(() => savePrefs(p))} /> : <Skeleton />,
    platform: rules.loading && !rules.view ? <Skeleton /> : <PlatformRulesSection rules={rules.view} />,
    me: me ? <MyProfileSection me={me} onSave={(f, l) => run(() => saveName(f, l))} /> : <Skeleton />,
    danger: organization ? (
      <DangerSection
        organizationName={organization.name}
        isOwner={isOwner}
        scheduledFor={organization.deletionScheduledFor}
        blockers={null}
        onRequestDeletion={(reason) => run(() => requestDeletion(organization.id, reason), 'Deletion scheduled. You have 90 days to cancel.')}
        onCancelDeletion={() => run(() => cancelDeletion(organization.id), 'Deletion cancelled')}
      />
    ) : null,
  };

  return <SettingsView tab={tab} isOwner={isOwner} panels={panels} onTab={(t) => router.replace(`/settings?tab=${t}`)} />;
}

export default function SettingsPage() {
  return (
    <Suspense fallback={null}>
      <SettingsContent />
    </Suspense>
  );
}
