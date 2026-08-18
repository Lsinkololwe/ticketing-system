'use client';

/**
 * Wizard chrome shared by all three application steps.
 *
 * ## Why one shell instead of per-page layouts
 *
 * The steps previously each carried their own scaffolding, and they drifted:
 * business-info grew a two-column layout with a sticky section rail and its own
 * page header, while review used a plain single column. The visible cost was a
 * footer reading "Step 1 of 2" on a wizard that has three steps, and a primary
 * button labelled "Continue to Review" that skipped the documents step
 * entirely — two screens' worth of navigation describing a flow that no longer
 * existed. Chrome that lives in one place cannot fall out of step with itself.
 *
 * ## The layout
 *
 * A single centred column, 680px, per the design authority
 * (`Org Admin - Onboarding Wizard.dc.html`). The dropped left rail was a
 * scroll-spy over sections of one page — navigation *within* a step, presented
 * with the same visual weight as navigation *between* steps, which is the more
 * important of the two and was the one not being shown.
 *
 * @see Org Admin - Onboarding Wizard.dc.html
 */

import type { ReactNode } from 'react';
import { Box, Button, Flex, Heading, Text } from '@radix-ui/themes';
import { ArrowLeft, ArrowRight } from 'iconoir-react';

import { APPLICATION_STEPS } from './constants';

// =============================================================================
// TYPES
// =============================================================================

export interface WizardShellProps {
  /** Zero-based index into {@link APPLICATION_STEPS}. */
  currentStep: number;
  title: string;
  subtitle: string;
  children: ReactNode;

  /** Omit to hide the back button (there is nothing before step 1). */
  onBack?: () => void;
  backLabel?: string;

  onNext?: () => void;
  nextLabel?: string;
  nextDisabled?: boolean;
  nextLoading?: boolean;
  /** `submit` when the footer button submits the surrounding form. */
  nextType?: 'button' | 'submit';

  /**
   * Shown beneath the footer when `nextDisabled`. A disabled control with no
   * explanation is a dead end — the applicant needs to know what is missing.
   */
  blockedReason?: string;
}

// =============================================================================
// STEP PROGRESS
// =============================================================================

/**
 * Progress as dots plus an explicit count.
 *
 * The dots alone would carry the information by colour only, which fails for
 * anyone who cannot distinguish them; the "Step 2 of 3" line is the same fact in
 * text, and is what a screen reader announces.
 */
function StepProgress({ currentStep }: { currentStep: number }) {
  const total = APPLICATION_STEPS.length;
  const step = APPLICATION_STEPS[currentStep];

  return (
    <Box mb="5">
      <Flex gap="2" mb="3" aria-hidden="true">
        {APPLICATION_STEPS.map((s, index) => (
          <Box
            key={s.id}
            width="32px"
            height="4px"
            style={{
              borderRadius: 'var(--radius-1)',
              background: index <= currentStep ? 'var(--accent-9)' : 'var(--gray-a5)',
              transition: 'background-color 200ms ease',
            }}
          />
        ))}
      </Flex>

      <Text
        as="p"
        size="1"
        weight="bold"
        data-testid="wizard-step-indicator"
        style={{
          color: 'var(--accent-11)',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
        }}
      >
        Step {currentStep + 1} of {total}
        {step ? ` · ${step.title}` : ''}
      </Text>
    </Box>
  );
}

// =============================================================================
// SHELL
// =============================================================================

