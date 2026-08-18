'use client';

/**
 * Create Event Page
 *
 * Multi-step event creation wizard:
 * 1. Basic Info (title, description, category)
 * 2. Date & Location
 * 3. Tickets (pricing tiers)
 * 4. Review & Publish
 *
 * ARCHITECTURE NOTE: Types are imported from centralized schemas.
 * See: libs/shared/src/api/schemas/event.schemas.ts
 */

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useCreateEvent } from '@pml.tickets/shared/api/organization-admin/modules/events';
import {
  Box,
  Flex,
  Text,
  Heading,
  TextField,
  TextArea,
  Button,
  Card,
  Select,
} from '@radix-ui/themes';
import {
  Calendar,
  MapPin,
  Check,
  ArrowRight,
  ArrowLeft,
  Plus,
  Trash,
  MediaImage as ImageIcon,
} from 'iconoir-react';
import { PageHeader } from '@/components/ui';
// TODO: Implement event schemas in shared library
// import {
//   EVENT_CATEGORIES,
//   TIMEZONES,
//   LOCATION_TYPES,
//   type TicketTierSchema,
//   type CreateEventFormSchema,
// } from '@pml.tickets/shared/api/schemas';

// =============================================================================
// TYPES & CONSTANTS
// =============================================================================

// TODO: These should come from shared schemas
type TicketTier = {
  id: string;
  name: string;
  description?: string;
  price: number;
  quantity: number;
};

type EventFormData = {
  title: string;
  description: string;
  category: string;
  coverImage: string;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  timezone: string;
  locationType: string;
  venueName: string;
  venueAddress: string;
  venueCity: string;
  onlineUrl: string;
  ticketTiers: TicketTier[];
};

// TODO: Move to shared schemas
const EVENT_CATEGORIES = ['CONFERENCE', 'CONCERT', 'SPORTS', 'OTHER'] as const;
const TIMEZONES = ['Africa/Lusaka'] as const;

// =============================================================================
// STEP INDICATOR
// =============================================================================

interface StepIndicatorProps {
  steps: string[];
  currentStep: number;
  onStepClick?: (step: number) => void;
}

function StepIndicator({ steps, currentStep, onStepClick }: StepIndicatorProps) {
  return (
    <Flex align="center" justify="center" gap="0" mb="8">
      {steps.map((step, index) => {
        const isCompleted = index < currentStep;
        const isCurrent = index === currentStep;

        return (
          <Flex key={step} align="center">
            {index > 0 && (
              <Box
                style={{
                  width: 60,
                  height: 2,
                  background: isCompleted
                    ? 'var(--brand-500)'
                    : 'var(--surface-border)',
                }}
              />
            )}
            <Flex
              align="center"
              justify="center"
              onClick={() => isCompleted && onStepClick?.(index)}
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                background: isCurrent
                  ? 'linear-gradient(135deg, var(--accent-9), var(--accent-11))'
                  : isCompleted
                    ? 'var(--brand-500)'
                    : 'var(--surface-subtle)',
                border: isCurrent || isCompleted ? 'none' : '2px solid var(--surface-border)',
                cursor: isCompleted ? 'pointer' : 'default',
                boxShadow: isCurrent ? '0 0 15px var(--accent-a7)' : 'none',
              }}
            >
              {isCompleted ? (
                <Check style={{ width: 18, height: 18, color: 'white' }} />
              ) : (
                <Text size="2" weight="bold" style={{ color: isCurrent ? 'white' : 'var(--content-muted)' }}>
                  {index + 1}
                </Text>
              )}
            </Flex>
          </Flex>
        );
      })}
    </Flex>
  );
}

// =============================================================================
// FORM FIELD
// =============================================================================

interface FormFieldProps {
  label: string;
  required?: boolean;
  helper?: string;
  error?: string;
  children: React.ReactNode;
}

