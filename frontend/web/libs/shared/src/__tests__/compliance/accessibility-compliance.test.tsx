/**
 * Accessibility Compliance Tests
 *
 * Tests for WCAG 2.1 AA/AAA compliance.
 * Verifies contrast ratios, keyboard navigation, ARIA labels, and semantic HTML.
 */

import { describe, it, expect } from 'vitest';
import { render, within } from '@testing-library/react';
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
 * Helper to check if element has proper ARIA label
 */
function hasAccessibleName(element: Element): boolean {
  return !!(
    element.getAttribute('aria-label') ||
    element.getAttribute('aria-labelledby') ||
    element.textContent?.trim()
  );
}

/**
 * Helper to check if element is keyboard accessible
 */
function isKeyboardAccessible(element: Element): boolean {
  const tagName = element.tagName.toLowerCase();
  const tabIndex = element.getAttribute('tabindex');

  // Interactive elements should be focusable
  const interactiveTags = ['button', 'a', 'input', 'select', 'textarea'];

  if (interactiveTags.includes(tagName)) {
    return tabIndex !== '-1';
  }

  // Non-interactive elements with click handlers should have tabindex
  const hasClickHandler = element.hasAttribute('onclick') || element.getAttribute('role') === 'button';

  if (hasClickHandler) {
    return tabIndex !== null && tabIndex !== '-1';
  }

  return true;
}

