/**
 * Compliance Testing Utilities
 *
 * Export all compliance test utilities for use across the application.
 */

export { scanComponentForViolations } from './radix-ui-compliance.test';
export { scanThemeViolations } from './theme-compliance.test';
export { scanAccessibilityViolations } from './accessibility-compliance.test';

/**
 * Combined compliance scan
 */
export function runComplianceScan(component: React.ReactElement) {
  const { scanComponentForViolations } = require('./radix-ui-compliance.test');
  const { scanThemeViolations } = require('./theme-compliance.test');
  const { scanAccessibilityViolations } = require('./accessibility-compliance.test');

  const radixViolations = scanComponentForViolations(component);
  const themeViolations = scanThemeViolations(component);
  const a11yViolations = scanAccessibilityViolations(component);

  return {
    radix: radixViolations,
    theme: themeViolations,
    accessibility: a11yViolations,
    totalViolations:
      radixViolations.rawButtons +
      radixViolations.rawInputs +
      radixViolations.rawTextElements +
      radixViolations.rawLayoutDivs +
      themeViolations.totalInlineStyles +
      a11yViolations.missingAriaLabels +
      a11yViolations.missingAltText +
      a11yViolations.keyboardInaccessible +
      a11yViolations.missingFormLabels,
  };
}
