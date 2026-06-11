/**
 * ESLint Rule: no-hardcoded-colors
 *
 * Detects hardcoded color values and suggests using Radix UI color tokens.
 */

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow hardcoded color values',
      category: 'Best Practices',
      recommended: true,
    },
    fixable: null,
    schema: [],
    messages: {
      noHexColors: 'Avoid hardcoded hex colors ({{value}}). Use Radix UI color prop instead (e.g., color="violet").',
      noRgbColors: 'Avoid hardcoded RGB colors ({{value}}). Use Radix UI color prop instead (e.g., color="blue").',
      noHslColors: 'Avoid hardcoded HSL colors ({{value}}). Use Radix UI color prop instead (e.g., color="red").',
      noNamedColors: 'Avoid named color values ({{value}}). Use Radix UI color prop instead (e.g., color="gray").',
    },
  },

  create(context) {
    /**
     * Check if a string value contains a color
     */
    function containsColor(value) {
      // Hex colors
      const hexPattern = /#[0-9A-Fa-f]{3,8}/;
      if (hexPattern.test(value)) {
        return { type: 'hex', value: value.match(hexPattern)[0] };
      }

      // RGB/RGBA colors
      const rgbPattern = /rgba?\([^)]+\)/;
      if (rgbPattern.test(value)) {
        return { type: 'rgb', value: value.match(rgbPattern)[0] };
      }

      // HSL/HSLA colors
      const hslPattern = /hsla?\([^)]+\)/;
      if (hslPattern.test(value)) {
        return { type: 'hsl', value: value.match(hslPattern)[0] };
      }

      // Named colors (common ones)
      const namedColors = [
        'black', 'white', 'red', 'blue', 'green', 'yellow', 'purple', 'orange',
        'pink', 'gray', 'grey', 'brown', 'cyan', 'magenta', 'lime', 'navy',
        'teal', 'aqua', 'maroon', 'olive', 'silver', 'gold'
      ];

      const lowerValue = value.toLowerCase().trim();
      if (namedColors.includes(lowerValue)) {
        return { type: 'named', value: lowerValue };
      }

      return null;
    }

    /**
     * Get the value of a literal or template literal
     */
    function getLiteralValue(node) {
      if (node.type === 'Literal') {
        return node.value;
      }
      if (node.type === 'TemplateLiteral') {
        // Simple template literals without expressions
        if (node.expressions.length === 0) {
          return node.quasis[0].value.cooked;
        }
      }
      return null;
    }

    /**
     * Check style object properties
     */
    function checkStyleProperty(property) {
      if (property.type === 'Property' && property.key.type === 'Identifier') {
        const styleName = property.key.name;

        // Color-related properties
        const colorProps = ['color', 'backgroundColor', 'borderColor', 'outlineColor'];

        if (colorProps.includes(styleName)) {
          const value = getLiteralValue(property.value);

          if (value) {
            const color = containsColor(value);

            if (color) {
              const messageId = {
                'hex': 'noHexColors',
                'rgb': 'noRgbColors',
                'hsl': 'noHslColors',
                'named': 'noNamedColors',
              }[color.type];

              context.report({
                node: property,
                messageId,
                data: {
                  value: color.value,
                },
              });
            }
          }
        }
      }
    }

    return {
      // Check inline style objects
      JSXAttribute(node) {
        if (node.name.name === 'style' && node.value) {
          if (node.value.type === 'JSXExpressionContainer') {
            const expression = node.value.expression;

            if (expression.type === 'ObjectExpression') {
              expression.properties.forEach(checkStyleProperty);
            }
          }
        }
      },

      // Check className with hardcoded colors in template literals
      JSXAttribute(node) {
        if (node.name.name === 'className' && node.value) {
          if (node.value.type === 'JSXExpressionContainer') {
            const expression = node.value.expression;

            if (expression.type === 'TemplateLiteral') {
              const value = getLiteralValue(expression);

              if (value) {
                const color = containsColor(value);

                if (color) {
                  context.report({
                    node: expression,
                    message: 'Avoid hardcoded colors in className. Use Radix UI color utilities instead.',
                  });
                }
              }
            }
          }
        }
      },

      // Check CSS-in-JS style objects
      ObjectExpression(node) {
        // Check if this object looks like a style object
        const parent = node.parent;

        // Check for styled-components or emotion patterns
        if (parent && parent.type === 'TaggedTemplateExpression') {
          return; // Skip for now, these are more complex
        }

        // Check object properties for color values
        node.properties.forEach(checkStyleProperty);
      },
    };
  },
};
