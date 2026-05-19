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
  Grid,
} from '@radix-ui/themes';
import { EventStatus } from '@/types/event';
import { useMockEventCategories, useMockCities } from '@/hooks/useMocks';
import { Filter, Xmark, Search, Calendar, MapPin } from 'iconoir-react';

interface EventFiltersProps {
  filters: {
    search: string;
    category: string;
    status: EventStatus | '';
    city: string;
    dateRange: string;
    priceRange: string;
  };
  onFiltersChange: (filters: any) => void;
  onClearFilters: () => void;
}

const EventFilters: React.FC<EventFiltersProps> = ({
  filters,
  onFiltersChange,
  onClearFilters,
}) => {
  const [isOpen, setIsOpen] = React.useState(false);

  // Fetch categories and cities from GraphQL
  const { data: categoriesData } = useMockEventCategories();
  const { data: citiesData } = useMockCities();

  const categories = (categoriesData as any)?.eventCategories || [];
  const cities = (citiesData as any)?.cities || [];

  const handleFilterChange = (key: string, value: string) => {
    onFiltersChange({
      ...filters,
      [key]: value,
    });
  };

  const getActiveFiltersCount = () => {
    let count = 0;
    if (filters.search) count++;
    if (filters.category) count++;
    if (filters.status) count++;
    if (filters.city) count++;
    if (filters.dateRange) count++;
    if (filters.priceRange) count++;
    return count;
  };

  const activeFiltersCount = getActiveFiltersCount();

  return (
    <Card size="2">
      <Box p="4">
        {/* Filter Header */}
        <Flex justify="between" align="center" mb="4">
          <Flex align="center" gap="2">
            <Filter style={{ width: '1.25rem', height: '1.25rem' }} />
            <Text size="4" weight="medium">
              Filters
            </Text>
            {activeFiltersCount > 0 && (
              <Badge color="iris" ml="2">
                {activeFiltersCount}
              </Badge>
            )}
          </Flex>

          <Flex align="center" gap="2">
            {activeFiltersCount > 0 && (
              <Button variant="ghost" color="red" size="1" onClick={onClearFilters}>
                <Xmark style={{ width: '1rem', height: '1rem' }} />
                Clear All
              </Button>
            )}

            <IconButton
              variant="ghost"
              size="1"
              onClick={() => setIsOpen(!isOpen)}
              className="md:hidden"
            >
              <Filter style={{ width: '1.25rem', height: '1.25rem' }} />
            </IconButton>
          </Flex>
        </Flex>

        {/* Search Bar - Always Visible */}
        <Box mb="4">
          <TextField.Root
            placeholder="Search events..."
            value={filters.search}
            onChange={(e) => handleFilterChange('search', e.target.value)}
            size="2"
          >
            <TextField.Slot>
              <Search style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
            </TextField.Slot>
          </TextField.Root>
        </Box>

        {/* Collapsible Filters */}
        {(isOpen || typeof window !== 'undefined') && (
          <Box style={{ display: isOpen ? 'block' : 'none' }} className="md:!block">
            <Grid columns={{ initial: '1', md: '2', lg: '4' }} gap="4">
              {/* Category Filter */}
              <Box>
                <Text size="2" weight="medium" mb="2" style={{ display: 'block' }}>
                  Category
                </Text>
                <Select.Root
                  value={filters.category || 'all'}
                  onValueChange={(value) => handleFilterChange('category', value === 'all' ? '' : value)}
                >
                  <Select.Trigger placeholder="Select Category" />
                  <Select.Content>
                    <Select.Item value="all">All Categories</Select.Item>
                    {categories.map((category: any) => (
                      <Select.Item key={category.id} value={category.id}>
                        {category.name}
                      </Select.Item>
                    ))}
                  </Select.Content>
                </Select.Root>
              </Box>

              {/* Status Filter */}
              <Box>
                <Text size="2" weight="medium" mb="2" style={{ display: 'block' }}>
                  Status
                </Text>
                <Select.Root
                  value={filters.status || 'all'}
                  onValueChange={(value) => handleFilterChange('status', value === 'all' ? '' : value)}
                >
                  <Select.Trigger placeholder="Select Status" />
                  <Select.Content>
                    <Select.Item value="all">All Status</Select.Item>
                    <Select.Item value={EventStatus.PUBLISHED}>Published</Select.Item>
                    <Select.Item value={EventStatus.DRAFT}>Draft</Select.Item>
                    <Select.Item value={EventStatus.PENDING_APPROVAL}>Pending</Select.Item>
                    <Select.Item value={EventStatus.CANCELLED}>Cancelled</Select.Item>
                    <Select.Item value={EventStatus.COMPLETED}>Completed</Select.Item>
                  </Select.Content>
                </Select.Root>
              </Box>

              {/* City Filter */}
              <Box>
                <Flex align="center" gap="1" mb="2">
                  <MapPin style={{ width: '1rem', height: '1rem' }} />
                  <Text size="2" weight="medium">
                    City
                  </Text>
                </Flex>
                <Select.Root
                  value={filters.city || 'all'}
                  onValueChange={(value) => handleFilterChange('city', value === 'all' ? '' : value)}
                >
                  <Select.Trigger placeholder="Select City" />
                  <Select.Content>
                    <Select.Item value="all">All Cities</Select.Item>
                    {cities.map((city: any) => (
                      <Select.Item key={city.id} value={city.id}>
                        {city.name}
                      </Select.Item>
                    ))}
                  </Select.Content>
                </Select.Root>
              </Box>

              {/* Date Range Filter */}
              <Box>
                <Flex align="center" gap="1" mb="2">
                  <Calendar style={{ width: '1rem', height: '1rem' }} />
                  <Text size="2" weight="medium">
                    Date Range
                  </Text>
                </Flex>
                <Select.Root
                  value={filters.dateRange || 'all'}
                  onValueChange={(value) => handleFilterChange('dateRange', value === 'all' ? '' : value)}
                >
                  <Select.Trigger placeholder="Select Date Range" />
                  <Select.Content>
                    <Select.Item value="all">All Dates</Select.Item>
                    <Select.Item value="today">Today</Select.Item>
                    <Select.Item value="tomorrow">Tomorrow</Select.Item>
                    <Select.Item value="this-week">This Week</Select.Item>
                    <Select.Item value="next-week">Next Week</Select.Item>
                    <Select.Item value="this-month">This Month</Select.Item>
                    <Select.Item value="next-month">Next Month</Select.Item>
                  </Select.Content>
                </Select.Root>
              </Box>
            </Grid>

            {/* Price Range Filter */}
            <Box mt="4">
              <Text size="2" weight="medium" mb="2" style={{ display: 'block' }}>
                Price Range
              </Text>
              <Select.Root
                value={filters.priceRange || 'all'}
                onValueChange={(value) => handleFilterChange('priceRange', value === 'all' ? '' : value)}
              >
                <Select.Trigger placeholder="Select Price Range" style={{ maxWidth: '200px' }} />
                <Select.Content>
                  <Select.Item value="all">All Prices</Select.Item>
                  <Select.Item value="free">Free</Select.Item>
                  <Select.Item value="0-25">$0 - $25</Select.Item>
                  <Select.Item value="25-50">$25 - $50</Select.Item>
                  <Select.Item value="50-100">$50 - $100</Select.Item>
                  <Select.Item value="100-250">$100 - $250</Select.Item>
                  <Select.Item value="250+">$250+</Select.Item>
                </Select.Content>
              </Select.Root>
            </Box>
          </Box>
        )}

        {/* Active Filters Display */}
        {activeFiltersCount > 0 && (
          <Box mt="4" pt="4" style={{ borderTop: '1px solid var(--gray-4)' }}>
            <Text size="2" weight="medium" mb="2" style={{ display: 'block' }}>
              Active Filters:
            </Text>
            <Flex gap="2" wrap="wrap">
              {filters.search && (
                <Flex align="center" gap="1">
                  <Badge color="blue" variant="outline">
                    Search: {filters.search}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    size="1"
                    onClick={() => handleFilterChange('search', '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              )}
              {filters.category && (
                <Flex align="center" gap="1">
                  <Badge color="iris" variant="outline">
                    Category: {filters.category}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    size="1"
                    onClick={() => handleFilterChange('category', '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              )}
              {filters.status && (
                <Flex align="center" gap="1">
                  <Badge color="green" variant="outline">
                    Status: {filters.status}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    size="1"
                    onClick={() => handleFilterChange('status', '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              )}
              {filters.city && (
                <Flex align="center" gap="1">
                  <Badge color="orange" variant="outline">
                    City: {filters.city}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    size="1"
                    onClick={() => handleFilterChange('city', '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              )}
              {filters.dateRange && (
                <Flex align="center" gap="1">
                  <Badge color="blue" variant="outline">
                    Date: {filters.dateRange}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    size="1"
                    onClick={() => handleFilterChange('dateRange', '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              )}
              {filters.priceRange && (
                <Flex align="center" gap="1">
                  <Badge color="gray" variant="outline">
                    Price: {filters.priceRange}
                  </Badge>
                  <IconButton
                    variant="ghost"
                    size="1"
                    onClick={() => handleFilterChange('priceRange', '')}
                  >
                    <Xmark style={{ width: '0.75rem', height: '0.75rem' }} />
                  </IconButton>
                </Flex>
              )}
            </Flex>
          </Box>
        )}
      </Box>
    </Card>
  );
};

export default EventFilters;
