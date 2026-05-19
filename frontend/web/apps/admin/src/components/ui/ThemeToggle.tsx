'use client';

/**
 * Theme Toggle Components
 *
 * Provides UI controls for switching between light, dark, and system themes.
 * Uses next-themes for state management and persistence.
 *
 * Components:
 * - ThemeToggleButton: Compact button for header (cycles through themes)
 * - ThemeToggleDropdown: Dropdown menu with all options
 * - ThemeSelector: Full selector for settings page
 */

import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';
import {
  Box,
  Flex,
  Text,
  Button,
  DropdownMenu,
  SegmentedControl,
  RadioGroup,
} from '@radix-ui/themes';
import { SunLight, HalfMoon, Computer } from 'iconoir-react';

// =============================================================================
// TYPES
// =============================================================================

type ThemeValue = 'light' | 'dark' | 'system';

interface ThemeOption {
  value: ThemeValue;
  label: string;
  icon: React.ReactNode;
  description: string;
}

// =============================================================================
// CONSTANTS
// =============================================================================

const THEME_OPTIONS: ThemeOption[] = [
  {
    value: 'light',
    label: 'Light',
    icon: <SunLight width={16} height={16} />,
    description: 'Always use light mode',
  },
  {
    value: 'dark',
    label: 'Dark',
    icon: <HalfMoon width={16} height={16} />,
    description: 'Always use dark mode',
  },
  {
    value: 'system',
    label: 'System',
    icon: <Computer width={16} height={16} />,
    description: 'Follow system preference',
  },
];

// =============================================================================
// HELPER HOOK
// =============================================================================

/**
 * Hook to safely access theme on client side
 * Prevents hydration mismatch by returning undefined during SSR
 */
function useThemeSafe() {
  const { theme, setTheme, resolvedTheme, systemTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  return {
    theme: mounted ? (theme as ThemeValue) : undefined,
    setTheme,
    resolvedTheme: mounted ? (resolvedTheme as 'light' | 'dark') : undefined,
    systemTheme: mounted ? (systemTheme as 'light' | 'dark') : undefined,
    mounted,
  };
}

// =============================================================================
// THEME TOGGLE BUTTON (Compact - for Header)
// =============================================================================

/**
 * Compact theme toggle button that cycles through themes
 * Shows current resolved theme icon
 */
export function ThemeToggleButton() {
  const { theme, setTheme, resolvedTheme, mounted } = useThemeSafe();

  // Cycle through themes: light -> dark -> system -> light
  const cycleTheme = () => {
    if (theme === 'light') setTheme('dark');
    else if (theme === 'dark') setTheme('system');
    else setTheme('light');
  };

  // Show placeholder during SSR
  if (!mounted) {
    return (
      <Button variant="ghost" size="2" style={{ width: 36, height: 36 }}>
        <Box style={{ width: 18, height: 18 }} />
      </Button>
    );
  }

  // Get icon based on current theme setting
  const getIcon = () => {
    if (theme === 'system') return <Computer style={{ width: 18, height: 18 }} />;
    if (resolvedTheme === 'dark') return <HalfMoon style={{ width: 18, height: 18 }} />;
    return <SunLight style={{ width: 18, height: 18 }} />;
  };

  return (
    <Button
      variant="ghost"
      size="2"
      onClick={cycleTheme}
      title={`Current: ${theme} (${resolvedTheme})`}
      style={{ width: 36, height: 36 }}
    >
      {getIcon()}
    </Button>
  );
}

// =============================================================================
// THEME TOGGLE DROPDOWN (for Header with options)
// =============================================================================

/**
 * Dropdown menu for theme selection
 * Shows all three options with icons
 */
export function ThemeToggleDropdown() {
  const { theme, setTheme, resolvedTheme, mounted } = useThemeSafe();

  // Show placeholder during SSR
  if (!mounted) {
    return (
      <Button variant="ghost" size="2">
        <Box style={{ width: 18, height: 18 }} />
      </Button>
    );
  }

  const currentIcon = resolvedTheme === 'dark'
    ? <HalfMoon style={{ width: 18, height: 18 }} />
    : <SunLight style={{ width: 18, height: 18 }} />;

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger>
        <Button variant="ghost" size="2">
          {currentIcon}
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Content align="end">
        <DropdownMenu.Label>Theme</DropdownMenu.Label>
        {THEME_OPTIONS.map((option) => (
          <DropdownMenu.Item
            key={option.value}
            onClick={() => setTheme(option.value)}
          >
            <Flex align="center" gap="2">
              {option.icon}
              <Text>{option.label}</Text>
              {theme === option.value && (
                <Text size="1" color="gray" ml="auto">
                  Active
                </Text>
              )}
            </Flex>
          </DropdownMenu.Item>
        ))}
      </DropdownMenu.Content>
    </DropdownMenu.Root>
  );
}