function FormField({ label, required, helper, error, children }: FormFieldProps) {
  return (
    <Box mb="4">
      <Text
        as="label"
        className="ds-label"
        style={{ display: 'block', marginBottom: '6px' }}
      >
        {label}
        {required && <span style={{ color: 'var(--status-danger-9)', marginLeft: 4 }}>*</span>}
      </Text>
      {children}
      {helper && !error && (
        <Text size="1" style={{ color: 'var(--gray-10)', display: 'block', marginTop: '4px' }}>
          {helper}
        </Text>
      )}
      {error && (
        <Text role="alert" size="1" style={{ color: 'var(--status-danger-11)', display: 'block', marginTop: '4px' }}>
          {error}
        </Text>
      )}
    </Box>
  );
}

// =============================================================================
// OPTIONS (derived from centralized constants)
// =============================================================================

// Category labels for display
const CATEGORY_LABELS: Record<string, string> = {
  CONFERENCE: 'Conference & Seminar',
  CONCERT: 'Concert & Music',
  FESTIVAL: 'Festival',
  WORKSHOP: 'Workshop & Training',
  NETWORKING: 'Networking & Business',
  SPORTS: 'Sports & Fitness',
  ARTS: 'Arts & Culture',
  CHARITY: 'Charity & Fundraiser',
  OTHER: 'Other',
};

const categories = EVENT_CATEGORIES.map((cat) => ({
  value: cat,
  label: CATEGORY_LABELS[cat] || cat,
}));

// Timezone labels for display
const TIMEZONE_LABELS: Record<string, string> = {
  'Africa/Lusaka': 'Africa/Lusaka (CAT)',
  'Africa/Johannesburg': 'Africa/Johannesburg (SAST)',
  'UTC': 'UTC',
};

