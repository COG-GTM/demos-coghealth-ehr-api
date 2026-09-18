import { afterEach, describe, expect, it, vi } from 'vitest';
import { calculateAge, formatDate, formatGender, formatPatientName, formatPhone } from './format';

afterEach(() => {
  vi.useRealTimers();
});

function freezeTo(date: string) {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(date));
}

describe('calculateAge', () => {
  it('does not count the birthday until it has passed', () => {
    freezeTo('2024-05-09T12:00:00Z');
    expect(calculateAge('1980-05-10')).toBe(43);
  });

  it('counts the birthday on the day itself', () => {
    freezeTo('2024-05-10T12:00:00Z');
    expect(calculateAge('1980-05-10')).toBe(44);
  });

  it('counts the birthday the day after', () => {
    freezeTo('2024-05-11T12:00:00Z');
    expect(calculateAge('1980-05-10')).toBe(44);
  });

  it('handles a leap-day birth date in a non-leap year', () => {
    freezeTo('2023-03-01T12:00:00Z');
    expect(calculateAge('2000-02-29')).toBe(23);
  });
});

describe('format helpers', () => {
  it('formats dates as MM/dd/yyyy by default', () => {
    expect(formatDate('1980-05-10')).toBe('05/10/1980');
  });

  it('formats patient names as last, first middle', () => {
    expect(formatPatientName({ firstName: 'Ada', lastName: 'Lovelace' })).toBe('Lovelace, Ada');
    expect(formatPatientName({ firstName: 'Ada', middleName: 'B', lastName: 'Lovelace' })).toBe('Lovelace, Ada B');
  });

  it('maps known genders to a single letter and passes others through', () => {
    expect(formatGender('FEMALE')).toBe('F');
    expect(formatGender('NONBINARY')).toBe('NONBINARY');
  });

  it('formats ten-digit phone numbers and leaves others untouched', () => {
    expect(formatPhone('5551234567')).toBe('(555) 123-4567');
    expect(formatPhone('+44 20 7946 0958')).toBe('+44 20 7946 0958');
    expect(formatPhone()).toBe('');
  });
});