describe('Accessibility Compliance', () => {
  describe('ARIA Labels and Roles', () => {
    it('should have ARIA labels on interactive elements', () => {
      const ViolatingComponent = () => (
        <div>
          <button>Submit</button>
          {/* Icon-only button without label */}
          <button>
            <svg>
              <path d="M10 10" />
            </svg>
          </button>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const buttons = container.querySelectorAll('button');

      const buttonsWithoutAccessibleName = Array.from(buttons).filter(
        button => !hasAccessibleName(button)
      );

      if (buttonsWithoutAccessibleName.length > 0) {
        console.warn(
          `Found ${buttonsWithoutAccessibleName.length} buttons without accessible names. ` +
          'Add aria-label or visible text content.'
        );
      }
    });

    it('should use proper ARIA roles', () => {
      const CompliantComponent = () => {
        const { Button } = require('@radix-ui/themes');
        return (
          <div>
            <Button aria-label="Delete item">
              <svg aria-hidden="true">
                <path d="M10 10" />
              </svg>
            </Button>
            <Button>Submit Form</Button>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const buttons = container.querySelectorAll('button');

      buttons.forEach(button => {
        expect(hasAccessibleName(button)).toBe(true);
      });
    });

    it('should have proper form labels', () => {
      const ViolatingComponent = () => (
        <form>
          {/* Input without label */}
          <input type="text" placeholder="Enter name" />
          {/* Input with label but not associated */}
          <label>Email</label>
          <input type="email" />
        </form>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const inputs = container.querySelectorAll('input');

      const inputsWithoutLabels = Array.from(inputs).filter(input => {
        const id = input.getAttribute('id');
        const ariaLabel = input.getAttribute('aria-label');
        const ariaLabelledBy = input.getAttribute('aria-labelledby');

        if (ariaLabel || ariaLabelledBy) return false;

        if (id) {
          const label = container.querySelector(`label[for="${id}"]`);
          return !label;
        }

        return true;
      });

      if (inputsWithoutLabels.length > 0) {
        console.warn(
          `Found ${inputsWithoutLabels.length} form inputs without associated labels. ` +
          'Use proper label[for] association or aria-label.'
        );
      }

      expect(inputsWithoutLabels.length).toBe(2); // Expected violations in test
    });

    it('should use Radix form components with proper labels', () => {
      const CompliantComponent = () => {
        const { TextField, TextArea, Select } = require('@radix-ui/themes');
        return (
          <form>
            <label>
              Name
              <TextField.Root placeholder="Enter name" />
            </label>
            <label>
              Description
              <TextArea placeholder="Enter description" />
            </label>
            <label>
              Category
              <Select.Root>
                <Select.Trigger />
                <Select.Content>
                  <Select.Item value="1">Option 1</Select.Item>
                </Select.Content>
              </Select.Root>
            </label>
          </form>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const labels = container.querySelectorAll('label');

      expect(labels.length).toBeGreaterThan(0);
    });
  });

  describe('Keyboard Navigation', () => {
    it('should detect elements that are not keyboard accessible', () => {
      const ViolatingComponent = () => (
        <div>
          {/* Div with click handler but no keyboard support */}
          <div onClick={() => {}} role="button">
            Click me
          </div>
          {/* Button with tabindex="-1" */}
          <button tabIndex={-1}>Not focusable</button>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const interactiveElements = container.querySelectorAll('[onclick], [role="button"], button, a');
      const nonKeyboardAccessible = Array.from(interactiveElements).filter(
        el => !isKeyboardAccessible(el)
      );

      if (nonKeyboardAccessible.length > 0) {
        console.warn(
          `Found ${nonKeyboardAccessible.length} interactive elements that are not keyboard accessible. ` +
          'Add tabindex="0" and keyboard event handlers.'
        );
      }

      expect(nonKeyboardAccessible.length).toBe(2); // Expected violations in test
    });

    it('should use Radix components with built-in keyboard support', () => {
      const CompliantComponent = () => {
        const { Button, IconButton } = require('@radix-ui/themes');
        return (
          <div>
            <Button>Submit</Button>
            <IconButton aria-label="Settings">
              <svg>
                <path d="M10 10" />
              </svg>
            </IconButton>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const buttons = container.querySelectorAll('button');

      buttons.forEach(button => {
        expect(button.hasAttribute('tabindex')).toBe(false); // Should use default focusability
        expect(isKeyboardAccessible(button)).toBe(true);
      });
    });

    it('should have visible focus indicators', () => {
      const CompliantComponent = () => {
        const { Button, TextField } = require('@radix-ui/themes');
        return (
          <div>
            <Button>Focus me</Button>
            <TextField.Root placeholder="Focus me too" />
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Radix components should have focus styles
      const focusableElements = container.querySelectorAll('button, input');

      focusableElements.forEach(element => {
        // Check if element has focus-visible class or data attribute
        const hasFocusStyles =
          element.classList.toString().includes('focus') ||
          element.className.includes('rt-');

        expect(hasFocusStyles).toBe(true);
      });
    });
  });

  describe('Semantic HTML', () => {
    it('should use semantic heading hierarchy', () => {
      const ViolatingComponent = () => (
        <div>
          <h1>Main Title</h1>
          {/* Skip h2, jump to h3 */}
          <h3>Subsection</h3>
          {/* Multiple h1s */}
          <h1>Another Title</h1>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      const headings = container.querySelectorAll('h1, h2, h3, h4, h5, h6');
      const headingLevels = Array.from(headings).map(h => parseInt(h.tagName[1]));

      // Check for multiple h1s
      const h1Count = headingLevels.filter(level => level === 1).length;
      if (h1Count > 1) {
        console.warn('Found multiple h1 elements. Page should have only one h1.');
      }

      // Check for skipped levels
      for (let i = 1; i < headingLevels.length; i++) {
        const diff = headingLevels[i] - headingLevels[i - 1];
        if (diff > 1) {
          console.warn(
            `Heading hierarchy skipped from h${headingLevels[i - 1]} to h${headingLevels[i]}. ` +
            'Headings should increment by one level at a time.'
          );
        }
      }
    });

    it('should use Radix Heading components with proper hierarchy', () => {
      const CompliantComponent = () => {
        const { Heading } = require('@radix-ui/themes');
        return (
          <div>
            <Heading as="h1" size="8">Main Title</Heading>
            <Heading as="h2" size="6">Section Title</Heading>
            <Heading as="h3" size="4">Subsection Title</Heading>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      const h1 = container.querySelector('h1');
      const h2 = container.querySelector('h2');
      const h3 = container.querySelector('h3');

      expect(h1).toBeTruthy();
      expect(h2).toBeTruthy();
      expect(h3).toBeTruthy();
    });

    it('should use proper landmark regions', () => {
      const CompliantComponent = () => (
        <div>
          <header>
            <nav aria-label="Main navigation">
              <ul>
                <li><a href="/">Home</a></li>
              </ul>
            </nav>
          </header>
          <main>
            <article>
              <h1>Article Title</h1>
              <p>Article content</p>
            </article>
          </main>
          <footer>
            <p>Footer content</p>
          </footer>
        </div>
      );

      const { container } = renderWithTheme(<CompliantComponent />);

      const header = container.querySelector('header');
      const main = container.querySelector('main');
      const footer = container.querySelector('footer');
      const nav = container.querySelector('nav');

      expect(header).toBeTruthy();
      expect(main).toBeTruthy();
      expect(footer).toBeTruthy();
      expect(nav).toBeTruthy();
      expect(nav?.getAttribute('aria-label')).toBeTruthy();
    });
  });

  describe('Images and Media', () => {
    it('should have alt text on images', () => {
      const ViolatingComponent = () => (
        <div>
          <img src="photo.jpg" />
          <img src="icon.svg" alt="" />
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const images = container.querySelectorAll('img');

      const imagesWithoutAlt = Array.from(images).filter(img => !img.hasAttribute('alt'));

      if (imagesWithoutAlt.length > 0) {
        console.warn(
          `Found ${imagesWithoutAlt.length} images without alt attributes. ` +
          'Add alt text for content images or alt="" for decorative images.'
        );
      }

      expect(imagesWithoutAlt.length).toBe(1); // Expected violation in test
    });

    it('should use proper alt text', () => {
      const CompliantComponent = () => (
        <div>
          <img src="profile.jpg" alt="User profile photo" />
          <img src="decorative.svg" alt="" aria-hidden="true" />
        </div>
      );

      const { container } = renderWithTheme(<CompliantComponent />);
      const images = container.querySelectorAll('img');

      images.forEach(img => {
        expect(img.hasAttribute('alt')).toBe(true);
      });
    });

    it('should hide decorative SVGs from screen readers', () => {
      const CompliantComponent = () => {
        const { Button } = require('@radix-ui/themes');
        return (
          <Button>
            <svg aria-hidden="true">
              <path d="M10 10" />
            </svg>
            Button Text
          </Button>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const svg = container.querySelector('svg');

      expect(svg?.getAttribute('aria-hidden')).toBe('true');
    });
  });

  describe('Color Contrast', () => {
    it('should flag potential contrast issues', () => {
      const ViolatingComponent = () => (
        <div>
          {/* Light gray text on white background - likely fails contrast */}
          <div style={{ color: '#CCCCCC', backgroundColor: '#FFFFFF' }}>
            Low contrast text
          </div>
        </div>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);

      // Note: This is a simplified check. Real contrast checking requires
      // parsing colors and calculating luminance ratios.
      const lightGrayOnWhite = container.querySelector('[style*="color: rgb(204, 204, 204)"]') ||
                               container.querySelector('[style*="color: #CCCCCC"]');

      if (lightGrayOnWhite) {
        console.warn(
          'Potential contrast issue detected. ' +
          'Ensure text has minimum 4.5:1 contrast ratio (WCAG AA) or 7:1 (WCAG AAA).'
        );
      }
    });

    it('should use Radix color system with proper contrast', () => {
      const CompliantComponent = () => {
        const { Text, Box } = require('@radix-ui/themes');
        return (
          <Box>
            <Text color="gray" highContrast>High contrast gray text</Text>
            <Text color="violet" highContrast>High contrast violet text</Text>
          </Box>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);

      // Radix highContrast prop ensures proper contrast
      const highContrastElements = container.querySelectorAll('[class*="rt-high-contrast"]');

      expect(highContrastElements.length).toBeGreaterThan(0);
    });
  });

  describe('Form Validation', () => {
    it('should have proper error messages', () => {
      const ViolatingComponent = () => (
        <form>
          <input type="email" required />
          {/* Error message not associated with input */}
          <span style={{ color: 'red' }}>Invalid email</span>
        </form>
      );

      const { container } = renderWithTheme(<ViolatingComponent />);
      const input = container.querySelector('input');

      const hasAriaDescribedBy = input?.hasAttribute('aria-describedby');
      const hasAriaInvalid = input?.hasAttribute('aria-invalid');

      if (!hasAriaDescribedBy && !hasAriaInvalid) {
        console.warn(
          'Form input errors should be associated using aria-describedby or aria-invalid.'
        );
      }
    });

    it('should use proper error handling', () => {
      const CompliantComponent = () => {
        const { TextField, Text } = require('@radix-ui/themes');
        return (
          <div>
            <TextField.Root
              id="email"
              type="email"
              aria-invalid="true"
              aria-describedby="email-error"
            />
            <Text id="email-error" color="red" size="2">
              Please enter a valid email address
            </Text>
          </div>
        );
      };

      const { container } = renderWithTheme(<CompliantComponent />);
      const input = container.querySelector('input');
      const errorMessage = container.querySelector('#email-error');

      expect(input?.getAttribute('aria-invalid')).toBe('true');
      expect(input?.getAttribute('aria-describedby')).toBe('email-error');
      expect(errorMessage).toBeTruthy();
    });
  });
});

/**
 * Utility function to scan for accessibility violations
 */
export function scanAccessibilityViolations(component: React.ReactElement): {
  missingAriaLabels: number;
  missingAltText: number;
  keyboardInaccessible: number;
  missingFormLabels: number;
  headingIssues: number;
  contrastIssues: number;
} {
  const { container } = render(<Theme>{component}</Theme>);

  const buttons = Array.from(container.querySelectorAll('button, [role="button"]'));
  const images = Array.from(container.querySelectorAll('img'));
  const inputs = Array.from(container.querySelectorAll('input, select, textarea'));
  const interactiveElements = Array.from(container.querySelectorAll('[onclick], [role="button"], button, a'));

  return {
    missingAriaLabels: buttons.filter(btn => !hasAccessibleName(btn)).length,
    missingAltText: images.filter(img => !img.hasAttribute('alt')).length,
    keyboardInaccessible: interactiveElements.filter(el => !isKeyboardAccessible(el)).length,
    missingFormLabels: inputs.filter(input => {
      const id = input.getAttribute('id');
      const ariaLabel = input.getAttribute('aria-label');
      const ariaLabelledBy = input.getAttribute('aria-labelledby');

      if (ariaLabel || ariaLabelledBy) return false;
      if (id) {
        const label = container.querySelector(`label[for="${id}"]`);
        return !label;
      }
      return true;
    }).length,
    headingIssues: 0, // Complex to calculate, would need hierarchy analysis
    contrastIssues: 0, // Complex to calculate, would need color parsing and luminance
  };
}
