'use client';

import { useId } from 'react';
import { FormProvider, type FieldValues, type UseFormReturn } from 'react-hook-form';
import { Button } from '@pml.tickets/shared/components/m3';
import { DateRHF, SelectRHF, TextFieldRHF } from '@pml.tickets/shared/forms';
import { WHEN_OPTIONS, moreCount, type FilterState } from './filters';

export interface DiscoverSearchProps {
  form: UseFormReturn<FilterState, unknown, FilterState>;
  onWhen: (when: FilterState['when']) => void;
  onSearch: () => void;
  onClear: () => void;
  cities: Array<{ id: string; name: string }>;
  categories: Array<{ id: string; name: string }>;
  moreOpen: boolean;
  onToggleMore: () => void;
}

/** What / Where / When strip with the collapsible "More filters" panel. */
export function DiscoverSearch({ form, onWhen, onSearch, onClear, cities, categories, moreOpen, onToggleMore }: DiscoverSearchProps) {
  const id = useId();
  const value = form.watch();
  const more = moreCount(value);
  const { onChange: whenChange, ...when } = form.register('when');
  // "All" is the empty filter; every other option is a category the backend lists.
  const categoryOptions = [{ value: '', label: 'All categories' }].concat(categories.map((c) => ({ value: c.id, label: c.name })));
  return (
    <FormProvider {...(form as unknown as UseFormReturn<FieldValues>)}>
    <form className="m3-site-search buyer-search" role="search" aria-label="Search and filter events" noValidate onSubmit={form.handleSubmit(onSearch)}>
      <label className="m3-site-search__field" htmlFor={`${id}-q`} data-wide="true">
        <span>What</span>
        <input id={`${id}-q`} type="search" placeholder="Artist, event or venue" autoComplete="off" {...form.register('q')} />
      </label>
      <label className="m3-site-search__field" htmlFor={`${id}-c`}>
        <span>Where</span>
        <select id={`${id}-c`} {...form.register('city')}>
          <option value="">All cities</option>
          {cities.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <label className="m3-site-search__field" htmlFor={`${id}-w`}>
        <span>When</span>
        <select
          id={`${id}-w`}
          {...when}
          onChange={(e) => {
            void whenChange(e);
            onWhen(e.target.value as FilterState['when']);
          }}
        >
          {WHEN_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
      <Button type="submit" variant="accent">
        Search
      </Button>
      <Button variant="text" aria-expanded={moreOpen} aria-controls={`${id}-more`} onClick={onToggleMore}>
        More filters{more ? <span className="buyer-count">{more}</span> : null}
      </Button>
      <div id={`${id}-more`} className="buyer-search__more" hidden={!moreOpen}>
        <SelectRHF name="categoryId" label="Category" options={categoryOptions} />
        <DateRHF name="from" label="From date" />
        <DateRHF name="to" label="To date" />
        <TextFieldRHF name="min" label="Min price (K)" inputMode="numeric" />
        <TextFieldRHF name="max" label="Max price (K)" inputMode="numeric" />
        <Button onClick={onClear}>Clear filters</Button>
      </div>
    </form>
    </FormProvider>
  );
}
