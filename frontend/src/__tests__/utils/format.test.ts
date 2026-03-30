import { describe, it, expect } from 'vitest';
import {
  formatDate,
  formatDateTime,
  formatTime,
  formatPhone,
  formatBloodPressure,
  formatVitalValue,
  calculateBmi,
  formatPatientName,
  formatProviderName,
  formatGender,
  calculateAge,
} from '../../utils/format';

describe('formatDate', () => {
  it('formats an ISO string to MM/dd/yyyy by default', () => {
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
  it('formats an ISO datetime string to MM/dd/yyyy h:mm a', () => {
    const result = formatDateTime('2024-01-15T14:30:00');
    expect(result).toMatch(/01\/15\/2024/);
    expect(result).toMatch(/\d{1,2}:\d{2}\s[AP]M/);
  });
});

describe('formatTime', () => {
  it('formats time from an ISO datetime string', () => {
    const result = formatTime('2024-01-15T14:30:00');
    expect(result).toMatch(/\d{1,2}:\d{2}\s[AP]M/);
  });
});

describe('formatPhone', () => {
  it('formats a 10-digit phone number', () => {
    expect(formatPhone('5551234567')).toBe('(555) 123-4567');
  });

  it('returns empty string when input is undefined', () => {
    expect(formatPhone(undefined)).toBe('');
  });

  it('returns empty string when input is empty', () => {
    expect(formatPhone('')).toBe('');
  });

  it('returns original value for non-10-digit inputs', () => {
    expect(formatPhone('555-123')).toBe('555-123');
  });
});

describe('formatBloodPressure', () => {
  it('formats systolic/diastolic', () => {
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
  it('formats a value with unit', () => {
    expect(formatVitalValue(98.6, 'F')).toBe('98.6 F');
  });

  it('returns -- when value is undefined', () => {
    expect(formatVitalValue(undefined, 'F')).toBe('--');
  });

  it('returns -- when value is null', () => {
    expect(formatVitalValue(null as unknown as undefined, 'bpm')).toBe('--');
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
  it('formats as Last, First', () => {
    expect(formatPatientName({ firstName: 'John', lastName: 'Doe' })).toBe('Doe, John');
  });

  it('includes middle name when provided', () => {
    expect(
      formatPatientName({ firstName: 'John', lastName: 'Doe', middleName: 'A' })
    ).toBe('Doe, John A');
  });
});

describe('formatProviderName', () => {
  it('formats as First Last', () => {
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
