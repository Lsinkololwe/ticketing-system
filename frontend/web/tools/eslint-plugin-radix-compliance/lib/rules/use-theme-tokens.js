/**
 * ESLint Rule: use-theme-tokens
 *
 * Requires using Radix UI theme tokens for spacing, sizing, and typography.
 */

module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Require Radix UI theme tokens for spacing, sizing, and typography',
      category: 'Best Practices',
      recommended: true,
    },
    fixable: null,
    schema: [],
    messages: {
      useSpacingToken: 'Use Radix UI spacing props instead of hardcoded values (e.g., p="4" instead of padding: "{{value}}").',
      useSizeToken: 'Use Radix UI size prop instead of hardcoded values (e.g., size="3").',
      useFontSizeToken: 'Use Radix UI Text/Heading size prop instead of hardcoded font-size.',
      useWeightToken: 'Use Radix UI weight prop instead of hardcoded font-weight.',
    },
  },

  create(context) {
    /**
     * Check if a value is a hardcoded pixel or rem value
     */
    function isHardcodedValue(value) {
      if (typeof value !== 'string') return false;

      // Match pixel values (e.g., "16px", "1.5rem", "2em")
      const hardcodedPattern = /^\d+(\.\d+)?(px|rem|em)$/;
      return hardcodedPattern.test(value.trim());
    }

    /**
     * Get the value from a node
     */
    function getNodeValue(node) {
      if (node.type === 'Literal') {
        return node.value;
      }
      if (node.type === 'TemplateLiteral' && node.expressions.length === 0) {
        return node.quasis[0].value.cooked;
      }
      return null;
    }

    /**
     * Check spacing-related style properties
     */
    function checkSpacingProperty(property) {
      if (property.type === 'Property' && property.key.type === 'Identifier') {
        const styleName = property.key.name;

        const spacingProps = [
          'padding', 'margin', 'gap',
          'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
          'marginTop', 'marginRight', 'marginBottom', 'marginLeft',
          'rowGap', 'columnGap'
        ];

        if (spacingProps.includes(styleName)) {
          const value = getNodeValue(property.value);

          if (value && isHardcodedValue(value)) {
            context.report({
              node: property,
              messageId: 'useSpacingToken',
              data: {
                value: value,
              },
            });
          }
        }
      }
    }

    /**
     * Check size-related style properties
     */
    function checkSizeProperty(property) {
      if (property.type === 'Property' && property.key.type === 'Identifier') {
        const styleName = property.key.name;

        const sizeProps = ['width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight'];

        if (sizeProps.includes(styleName)) {
          const value = getNodeValue(property.value);

          if (value && isHardcodedValue(value)) {
            context.report({
              node: property,
              messageId: 'useSizeToken',
              data: {
                value: value,
              },
            });
          }
        }
      }
    }

    /**
     * Check typography-related style properties
     */
    function checkTypographyProperty(property) {
      if (property.type === 'Property' && property.key.type === 'Identifier') {
        const styleName = property.key.name;

        // Font size
        if (styleName === 'fontSize') {
          const value = getNodeValue(property.value);

          if (value && isHardcodedValue(value)) {
            context.report({
              node: property,
              messageId: 'useFontSizeToken',
            });
          }
        }

        // Font weight
        if (styleName === 'fontWeight') {
          const value = getNodeValue(property.value);

          // Check for numeric weights (400, 500, 600, 700) or string weights
          if (typeof value === 'number' || (typeof value === 'string' && !['inherit', 'initial', 'unset'].includes(value))) {
            context.report({
              node: property,
              messageId: 'useWeightToken',
            });
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
              expression.properties.forEach(property => {
                checkSpacingProperty(property);
                checkSizeProperty(property);
                checkTypographyProperty(property);
              });
            }
          }
        }
      },

      // Check CSS-in-JS style objects
      ObjectExpression(node) {
        // Check if this looks like a style object
        const parent = node.parent;

        // Skip tagged template expressions for now
        if (parent && parent.type === 'TaggedTemplateExpression') {
          return;
        }

        // Check object properties
        node.properties.forEach(property => {
          checkSpacingProperty(property);
          checkSizeProperty(property);
          checkTypographyProperty(property);
        });
      },

      // Check for hardcoded size values in component props
      JSXAttribute(node) {
        const propName = node.name.name;

        // Check width/height props with hardcoded values
        if (['width', 'height'].includes(propName) && node.value) {
          if (node.value.type === 'Literal') {
            const value = node.value.value;

            if (typeof value === 'string' && isHardcodedValue(value)) {
              context.report({
                node: node,
                messageId: 'useSizeToken',
                data: {
                  value: value,
                },
              });
            }
          }
        }
      },
    };
  },
};
