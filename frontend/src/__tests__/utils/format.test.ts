import { describe, it, expect } from 'vitest';
import {
  formatDate,
  formatDateTime,
  formatTime,
  formatRelative,
  calculateAge,
  formatPatientName,
  formatProviderName,
  formatGender,
  formatPhone,
  formatVitalValue,
  formatBloodPressure,
  calculateBmi,
} from '../../utils/format';

describe('formatDate', () => {
  it('formats ISO string to MM/dd/yyyy by default', () => {
    expect(formatDate('2024-01-15')).toBe('01/15/2024');
  });

  it('accepts a custom format string', () => {
    expect(formatDate('2024-01-15', 'yyyy-MM-dd')).toBe('2024-01-15');
  });

  it('accepts a Date object', () => {
    const date = new Date(2024, 0, 15); // Jan 15, 2024
    expect(formatDate(date)).toBe('01/15/2024');
  });
});

describe('formatDateTime', () => {
  it('formats date and time correctly', () => {
    const result = formatDateTime('2024-01-15T14:30:00');
    expect(result).toMatch(/01\/15\/2024/);
    expect(result).toMatch(/\d{1,2}:\d{2}\s[AP]M/i);
  });
});

describe('formatTime', () => {
  it('formats time from ISO string', () => {
    const result = formatTime('2024-01-15T14:30:00');
    expect(result).toMatch(/\d{1,2}:\d{2}\s[AP]M/i);
  });
});

describe('formatRelative', () => {
  it('returns a relative time string with suffix', () => {
    const pastDate = new Date(Date.now() - 3600 * 1000).toISOString();
    const result = formatRelative(pastDate);
    expect(result).toContain('ago');
  });
});

describe('formatPhone', () => {
  it('formats a 10-digit phone number', () => {
    expect(formatPhone('5551234567')).toBe('(555) 123-4567');
  });

  it('returns empty string for undefined input', () => {
    expect(formatPhone(undefined)).toBe('');
  });

  it('returns empty string for empty string input', () => {
    expect(formatPhone('')).toBe('');
  });

  it('returns original value for non-10-digit inputs', () => {
    expect(formatPhone('555-123')).toBe('555-123');
  });
});

describe('formatBloodPressure', () => {
  it('formats systolic/diastolic correctly', () => {
    expect(formatBloodPressure(120, 80)).toBe('120/80');
  });

  it('returns --/-- when systolic is undefined', () => {
    expect(formatBloodPressure(undefined, 80)).toBe('--/--');
  });

  it('returns --/-- when diastolic is undefined', () => {
    expect(formatBloodPressure(120, undefined)).toBe('--/--');
  });
});

describe('formatVitalValue', () => {
  it('formats value with unit', () => {
    expect(formatVitalValue(98.6, '°F')).toBe('98.6 °F');
  });

  it('returns -- for undefined value', () => {
    expect(formatVitalValue(undefined, '°F')).toBe('--');
  });

  it('returns -- for null value', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect(formatVitalValue(null as any, 'bpm')).toBe('--');
  });
});

describe('calculateBmi', () => {
  it('calculates BMI correctly', () => {
    const bmi = calculateBmi(70, 154);
    expect(bmi).not.toBeNull();
    expect(bmi!).toBeCloseTo(22.1, 0);
  });

  it('returns null when height is undefined', () => {
    expect(calculateBmi(undefined, 154)).toBeNull();
  });

  it('returns null when weight is undefined', () => {
    expect(calculateBmi(70, undefined)).toBeNull();
  });
});

describe('formatPatientName', () => {
  it('formats last, first', () => {
    expect(formatPatientName({ firstName: 'John', lastName: 'Doe' })).toBe('Doe, John');
  });

  it('includes middle name when provided', () => {
    expect(
      formatPatientName({ firstName: 'John', lastName: 'Doe', middleName: 'A' })
    ).toBe('Doe, John A');
  });
});

describe('formatProviderName', () => {
  it('formats first last', () => {
    expect(formatProviderName({ firstName: 'Jane', lastName: 'Smith' })).toBe('Jane Smith');
  });

  it('includes credentials when provided', () => {
    expect(
      formatProviderName({ firstName: 'Jane', lastName: 'Smith', credentials: 'MD' })
    ).toBe('Jane Smith, MD');
  });
});

describe('formatGender', () => {
  it('maps MALE to M', () => {
    expect(formatGender('MALE')).toBe('M');
  });

  it('maps FEMALE to F', () => {
    expect(formatGender('FEMALE')).toBe('F');
  });

  it('maps OTHER to O', () => {
    expect(formatGender('OTHER')).toBe('O');
  });

  it('returns original string for unknown values', () => {
    expect(formatGender('NONBINARY')).toBe('NONBINARY');
  });
});

describe('calculateAge', () => {
  it('returns a non-negative integer for a past date of birth', () => {
    const age = calculateAge('1990-01-01');
    expect(age).toBeGreaterThanOrEqual(30);
    expect(Number.isInteger(age)).toBe(true);
  });
});
