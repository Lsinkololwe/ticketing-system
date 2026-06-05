'use client';

/**
 * Notification Settings Page
 *
 * Manage notification preferences:
 * - Email notifications
 * - Push notifications
 * - SMS notifications
 * - Notification categories
 */

import { useState, useCallback } from 'react';
import { Box, Flex, Text, Button, Card, Switch } from '@radix-ui/themes';
import { Bell, Mail, MessageText, SmartphoneDevice, FloppyDisk } from 'iconoir-react';
import { PageHeader } from '@/components/ui';

// =============================================================================
// TYPES
// =============================================================================

interface NotificationCategory {
  id: string;
  title: string;
  description: string;
  email: boolean;
  push: boolean;
  sms: boolean;
}

// =============================================================================
// NOTIFICATION ROW COMPONENT
// =============================================================================

interface NotificationRowProps {
  category: NotificationCategory;
  onChange: (id: string, channel: 'email' | 'push' | 'sms', value: boolean) => void;
}

function NotificationRow({ category, onChange }: NotificationRowProps) {
  return (
    <Flex
      justify="between"
      align="center"
      py="4"
      style={{
        borderBottom: '1px solid var(--surface-border)',
      }}
    >
      <Box style={{ flex: 1, maxWidth: '400px' }}>
        <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
          {category.title}
        </Text>
        <Text size="1" style={{ color: 'var(--content-muted)' }}>
          {category.description}
        </Text>
      </Box>

      <Flex gap="6" align="center">
        <Flex direction="column" align="center" gap="1">
          <Switch
            size="2"
            checked={category.email}
            onCheckedChange={(checked) => onChange(category.id, 'email', checked)}
          />
          <Text size="1" style={{ color: 'var(--content-muted)' }} className="hidden-mobile">
            Email
          </Text>
        </Flex>

        <Flex direction="column" align="center" gap="1">
          <Switch
            size="2"
            checked={category.push}
            onCheckedChange={(checked) => onChange(category.id, 'push', checked)}
          />
          <Text size="1" style={{ color: 'var(--content-muted)' }} className="hidden-mobile">
            Push
          </Text>
        </Flex>

        <Flex direction="column" align="center" gap="1">
          <Switch
            size="2"
            checked={category.sms}
            onCheckedChange={(checked) => onChange(category.id, 'sms', checked)}
          />
          <Text size="1" style={{ color: 'var(--content-muted)' }} className="hidden-mobile">
            SMS
          </Text>
        </Flex>
      </Flex>
    </Flex>
  );
}

// =============================================================================
// INITIAL DATA
// =============================================================================

