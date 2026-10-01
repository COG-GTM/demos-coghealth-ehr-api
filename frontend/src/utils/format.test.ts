import { describe, expect, it, vi, afterEach } from 'vitest';
import {
  calculateAge,
  calculateBmi,
  formatBloodPressure,
  formatDate,
  formatGender,
  formatPatientName,
  formatPhone,
  formatProviderName,
  formatVitalValue,
} from './format';

describe('calculateBmi', () => {
  it('applies the imperial formula rounded to one decimal', () => {
    expect(calculateBmi(70, 180)).toBe(25.8);
    expect(calculateBmi(64, 120)).toBe(20.6);
  });

  it('returns null when height or weight is missing or zero', () => {
    expect(calculateBmi(undefined, 180)).toBeNull();
    expect(calculateBmi(70, undefined)).toBeNull();
    expect(calculateBmi(0, 180)).toBeNull();
    expect(calculateBmi(70, 0)).toBeNull();
  });
});

describe('formatPhone', () => {
  it('formats exactly ten digits', () => {
    expect(formatPhone('5551234567')).toBe('(555) 123-4567');
    expect(formatPhone('(555) 123-4567')).toBe('(555) 123-4567');
    expect(formatPhone('555-123-4567')).toBe('(555) 123-4567');
  });

  it('returns the raw input outside the ten-digit boundary', () => {
    expect(formatPhone('555123456')).toBe('555123456');
    expect(formatPhone('15551234567')).toBe('15551234567');
  });

  it('returns an empty string for missing input', () => {
    expect(formatPhone()).toBe('');
    expect(formatPhone('')).toBe('');
  });
});

describe('formatBloodPressure', () => {
  it('joins systolic and diastolic', () => {
    expect(formatBloodPressure(120, 80)).toBe('120/80');
  });

  it('falls back when either value is missing', () => {
    expect(formatBloodPressure(120, undefined)).toBe('--/--');
    expect(formatBloodPressure(undefined, 80)).toBe('--/--');
    expect(formatBloodPressure()).toBe('--/--');
  });
});

describe('formatVitalValue', () => {
  it('appends the unit', () => {
    expect(formatVitalValue(98.6, 'F')).toBe('98.6 F');
    expect(formatVitalValue(0, 'mg/dL')).toBe('0 mg/dL');
  });

  it('falls back when the value is missing', () => {
    expect(formatVitalValue(undefined, 'F')).toBe('--');
  });
});

describe('formatGender', () => {
  it('maps known codes', () => {
    expect(formatGender('MALE')).toBe('M');
    expect(formatGender('FEMALE')).toBe('F');
    expect(formatGender('OTHER')).toBe('O');
    expect(formatGender('UNKNOWN')).toBe('U');
  });

  it('passes through unmapped values', () => {
    expect(formatGender('NONBINARY')).toBe('NONBINARY');
    expect(formatGender('')).toBe('');
  });
});

describe('calculateAge', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns completed years relative to now', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-15T12:00:00Z'));
    expect(calculateAge('1990-06-15')).toBe(36);
    expect(calculateAge('1990-06-16')).toBe(35);
  });
});

describe('name and date helpers', () => {
  it('formats patient names last-name first', () => {
    expect(formatPatientName({ firstName: 'Ada', lastName: 'Lovelace' })).toBe('Lovelace, Ada');
    expect(
      formatPatientName({ firstName: 'Ada', lastName: 'Lovelace', middleName: 'Byron' }),
    ).toBe('Lovelace, Ada Byron');
  });

  it('appends provider credentials when present', () => {
    expect(formatProviderName({ firstName: 'Gregory', lastName: 'House' })).toBe('Gregory House');
    expect(
      formatProviderName({ firstName: 'Gregory', lastName: 'House', credentials: 'MD' }),
    ).toBe('Gregory House, MD');
  });

  it('formats ISO dates with the default and custom patterns', () => {
    expect(formatDate('2026-03-09T08:30:00')).toBe('03/09/2026');
    expect(formatDate('2026-03-09T08:30:00', 'yyyy-MM-dd')).toBe('2026-03-09');
  });
});
