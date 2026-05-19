'use client';

/**
 * System Settings Page
 *
 * Allows administrators to configure:
 * - Theme preferences (light/dark/system)
 * - Notification settings
 * - Display preferences
 * - Account settings
 */

import { Box, Flex, Grid, Heading, Text, Separator, Switch } from '@radix-ui/themes';
import { SunLight, Bell, Eye, UserCircle, Shield, Database } from 'iconoir-react';
import { StyledCard } from '@/components/ui/StyledCard';
import { ThemeSelector } from '@/components/ui/ThemeToggle';

// =============================================================================
// SETTINGS SECTION COMPONENT
// =============================================================================

interface SettingsSectionProps {
  title: string;
  description: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}

function SettingsSection({ title, description, icon, children }: SettingsSectionProps) {
  return (
    <StyledCard>
      <Flex direction="column" gap="4">
        {/* Header */}
        <Flex align="center" gap="3">
          <Box
            style={{
              padding: '10px',
              borderRadius: 'var(--radius-3)',
              backgroundColor: 'var(--violet-a3)',
            }}
          >
            {icon}
          </Box>
          <Box>
            <Heading size="4" weight="medium">{title}</Heading>
            <Text size="2" color="gray">{description}</Text>
          </Box>
        </Flex>

        <Separator size="4" />

        {/* Content */}
        {children}
      </Flex>
    </StyledCard>
  );
}

// =============================================================================
// SETTING ROW COMPONENT
// =============================================================================

interface SettingRowProps {
  label: string;
  description?: string;
  children: React.ReactNode;
}

function SettingRow({ label, description, children }: SettingRowProps) {
  return (
    <Flex justify="between" align="center" py="2">
      <Box>
        <Text weight="medium" size="2">{label}</Text>
        {description && (
          <Text size="1" color="gray" style={{ display: 'block', marginTop: 2 }}>
            {description}
          </Text>
        )}
      </Box>
      {children}
    </Flex>
  );
}

// =============================================================================
// MAIN SETTINGS PAGE
// =============================================================================

export default function SettingsPage() {
  return (
    <Box>
      <Flex direction="column" gap="6">
        {/* Page Header */}
        <Flex direction="column" gap="1">
          <Heading size="6">System Settings</Heading>
          <Text color="gray" size="2">
            Configure your dashboard preferences and system settings.
          </Text>
        </Flex>

        {/* Settings Grid */}
        <Grid columns={{ initial: '1', lg: '2' }} gap="4">

          {/* ============================================== */}
          {/* APPEARANCE SETTINGS */}
          {/* ============================================== */}
          <SettingsSection
            title="Appearance"
            description="Customize how the dashboard looks"
            icon={<SunLight style={{ width: 20, height: 20, color: 'var(--violet-11)' }} />}
          >
            <Flex direction="column" gap="4">
              <Box>
                <Text size="2" weight="medium" mb="3" style={{ display: 'block' }}>
                  Theme
                </Text>
                <ThemeSelector />
              </Box>
            </Flex>
          </SettingsSection>

          {/* ============================================== */}
          {/* NOTIFICATION SETTINGS */}
          {/* ============================================== */}
          <SettingsSection
            title="Notifications"
            description="Manage your notification preferences"
            icon={<Bell style={{ width: 20, height: 20, color: 'var(--violet-11)' }} />}
          >
            <Flex direction="column" gap="3">
              <SettingRow
                label="Email notifications"
                description="Receive email alerts for important events"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="Push notifications"
                description="Browser notifications for real-time updates"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="New organizer applications"
                description="Alert when new organizers apply"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="Payment alerts"
                description="Notifications for payment issues"
              >
                <Switch defaultChecked />
              </SettingRow>
            </Flex>
          </SettingsSection>

          {/* ============================================== */}
          {/* DISPLAY SETTINGS */}
          {/* ============================================== */}
          <SettingsSection
            title="Display"
            description="Configure display and accessibility options"
            icon={<Eye style={{ width: 20, height: 20, color: 'var(--violet-11)' }} />}
          >
            <Flex direction="column" gap="3">
              <SettingRow
                label="Compact mode"
                description="Reduce spacing for more content"
              >
                <Switch />
              </SettingRow>

              <SettingRow
                label="Show avatars"
                description="Display user avatars in lists"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="Animations"
                description="Enable UI animations and transitions"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="High contrast"
                description="Increase contrast for better visibility"
              >
                <Switch />
              </SettingRow>
            </Flex>
          </SettingsSection>

          {/* ============================================== */}
          {/* ACCOUNT SETTINGS */}
          {/* ============================================== */}
          <SettingsSection
            title="Account"
            description="Manage your account settings"
            icon={<UserCircle style={{ width: 20, height: 20, color: 'var(--violet-11)' }} />}
          >
            <Flex direction="column" gap="3">
              <SettingRow
                label="Two-factor authentication"
                description="Add an extra layer of security"
              >
                <Switch />
              </SettingRow>

              <SettingRow
                label="Session timeout"
                description="Auto-logout after 8 hours of inactivity"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="Login notifications"
                description="Email alerts for new login sessions"
              >
                <Switch defaultChecked />
              </SettingRow>
            </Flex>
          </SettingsSection>

          {/* ============================================== */}
          {/* SECURITY SETTINGS */}
          {/* ============================================== */}
          <SettingsSection
            title="Security"
            description="Security and privacy settings"
            icon={<Shield style={{ width: 20, height: 20, color: 'var(--violet-11)' }} />}
          >
            <Flex direction="column" gap="3">
              <SettingRow
                label="Audit logging"
                description="Log all admin actions for review"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="IP allowlisting"
                description="Restrict access to specific IPs"
              >
                <Switch />
              </SettingRow>

              <SettingRow
                label="API access"
                description="Allow API access to your account"
              >
                <Switch defaultChecked />
              </SettingRow>
            </Flex>
          </SettingsSection>

          {/* ============================================== */}
          {/* DATA SETTINGS */}
          {/* ============================================== */}
          <SettingsSection
            title="Data & Storage"
            description="Manage data preferences"
            icon={<Database style={{ width: 20, height: 20, color: 'var(--violet-11)' }} />}
          >
            <Flex direction="column" gap="3">
              <SettingRow
                label="Auto-save drafts"
                description="Automatically save form drafts"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="Cache dashboard data"
                description="Store data locally for faster loading"
              >
                <Switch defaultChecked />
              </SettingRow>

              <SettingRow
                label="Analytics"
                description="Help improve the platform with usage data"
              >
                <Switch defaultChecked />
              </SettingRow>
            </Flex>
          </SettingsSection>
        </Grid>
      </Flex>
    </Box>
  );
}
