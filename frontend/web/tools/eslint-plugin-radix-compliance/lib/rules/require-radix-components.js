/**
 * ESLint Rule: require-radix-components
 *
 * Requires using Radix UI components instead of raw HTML elements.
 */

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Require Radix UI components instead of raw HTML elements',
      category: 'Best Practices',
      recommended: true,
    },
    fixable: null,
    schema: [],
    messages: {
      useRadixButton: 'Use <Button> from @radix-ui/themes instead of raw <button> element.',
      useRadixInput: 'Use <TextField> from @radix-ui/themes instead of raw <input> element.',
      useRadixTextArea: 'Use <TextArea> from @radix-ui/themes instead of raw <textarea> element.',
      useRadixSelect: 'Use <Select> from @radix-ui/themes instead of raw <select> element.',
      useRadixText: 'Use <Text> or <Heading> from @radix-ui/themes instead of <p> or <span> for text content.',
      useRadixLayout: 'Use <Flex>, <Box>, or <Grid> from @radix-ui/themes instead of raw <div> for layout.',
      useRadixCard: 'Use <Card> or <Container> from @radix-ui/themes instead of <section> or <article>.',
    },
  },

  create(context) {
    /**
     * Check if a JSX element has Radix UI classes or props
     */
    function hasRadixProps(node) {
      if (!node.attributes) return false;

      return node.attributes.some(attr => {
        if (attr.type === 'JSXAttribute') {
          // Check for className with "rt-" prefix
          if (attr.name.name === 'className' && attr.value) {
            if (attr.value.type === 'Literal') {
              return attr.value.value.includes('rt-');
            }
            if (attr.value.type === 'JSXExpressionContainer') {
              // This is a more complex case with dynamic classNames
              // We'll assume it might be valid
              return true;
            }
          }

          // Check for Radix-specific props
          const radixProps = ['size', 'variant', 'color', 'highContrast', 'weight'];
          if (radixProps.includes(attr.name.name)) {
            return true;
          }
        }

        return false;
      });
    }

    /**
     * Check if element has layout-related inline styles
     */
    function hasLayoutStyles(node) {
      if (!node.attributes) return false;

      return node.attributes.some(attr => {
        if (attr.type === 'JSXAttribute' && attr.name.name === 'style') {
          if (attr.value && attr.value.type === 'JSXExpressionContainer') {
            const expression = attr.value.expression;
            if (expression.type === 'ObjectExpression') {
              return expression.properties.some(prop => {
                if (prop.type === 'Property' && prop.key.type === 'Identifier') {
                  const layoutProps = ['display', 'flexDirection', 'justifyContent', 'alignItems', 'gap', 'gridTemplateColumns'];
                  return layoutProps.includes(prop.key.name);
                }
                return false;
              });
            }
          }
        }
        return false;
      });
    }

    /**
     * Check if div has text content (should use Text component)
     */
    function hasTextContent(node) {
      if (!node.children) return false;

      return node.children.some(child => {
        return child.type === 'JSXText' && child.value.trim().length > 0;
      });
    }

    return {
      JSXElement(node) {
        const elementName = node.openingElement.name.name;

        // Check for raw button elements
        if (elementName === 'button' && !hasRadixProps(node.openingElement)) {
          context.report({
            node: node,
            messageId: 'useRadixButton',
          });
        }

        // Check for raw input elements
        if (elementName === 'input' && !hasRadixProps(node.openingElement)) {
          context.report({
            node: node,
            messageId: 'useRadixInput',
          });
        }

        // Check for raw textarea elements
        if (elementName === 'textarea' && !hasRadixProps(node.openingElement)) {
          context.report({
            node: node,
            messageId: 'useRadixTextArea',
          });
        }

        // Check for raw select elements
        if (elementName === 'select' && !hasRadixProps(node.openingElement)) {
          context.report({
            node: node,
            messageId: 'useRadixSelect',
          });
        }

        // Check for raw text elements (p, span)
        if (['p', 'span'].includes(elementName) && !hasRadixProps(node.openingElement)) {
          // Allow if it's wrapping other components
          const hasOnlyText = node.children.every(child =>
            child.type === 'JSXText' || (child.type === 'JSXExpressionContainer' && child.expression.type !== 'JSXElement')
          );

          if (hasOnlyText) {
            context.report({
              node: node,
              messageId: 'useRadixText',
            });
          }
        }

        // Check for raw div elements used for layout
        if (elementName === 'div' && !hasRadixProps(node.openingElement)) {
          if (hasLayoutStyles(node.openingElement) || hasTextContent(node)) {
            context.report({
              node: node,
              messageId: 'useRadixLayout',
            });
          }
        }

        // Check for raw section/article elements
        if (['section', 'article'].includes(elementName) && !hasRadixProps(node.openingElement)) {
          context.report({
            node: node,
            messageId: 'useRadixCard',
          });
        }
      },
    };
  },
};