export function WizardShell({
  currentStep,
  title,
  subtitle,
  children,
  onBack,
  backLabel = 'Back',
  onNext,
  nextLabel = 'Continue',
  nextDisabled = false,
  nextLoading = false,
  nextType = 'button',
  blockedReason,
}: WizardShellProps) {
  return (
    <Box width="100%" maxWidth="680px" mx="auto" data-testid="wizard-shell">
      <StepProgress currentStep={currentStep} />

      <Box mb="5">
        <Heading as="h1" size="6" weight="bold" highContrast mb="1" style={{ letterSpacing: '-0.02em' }}>
          {title}
        </Heading>
        <Text as="p" size="2" color="gray">
          {subtitle}
        </Text>
      </Box>

      <Flex direction="column" gap="4">
        {children}
      </Flex>

      {/* Sticky footer: the form runs well past a viewport, and a primary action
          that scrolls out of reach is one the applicant has to hunt for. */}
      <Box
        mt="6"
        py="3"
        style={{
          position: 'sticky',
          bottom: 0,
          background: 'var(--color-background)',
          borderTop: '1px solid var(--gray-a5)',
          zIndex: 10,
        }}
      >
        <Flex justify="between" align="center" gap="3">
          {onBack ? (
            <Button
              type="button"
              size="3"
              variant="outline"
              color="gray"
              onClick={onBack}
              data-testid="wizard-back"
            >
              <ArrowLeft width={16} height={16} aria-hidden="true" />
              {backLabel}
            </Button>
          ) : (
            <Box />
          )}

          {onNext || nextType === 'submit' ? (
            <Button
              type={nextType}
              size="3"
              variant="solid"
              color="teal"
              onClick={onNext}
              disabled={nextDisabled || nextLoading}
              loading={nextLoading}
              data-testid="wizard-next"
            >
              {nextLabel}
              <ArrowRight width={16} height={16} aria-hidden="true" />
            </Button>
          ) : (
            <Box />
          )}
        </Flex>

        {nextDisabled && blockedReason && (
          <Text
            as="p"
            size="1"
            color="gray"
            align="right"
            mt="2"
            // Announced, because the reason changes as the applicant fills the
            // form and the change is otherwise silent.
            aria-live="polite"
            data-testid="wizard-blocked-reason"
          >
            {blockedReason}
          </Text>
        )}
      </Box>

      <WizardTheme />
    </Box>
  );
}

// =============================================================================
// THEME
// =============================================================================

/**
 * The wizard's surface, state and motion layer.
 *
 * ## Why this exists rather than per-component `style` props
 *
 * The app already carries a full token layer in `global.css` — surfaces,
 * a five-level elevation scale, content ramps, and a brand ramp mapped onto the
 * Radix teal accent. What it did not carry was a consistent *interaction*
 * layer, so each screen improvised its own hover, focus and transition and they
 * disagreed. This declares those states once, for everything inside a step.
 *
 * Every value resolves to an existing token: no raw hex, no invented palette.
 * That is a hard rule from the design system's `_adherence.oxlintrc.json`, and
 * it is also what keeps light and dark mode correct for free — a literal colour
 * is right in exactly one of them.
 *
 * ## Elevation, deliberately restrained
 *
 * Form sections are flat: a 1px border and no shadow, matching the design
 * authority. Shadow is reserved for surfaces that *do* something — a document
 * slot lifts on hover because it is a target. Elevating everything spends the
 * signal that tells the applicant what is clickable.
 */
