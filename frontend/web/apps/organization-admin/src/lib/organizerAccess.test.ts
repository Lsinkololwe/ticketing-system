import { describe, expect, it } from 'vitest';
import { hasOrganizerAccess } from './organizerAccess';

describe('hasOrganizerAccess', () => {
  it('is true for an organizer or an administrator', () => {
    expect(hasOrganizerAccess(['CUSTOMER', 'ORGANIZER'])).toBe(true);
    expect(hasOrganizerAccess(['ADMIN'])).toBe(true);
  });

  it('is false for a member whose platform role has not arrived yet', () => {
    expect(hasOrganizerAccess(['CUSTOMER'])).toBe(false);
    expect(hasOrganizerAccess([])).toBe(false);
    expect(hasOrganizerAccess(null)).toBe(false);
  });
});