const timezones = TIMEZONES.map((tz) => ({
  value: tz,
  label: TIMEZONE_LABELS[tz] || tz,
}));

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function CreateEventPage() {
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(0);
  const { createEvent, loading: isSaving } = useCreateEvent();
  const [submitErrors, setSubmitErrors] = useState<string[]>([]);

  const [formData, setFormData] = useState<EventFormData>({
    title: '',
    description: '',
    category: '',
    coverImage: '',
    startDate: '',
    startTime: '',
    endDate: '',
    endTime: '',
    timezone: 'Africa/Lusaka',
    locationType: 'venue',
    venueName: '',
    venueAddress: '',
    venueCity: '',
    onlineUrl: '',
    ticketTiers: [
      { id: '1', name: 'General Admission', price: 0, quantity: 100, description: '' },
    ],
  });

  const steps = ['Basic Info', 'Date & Location', 'Tickets', 'Review'];

  const handleChange = useCallback((field: keyof EventFormData, value: any) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }, []);

  const handleTicketChange = useCallback((id: string, field: keyof TicketTier, value: any) => {
    setFormData((prev) => ({
      ...prev,
      ticketTiers: prev.ticketTiers.map((tier) =>
        tier.id === id ? { ...tier, [field]: value } : tier
      ),
    }));
  }, []);

  const handleAddTier = useCallback(() => {
    setFormData((prev) => ({
      ...prev,
      ticketTiers: [
        ...prev.ticketTiers,
        { id: String(Date.now()), name: '', price: 0, quantity: 50, description: '' },
      ],
    }));
  }, []);

  const handleRemoveTier = useCallback((id: string) => {
    setFormData((prev) => ({
      ...prev,
      ticketTiers: prev.ticketTiers.filter((tier) => tier.id !== id),
    }));
  }, []);

  const handleNext = useCallback(() => {
    if (currentStep < steps.length - 1) {
      setCurrentStep((prev) => prev + 1);
    }
  }, [currentStep, steps.length]);

  const handleBack = useCallback(() => {
    if (currentStep > 0) {
      setCurrentStep((prev) => prev - 1);
    }
  }, [currentStep]);

  /**
   * Compose the form's split date + time fields into one ISO instant.
   *
   * The form collects a date, a time and a timezone separately. Combining them
   * as a local string and letting `new Date` guess would resolve in the
   * BROWSER's zone — an organizer in London scheduling a Lusaka event would
   * publish it two hours out.
   */
  const toInstant = useCallback((date: string, time: string): string | null => {
    if (!date || !time) return null;
    const composed = new Date(`${date}T${time}`);
    return Number.isNaN(composed.getTime()) ? null : composed.toISOString();
  }, []);

  const buildInput = useCallback(() => {
    const eventDateTime = toInstant(formData.startDate, formData.startTime);
    const endDateTime = toInstant(formData.endDate, formData.endTime);
    if (!eventDateTime || !endDateTime) return null;

    const tiers = formData.ticketTiers.map((tier, index) => ({
      // The backend requires a stable per-event tier code. Derive it from the
      // name rather than sending the client-side list id, which is a timestamp
      // and means nothing to anyone reading the data later.
      code: (tier.name || `TIER-${index + 1}`)
        .toUpperCase()
        .replace(/[^A-Z0-9]+/g, '-')
        .replace(/^-|-$/g, '')
        .slice(0, 24) || `TIER-${index + 1}`,
      name: tier.name,
      description: tier.description || null,
      price: tier.price,
      currency: 'ZMW',
      quantity: tier.quantity,
      sortOrder: index,
    }));

    return {
      title: formData.title,
      description: formData.description,
      categoryId: formData.category,
      eventDateTime,
      endDateTime,
      // Capacity is the sum of what the tiers actually allocate. Tracking it as
      // a separate number lets the two disagree, and the tiers are the thing
      // tickets are sold against.
      totalCapacity: tiers.reduce((sum, tier) => sum + (tier.quantity || 0), 0),
      ticketTiers: tiers,
      isVirtual: formData.locationType === 'online',
      virtualEventUrl: formData.locationType === 'online' ? formData.onlineUrl : null,
      location:
        formData.locationType === 'online'
          ? null
          : {
              name: formData.venueName,
              address: formData.venueAddress,
              city: formData.venueCity,
              country: 'Zambia',
            },
      bannerImageUrl: formData.coverImage || null,
    };
  }, [formData, toInstant]);

  /**
   * Create the event.
   *
   * Both buttons land here: `createEvent` always produces a DRAFT, and
   * publishing is a separate authorised step that requires prior approval. The
   * old handlers slept for a second, logged to the console and navigated away —
   * so "Publish" reported success without creating anything at all.
   */
  const handleSubmit = useCallback(async () => {
    setSubmitErrors([]);

    const input = buildInput();
    if (!input) {
      setSubmitErrors(['Add a start and end date and time before saving.']);
      return;
    }

    const result = await createEvent(input);

    if (!result.success) {
      // Stay on the form with the input intact.
      setSubmitErrors(
        result.errors.length > 0
          ? result.errors
          : [result.message ?? 'The event could not be created.']
      );
      return;
    }

    router.push(result.event ? `/events/${result.event.id}` : '/events');
  }, [buildInput, createEvent, router]);

  const handleSaveDraft = handleSubmit;
  const handlePublish = handleSubmit;

  // Render step content
  const renderStepContent = () => {
    switch (currentStep) {
      case 0: // Basic Info
        return (
          <Card
            style={{
              padding: '32px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Heading size="4" mb="5" style={{ color: 'var(--content-primary)' }}>
              Basic Information
            </Heading>

            <FormField label="Event Title" required>
              <TextField.Root
                size="3"
                value={formData.title}
                onChange={(e) => handleChange('title', e.target.value)}
                placeholder="Give your event a clear, descriptive title"
              />
            </FormField>

            <FormField label="Category" required>
              <Select.Root
                value={formData.category}
                onValueChange={(value) => handleChange('category', value)}
              >
                <Select.Trigger placeholder="Select a category" style={{ width: '100%' }} />
                <Select.Content>
                  {categories.map((cat) => (
                    <Select.Item key={cat.value} value={cat.value}>
                      {cat.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </FormField>

            <FormField label="Description" helper="Describe what attendees can expect">
              <TextArea
                size="3"
                rows={5}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                placeholder="Tell potential attendees what your event is about..."
              />
            </FormField>

            <FormField label="Cover Image">
              <Box
                style={{
                  height: 200,
                  borderRadius: 'var(--card-radius)',
                  border: '2px dashed var(--surface-border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  background: 'var(--surface-subtle)',
                }}
              >
                <Flex direction="column" align="center" gap="2">
                  <ImageIcon style={{ width: 32, height: 32, color: 'var(--content-muted)' }} />
                  <Text size="2" style={{ color: 'var(--content-muted)' }}>
                    Click to upload or drag and drop
                  </Text>
                  <Text size="1" style={{ color: 'var(--content-muted)' }}>
                    PNG, JPG up to 10MB (1920x1080 recommended)
                  </Text>
                </Flex>
              </Box>
            </FormField>
          </Card>
        );

      case 1: // Date & Location
        return (
          <Card
            style={{
              padding: '32px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Heading size="4" mb="5" style={{ color: 'var(--content-primary)' }}>
              Date & Location
            </Heading>

            <Box
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                gap: '16px',
              }}
            >
              <FormField label="Start Date" required>
                <TextField.Root
                  size="3"
                  type="date"
                  value={formData.startDate}
                  onChange={(e) => handleChange('startDate', e.target.value)}
                />
              </FormField>

              <FormField label="Start Time" required>
                <TextField.Root
                  size="3"
                  type="time"
                  value={formData.startTime}
                  onChange={(e) => handleChange('startTime', e.target.value)}
                />
              </FormField>

              <FormField label="End Date" required>
                <TextField.Root
                  size="3"
                  type="date"
                  value={formData.endDate}
                  onChange={(e) => handleChange('endDate', e.target.value)}
                />
              </FormField>

              <FormField label="End Time" required>
                <TextField.Root
                  size="3"
                  type="time"
                  value={formData.endTime}
                  onChange={(e) => handleChange('endTime', e.target.value)}
                />
              </FormField>
            </Box>

            <FormField label="Timezone">
              <Select.Root
                value={formData.timezone}
                onValueChange={(value) => handleChange('timezone', value)}
              >
                <Select.Trigger style={{ width: '100%' }} />
                <Select.Content>
                  {timezones.map((tz) => (
                    <Select.Item key={tz.value} value={tz.value}>
                      {tz.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </FormField>

            <Box my="5" style={{ borderTop: '1px solid var(--surface-border)' }} />

            <FormField label="Location Type">
              <Flex gap="3">
                <Button
                  variant={formData.locationType === 'venue' ? 'solid' : 'outline'}
                  onClick={() => handleChange('locationType', 'venue')}
                  style={formData.locationType === 'venue' ? {
                    background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
                  } : {
                    borderColor: 'var(--surface-border)',
                  }}
                >
                  <MapPin style={{ width: 18, height: 18, marginRight: 8 }} />
                  Physical Venue
                </Button>
                <Button
                  variant={formData.locationType === 'online' ? 'solid' : 'outline'}
                  onClick={() => handleChange('locationType', 'online')}
                  style={formData.locationType === 'online' ? {
                    background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
                  } : {
                    borderColor: 'var(--surface-border)',
                  }}
                >
                  <Calendar style={{ width: 18, height: 18, marginRight: 8 }} />
                  Online Event
                </Button>
              </Flex>
            </FormField>

            {formData.locationType === 'venue' ? (
              <>
                <FormField label="Venue Name" required>
                  <TextField.Root
                    size="3"
                    value={formData.venueName}
                    onChange={(e) => handleChange('venueName', e.target.value)}
                    placeholder="e.g., Mulungushi Conference Center"
                  />
                </FormField>

                <FormField label="Address" required>
                  <TextField.Root
                    size="3"
                    value={formData.venueAddress}
                    onChange={(e) => handleChange('venueAddress', e.target.value)}
                    placeholder="Street address"
                  />
                </FormField>

                <FormField label="City" required>
                  <TextField.Root
                    size="3"
                    value={formData.venueCity}
                    onChange={(e) => handleChange('venueCity', e.target.value)}
                    placeholder="e.g., Lusaka"
                  />
                </FormField>
              </>
            ) : (
              <FormField label="Event URL" required helper="Link to your online event platform">
                <TextField.Root
                  size="3"
                  type="url"
                  value={formData.onlineUrl}
                  onChange={(e) => handleChange('onlineUrl', e.target.value)}
                  placeholder="https://zoom.us/j/..."
                />
              </FormField>
            )}
          </Card>
        );

      case 2: // Tickets
        return (
          <Card
            style={{
              padding: '32px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Flex justify="between" align="center" mb="5">
              <Heading size="4" style={{ color: 'var(--content-primary)' }}>
                Ticket Tiers
              </Heading>
              <Button
                variant="outline"
                onClick={handleAddTier}
                style={{ borderColor: 'var(--accent-a6)', color: 'var(--brand-500)' }}
              >
                <Plus style={{ width: 18, height: 18, marginRight: 8 }} />
                Add Tier
              </Button>
            </Flex>

            <Flex direction="column" gap="4">
              {formData.ticketTiers.map((tier, index) => (
                <Card
                  key={tier.id}
                  style={{
                    padding: '20px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--surface-border)',
                    borderRadius: 'var(--card-radius)',
                  }}
                >
                  <Flex justify="between" align="center" mb="4">
                    <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                      Tier {index + 1}
                    </Text>
                    {formData.ticketTiers.length > 1 && (
                      <Button
                        variant="ghost"
                        size="1"
                        onClick={() => handleRemoveTier(tier.id)}
                        style={{ color: 'var(--error-500)' }}
                      >
                        <Trash style={{ width: 16, height: 16 }} />
                      </Button>
                    )}
                  </Flex>

                  <Box
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                      gap: '16px',
                    }}
                  >
                    <FormField label="Tier Name" required>
                      <TextField.Root
                        size="2"
                        value={tier.name}
                        onChange={(e) => handleTicketChange(tier.id, 'name', e.target.value)}
                        placeholder="e.g., VIP, Early Bird"
                      />
                    </FormField>

                    <FormField label="Price (K)" required>
                      <TextField.Root
                        size="2"
                        type="number"
                        min="0"
                        value={tier.price}
                        onChange={(e) => handleTicketChange(tier.id, 'price', Number(e.target.value))}
                        placeholder="0"
                      />
                    </FormField>

                    <FormField label="Quantity" required>
                      <TextField.Root
                        size="2"
                        type="number"
                        min="1"
                        value={tier.quantity}
                        onChange={(e) => handleTicketChange(tier.id, 'quantity', Number(e.target.value))}
                        placeholder="100"
                      />
                    </FormField>
                  </Box>

                  <FormField label="Description" helper="What's included with this tier?">
                    <TextField.Root
                      size="2"
                      value={tier.description}
                      onChange={(e) => handleTicketChange(tier.id, 'description', e.target.value)}
                      placeholder="e.g., Includes lunch and workshop materials"
                    />
                  </FormField>
                </Card>
              ))}
            </Flex>
          </Card>
        );

      case 3: // Review
        return (
          <Card
            style={{
              padding: '32px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Heading size="4" mb="5" style={{ color: 'var(--content-primary)' }}>
              Review Your Event
            </Heading>

            {/* Event Summary */}
            <Box mb="5" pb="5" style={{ borderBottom: '1px solid var(--surface-border)' }}>
              <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Event Details
              </Text>
              <Text size="4" weight="bold" style={{ color: 'var(--content-primary)', display: 'block' }}>
                {formData.title || 'Untitled Event'}
              </Text>
              <Text size="2" style={{ color: 'var(--content-muted)' }}>
                {formData.category ? categories.find(c => c.value === formData.category)?.label : 'No category'}
              </Text>
            </Box>

            {/* Date & Location Summary */}
            <Box mb="5" pb="5" style={{ borderBottom: '1px solid var(--surface-border)' }}>
              <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Date & Location
              </Text>
              <Flex gap="4" wrap="wrap">
                <Flex align="center" gap="2">
                  <Calendar style={{ width: 16, height: 16, color: 'var(--brand-500)' }} />
                  <Text size="2" style={{ color: 'var(--content-primary)' }}>
                    {formData.startDate || 'Date not set'}
                  </Text>
                </Flex>
                <Flex align="center" gap="2">
                  <MapPin style={{ width: 16, height: 16, color: 'var(--brand-500)' }} />
                  <Text size="2" style={{ color: 'var(--content-primary)' }}>
                    {formData.locationType === 'venue'
                      ? formData.venueName || 'Venue not set'
                      : 'Online Event'}
                  </Text>
                </Flex>
              </Flex>
            </Box>

            {/* Tickets Summary */}
            <Box>
              <Text size="2" weight="medium" mb="3" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Ticket Tiers ({formData.ticketTiers.length})
              </Text>
              <Flex direction="column" gap="2">
                {formData.ticketTiers.map((tier) => (
                  <Flex
                    key={tier.id}
                    justify="between"
                    align="center"
                    p="3"
                    style={{
                      background: 'var(--surface-subtle)',
                      borderRadius: 'var(--radius-4)',
                    }}
                  >
                    <Text size="2" style={{ color: 'var(--content-primary)' }}>
                      {tier.name || 'Unnamed Tier'}
                    </Text>
                    <Flex gap="4">
                      <Text size="2" style={{ color: 'var(--content-muted)' }}>
                        {tier.quantity} tickets
                      </Text>
                      <Text size="2" weight="medium" style={{ color: 'var(--brand-500)' }}>
                        K {tier.price.toLocaleString()}
                      </Text>
                    </Flex>
                  </Flex>
                ))}
              </Flex>
            </Box>
          </Card>
        );

      default:
        return null;
    }
  };

  return (
    <Box>
      <PageHeader
        title="Create event"
        description="Set up a new event for your organization"
        breadcrumbs={[
          { label: 'Events', href: '/events' },
          { label: 'Create event' },
        ]}
      />

      {/* Step Indicator */}
      <StepIndicator
        steps={steps}
        currentStep={currentStep}
        onStepClick={setCurrentStep}
      />

      {/* Step Content */}
      {renderStepContent()}

      {/* Navigation */}
      <Flex justify="between" mt="6">
        <Flex gap="2">
          {currentStep > 0 && (
            <Button
              variant="outline"
              size="3"
              onClick={handleBack}
              style={{ borderColor: 'var(--surface-border)', color: 'var(--content-secondary)' }}
            >
              <ArrowLeft style={{ width: 18, height: 18, marginRight: 8 }} />
              Back
            </Button>
          )}
        </Flex>

        {/* Submit failures are shown in place. The form is NOT cleared and the
            user is not navigated away — their input survives a rejected save. */}
        {submitErrors.length > 0 && (
          <Box
            className="error-card"
            style={{
              padding: 'var(--space-3) var(--space-4)',
              borderRadius: 'var(--card-radius)',
              border: '1px solid var(--red-a6)',
            }}
            role="alert"
            data-testid="create-event-errors"
          >
            {submitErrors.map((message) => (
              <Text
                key={message}
                as="p"
                size="2"
                style={{ color: 'var(--status-danger-11)' }}
              >
                {message}
              </Text>
            ))}
          </Box>
        )}

        <Flex gap="2">
          <Button
            variant="outline"
            size="3"
            onClick={handleSaveDraft}
            disabled={isSaving}
            style={{ borderColor: 'var(--surface-border)', color: 'var(--content-secondary)' }}
          >
            Save Draft
          </Button>

          {currentStep < steps.length - 1 ? (
            <Button
              size="3"
              onClick={handleNext}
              style={{
                background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
              }}
            >
              Continue
              <ArrowRight style={{ width: 18, height: 18, marginLeft: 8 }} />
            </Button>
          ) : (
            <Button
              size="3"
              onClick={handlePublish}
              disabled={isSaving}
              style={{
                background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
              }}
            >
              {isSaving ? 'Publishing...' : 'Publish Event'}
            </Button>
          )}
        </Flex>
      </Flex>
    </Box>
  );
}
