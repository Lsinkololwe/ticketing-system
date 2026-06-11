/**
 * ESLint Plugin: radix-compliance
 *
 * Custom ESLint plugin to enforce Radix UI and theming standards.
 */

const noInlineStyles = require('./rules/no-inline-styles');
const requireRadixComponents = require('./rules/require-radix-components');
const noHardcodedColors = require('./rules/no-hardcoded-colors');
const useThemeTokens = require('./rules/use-theme-tokens');

module.exports = {
  rules: {
    'no-inline-styles': noInlineStyles,
    'require-radix-components': requireRadixComponents,
    'no-hardcoded-colors': noHardcodedColors,
    'use-theme-tokens': useThemeTokens,
  },
  configs: {
    recommended: {
      plugins: ['radix-compliance'],
      rules: {
        'radix-compliance/no-inline-styles': 'error',
        'radix-compliance/require-radix-components': 'warn',
        'radix-compliance/no-hardcoded-colors': 'error',
        'radix-compliance/use-theme-tokens': 'warn',
      },
    },
    strict: {
      plugins: ['radix-compliance'],
      rules: {
        'radix-compliance/no-inline-styles': 'error',
        'radix-compliance/require-radix-components': 'error',
        'radix-compliance/no-hardcoded-colors': 'error',
        'radix-compliance/use-theme-tokens': 'error',
      },
    },
  },
};
