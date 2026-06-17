'use client';

/**
 * ValidationSummary Component
 *
 * Displays a summary of validation errors for a form.
 * Shows both form-level errors and field-level errors in a styled card.
 *
 * @example
 * ```tsx
 * <ValidationSummary
 *   errors={form.errors}
 *   formErrors={form.formErrors}
 *   fieldLabels={{ name: 'Organization Name', email: 'Business Email' }}
 * />
 * ```
 */

import { Box, Flex, Text, Card } from '@radix-ui/themes';
import { WarningTriangle, Check } from 'iconoir-react';

// =============================================================================
// TYPES
// =============================================================================

export interface ValidationSummaryProps {
  /** Field-level errors (field name -> error message) */
  errors?: Record<string, string>;
  /** Form-level errors (array of messages) */
  formErrors?: string[];
  /** Optional mapping of field names to display labels */
  fieldLabels?: Record<string, string>;
  /** Whether to show the summary (default: show if there are errors) */
  show?: boolean;
  /** Title for the summary card */
  title?: string;
  /** Custom styles */
  style?: React.CSSProperties;
}

export interface ValidationItemProps {
  /** Display label for the field */
  label: string;
  /** Whether the field is valid */
  isValid: boolean;
  /** Error message (shown if not valid) */
  errorMessage?: string;
}

// =============================================================================
// VALIDATION ITEM COMPONENT
// =============================================================================

export function ValidationItem({ label, isValid, errorMessage }: ValidationItemProps) {
  return (
    <Flex align="center" gap="2" py="1">
      {isValid ? (
        <Check style={{ width: 16, height: 16, color: 'var(--success-500)', flexShrink: 0 }} />
      ) : (
        <WarningTriangle style={{ width: 16, height: 16, color: 'var(--warning-500)', flexShrink: 0 }} />
      )}
      <Text size="2" style={{ color: isValid ? 'var(--content-tertiary)' : 'var(--warning-500)' }}>
        {label}
        {!isValid && errorMessage && (
          <span style={{ color: 'var(--content-tertiary)', marginLeft: '4px' }}>
            - {errorMessage}
          </span>
        )}
      </Text>
    </Flex>
  );
}

// =============================================================================
// VALIDATION SUMMARY COMPONENT
// =============================================================================

export function ValidationSummary({
  errors = {},
  formErrors = [],
  fieldLabels = {},
  show,
  title = 'Please complete all required fields',
  style,
}: ValidationSummaryProps) {
  const hasFieldErrors = Object.keys(errors).length > 0;
  const hasFormErrors = formErrors.length > 0;
  const hasAnyErrors = hasFieldErrors || hasFormErrors;

  // Don't show if no errors (unless explicitly shown)
  if (show === false || (show === undefined && !hasAnyErrors)) {
    return null;
  }

  return (
    <Card
      style={{
        padding: '20px',
        background: 'var(--warning-50)',
        border: '1px solid var(--warning-500)',
        borderRadius: 'var(--radius-lg)',
        marginBottom: '16px',
        ...style,
      }}
    >
      <Flex align="center" gap="3" mb="3">
        <WarningTriangle style={{ width: 20, height: 20, color: 'var(--warning-500)' }} />
        <Text size="2" weight="medium" style={{ color: 'var(--warning-600)' }}>
          {title}
        </Text>
      </Flex>

      <Box pl="7">
        {/* Form-level errors */}
        {formErrors.map((error, index) => (
          <ValidationItem
            key={`form-error-${index}`}
            label={error}
            isValid={false}
          />
        ))}

        {/* Field-level errors */}
        {Object.entries(errors).map(([field, errorMessage]) => (
          <ValidationItem
            key={field}
            label={fieldLabels[field] || field}
            isValid={false}
            errorMessage={errorMessage}
          />
        ))}
      </Box>
    </Card>
  );
}

// =============================================================================
// VALIDATION CHECKLIST COMPONENT
// =============================================================================

export interface ValidationChecklistProps {
  /** Validation rules to check */
  validations: Array<{
    field: string;
    label: string;
    isValid: boolean;
  }>;
  /** Title for the checklist */
  title?: string;
  /** Whether to show the checklist (default: show if any invalid) */
  show?: boolean;
  /** Custom styles */
  style?: React.CSSProperties;
}

/**
 * ValidationChecklist Component
 *
 * Shows a checklist of validation requirements with pass/fail indicators.
 * Useful for showing required fields status before submission.
 */
export function ValidationChecklist({
  validations,
  title = 'Please complete all required fields',
  show,
  style,
}: ValidationChecklistProps) {
  const allValid = validations.every((v) => v.isValid);
  const hasInvalid = validations.some((v) => !v.isValid);

  // Don't show if all valid (unless explicitly shown)
  if (show === false || (show === undefined && allValid)) {
    return null;
  }

  return (
    <Card
      style={{
        padding: '20px',
        background: hasInvalid ? 'var(--warning-50)' : 'var(--success-50)',
        border: hasInvalid
          ? '1px solid var(--warning-500)'
          : '1px solid var(--success-500)',
        borderRadius: 'var(--radius-lg)',
        marginBottom: '16px',
        ...style,
      }}
    >
      <Flex align="center" gap="3" mb="3">
        {hasInvalid ? (
          <WarningTriangle style={{ width: 20, height: 20, color: 'var(--warning-500)' }} />
        ) : (
          <Check style={{ width: 20, height: 20, color: 'var(--success-500)' }} />
        )}
        <Text
          size="2"
          weight="medium"
          style={{ color: hasInvalid ? 'var(--warning-600)' : 'var(--success-600)' }}
        >
          {hasInvalid ? title : 'All requirements met'}
        </Text>
      </Flex>

      <Box pl="7">
        {validations.map(({ field, label, isValid }) => (
          <ValidationItem key={field} label={label} isValid={isValid} />
        ))}
      </Box>
    </Card>
  );
}

export default ValidationSummary;
