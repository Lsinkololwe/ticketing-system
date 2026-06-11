/**
 * ESLint Rule: no-inline-styles
 *
 * Detects inline style props and suggests using Radix UI props instead.
 */

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow inline style props',
      category: 'Best Practices',
      recommended: true,
    },
    fixable: null,
    schema: [],
    messages: {
      noInlineStyles: 'Avoid inline styles. Use Radix UI props instead (e.g., p="4", m="3", color="violet").',
      noInlineColor: 'Avoid inline color styles. Use Radix UI color prop instead (e.g., color="violet").',
      noInlineSpacing: 'Avoid inline spacing styles. Use Radix UI spacing props instead (e.g., p="4", m="3", gap="2").',
      noInlineFonts: 'Avoid inline font styles. Use Radix UI Text/Heading components with size and weight props.',
    },
  },

  create(context) {
    return {
      JSXAttribute(node) {
        // Check if attribute is "style"
        if (node.name.name !== 'style') {
          return;
        }

        // Get the style value
        const styleValue = node.value;

        // Check if it's an object expression
        if (styleValue && styleValue.type === 'JSXExpressionContainer') {
          const expression = styleValue.expression;

          if (expression.type === 'ObjectExpression') {
            const properties = expression.properties;

            // Check each style property
            properties.forEach(property => {
              if (property.type === 'Property' && property.key.type === 'Identifier') {
                const styleName = property.key.name;

                // Check for color-related styles
                if (['color', 'backgroundColor', 'borderColor'].includes(styleName)) {
                  context.report({
                    node: property,
                    messageId: 'noInlineColor',
                  });
                }

                // Check for spacing-related styles
                if (['padding', 'margin', 'gap', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
                     'marginTop', 'marginRight', 'marginBottom', 'marginLeft'].includes(styleName)) {
                  context.report({
                    node: property,
                    messageId: 'noInlineSpacing',
                  });
                }

                // Check for font-related styles
                if (['fontSize', 'fontWeight', 'fontFamily', 'lineHeight'].includes(styleName)) {
                  context.report({
                    node: property,
                    messageId: 'noInlineFonts',
                  });
                }
              }
            });

            // Report generic warning for any inline styles
            if (properties.length > 0) {
              context.report({
                node: node,
                messageId: 'noInlineStyles',
              });
            }
          }
        }
      },
    };
  },
};
