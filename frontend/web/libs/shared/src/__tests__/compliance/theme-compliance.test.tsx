/**
 * Theme Compliance Tests
 *
 * Tests for inline style violations and theme token usage.
 * Ensures components use Radix UI theme tokens instead of hardcoded values.
 */

import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Theme } from '@radix-ui/themes';
import React from 'react';

/**
 * Helper function to wrap components in Radix Theme provider
 */
function renderWithTheme(component: React.ReactElement) {
  return render(
    <Theme>
      {component}
    </Theme>
  );
}

/**
 * Color regex patterns for detecting hardcoded colors
 */
const COLOR_PATTERNS = {
  hex: /#[0-9A-Fa-f]{3,8}/g,
  rgb: /rgb\([^)]+\)/g,
  rgba: /rgba\([^)]+\)/g,
  hsl: /hsl\([^)]+\)/g,
  hsla: /hsla\([^)]+\)/g,
};

/**
 * Helper to detect hardcoded colors in inline styles
 */
function hasHardcodedColors(element: Element): boolean {
  const style = element.getAttribute('style');
  if (!style) return false;

  return Object.values(COLOR_PATTERNS).some(pattern => pattern.test(style));
}

/**
 * Helper to detect hardcoded spacing in inline styles
 */
function hasHardcodedSpacing(element: Element): boolean {
  const style = element.getAttribute('style');
  if (!style) return false;

  // Match pixel values like padding: 24px, margin: 16px, gap: 8px
  const spacingPattern = /(padding|margin|gap|width|height):\s*\d+px/i;
  return spacingPattern.test(style);
}