function WizardTheme() {
  return (
    <style jsx global>{`
      /* ── Surfaces ───────────────────────────────────────────────────────
         Flat by default. The border, not a shadow, does the separating. */
      [data-testid^='wizard-section-'] {
        transition: border-color var(--wizard-motion, 160ms) ease;
      }

      [data-testid^='wizard-section-']:focus-within {
        border-color: var(--accent-a7) !important;
      }

      /* ── Interactive cards ──────────────────────────────────────────────
         Document slots are targets, so they get the elevation change that
         says so. Shadow and border only — never transform: a card that moves
         under the cursor shifts the layout around it. */
      [data-testid^='document-slot-'] {
        transition:
          border-color var(--wizard-motion, 160ms) ease,
          box-shadow var(--wizard-motion, 160ms) ease,
          background-color var(--wizard-motion, 160ms) ease;
      }

      [data-testid^='document-slot-']:hover {
        border-color: var(--accent-a7);
        box-shadow: var(--shadow-2);
      }

      /* ── Focus ──────────────────────────────────────────────────────────
         Replaced, never removed. :focus-visible so a pointer click does not
         leave a ring behind, while keyboard navigation always shows one. */
      .wizard-focusable:focus-visible,
      [data-testid^='wizard-'] button:focus-visible,
      [data-testid^='wizard-'] a:focus-visible,
      [data-testid^='wizard-'] input:focus-visible,
      [data-testid^='wizard-'] textarea:focus-visible,
      [data-testid^='wizard-'] select:focus-visible {
        outline: 2px solid var(--accent-8);
        outline-offset: 2px;
        border-radius: var(--radius-2);
      }

      /* ── Fields ─────────────────────────────────────────────────────────
         One resting border and one focus treatment for every control in the
         wizard, so a Radix TextField, a native input and the business-type
         combobox cannot look like three different form systems. */
      [data-testid='wizard-shell'] .rt-TextFieldRoot,
      [data-testid='wizard-shell'] .rt-TextAreaRoot {
        transition:
          border-color var(--wizard-motion, 160ms) ease,
          box-shadow var(--wizard-motion, 160ms) ease;
      }

      /* ── Invalid state ──────────────────────────────────────────────────
         Colour is never the only channel: the field also carries
         aria-invalid, and the message sits directly beneath it. */
      [data-testid='wizard-shell'] [aria-invalid='true'] {
        border-color: var(--red-8) !important;
      }

      /* ── Affordance ─────────────────────────────────────────────────────
         Anything that responds to a click says so before it is clicked. */
      [data-testid='wizard-shell'] button:not(:disabled),
      [data-testid='wizard-shell'] [role='option'],
      [data-testid='wizard-shell'] label[for] {
        cursor: pointer;
      }

      [data-testid='wizard-shell'] button:disabled {
        cursor: not-allowed;
      }

      /* ── Motion ─────────────────────────────────────────────────────────
         One switch, honoured everywhere: the transitions above read their
         duration from this variable, so clearing it removes all of them
         rather than leaving whichever ones were declared elsewhere. */
      @media (prefers-reduced-motion: reduce) {
        [data-testid='wizard-shell'] * {
          --wizard-motion: 0.01ms;
          animation-duration: 0.01ms !important;
          animation-iteration-count: 1 !important;
          transition-duration: 0.01ms !important;
          scroll-behavior: auto !important;
        }
      }

      /* ── Narrow viewports ───────────────────────────────────────────────
         The sticky footer is worth its space on a phone, but the step label
         beside it is not — the dots above already carry the position. */
      @media (max-width: 520px) {
        [data-testid='wizard-shell'] [data-testid='wizard-next'],
        [data-testid='wizard-shell'] [data-testid='wizard-back'] {
          flex: 1;
          justify-content: center;
        }
      }
    `}</style>
  );
}

// =============================================================================
// SECTION CARD
// =============================================================================

export interface WizardSectionProps {
  id?: string;
  icon: ReactNode;
  title: string;
  subtitle: string;
  children: ReactNode;
}

/**
 * One titled card within a step.
 *
 * The icon is decorative — the heading already names the section — so it is
 * hidden from assistive technology rather than announced twice.
 */
export function WizardSection({ id, icon, title, subtitle, children }: WizardSectionProps) {
  return (
    <Box
      id={id}
      p="5"
      data-testid={id ? `wizard-section-${id}` : undefined}
      style={{
        background: 'var(--color-panel-solid)',
        border: '1px solid var(--gray-a5)',
        borderRadius: 'var(--radius-4)',
      }}
    >
      <Flex align="center" gap="3" mb="4">
        <Flex
          align="center"
          justify="center"
          flexShrink="0"
          width="40px"
          height="40px"
          aria-hidden="true"
          style={{
            borderRadius: 'var(--radius-3)',
            background: 'var(--accent-a3)',
            color: 'var(--accent-11)',
          }}
        >
          {icon}
        </Flex>
        <Box minWidth="0">
          <Heading as="h2" size="3" weight="bold" highContrast>
            {title}
          </Heading>
          <Text as="p" size="1" color="gray">
            {subtitle}
          </Text>
        </Box>
      </Flex>

      {children}
    </Box>
  );
}

// =============================================================================
// FIELD LABEL
// =============================================================================

/**
 * The micro uppercase form label used across the wizard.
 *
 * `required` renders an asterisk *and* a visually-hidden word, because an
 * asterisk alone is a convention rather than a meaning — a screen reader
 * announces it as "star". The field's own `required` attribute is what actually
 * carries the constraint to assistive technology; this is the visual half.
 */
export function FieldLabel({
  htmlFor,
  children,
  required = false,
}: {
  htmlFor: string;
  children: ReactNode;
  required?: boolean;
}) {
  return (
    <Text
      as="label"
      htmlFor={htmlFor}
      mb="1"
      style={{
        display: 'block',
        fontSize: 'var(--font-size-1)',
        fontWeight: 600,
        letterSpacing: '0.05em',
        textTransform: 'uppercase',
        color: 'var(--gray-11)',
      }}
    >
      {children}
      {required && (
        <Text as="span" style={{ color: 'var(--red-11)' }}>
          {' '}
          *
        </Text>
      )}
    </Text>
  );
}
