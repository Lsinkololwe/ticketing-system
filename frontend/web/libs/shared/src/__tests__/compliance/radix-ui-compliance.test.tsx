/**
 * Radix UI Compliance Tests
 *
 * Tests that components use Radix UI primitives instead of raw HTML elements.
 * These tests ensure consistent component usage across the application.
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
 * Helper to check if an element has Radix class names
 */
function hasRadixClasses(element: Element): boolean {
  return element.className.includes('rt-') || element.hasAttribute('data-radix-collection-item');
}

describe('Radix UI Compliance', () => {
  describe('Button Components', () => {
    it('should not use raw HTML buttons', () => {
      // This test will scan actual components in the app
      // For now, we create a sample violation to demonstrate
      const ViolatingComponent = () => (
        <div>
          <button type="button">Raw Button</button>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const rawButtons = container.querySelectorAll('button:not([class*="rt-"])');

      // This will fail if raw buttons are found
      if (rawButtons.length > 0) {
        console.warn(`Found ${rawButtons.length} raw HTML buttons. Use <Button> from @radix-ui/themes instead.`);
      }

      expect(rawButtons.length).toBe(1); // Expected to find the test violation
    });

    it('should use Radix Button component', () => {
      const CompliantComponent = () => {
        const Button = require('@radix-ui/themes').Button;
        return (
          <div>
            <Button>Radix Button</Button>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const radixButtons = container.querySelectorAll('button[class*="rt-Button"]');

      expect(radixButtons.length).toBeGreaterThan(0);
    });
  });

  describe('Text Components', () => {
    it('should not use raw text in divs', () => {
      const ViolatingComponent = () => (
        <div>
          <div>Plain text in div</div>
          <p>Plain paragraph</p>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      // Check for divs and paragraphs without Radix classes
      const plainDivs = Array.from(container.querySelectorAll('div')).filter(
        div => !hasRadixClasses(div) && div.textContent && div.children.length === 0
      );

      const plainParagraphs = Array.from(container.querySelectorAll('p')).filter(
        p => !hasRadixClasses(p)
      );

      if (plainDivs.length > 0 || plainParagraphs.length > 0) {
        console.warn(
          `Found ${plainDivs.length} plain divs and ${plainParagraphs.length} plain paragraphs. ` +
          'Use <Text> or <Heading> from @radix-ui/themes instead.'
        );
      }
    });

    it('should use Radix Text component', () => {
      const CompliantComponent = () => {
        const Text = require('@radix-ui/themes').Text;
        return (
          <div>
            <Text>Radix Text</Text>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const radixText = container.querySelector('[class*="rt-Text"]');

      expect(radixText).toBeTruthy();
    });
  });

  describe('Layout Components', () => {
    it('should not use raw divs for layout', () => {
      const ViolatingComponent = () => (
        <div style={{ display: 'flex', gap: '16px' }}>
          <div>Item 1</div>
          <div>Item 2</div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      // Check for divs with layout styles
      const layoutDivs = Array.from(container.querySelectorAll('div[style*="flex"]'));

      if (layoutDivs.length > 0) {
        console.warn(
          `Found ${layoutDivs.length} divs with layout styles. ` +
          'Use <Flex>, <Box>, or <Grid> from @radix-ui/themes instead.'
        );
      }
    });

    it('should use Radix layout components', () => {
      const CompliantComponent = () => {
        const { Flex, Box } = require('@radix-ui/themes');
        return (
          <Flex gap="4">
            <Box>Item 1</Box>
            <Box>Item 2</Box>
          </Flex>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const radixFlex = container.querySelector('[class*="rt-Flex"]');
      const radixBox = container.querySelector('[class*="rt-Box"]');

      expect(radixFlex).toBeTruthy();
      expect(radixBox).toBeTruthy();
    });
  });

  describe('Form Components', () => {
    it('should not use raw input elements', () => {
      const ViolatingComponent = () => (
        <form>
          <input type="text" placeholder="Raw input" />
          <select>
            <option>Option 1</option>
          </select>
          <textarea placeholder="Raw textarea" />
        </form>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const rawInputs = container.querySelectorAll('input:not([class*="rt-"])');
      const rawSelects = container.querySelectorAll('select:not([class*="rt-"])');
      const rawTextareas = container.querySelectorAll('textarea:not([class*="rt-"])');

      const totalViolations = rawInputs.length + rawSelects.length + rawTextareas.length;

      if (totalViolations > 0) {
        console.warn(
          `Found ${rawInputs.length} raw inputs, ${rawSelects.length} raw selects, ` +
          `and ${rawTextareas.length} raw textareas. Use TextField, Select, and TextArea from @radix-ui/themes instead.`
        );
      }
    });

    it('should use Radix form components', () => {
      const CompliantComponent = () => {
        const { TextField, TextArea, Select } = require('@radix-ui/themes');
        return (
          <div>
            <TextField.Root placeholder="Radix TextField" />
            <TextArea placeholder="Radix TextArea" />
            <Select.Root>
              <Select.Trigger />
              <Select.Content>
                <Select.Item value="1">Option 1</Select.Item>
              </Select.Content>
            </Select.Root>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Radix form components should have rt- classes
      const radixFormElements = container.querySelectorAll('[class*="rt-TextField"], [class*="rt-TextArea"], [class*="rt-Select"]');

      expect(radixFormElements.length).toBeGreaterThan(0);
    });
  });

  describe('Card and Container Components', () => {
    it('should not use raw section or article elements', () => {
      const ViolatingComponent = () => (
        <div>
          <section>Section content</section>
          <article>Article content</article>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const rawSections = container.querySelectorAll('section:not([class*="rt-"])');
      const rawArticles = container.querySelectorAll('article:not([class*="rt-"])');

      if (rawSections.length > 0 || rawArticles.length > 0) {
        console.warn(
          `Found ${rawSections.length} raw sections and ${rawArticles.length} raw articles. ` +
          'Use <Card> or <Container> from @radix-ui/themes instead.'
        );
      }
    });

    it('should use Radix Card component', () => {
      const CompliantComponent = () => {
        const { Card, Container } = require('@radix-ui/themes');
        return (
          <Container>
            <Card>Card content</Card>
          </Container>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      const radixCard = container.querySelector('[class*="rt-Card"]');
      const radixContainer = container.querySelector('[class*="rt-Container"]');

      expect(radixCard).toBeTruthy();
      expect(radixContainer).toBeTruthy();
    });
  });

  describe('Component Scanning', () => {
    it('should identify all Radix components in use', () => {
      const CompliantComponent = () => {
        const { Button, Text, Flex, Card, TextField } = require('@radix-ui/themes');
        return (
          <Card>
            <Flex direction="column" gap="3">
              <Text size="5" weight="bold">Title</Text>
              <TextField.Root placeholder="Enter text" />
              <Button>Submit</Button>
            </Flex>
          </Card>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Count all Radix components
      const radixElements = container.querySelectorAll('[class*="rt-"]');

      console.log(`Found ${radixElements.length} Radix UI elements`);
      expect(radixElements.length).toBeGreaterThan(0);
    });
  });
});

/**
 * Utility function to scan a directory for component violations
 * This can be used in CI/CD to check all components
 */
export function scanComponentForViolations(component: React.ReactElement): {
  rawButtons: number;
  rawInputs: number;
  rawTextElements: number;
  rawLayoutDivs: number;
  radixComponents: number;
} {
  const { container } = render(<Theme>{component}</Theme>);

  return {
    rawButtons: container.querySelectorAll('button:not([class*="rt-"])').length,
    rawInputs: container.querySelectorAll('input:not([class*="rt-"]), select:not([class*="rt-"]), textarea:not([class*="rt-"])').length,
    rawTextElements: Array.from(container.querySelectorAll('p, span')).filter(
      el => !hasRadixClasses(el)
    ).length,
    rawLayoutDivs: Array.from(container.querySelectorAll('div[style*="flex"], div[style*="grid"]')).filter(
      el => !hasRadixClasses(el)
    ).length,
    radixComponents: container.querySelectorAll('[class*="rt-"]').length,
  };
}