// =============================================================================
// THEME SEGMENTED CONTROL (Compact inline selector)
// =============================================================================

/**
 * Segmented control for theme selection
 * Compact inline style, good for settings cards
 */
export function ThemeSegmentedControl() {
  const { theme, setTheme, mounted } = useThemeSafe();

  if (!mounted) {
    return <Box style={{ height: 32 }} />;
  }

  return (
    <SegmentedControl.Root
      value={theme || 'system'}
      onValueChange={(value) => setTheme(value)}
    >
      {THEME_OPTIONS.map((option) => (
        <SegmentedControl.Item key={option.value} value={option.value}>
          <Flex align="center" gap="1">
            {option.icon}
            <Text size="1">{option.label}</Text>
          </Flex>
        </SegmentedControl.Item>
      ))}
    </SegmentedControl.Root>
  );
}

// =============================================================================
// THEME SELECTOR (Full - for Settings Page)
// =============================================================================

/**
 * Full theme selector with radio buttons
 * Shows descriptions for each option
 */
export function ThemeSelector() {
  const { theme, setTheme, resolvedTheme, systemTheme, mounted } = useThemeSafe();

  if (!mounted) {
    return (
      <Box style={{ height: 200 }}>
        <Text color="gray" size="2">Loading theme preferences...</Text>
      </Box>
    );
  }

  return (
    <Flex direction="column" gap="4">
      <RadioGroup.Root
        value={theme || 'system'}
        onValueChange={(value) => setTheme(value)}
      >
        <Flex direction="column" gap="3">
          {THEME_OPTIONS.map((option) => (
            <Box
              key={option.value}
              p="4"
              style={{
                backgroundColor: theme === option.value ? 'var(--violet-a3)' : 'var(--gray-a2)',
                borderRadius: 'var(--radius-3)',
                border: theme === option.value
                  ? '1px solid var(--violet-7)'
                  : '1px solid var(--gray-a4)',
                cursor: 'pointer',
                transition: 'all 150ms ease',
              }}
              onClick={() => setTheme(option.value)}
            >
              <Flex align="center" gap="3">
                <RadioGroup.Item value={option.value} />
                <Box
                  style={{
                    padding: '8px',
                    borderRadius: 'var(--radius-2)',
                    backgroundColor: theme === option.value
                      ? 'var(--violet-a4)'
                      : 'var(--gray-a3)',
                  }}
                >
                  {option.icon}
                </Box>
                <Flex direction="column" gap="1">
                  <Text weight="medium">{option.label}</Text>
                  <Text size="1" color="gray">{option.description}</Text>
                </Flex>
              </Flex>
            </Box>
          ))}
        </Flex>
      </RadioGroup.Root>

      {/* Current theme info */}
      <Box
        p="3"
        style={{
          backgroundColor: 'var(--gray-a2)',
          borderRadius: 'var(--radius-2)',
        }}
      >
        <Flex direction="column" gap="1">
          <Text size="1" color="gray">
            Current setting: <Text weight="medium">{theme}</Text>
          </Text>
          <Text size="1" color="gray">
            Active appearance: <Text weight="medium">{resolvedTheme}</Text>
          </Text>
          {theme === 'system' && (
            <Text size="1" color="gray">
              System preference: <Text weight="medium">{systemTheme}</Text>
            </Text>
          )}
        </Flex>
      </Box>
    </Flex>
  );
}

// =============================================================================
// EXPORTS
// =============================================================================

export { useThemeSafe };
export type { ThemeValue, ThemeOption };
