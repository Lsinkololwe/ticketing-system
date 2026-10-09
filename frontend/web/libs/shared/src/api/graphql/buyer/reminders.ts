'use client';

import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';
import type { MyEventRemindersQuery } from '../../../types/graphql';

export interface ReminderRow {
  id: string;
  eventId: string;
  ticketId: string;
  status: string;
}

const LIST = gql`
  query MyEventReminders {
    myEventReminders {
      id
      eventId
      ticketId
      status
    }
  }
`;
const SET = gql`
  mutation SetEventReminder($input: SetEventReminderInput!) {
    setEventReminder(input: $input) {
      id
    }
  }
`;
const CANCEL = gql`
  mutation CancelEventReminder($reminderId: ID!) {
    cancelEventReminder(reminderId: $reminderId)
  }
`;

/** Per-ticket event reminders. A booking-level switch sets or cancels one for each ticket. */
export function useReminders() {
  const { data } = useQuery<MyEventRemindersQuery>(LIST, { fetchPolicy: 'cache-and-network' });
  const [set] = useMutation(SET, { refetchQueries: [LIST] });
  const [cancel] = useMutation(CANCEL, { refetchQueries: [LIST] });
  const reminders = (data?.myEventReminders ?? []).filter((r) => r.status !== 'CANCELLED');
  return {
    reminders,
    setFor: (ticketId: string, eventStartsAt: string) => set({ variables: { input: { ticketId, eventStartsAt } } }),
    cancelFor: (reminderId: string) => cancel({ variables: { reminderId } }),
  };
}
