// TanStack Query client configuration
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 10 * 60 * 1000, // 10 minutes (formerly cacheTime)
      retry: (failureCount: number, error: any) => {
        // Don't retry on 4xx errors
        if (error instanceof Error && 'status' in error) {
          const status = (error as any).status;
          if (status >= 400 && status < 500) {
            return false;
          }
        }
        return failureCount < 3;
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 1,
    },
  },
});

// Query keys for consistent caching
export const queryKeys = {
  events: {
    all: ['events'] as const,
    lists: () => [...queryKeys.events.all, 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.events.lists(), filters] as const,
    details: () => [...queryKeys.events.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.events.details(), id] as const,
    stats: () => [...queryKeys.events.all, 'stats'] as const,
    upcoming: () => [...queryKeys.events.all, 'upcoming'] as const,
    published: () => [...queryKeys.events.all, 'published'] as const,
    search: (query: string) => [...queryKeys.events.all, 'search', query] as const,
  },
  users: {
    all: ['users'] as const,
    lists: () => [...queryKeys.users.all, 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.users.lists(), filters] as const,
    details: () => [...queryKeys.users.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.users.details(), id] as const,
    current: () => [...queryKeys.users.all, 'current'] as const,
  },
  tickets: {
    all: ['tickets'] as const,
    lists: () => [...queryKeys.tickets.all, 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.tickets.lists(), filters] as const,
    details: () => [...queryKeys.tickets.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.tickets.details(), id] as const,
    myTickets: (buyerId: string) => [...queryKeys.tickets.all, 'my', buyerId] as const,
  },
  locations: {
    all: ['locations'] as const,
    lists: () => [...queryKeys.locations.all, 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.locations.lists(), filters] as const,
    details: () => [...queryKeys.locations.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.locations.details(), id] as const,
    byCity: (city: string) => [...queryKeys.locations.all, 'city', city] as const,
    nearby: (input: Record<string, any>) => [...queryKeys.locations.all, 'nearby', input] as const,
  },
  organizers: {
    all: ['organizers'] as const,
    lists: () => [...queryKeys.organizers.all, 'list'] as const,
    list: (filters: Record<string, any>) => [...queryKeys.organizers.lists(), filters] as const,
    details: () => [...queryKeys.organizers.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.organizers.details(), id] as const,
  },
  financial: {
    all: ['financial'] as const,
    transactions: () => [...queryKeys.financial.all, 'transactions'] as const,
    payouts: () => [...queryKeys.financial.all, 'payouts'] as const,
    stats: () => [...queryKeys.financial.all, 'stats'] as const,
  },
  stats: {
    all: ['stats'] as const,
    events: () => [...queryKeys.stats.all, 'events'] as const,
    users: () => [...queryKeys.stats.all, 'users'] as const,
    financial: () => [...queryKeys.stats.all, 'financial'] as const,
  },
} as const;
