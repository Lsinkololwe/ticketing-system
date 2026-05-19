'use client';

import { useQuery } from '@apollo/client/react';
import { GET_EVENTS, GET_EVENT_STATS } from '@pml.tickets/shared';
import { EventStatus } from '@/types/event';

// Stub hooks that would normally call backend APIs
// These are placeholders that can be replaced with real GraphQL queries

export function useMockEvents() {
  return useQuery(GET_EVENTS, {
    variables: {
      filter: {
        published: true,
        status: EventStatus.PUBLISHED,
        limit: 20,
        offset: 0,
      },
    },
    fetchPolicy: 'cache-and-network',
  });
}

export function useMockEventStats() {
  return useQuery(GET_EVENT_STATS, {
    fetchPolicy: 'cache-and-network',
  });
}

export function useMockEventCategories() {
  // Return mock data for event categories
  return {
    data: {
      eventCategories: [
        { id: '1', name: 'Music' },
        { id: '2', name: 'Sports' },
        { id: '3', name: 'Arts & Theater' },
        { id: '4', name: 'Conference' },
        { id: '5', name: 'Community' },
        { id: '6', name: 'Food & Drink' },
      ],
    },
    loading: false,
    error: null,
  };
}

export function useMockCities() {
  // Return mock data for cities
  return {
    data: {
      cities: [
        { id: 'lusaka', name: 'Lusaka' },
        { id: 'kitwe', name: 'Kitwe' },
        { id: 'ndola', name: 'Ndola' },
        { id: 'livingstone', name: 'Livingstone' },
        { id: 'kabwe', name: 'Kabwe' },
      ],
    },
    loading: false,
    error: null,
  };
}

export function useUserRole() {
  // Return mock user role data
  return {
    isAdmin: false,
    isOrganizer: false,
    isAttendee: true,
  };
}
