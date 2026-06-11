/**
 * ReviewPage Component Tests
 *
 * Test Coverage:
 * - Data display from organization
 * - Validation warnings
 * - Terms and conditions checkboxes
 * - Submit button enablement logic
 * - Successful submission flow
 * - Error handling
 * - Navigation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../../../../test/mocks/server';
import { createMockHandlers } from '../../../../../test/mocks/handlers';
import { render } from '../../../../../test/utils';
import ReviewPage from '../page';

const mockPush = vi.fn();
const mockReplace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    replace: mockReplace,
  }),
  usePathname: () => '/apply/review',
}));

describe('ReviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Data Display', () => {
    it('displays all organization information', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Events Ltd',
          type: 'BUSINESS',
          businessEmail: 'contact@testevents.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('Test Events Ltd')).toBeInTheDocument();
      });

      expect(screen.getByText(/business \/ company/i)).toBeInTheDocument();
      expect(screen.getByText('contact@testevents.com')).toBeInTheDocument();
      expect(screen.getByText('+260971234567')).toBeInTheDocument();
      expect(screen.getByText(/lusaka/i)).toBeInTheDocument();
    });

    it('shows dash for missing optional fields', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Company',
          website: null,
          tagline: null,
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('Test Company')).toBeInTheDocument();
      });

      const dashElements = screen.getAllByText('-');
      expect(dashElements.length).toBeGreaterThan(0);
    });

    it('displays description when provided', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          description: 'We organize the best concerts in Zambia',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText(/we organize the best concerts/i)).toBeInTheDocument();
      });
    });

    it('shows social media links when provided', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          socialLinks: {
            facebook: 'https://facebook.com/testevents',
            instagram: 'https://instagram.com/testevents',
            twitter: null,
            linkedin: null,
            youtube: null,
            tiktok: null,
          },
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('https://facebook.com/testevents')).toBeInTheDocument();
      });

      expect(screen.getByText('https://instagram.com/testevents')).toBeInTheDocument();
    });
  });

  describe('Validation Warnings', () => {
    it('shows validation warning when required fields are missing', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: '', // Missing required field
          businessEmail: 'test@example.com',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText(/please complete all required fields/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/organization name/i)).toBeInTheDocument();
    });

    it('displays individual validation items', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: '',
          businessEmail: '',
          businessPhone: '',
          city: '',
          province: null,
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText(/organization name/i)).toBeInTheDocument();
        expect(screen.getByText(/business email/i)).toBeInTheDocument();
        expect(screen.getByText(/phone number/i)).toBeInTheDocument();
        expect(screen.getByText(/city/i)).toBeInTheDocument();
        expect(screen.getByText(/province/i)).toBeInTheDocument();
      });
    });

    it('does not show validation warning when all fields are valid', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Complete Company',
          businessEmail: 'contact@complete.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('Complete Company')).toBeInTheDocument();
      });

      expect(screen.queryByText(/please complete all required fields/i)).not.toBeInTheDocument();
    });
  });

  describe('Terms and Conditions', () => {
    it('shows both terms checkboxes unchecked by default', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Company',
          businessEmail: 'test@example.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        const checkboxes = screen.getAllByRole('checkbox');
        expect(checkboxes).toHaveLength(2);
      });

      const checkboxes = screen.getAllByRole('checkbox');
      checkboxes.forEach((checkbox) => {
        expect(checkbox).not.toBeChecked();
      });
    });

    it('enables submit button only when both checkboxes are checked and form is valid', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Company',
          businessEmail: 'test@example.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /submit application/i })).toBeInTheDocument();
      });

      const submitButton = screen.getByRole('button', { name: /submit application/i });
      expect(submitButton).toBeDisabled();

      // Check first checkbox
      const checkboxes = screen.getAllByRole('checkbox');
      await user.click(checkboxes[0]);
      expect(submitButton).toBeDisabled();

      // Check second checkbox
      await user.click(checkboxes[1]);
      expect(submitButton).not.toBeDisabled();
    });

    it('disables submit button if validation fails even with checkboxes checked', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          name: '', // Invalid
          businessEmail: 'test@example.com',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        const checkboxes = screen.getAllByRole('checkbox');
        expect(checkboxes).toHaveLength(2);
      });

      // Check both checkboxes
      const checkboxes = screen.getAllByRole('checkbox');
      await user.click(checkboxes[0]);
      await user.click(checkboxes[1]);

      const submitButton = screen.getByRole('button', { name: /submit application/i });
      expect(submitButton).toBeDisabled();
    });
  });

  describe('Edit Functionality', () => {
    it('navigates to business info page when Edit is clicked', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Company',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        const editButtons = screen.getAllByRole('button', { name: /edit/i });
        expect(editButtons.length).toBeGreaterThan(0);
      });

      const editButtons = screen.getAllByRole('button', { name: /edit/i });
      await user.click(editButtons[0]);

      expect(mockPush).toHaveBeenCalledWith('/apply/business-info');
    });
  });

  describe('Submission Flow', () => {
    it('successfully submits application', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          id: 'org-123',
          name: 'Test Company',
          businessEmail: 'test@example.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('Test Company')).toBeInTheDocument();
      });

      // Check both terms checkboxes
      const checkboxes = screen.getAllByRole('checkbox');
      await user.click(checkboxes[0]);
      await user.click(checkboxes[1]);

      const submitButton = screen.getByRole('button', { name: /submit application/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith('/apply/status');
      });
    });

    it('shows loading state during submission', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          id: 'org-123',
          name: 'Test Company',
          businessEmail: 'test@example.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('Test Company')).toBeInTheDocument();
      });

      const checkboxes = screen.getAllByRole('checkbox');
      await user.click(checkboxes[0]);
      await user.click(checkboxes[1]);

      const submitButton = screen.getByRole('button', { name: /submit application/i });
      await user.click(submitButton);

      expect(screen.getByText(/submitting/i)).toBeInTheDocument();
    });

    it('displays error message on submission failure', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          id: 'org-123',
          name: 'Test Company',
          businessEmail: 'test@example.com',
          businessPhone: '+260971234567',
          city: 'Lusaka',
          province: 'LUSAKA',
          status: 'DRAFT',
        }),
        createMockHandlers.withNetworkError('mutation', 'SubmitOrganizationForReview')
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText('Test Company')).toBeInTheDocument();
      });

      const checkboxes = screen.getAllByRole('checkbox');
      await user.click(checkboxes[0]);
      await user.click(checkboxes[1]);

      const submitButton = screen.getByRole('button', { name: /submit application/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/network error/i)).toBeInTheDocument();
      });
    });
  });

  describe('Navigation', () => {
    it('navigates back to business info when Back is clicked', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Company',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
      });

      const backButton = screen.getByRole('button', { name: /back/i });
      await user.click(backButton);

      expect(mockPush).toHaveBeenCalledWith('/apply/business-info');
    });

    it('redirects to welcome if no organization exists', async () => {
      render(<ReviewPage />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith('/welcome');
      });
    });

    it('redirects to appropriate page if status prevents review', async () => {
      server.use(
        createMockHandlers.withStatus('PENDING_REVIEW')
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalled();
      });
    });
  });

  describe('Information Display', () => {
    it('shows what happens next information', async () => {
      server.use(
        createMockHandlers.withExistingOrganization({
          name: 'Test Company',
          status: 'DRAFT',
        })
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText(/what happens next/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/2-3 business days/i)).toBeInTheDocument();
    });
  });

  describe('Loading States', () => {
    it('shows loading spinner while fetching organization', async () => {
      render(<ReviewPage />);

      expect(screen.getByText(/loading your application/i)).toBeInTheDocument();
    });
  });

  describe('Error States', () => {
    it('shows error state with retry button', async () => {
      const user = userEvent.setup();
      server.use(
        createMockHandlers.withNetworkError('query', 'MyOrganization')
      );

      render(<ReviewPage />);

      await waitFor(() => {
        expect(screen.getByText(/failed to load application/i)).toBeInTheDocument();
      });

      expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    });
  });
});
