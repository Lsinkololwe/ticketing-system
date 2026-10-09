import { describe, expect, it } from 'vitest';
import * as labels from '../enumLabels';
import { ALERT_SEVERITY_LABELS, COMMISSION_STATUS_LABELS, enumOptions, enumSchema, enumValues } from '../enumLabels';
import { humanize } from '../format';

describe('enum label maps', () => {
  it('derive options and values from the map, in the order the map declares', () => {
    expect(enumValues(COMMISSION_STATUS_LABELS)).toEqual(['PENDING', 'EARNED', 'CLAWED_BACK', 'CANCELLED']);
    expect(enumOptions(COMMISSION_STATUS_LABELS)[2]).toEqual({ value: 'CLAWED_BACK', label: 'Clawed back' });
  });

  it('the form schema accepts exactly the map keys', () => {
    const schema = enumSchema(ALERT_SEVERITY_LABELS);
    expect(schema.safeParse('WARNING').success).toBe(true);
    expect(schema.safeParse('toString').success).toBe(false);
    expect(schema.safeParse('urgent').success).toBe(false);
    expect(schema.safeParse(undefined).success).toBe(false);
  });

  it('every label is the console wording for its value (maps that are not a plain humanising are the named exceptions)', () => {
    const custom = new Set(['ANNOUNCEMENT_SEGMENT_LABELS', 'ALERT_SEVERITY_LABELS', 'PAYOUT_METHOD_LABELS', 'STOCK_IMAGE_PURPOSE_LABELS']);
    for (const [name, map] of Object.entries(labels)) {
      if (!name.endsWith('_LABELS') || custom.has(name)) continue;
      for (const [value, label] of Object.entries(map as Record<string, string>)) {
        expect(label, `${name}.${value}`).toBe(humanize(value));
      }
    }
  });
});
