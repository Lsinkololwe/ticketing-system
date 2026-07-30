'use client';

import React from 'react';
import {
  Card,
  Box,
  Flex,
  Text,
  Select,
  TextField,
  Button,
  Badge,
  IconButton,
} from '@radix-ui/themes';
import { useActiveEventCategories, useCitiesWithEvents, type EventStatus } from '@pml.tickets/shared';
import { Filter, Xmark, Search } from 'iconoir-react';

export interface EventFilterValues {
  search: string;
  category: string;
  status: EventStatus | '';
  city: string;
  dateRange: string;
  priceRange: string;
}

interface EventFiltersProps {
  filters: EventFilterValues;
  onFiltersChange: (filters: EventFilterValues) => void;
  onClearFilters: () => void;
}

/** Human labels for the machine values we store in filter state. */
const DATE_LABELS: Record<string, string> = {
  today: 'Today',
  tomorrow: 'Tomorrow',
  'this-week': 'This week',
  'next-week': 'Next week',
  'this-month': 'This month',
  'next-month': 'Next month',
};

const PRICE_LABELS: Record<string, string> = {
  free: 'Free',
  '0-100': 'K 0 – K 100',
  '100-250': 'K 100 – K 250',
  '250-500': 'K 250 – K 500',
  '500-1000': 'K 500 – K 1,000',
  '1000+': 'K 1,000+',
};

/** Micro uppercase form label (spec §3 / §8). */
function FieldLabel({ children }: { children: React.ReactNode }) {
  return (
    <Text as="div" size="1" className="ds-label" mb="2">
      {children}
    </Text>
  );
}

const EventFilters: React.FC<EventFiltersProps> = ({
  filters,
  onFiltersChange,
  onClearFilters,
}) => {
  const { categories } = useActiveEventCategories();
  const { cities } = useCitiesWithEvents();

  const set = (key: keyof EventFilterValues, value: string) =>
    onFiltersChange({ ...filters, [key]: value } as EventFilterValues);

  /* Note: `status` is deliberately NOT exposed to customers. Draft /
     pending-review / changes-requested are organizer workflow states; the
     public browse only ever shows what is live. The field stays in the shape
     so the shared filter type is unchanged. */
  const activeChips = [
    filters.search && { key: 'search' as const, label: `“${filters.search}”` },
    filters.category && { key: 'category' as const, label: filters.category },
    filters.city && { key: 'city' as const, label: filters.city },
    filters.dateRange && {
      key: 'dateRange' as const,
      label: DATE_LABELS[filters.dateRange] ?? filters.dateRange,
    },
    filters.priceRange && {
      key: 'priceRange' as const,
      label: PRICE_LABELS[filters.priceRange] ?? filters.priceRange,
    },
  ].filter(Boolean) as { key: keyof EventFilterValues; label: string }[];

  return (
    <Card size="2" style={{ borderRadius: 'var(--card-radius-bento)' }}>
      <Box p="3">
        <Flex justify="between" align="center" mb="4">
          <Flex align="center" gap="2">
            <Filter style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
            <Text size="3" weight="medium">
              Filters
            </Text>
            {activeChips.length > 0 && (
              <Badge color="iris" variant="soft" radius="full">
                {activeChips.length}
              </Badge>
            )}
          </Flex>

          {activeChips.length > 0 && (
            <Button
              variant="ghost"
              color="gray"
              size="1"
              onClick={onClearFilters}
              data-testid="filters-clear"
            >
              Clear all
            </Button>
          )}
        </Flex>

        <Box mb="4">
          <FieldLabel>Search</FieldLabel>
          <TextField.Root
            placeholder="Event, venue, or artist"
            value={filters.search}
            onChange={(e) => set('search', e.target.value)}
            size="2"
            data-testid="filters-search"
          >
            <TextField.Slot>
              <Search style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
            </TextField.Slot>
          </TextField.Root>
        </Box>

        <Flex direction="column" gap="4">
          <Box>
            <FieldLabel>Category</FieldLabel>
            <Select.Root
              value={filters.category || 'all'}
              onValueChange={(v) => set('category', v === 'all' ? '' : v)}
            >
              <Select.Trigger placeholder="Any category" style={{ width: '100%' }} />
              <Select.Content>
                <Select.Item value="all">Any category</Select.Item>
                {categories.map((category) => (
                  <Select.Item key={category.id} value={category.name}>
                    {category.name}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Box>

          <Box>
            <FieldLabel>City</FieldLabel>
            <Select.Root
              value={filters.city || 'all'}
              onValueChange={(v) => set('city', v === 'all' ? '' : v)}
            >
              <Select.Trigger placeholder="Anywhere" style={{ width: '100%' }} />
              <Select.Content>
                <Select.Item value="all">Anywhere</Select.Item>
                {cities.map((city) => (
                  <Select.Item key={city.id} value={city.name}>
                    {city.name}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Box>

          <Box>
            <FieldLabel>When</FieldLabel>
            <Select.Root
              value={filters.dateRange || 'all'}
              onValueChange={(v) => set('dateRange', v === 'all' ? '' : v)}
            >
              <Select.Trigger placeholder="Any time" style={{ width: '100%' }} />
              <Select.Content>
                <Select.Item value="all">Any time</Select.Item>
                {Object.entries(DATE_LABELS).map(([value, label]) => (
                  <Select.Item key={value} value={value}>
                    {label}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Box>

          <Box>
            <FieldLabel>Price</FieldLabel>
            <Select.Root
              value={filters.priceRange || 'all'}
              onValueChange={(v) => set('priceRange', v === 'all' ? '' : v)}
            >
              <Select.Trigger placeholder="Any price" style={{ width: '100%' }} />
              <Select.Content>
                <Select.Item value="all">Any price</Select.Item>
                {Object.entries(PRICE_LABELS).map(([value, label]) => (
                  <Select.Item key={value} value={value}>
                    {label}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Box>
        </Flex>

        {activeChips.length > 0 && (
          <Box mt="5" pt="4" style={{ borderTop: 'var(--hairline)' }}>
            <Text as="div" size="1" className="ds-label" mb="2">
              Applied
            </Text>
            <Flex gap="2" wrap="wrap">
              {activeChips.map((chip) => (
                <Flex key={chip.key} align="center" gap="1">
                  <Badge color="iris" variant="soft" radius="full">
                    {chip.label}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    color="gray"
                    size="1"
                    aria-label={`Remove ${chip.label} filter`}
                    onClick={() => set(chip.key, '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              ))}
            </Flex>
          </Box>
        )}
      </Box>
    </Card>
  );
};

export default EventFilters;
