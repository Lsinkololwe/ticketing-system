import { ReactElement } from 'react';
import { render, RenderOptions, screen } from '@testing-library/react';
import { ApolloClient, InMemoryCache, HttpLink } from '@apollo/client';
import { ApolloProvider } from '@apollo/client/react';
import { Theme } from '@radix-ui/themes';
import { vi, expect } from 'vitest';

// Create a mock Apollo Client
const createMockApolloClient = () => {
  return new ApolloClient({
    link: new HttpLink({ uri: 'http://localhost:4000/graphql' }),
    cache: new InMemoryCache({
      typePolicies: {
        Organization: {
          keyFields: ['id'],
        },
      },
    }),
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'no-cache',
      },
      query: {
        fetchPolicy: 'no-cache',
      },
    },
  });
};

interface AllTheProvidersProps {
  children: React.ReactNode;
}

// Providers wrapper for tests
const AllTheProviders = ({ children }: AllTheProvidersProps) => {
  const client = createMockApolloClient();

  return (
    <ApolloProvider client={client}>
      <Theme>{children}</Theme>
    </ApolloProvider>
  );
};

// Custom render function that includes providers
const customRender = (ui: ReactElement, options?: Omit<RenderOptions, 'wrapper'>) =>
  render(ui, { wrapper: AllTheProviders, ...options });

// Re-export everything from React Testing Library
export * from '@testing-library/react';
export { customRender as render };

// Helper to wait for async updates
export const waitForLoadingToFinish = () =>
  new Promise((resolve) => setTimeout(resolve, 0));

// Helper to create mock router
export const createMockRouter = (overrides = {}) => ({
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  forward: vi.fn(),
  refresh: vi.fn(),
  prefetch: vi.fn(),
  ...overrides,
});

// Helper to create form data
export const createFormData = (overrides = {}) => ({
  name: 'Test Organization',
  type: 'BUSINESS' as const,
  tagline: 'Test tagline',
  description: 'Test description',
  businessEmail: 'test@example.com',
  businessPhone: '+260971234567',
  website: 'https://test.com',
  city: 'Lusaka',
  province: 'LUSAKA',
  country: 'Zambia',
  facebook: '',
  instagram: '',
  twitter: '',
  ...overrides,
});

// XSS test payloads
export const xssPayloads = [
  '<script>alert("XSS")</script>',
  '"><script>alert(String.fromCharCode(88,83,83))</script>',
  '<img src=x onerror=alert("XSS")>',
  'javascript:alert("XSS")',
  '<iframe src="javascript:alert(\'XSS\')">',
  '<svg/onload=alert("XSS")>',
  '\'><script>alert(document.cookie)</script>',
];

// SQL injection payloads
export const sqlInjectionPayloads = [
  "' OR '1'='1",
  "'; DROP TABLE users--",
  "1' UNION SELECT NULL--",
  "admin'--",
  "' OR 1=1--",
];

// Unicode and special characters
export const specialCharacterPayloads = [
  '日本語',
  'Español ñ',
  'العربية',
  '中文',
  'Emoji 🎉🎊',
  'Zero-width: ​‌‍',
  'RTL override: ‮',
];

// Helper to simulate typing with user-event
export const typeIntoField = async (user: any, element: HTMLElement, text: string) => {
  await user.clear(element);
  await user.type(element, text);
};

// Helper to select from dropdown
export const selectFromDropdown = async (
  user: any,
  trigger: HTMLElement,
  optionText: string
) => {
  await user.click(trigger);
  const option = await screen.findByText(optionText);
  await user.click(option);
};

// Mock GraphQL responses
export const mockGraphQLResponse = (data: any) => ({
  data,
  loading: false,
  error: undefined,
  networkStatus: 7,
  called: true,
});

export const mockGraphQLError = (message: string, code = 'INTERNAL_SERVER_ERROR') => ({
  data: undefined,
  loading: false,
  error: {
    message,
    graphQLErrors: [
      {
        message,
        extensions: { code },
      },
    ],
  },
  networkStatus: 8,
  called: true,
});

// Helper to assert accessibility
export const assertAccessibility = async (container: HTMLElement) => {
  // Check for proper ARIA labels
  const buttons = container.querySelectorAll('button');
  buttons.forEach((button) => {
    expect(
      button.textContent || button.getAttribute('aria-label')
    ).toBeTruthy();
  });

  // Check for proper form labels
  const inputs = container.querySelectorAll('input, textarea, select');
  inputs.forEach((input) => {
    const id = input.getAttribute('id');
    if (id) {
      const label = container.querySelector(`label[for="${id}"]`);
      expect(label || input.getAttribute('aria-label')).toBeTruthy();
    }
  });
};

// Helper to simulate network delay
export const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