const initialCategories: NotificationCategory[] = [
  {
    id: 'ticket_sales',
    title: 'Ticket Sales',
    description: 'Get notified when tickets are sold for your events',
    email: true,
    push: true,
    sms: false,
  },
  {
    id: 'event_reminders',
    title: 'Event Reminders',
    description: 'Reminders about upcoming events you\'re managing',
    email: true,
    push: true,
    sms: false,
  },
  {
    id: 'payout_updates',
    title: 'Payout Updates',
    description: 'Updates on payout requests and settlements',
    email: true,
    push: true,
    sms: true,
  },
  {
    id: 'team_activity',
    title: 'Team Activity',
    description: 'When team members join, leave, or change roles',
    email: true,
    push: false,
    sms: false,
  },
  {
    id: 'check_in_alerts',
    title: 'Check-in Alerts',
    description: 'Real-time alerts during event check-in',
    email: false,
    push: true,
    sms: false,
  },
  {
    id: 'sales_reports',
    title: 'Sales Reports',
    description: 'Daily and weekly sales summary reports',
    email: true,
    push: false,
    sms: false,
  },
  {
    id: 'security_alerts',
    title: 'Security Alerts',
    description: 'Important security-related notifications',
    email: true,
    push: true,
    sms: true,
  },
  {
    id: 'marketing',
    title: 'Marketing & Tips',
    description: 'Tips for growing your events and platform updates',
    email: true,
    push: false,
    sms: false,
  },
];

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function NotificationSettingsPage() {
  const [categories, setCategories] = useState<NotificationCategory[]>(initialCategories);
  const [isSaving, setIsSaving] = useState(false);

  const handleChange = useCallback(
    (id: string, channel: 'email' | 'push' | 'sms', value: boolean) => {
      setCategories((prev) =>
        prev.map((cat) =>
          cat.id === id ? { ...cat, [channel]: value } : cat
        )
      );
    },
    []
  );

  const handleSave = useCallback(async () => {
    setIsSaving(true);
    try {
      // TODO: Save to GraphQL API
      await new Promise((resolve) => setTimeout(resolve, 1000));
      console.log('Saving notification preferences:', categories);
    } catch (error) {
      console.error('Failed to save:', error);
    } finally {
      setIsSaving(false);
    }
  }, [categories]);

  const handleEnableAll = useCallback((channel: 'email' | 'push' | 'sms') => {
    setCategories((prev) =>
      prev.map((cat) => ({ ...cat, [channel]: true }))
    );
  }, []);

  const handleDisableAll = useCallback((channel: 'email' | 'push' | 'sms') => {
    setCategories((prev) =>
      prev.map((cat) => ({ ...cat, [channel]: false }))
    );
  }, []);

  return (
    <Box>
      <PageHeader
        title="Notifications"
        description="Manage how you receive notifications"
        breadcrumbs={[
          { label: 'Settings', href: '/settings' },
          { label: 'Notifications' },
        ]}
        actions={[
          {
            label: isSaving ? 'Saving...' : 'Save Changes',
            icon: <FloppyDisk style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: handleSave,
            disabled: isSaving,
          },
        ]}
      />

      {/* Channel Overview Cards */}
      <Box
        mb="6"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '16px',
        }}
      >
        {/* Email */}
        <Card
          style={{
            padding: '20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
          }}
        >
          <Flex align="center" gap="3" mb="3">
            <Box
              style={{
                width: 40,
                height: 40,
                borderRadius: '10px',
                background: 'rgba(59, 130, 246, 0.1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Mail style={{ width: 20, height: 20, color: '#3B82F6' }} />
            </Box>
            <Box>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                Email
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {categories.filter((c) => c.email).length} of {categories.length} enabled
              </Text>
            </Box>
          </Flex>
          <Flex gap="2">
            <Button
              variant="ghost"
              size="1"
              onClick={() => handleEnableAll('email')}
              style={{ color: 'var(--brand-500)', flex: 1 }}
            >
              Enable All
            </Button>
            <Button
              variant="ghost"
              size="1"
              onClick={() => handleDisableAll('email')}
              style={{ color: 'var(--content-muted)', flex: 1 }}
            >
              Disable All
            </Button>
          </Flex>
        </Card>

        {/* Push */}
        <Card
          style={{
            padding: '20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
          }}
        >
          <Flex align="center" gap="3" mb="3">
            <Box
              style={{
                width: 40,
                height: 40,
                borderRadius: '10px',
                background: 'rgba(16, 185, 129, 0.1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Bell style={{ width: 20, height: 20, color: '#10B981' }} />
            </Box>
            <Box>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                Push
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {categories.filter((c) => c.push).length} of {categories.length} enabled
              </Text>
            </Box>
          </Flex>
          <Flex gap="2">
            <Button
              variant="ghost"
              size="1"
              onClick={() => handleEnableAll('push')}
              style={{ color: 'var(--brand-500)', flex: 1 }}
            >
              Enable All
            </Button>
            <Button
              variant="ghost"
              size="1"
              onClick={() => handleDisableAll('push')}
              style={{ color: 'var(--content-muted)', flex: 1 }}
            >
              Disable All
            </Button>
          </Flex>
        </Card>

        {/* SMS */}
        <Card
          style={{
            padding: '20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
          }}
        >
          <Flex align="center" gap="3" mb="3">
            <Box
              style={{
                width: 40,
                height: 40,
                borderRadius: '10px',
                background: 'rgba(168, 85, 247, 0.1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <MessageText style={{ width: 20, height: 20, color: '#A855F7' }} />
            </Box>
            <Box>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                SMS
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {categories.filter((c) => c.sms).length} of {categories.length} enabled
              </Text>
            </Box>
          </Flex>
          <Flex gap="2">
            <Button
              variant="ghost"
              size="1"
              onClick={() => handleEnableAll('sms')}
              style={{ color: 'var(--brand-500)', flex: 1 }}
            >
              Enable All
            </Button>
            <Button
              variant="ghost"
              size="1"
              onClick={() => handleDisableAll('sms')}
              style={{ color: 'var(--content-muted)', flex: 1 }}
            >
              Disable All
            </Button>
          </Flex>
        </Card>
      </Box>

      {/* Notification Categories */}
      <Card
        style={{
          padding: '24px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Flex align="center" gap="2" mb="4">
          <Bell style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
          <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
            Notification Categories
          </Text>
        </Flex>

        {/* Header Row */}
        <Flex
          justify="between"
          align="center"
          py="3"
          style={{
            borderBottom: '2px solid var(--surface-border)',
          }}
        >
          <Text size="2" weight="medium" style={{ color: 'var(--content-secondary)', flex: 1, maxWidth: '400px' }}>
            Category
          </Text>
          <Flex gap="6" align="center">
            <Flex direction="column" align="center" style={{ width: 50 }}>
              <Mail style={{ width: 16, height: 16, color: '#3B82F6' }} />
              <Text size="1" style={{ color: 'var(--content-muted)' }}>Email</Text>
            </Flex>
            <Flex direction="column" align="center" style={{ width: 50 }}>
              <Bell style={{ width: 16, height: 16, color: '#10B981' }} />
              <Text size="1" style={{ color: 'var(--content-muted)' }}>Push</Text>
            </Flex>
            <Flex direction="column" align="center" style={{ width: 50 }}>
              <MessageText style={{ width: 16, height: 16, color: '#A855F7' }} />
              <Text size="1" style={{ color: 'var(--content-muted)' }}>SMS</Text>
            </Flex>
          </Flex>
        </Flex>

        {/* Category Rows */}
        {categories.map((category) => (
          <NotificationRow
            key={category.id}
            category={category}
            onChange={handleChange}
          />
        ))}
      </Card>

      {/* SMS Notice */}
      <Card
        mt="4"
        style={{
          padding: '16px 20px',
          background: 'rgba(168, 85, 247, 0.1)',
          border: '1px solid rgba(168, 85, 247, 0.2)',
          borderRadius: '12px',
        }}
      >
        <Flex align="center" gap="3">
          <SmartphoneDevice style={{ width: 20, height: 20, color: '#A855F7' }} />
          <Text size="2" style={{ color: 'var(--content-secondary)' }}>
            SMS notifications are sent to your registered phone number. Standard message rates may apply.
          </Text>
        </Flex>
      </Card>

      <style jsx global>{`
        @media (max-width: 640px) {
          .hidden-mobile {
            display: none !important;
          }
        }
      `}</style>
    </Box>
  );
}