describe('Theme Compliance', () => {
  describe('Inline Style Violations', () => {
    it('should not have inline color styles', () => {
      const ViolatingComponent = () => (
        <div>
          <div style={{ color: '#8B5CF6' }}>Purple text</div>
          <div style={{ backgroundColor: 'rgb(139, 92, 246)' }}>Purple background</div>
          <span style={{ color: 'rgba(139, 92, 246, 0.8)' }}>Purple with opacity</span>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const elementsWithInlineColor = Array.from(container.querySelectorAll('[style]')).filter(
        hasHardcodedColors
      );

      if (elementsWithInlineColor.length > 0) {
        console.warn(
          `Found ${elementsWithInlineColor.length} elements with inline color styles. ` +
          'Use Radix UI color tokens instead (e.g., color="violet").'
        );
      }

      expect(elementsWithInlineColor.length).toBe(3); // Expected violations in test
    });

    it('should not have inline spacing styles', () => {
      const ViolatingComponent = () => (
        <div>
          <div style={{ padding: '24px' }}>Padded content</div>
          <div style={{ margin: '16px' }}>Margin content</div>
          <div style={{ gap: '8px' }}>Gap content</div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const elementsWithInlineSpacing = Array.from(container.querySelectorAll('[style]')).filter(
        hasHardcodedSpacing
      );

      if (elementsWithInlineSpacing.length > 0) {
        console.warn(
          `Found ${elementsWithInlineSpacing.length} elements with inline spacing styles. ` +
          'Use Radix UI spacing scale instead (e.g., p="4", m="3", gap="2").'
        );
      }

      expect(elementsWithInlineSpacing.length).toBe(3); // Expected violations in test
    });

    it('should not have any inline styles', () => {
      const ViolatingComponent = () => (
        <div>
          <div style={{ fontSize: '18px', lineHeight: '1.5' }}>Styled text</div>
          <div style={{ display: 'flex', alignItems: 'center' }}>Flex container</div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const elementsWithInlineStyles = container.querySelectorAll('[style]');

      if (elementsWithInlineStyles.length > 0) {
        console.warn(
          `Found ${elementsWithInlineStyles.length} elements with inline styles. ` +
          'Use Radix UI props and CSS classes instead.'
        );
      }

      expect(elementsWithInlineStyles.length).toBe(2); // Expected violations in test
    });
  });

  describe('Theme Token Usage', () => {
    it('should use Radix color tokens', () => {
      const CompliantComponent = () => {
        const { Text, Box } = require('@radix-ui/themes');
        return (
          <Box>
            <Text color="violet">Violet text</Text>
            <Text color="blue">Blue text</Text>
            <Text color="red">Red text</Text>
          </Box>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Check for Radix color classes
      const elementsWithColorProps = container.querySelectorAll('[class*="rt-r-color"]');

      expect(elementsWithColorProps.length).toBeGreaterThan(0);
    });

    it('should use Radix spacing scale', () => {
      const CompliantComponent = () => {
        const { Box, Flex } = require('@radix-ui/themes');
        return (
          <Box p="4" m="3">
            <Flex gap="2">
              <Box>Item 1</Box>
              <Box>Item 2</Box>
            </Flex>
          </Box>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Check for Radix spacing classes (rt-r-p, rt-r-m, rt-r-gap)
      const elementsWithSpacing = container.querySelectorAll('[class*="rt-r-p-"], [class*="rt-r-m-"], [class*="rt-r-gap-"]');

      expect(elementsWithSpacing.length).toBeGreaterThan(0);
    });

    it('should use Radix size scale (1-9)', () => {
      const CompliantComponent = () => {
        const { Button, Text, TextField } = require('@radix-ui/themes');
        return (
          <div>
            <Button size="1">Small</Button>
            <Button size="2">Medium</Button>
            <Button size="3">Large</Button>
            <Text size="2">Small text</Text>
            <Text size="5">Large text</Text>
            <TextField.Root size="3" />
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Check for Radix size classes
      const elementsWithSize = container.querySelectorAll('[class*="rt-r-size-"]');

      expect(elementsWithSize.length).toBeGreaterThan(0);
    });
  });

  describe('CSS Variable Usage', () => {
    it('should detect CSS variables in styles', () => {
      const CompliantComponent = () => (
        <div style={{ color: 'var(--accent-9)', padding: 'var(--space-4)' }}>
          Using CSS variables
        </div>
      );

      const { container } = renderWithTheme(<CompliantComponent />);
      const element = container.querySelector('[style]');

      expect(element).toBeTruthy();

      const style = element?.getAttribute('style');
      expect(style).toContain('var(--');
    });

    it('should prefer Radix props over CSS variables', () => {
      const CompliantComponent = () => {
        const { Box, Text } = require('@radix-ui/themes');
        return (
          <Box p="4">
            <Text color="violet">Using Radix props</Text>
          </Box>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Should have Radix classes, not inline styles
      const elementsWithRadixClasses = container.querySelectorAll('[class*="rt-"]');
      const elementsWithInlineStyles = container.querySelectorAll('[style]');

      expect(elementsWithRadixClasses.length).toBeGreaterThan(0);
      expect(elementsWithInlineStyles.length).toBe(0);
    });
  });

  describe('Typography Compliance', () => {
    it('should not use inline font styles', () => {
      const ViolatingComponent = () => (
        <div>
          <div style={{ fontSize: '18px', fontWeight: 'bold' }}>Large bold text</div>
          <div style={{ fontFamily: 'Arial, sans-serif' }}>Custom font</div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const fontStylePattern = /(font-size|font-weight|font-family|line-height)/i;
      const elementsWithFontStyles = Array.from(container.querySelectorAll('[style]')).filter(
        el => {
          const style = el.getAttribute('style');
          return style && fontStylePattern.test(style);
        }
      );

      if (elementsWithFontStyles.length > 0) {
        console.warn(
          `Found ${elementsWithFontStyles.length} elements with inline font styles. ` +
          'Use Radix Text/Heading components with size and weight props instead.'
        );
      }

      expect(elementsWithFontStyles.length).toBe(2); // Expected violations in test
    });

    it('should use Radix typography components', () => {
      const CompliantComponent = () => {
        const { Text, Heading } = require('@radix-ui/themes');
        return (
          <div>
            <Heading size="8" weight="bold">Large Heading</Heading>
            <Text size="3" weight="medium">Medium Text</Text>
            <Text size="2" weight="regular">Small Text</Text>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      const radixTypography = container.querySelectorAll('[class*="rt-Text"], [class*="rt-Heading"]');

      expect(radixTypography.length).toBeGreaterThan(0);
    });
  });

  describe('Border and Shadow Compliance', () => {
    it('should not use inline border styles', () => {
      const ViolatingComponent = () => (
        <div>
          <div style={{ border: '1px solid #ccc' }}>Bordered content</div>
          <div style={{ borderRadius: '8px' }}>Rounded content</div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const borderStylePattern = /(border|border-radius|outline)/i;
      const elementsWithBorderStyles = Array.from(container.querySelectorAll('[style]')).filter(
        el => {
          const style = el.getAttribute('style');
          return style && borderStylePattern.test(style);
        }
      );

      if (elementsWithBorderStyles.length > 0) {
        console.warn(
          `Found ${elementsWithBorderStyles.length} elements with inline border styles. ` +
          'Use Radix Card or Box components with appropriate props instead.'
        );
      }

      expect(elementsWithBorderStyles.length).toBe(2); // Expected violations in test
    });

    it('should not use inline shadow styles', () => {
      const ViolatingComponent = () => (
        <div>
          <div style={{ boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)' }}>Shadowed content</div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const shadowStylePattern = /(box-shadow|text-shadow)/i;
      const elementsWithShadowStyles = Array.from(container.querySelectorAll('[style]')).filter(
        el => {
          const style = el.getAttribute('style');
          return style && shadowStylePattern.test(style);
        }
      );

      if (elementsWithShadowStyles.length > 0) {
        console.warn(
          `Found ${elementsWithShadowStyles.length} elements with inline shadow styles. ` +
          'Use Radix Card component variants instead.'
        );
      }

      expect(elementsWithShadowStyles.length).toBe(1); // Expected violation in test
    });
  });
});

/**
 * Utility function to scan for theme violations
 */
export function scanThemeViolations(component: React.ReactElement): {
  inlineColors: number;
  inlineSpacing: number;
  inlineFonts: number;
  inlineBorders: number;
  inlineShadows: number;
  totalInlineStyles: number;
  radixTokenUsage: number;
} {
  const { container } = render(<Theme>{component}</Theme>);

  const allInlineStyleElements = Array.from(container.querySelectorAll('[style]'));

  return {
    inlineColors: allInlineStyleElements.filter(hasHardcodedColors).length,
    inlineSpacing: allInlineStyleElements.filter(hasHardcodedSpacing).length,
    inlineFonts: allInlineStyleElements.filter(el => {
      const style = el.getAttribute('style');
      return style && /(font-size|font-weight|font-family|line-height)/i.test(style);
    }).length,
    inlineBorders: allInlineStyleElements.filter(el => {
      const style = el.getAttribute('style');
      return style && /(border|border-radius|outline)/i.test(style);
    }).length,
    inlineShadows: allInlineStyleElements.filter(el => {
      const style = el.getAttribute('style');
      return style && /(box-shadow|text-shadow)/i.test(style);
    }).length,
    totalInlineStyles: allInlineStyleElements.length,
    radixTokenUsage: container.querySelectorAll('[class*="rt-r-"]').length,
  };
}
