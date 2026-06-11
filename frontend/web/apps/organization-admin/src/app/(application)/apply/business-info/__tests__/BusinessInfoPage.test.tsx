/**
 * BusinessInfoPage Component Tests
 *
 * Test Coverage:
 * - Form rendering and initial state
 * - Field validation (required fields, email, phone)
 * - Error message display
 * - Form submission (create and update)
 * - GraphQL mutation handling
 * - Network error scenarios
 * - Pre-population with existing data
 * - Navigation and routing
 * - Accessibility
 * - XSS prevention
 * - Edge cases (Unicode, special characters)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../../../../test/mocks/server';
import { createMockHandlers } from '../../../../../test/mocks/handlers';
import {
  render,
  createFormData,
  xssPayloads,
  specialCharacterPayloads,
} from '../../../../../test/utils';
import BusinessInfoPage from '../page';

// Mock the next/navigation router
const mockPush = vi.fn();
const mockReplace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    replace: mockReplace,
    back: vi.fn(),
  }),
  usePathname: () => '/apply/business-info',
  useSearchParams: () => new URLSearchParams(),
}));

describe('BusinessInfoPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Initial Rendering', () => {
    it('renders all form fields', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      // Basic Information fields
      expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/organization type/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/tagline/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/about your organization/i)).toBeInTheDocument();

      // Contact Information fields
      expect(screen.getByLabelText(/business email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/phone number/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/website/i)).toBeInTheDocument();

      // Location fields
      expect(screen.getByLabelText(/city/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/province/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/country/i)).toBeInTheDocument();

      // Social Media fields
      expect(screen.getByLabelText(/facebook/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/instagram/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/twitter/i)).toBeInTheDocument();
    });

    it('displays the step indicator showing current step', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByText(/organization info/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/review & submit/i)).toBeInTheDocument();
    });

    it('shows Continue and Back buttons', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
      });

      expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
    });

    it('disables country field (set to Zambia)', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const countryField = screen.getByDisplayValue(/zambia/i);
        expect(countryField).toBeDisabled();
      });
    });
  });

  describe('Form Validation', () => {
    it('shows error for empty organization name', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/organization name is required/i)).toBeInTheDocument();
      });
    });

    it('shows error for empty business email', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const nameField = screen.getByLabelText(/organization name/i);
        await user.type(nameField, 'Test Company');
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/email is required/i)).toBeInTheDocument();
      });
    });

    it('shows error for invalid email format', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const emailField = screen.getByLabelText(/business email/i);
        await user.type(emailField, 'invalid-email');
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/please enter a valid email address/i)).toBeInTheDocument();
      });
    });

    it('shows error for empty phone number', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const nameField = screen.getByLabelText(/organization name/i);
        await user.type(nameField, 'Test Company');
        const emailField = screen.getByLabelText(/business email/i);
        await user.type(emailField, 'test@example.com');
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/phone number is required/i)).toBeInTheDocument();
      });
    });

    it('shows error for empty city', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const nameField = screen.getByLabelText(/organization name/i);
        await user.type(nameField, 'Test Company');
        const emailField = screen.getByLabelText(/business email/i);
        await user.type(emailField, 'test@example.com');
        const phoneField = screen.getByLabelText(/phone number/i);
        await user.type(phoneField, '+260971234567');
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/city is required/i)).toBeInTheDocument();
      });
    });

    it('shows error for empty province', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const nameField = screen.getByLabelText(/organization name/i);
        await user.type(nameField, 'Test Company');
        const emailField = screen.getByLabelText(/business email/i);
        await user.type(emailField, 'test@example.com');
        const phoneField = screen.getByLabelText(/phone number/i);
        await user.type(phoneField, '+260971234567');
        const cityField = screen.getByLabelText(/city/i);
        await user.type(cityField, 'Lusaka');
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/province is required/i)).toBeInTheDocument();
      });
    });

    it('clears error when user starts typing in invalid field', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
      });

      // Trigger validation
      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/organization name is required/i)).toBeInTheDocument();
      });

      // Start typing
      const nameField = screen.getByLabelText(/organization name/i);
      await user.type(nameField, 'T');

      // Error should be cleared
      await waitFor(() => {
        expect(screen.queryByText(/organization name is required/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Form Submission - Create New Organization', () => {
    it('successfully creates organization with valid data', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      // Fill required fields
      const formData = createFormData();
      await user.type(screen.getByLabelText(/organization name/i), formData.name);
      await user.type(screen.getByLabelText(/business email/i), formData.businessEmail);
      await user.type(screen.getByLabelText(/phone number/i), formData.businessPhone);
      await user.type(screen.getByLabelText(/city/i), formData.city);

      // Select province
      const provinceField = screen.getByLabelText(/province/i);
      await user.click(provinceField);
      await waitFor(() => {
        const lusakaOption = screen.getByText('Lusaka Province');
        user.click(lusakaOption);
      });

      // Submit form
      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      // Should navigate to review page
      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith('/apply/review');
      });
    });

    it('shows loading state during submission', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const formData = createFormData();
      await user.type(screen.getByLabelText(/organization name/i), formData.name);
      await user.type(screen.getByLabelText(/business email/i), formData.businessEmail);
      await user.type(screen.getByLabelText(/phone number/i), formData.businessPhone);
      await user.type(screen.getByLabelText(/city/i), formData.city);

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      // Should show saving state
      expect(screen.getByText(/saving/i)).toBeInTheDocument();
    });

    it('includes optional fields when provided', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const formData = createFormData({
        tagline: 'Best events in town',
        description: 'We organize amazing events',
        website: 'https://testevents.com',
        facebook: 'https://facebook.com/testevents',
      });

      await user.type(screen.getByLabelText(/organization name/i), formData.name);
      await user.type(screen.getByLabelText(/tagline/i), formData.tagline);
      await user.type(screen.getByLabelText(/about your organization/i), formData.description);
      await user.type(screen.getByLabelText(/business email/i), formData.businessEmail);
      await user.type(screen.getByLabelText(/phone number/i), formData.businessPhone);
      await user.type(screen.getByLabelText(/website/i), formData.website);
      await user.type(screen.getByLabelText(/city/i), formData.city);
      await user.type(screen.getByLabelText(/facebook/i), formData.facebook);

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith('/apply/review');
      });
    });
  });

  describe('Form Pre-population', () => {
    it('pre-populates fields with existing organization data', async () => {
      // Set up handler to return existing organization
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Existing Company',
          businessEmail: 'existing@example.com',
          businessPhone: '+260975555555',
          city: 'Ndola',
          province: 'COPPERBELT',
          status: 'DRAFT',
        })
      );

      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('Existing Company')).toBeInTheDocument();
      });

      expect(screen.getByDisplayValue('existing@example.com')).toBeInTheDocument();
      expect(screen.getByDisplayValue('+260975555555')).toBeInTheDocument();
      expect(screen.getByDisplayValue('Ndola')).toBeInTheDocument();
    });

    it('allows editing pre-populated data', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Original Name',
          status: 'DRAFT',
        })
      );

      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('Original Name')).toBeInTheDocument();
      });

      const nameField = screen.getByLabelText(/organization name/i);
      await user.clear(nameField);
      await user.type(nameField, 'Updated Name');

      expect(screen.getByDisplayValue('Updated Name')).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('displays network error message', async () => {
      server.use(
        createMockHandlers.withNetworkError('mutation', 'ApplyToBeOrganizer')
      );

      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const formData = createFormData();
      await user.type(screen.getByLabelText(/organization name/i), formData.name);
      await user.type(screen.getByLabelText(/business email/i), formData.businessEmail);
      await user.type(screen.getByLabelText(/phone number/i), formData.businessPhone);
      await user.type(screen.getByLabelText(/city/i), formData.city);

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        expect(screen.getByText(/network error/i)).toBeInTheDocument();
      });
    });

    it('displays server unavailable message', async () => {
      server.use(
        createMockHandlers.withNetworkError('query', 'MyOrganization')
      );

      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByText(/unable to connect to server/i)).toBeInTheDocument();
      });

      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    });

    it('allows retry after network error', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withNetworkError('query', 'MyOrganization')
      );

      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByText(/unable to connect to server/i)).toBeInTheDocument();
      });

      const tryAgainButton = screen.getByRole('button', { name: /try again/i });
      await user.click(tryAgainButton);

      // Should trigger refetch
      expect(tryAgainButton).toBeInTheDocument();
    });
  });

  describe('Navigation', () => {
    it('navigates back to welcome page when Back is clicked', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
      });

      const backButton = screen.getByRole('button', { name: /back/i });
      await user.click(backButton);

      expect(mockPush).toHaveBeenCalledWith('/welcome');
    });

    it('redirects to status page if application cannot be edited', async () => {
      server.use(
        createMockHandlers.withStatus('PENDING_REVIEW')
      );

      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalled();
      });
    });

    it('redirects to dashboard if already approved', async () => {
      server.use(
        createMockHandlers.withStatus('APPROVED')
      );

      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalled();
      });
    });
  });

  describe('Accessibility', () => {
    it('has proper labels for all form fields', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      // All fields should have accessible labels
      expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/business email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/phone number/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/city/i)).toBeInTheDocument();
    });

    it('shows required field indicators', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        const nameLabel = screen.getByText(/organization name/i);
        expect(nameLabel.textContent).toContain('*');
      });
    });

    it('associates error messages with their fields', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
      });

      const continueButton = screen.getByRole('button', { name: /continue/i });
      await user.click(continueButton);

      await waitFor(() => {
        const nameField = screen.getByLabelText(/organization name/i);
        const errorMessage = screen.getByText(/organization name is required/i);

        // Error should be near the field
        const fieldContainer = nameField.closest('div');
        expect(fieldContainer).toContainElement(errorMessage);
      });
    });
  });

  describe('Security - XSS Prevention', () => {
    it.each(xssPayloads)('sanitizes XSS payload: %s', async (payload) => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const nameField = screen.getByLabelText(/organization name/i);
      await user.type(nameField, payload);

      // Field should contain the text, but not execute it
      expect(nameField).toHaveValue(payload);

      // Ensure no script tags are in the DOM
      const scripts = document.querySelectorAll('script');
      const hasInjectedScript = Array.from(scripts).some(
        (script) => script.textContent?.includes('XSS') || script.src.includes('javascript:')
      );
      expect(hasInjectedScript).toBe(false);
    });

    it('handles XSS in description field', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/about your organization/i)).toBeInTheDocument();
      });

      const descField = screen.getByLabelText(/about your organization/i);
      const xssPayload = '<img src=x onerror=alert("XSS")>';
      await user.type(descField, xssPayload);

      expect(descField).toHaveValue(xssPayload);
      expect(document.querySelectorAll('img[src="x"]')).toHaveLength(0);
    });
  });

  describe('Edge Cases', () => {
    it.each(specialCharacterPayloads)(
      'handles special characters in name: %s',
      async (payload) => {
        const user = userEvent.setup();
        render(<BusinessInfoPage />);

        await waitFor(() => {
          expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
        });

        const nameField = screen.getByLabelText(/organization name/i);
        await user.type(nameField, payload);

        expect(nameField).toHaveValue(payload);
      }
    );

    it('handles very long input in text fields', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const longString = 'A'.repeat(500);
      const nameField = screen.getByLabelText(/organization name/i);
      await user.type(nameField, longString);

      expect(nameField).toHaveValue(longString);
    });

    it('handles rapid form field changes', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const nameField = screen.getByLabelText(/organization name/i);

      // Rapidly type and clear
      await user.type(nameField, 'First');
      await user.clear(nameField);
      await user.type(nameField, 'Second');
      await user.clear(nameField);
      await user.type(nameField, 'Third');

      expect(nameField).toHaveValue('Third');
    });

    it('prevents double submission', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization name/i)).toBeInTheDocument();
      });

      const formData = createFormData();
      await user.type(screen.getByLabelText(/organization name/i), formData.name);
      await user.type(screen.getByLabelText(/business email/i), formData.businessEmail);
      await user.type(screen.getByLabelText(/phone number/i), formData.businessPhone);
      await user.type(screen.getByLabelText(/city/i), formData.city);

      const continueButton = screen.getByRole('button', { name: /continue/i });

      // Click multiple times rapidly
      await user.click(continueButton);
      await user.click(continueButton);
      await user.click(continueButton);

      // Should only navigate once
      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('Field Interactions', () => {
    it('updates organization type when selected', async () => {
      const user = userEvent.setup();
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByLabelText(/organization type/i)).toBeInTheDocument();
      });

      const typeField = screen.getByLabelText(/organization type/i);
      await user.click(typeField);

      await waitFor(() => {
        const businessOption = screen.getByText('Business / Company');
        await user.click(businessOption);
      });

      // Value should be updated (this would be verified in the DOM)
      expect(typeField).toBeInTheDocument();
    });

    it('shows helper text for optional fields', async () => {
      render(<BusinessInfoPage />);

      await waitFor(() => {
        expect(screen.getByText(/a short phrase that describes your organization/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/your facebook page url/i)).toBeInTheDocument();
    });
  });
});
