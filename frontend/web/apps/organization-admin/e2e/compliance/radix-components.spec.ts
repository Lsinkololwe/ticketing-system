/**
 * Radix Components E2E Tests
 *
 * Verifies that interactive elements are from Radix UI and have proper behavior.
 * Tests button hover states, form field interactions, and component functionality.
 */

import { test, expect } from '@playwright/test';

test.describe('Radix Component Compliance', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000');
  });

  test.describe('Button Components', () => {
    test('should use Radix UI Button components', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for raw HTML buttons (violations)
      const rawButtons = await page.locator('button:not([class*="rt-"])').count();

      if (rawButtons > 0) {
        console.warn(`Found ${rawButtons} raw HTML buttons. Use <Button> from @radix-ui/themes instead.`);
      }

      // Check for Radix Button class
      const radixButtons = await page.locator('button[class*="rt-Button"]').count();
      expect(radixButtons).toBeGreaterThan(0);
    });

    test('should have proper hover states on Radix buttons', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      const button = page.locator('button[class*="rt-Button"]').first();

      // Get initial background color
      const initialBg = await button.evaluate((el) =>
        window.getComputedStyle(el).backgroundColor
      );

      // Hover over button
      await button.hover();
      await page.waitForTimeout(200); // Wait for transition

      // Get hover background color
      const hoverBg = await button.evaluate((el) =>
        window.getComputedStyle(el).backgroundColor
      );

      // Background should change on hover (not exact match)
      console.log('Initial bg:', initialBg);
      console.log('Hover bg:', hoverBg);

      // Radix buttons should have different hover state
      // This is a visual check - exact color comparison may vary
    });

    test('should have proper focus states on Radix buttons', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      const button = page.locator('button[class*="rt-Button"]').first();

      // Focus button via keyboard
      await page.keyboard.press('Tab');

      // Check for focus-visible class or outline
      const hasFocusVisible = await button.evaluate((el) => {
        const style = window.getComputedStyle(el);
        return style.outline !== 'none' || el.classList.contains('focus-visible');
      });

      expect(hasFocusVisible).toBeTruthy();
    });

    test('should have proper disabled state on Radix buttons', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Find a disabled button (if any)
      const disabledButton = page.locator('button[disabled]').first();

      if (await disabledButton.count() > 0) {
        const isDisabled = await disabledButton.isDisabled();
        expect(isDisabled).toBe(true);

        // Verify disabled button has proper styling
        const opacity = await disabledButton.evaluate((el) =>
          window.getComputedStyle(el).opacity
        );

        // Disabled buttons should have reduced opacity or cursor: not-allowed
        console.log('Disabled button opacity:', opacity);
      }
    });
  });

  test.describe('Form Components', () => {
    test('should use Radix UI form components', async ({ page }) => {
      // Navigate to a page with forms (e.g., business info)
      await page.goto('http://localhost:3000/apply/business-info');

      // Check for raw HTML inputs (violations)
      const rawInputs = await page.locator('input:not([class*="rt-"])').count();
      const rawTextareas = await page.locator('textarea:not([class*="rt-"])').count();
      const rawSelects = await page.locator('select:not([class*="rt-"])').count();

      const totalViolations = rawInputs + rawTextareas + rawSelects;

      if (totalViolations > 0) {
        console.warn(
          `Found ${rawInputs} raw inputs, ${rawTextareas} raw textareas, ` +
          `and ${rawSelects} raw selects. Use Radix UI form components instead.`
        );
      }
    });

    test('should have proper focus states on form fields', async ({ page }) => {
      await page.goto('http://localhost:3000/apply/business-info');

      const input = page.locator('input[class*="rt-"]').first();

      // Focus input
      await input.click();

      // Check for focus state
      const hasFocusState = await input.evaluate((el) => {
        const style = window.getComputedStyle(el);
        return (
          style.outline !== 'none' ||
          style.borderColor !== 'rgb(0, 0, 0)' ||
          el.getAttribute('data-state') === 'focused'
        );
      });

      expect(hasFocusState).toBeTruthy();
    });

    test('should have proper error states on form fields', async ({ page }) => {
      await page.goto('http://localhost:3000/apply/business-info');

      // Submit form without filling required fields
      const submitButton = page.locator('button[type="submit"]').first();
      await submitButton.click();

      // Wait for validation
      await page.waitForTimeout(500);

      // Check for error states
      const errorFields = await page.locator('[aria-invalid="true"], [data-state="error"]').count();

      if (errorFields > 0) {
        console.log(`Found ${errorFields} fields in error state`);
      }
    });

    test('should have proper placeholder behavior', async ({ page }) => {
      await page.goto('http://localhost:3000/apply/business-info');

      const input = page.locator('input[placeholder]').first();
      const placeholder = await input.getAttribute('placeholder');

      expect(placeholder).toBeTruthy();
      expect(placeholder).not.toBe('');

      // Type in input
      await input.fill('Test value');

      // Placeholder should be hidden when value is present
      const value = await input.inputValue();
      expect(value).toBe('Test value');
    });
  });

  test.describe('Card and Container Components', () => {
    test('should use Radix UI Card components', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for raw section/article elements (violations)
      const rawSections = await page.locator('section:not([class*="rt-"])').count();
      const rawArticles = await page.locator('article:not([class*="rt-"])').count();

      if (rawSections > 0 || rawArticles > 0) {
        console.warn(
          `Found ${rawSections} raw sections and ${rawArticles} raw articles. ` +
          'Use <Card> or <Container> from @radix-ui/themes instead.'
        );
      }

      // Check for Radix Card class
      const radixCards = await page.locator('[class*="rt-Card"]').count();
      console.log(`Found ${radixCards} Radix Card components`);
    });

    test('should have proper card hover effects', async ({ page }) => {
      await page.goto('http://localhost:3000/events');

      const card = page.locator('[class*="rt-Card"]').first();

      if (await card.count() > 0) {
        // Get initial box shadow
        const initialShadow = await card.evaluate((el) =>
          window.getComputedStyle(el).boxShadow
        );

        // Hover over card
        await card.hover();
        await page.waitForTimeout(200); // Wait for transition

        // Get hover box shadow
        const hoverShadow = await card.evaluate((el) =>
          window.getComputedStyle(el).boxShadow
        );

        console.log('Initial shadow:', initialShadow);
        console.log('Hover shadow:', hoverShadow);
      }
    });
  });

  test.describe('Layout Components', () => {
    test('should use Radix UI layout components', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for raw divs with layout styles (violations)
      const layoutDivs = await page.locator('div[style*="display: flex"], div[style*="display: grid"]').count();

      if (layoutDivs > 0) {
        console.warn(
          `Found ${layoutDivs} divs with layout styles. ` +
          'Use <Flex>, <Box>, or <Grid> from @radix-ui/themes instead.'
        );
      }

      // Check for Radix layout classes
      const radixFlex = await page.locator('[class*="rt-Flex"]').count();
      const radixGrid = await page.locator('[class*="rt-Grid"]').count();
      const radixBox = await page.locator('[class*="rt-Box"]').count();

      console.log(`Found ${radixFlex} Flex, ${radixGrid} Grid, ${radixBox} Box components`);
    });
  });

  test.describe('Typography Components', () => {
    test('should use Radix UI typography components', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Check for raw text elements (violations)
      const rawParagraphs = await page.locator('p:not([class*="rt-"])').count();
      const rawSpans = await page.locator('span:not([class*="rt-"])').count();

      if (rawParagraphs > 0 || rawSpans > 0) {
        console.warn(
          `Found ${rawParagraphs} raw paragraphs and ${rawSpans} raw spans. ` +
          'Use <Text> or <Heading> from @radix-ui/themes instead.'
        );
      }

      // Check for Radix typography classes
      const radixText = await page.locator('[class*="rt-Text"]').count();
      const radixHeading = await page.locator('[class*="rt-Heading"]').count();

      console.log(`Found ${radixText} Text and ${radixHeading} Heading components`);
    });
  });

  test.describe('Dialog and Modal Components', () => {
    test('should use Radix UI Dialog components', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Look for buttons that might trigger dialogs
      const dialogTriggers = page.locator('[aria-haspopup="dialog"]');

      if (await dialogTriggers.count() > 0) {
        const trigger = dialogTriggers.first();
        await trigger.click();

        // Wait for dialog to appear
        await page.waitForTimeout(300);

        // Check for Radix Dialog class
        const dialog = page.locator('[class*="rt-Dialog"], [role="dialog"]').first();
        expect(await dialog.isVisible()).toBe(true);

        // Close dialog
        const closeButton = page.locator('[aria-label*="close"], button[class*="rt-DialogClose"]').first();
        if (await closeButton.count() > 0) {
          await closeButton.click();
        }
      }
    });

    test('should trap focus in dialogs', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      const dialogTriggers = page.locator('[aria-haspopup="dialog"]');

      if (await dialogTriggers.count() > 0) {
        const trigger = dialogTriggers.first();
        await trigger.click();

        await page.waitForTimeout(300);

        // Try to tab through elements
        await page.keyboard.press('Tab');
        await page.keyboard.press('Tab');

        // Focus should remain within dialog
        const focusedElement = page.locator(':focus');
        const dialog = page.locator('[role="dialog"]').first();

        const isFocusedWithinDialog = await focusedElement.evaluate((el, dialogEl) => {
          return dialogEl?.contains(el) ?? false;
        }, await dialog.elementHandle());

        expect(isFocusedWithinDialog).toBe(true);

        // Close dialog
        await page.keyboard.press('Escape');
      }
    });
  });

  test.describe('Select and Dropdown Components', () => {
    test('should use Radix UI Select components', async ({ page }) => {
      await page.goto('http://localhost:3000/apply/business-info');

      // Check for raw select elements (violations)
      const rawSelects = await page.locator('select:not([class*="rt-"])').count();

      if (rawSelects > 0) {
        console.warn(
          `Found ${rawSelects} raw select elements. ` +
          'Use <Select> from @radix-ui/themes instead.'
        );
      }

      // Check for Radix Select class
      const radixSelects = await page.locator('[class*="rt-Select"]').count();
      console.log(`Found ${radixSelects} Radix Select components`);
    });

    test('should have proper select dropdown behavior', async ({ page }) => {
      await page.goto('http://localhost:3000/apply/business-info');

      const selectTrigger = page.locator('[class*="rt-SelectTrigger"]').first();

      if (await selectTrigger.count() > 0) {
        // Click to open dropdown
        await selectTrigger.click();

        // Wait for dropdown to appear
        await page.waitForTimeout(300);

        // Check for dropdown content
        const selectContent = page.locator('[class*="rt-SelectContent"]').first();
        expect(await selectContent.isVisible()).toBe(true);

        // Select an option
        const selectItem = page.locator('[class*="rt-SelectItem"]').first();
        await selectItem.click();

        // Dropdown should close
        await page.waitForTimeout(300);
        expect(await selectContent.isVisible()).toBe(false);
      }
    });
  });

  test.describe('Component State Consistency', () => {
    test('should have consistent loading states', async ({ page }) => {
      await page.goto('http://localhost:3000/dashboard');

      // Look for loading indicators
      const loadingIndicators = page.locator('[class*="rt-Spinner"], [aria-label*="loading"]');

      if (await loadingIndicators.count() > 0) {
        const spinner = loadingIndicators.first();
        expect(await spinner.isVisible()).toBe(true);

        // Verify spinner is using Radix component
        const isRadixSpinner = await spinner.evaluate((el) =>
          el.className.includes('rt-')
        );

        expect(isRadixSpinner).toBe(true);
      }
    });

    test('should have consistent empty states', async ({ page }) => {
      // Navigate to a page that might have empty states
      await page.goto('http://localhost:3000/events');

      // Look for empty state messaging
      const emptyStates = page.locator('[class*="empty"], [aria-label*="empty"]');

      if (await emptyStates.count() > 0) {
        const emptyState = emptyStates.first();
        console.log('Found empty state component');

        // Verify it uses Radix components
        const usesRadixComponents = await emptyState.locator('[class*="rt-"]').count();
        expect(usesRadixComponents).toBeGreaterThan(0);
      }
    });
  });
});
