import { describe, expect, it } from 'vitest';
import { VITAL_RANGES, getLatestVital, getVitalStatus, sortVitalsByRecordedAt } from './vitals';
import type { Vital } from '../types';

describe('getVitalStatus', () => {
  it('returns normal for an undefined value', () => {
    expect(getVitalStatus('systolic', undefined)).toBe('normal');
  });

  it('returns normal for an unknown vital type', () => {
    expect(getVitalStatus('glucose', 500)).toBe('normal');
  });

  describe.each(Object.entries(VITAL_RANGES))('%s', (type, range) => {
    const step = type === 'temperature' ? 0.1 : 1;

    it('classifies the critical bounds inclusively', () => {
      expect(getVitalStatus(type, range.criticalLow)).toBe('critical');
      expect(getVitalStatus(type, range.criticalHigh)).toBe('critical');
      expect(getVitalStatus(type, range.criticalLow - step)).toBe('critical');
      expect(getVitalStatus(type, range.criticalHigh + step)).toBe('critical');
    });

    it('classifies values just inside the critical bounds as low or high', () => {
      expect(getVitalStatus(type, range.criticalLow + step)).toBe('low');
      if (range.criticalHigh - step > range.high) {
        expect(getVitalStatus(type, range.criticalHigh - step)).toBe('high');
      }
    });

    it('classifies the normal bounds inclusively', () => {
      expect(getVitalStatus(type, range.low)).toBe('normal');
      expect(getVitalStatus(type, range.high)).toBe('normal');
    });

    it('classifies values just outside the normal bounds as low or high', () => {
      expect(getVitalStatus(type, range.low - step)).toBe('low');
      if (range.high + step < range.criticalHigh) {
        expect(getVitalStatus(type, range.high + step)).toBe('high');
      }
    });
  });

  it('classifies documented clinical boundaries', () => {
    expect(getVitalStatus('systolic', 180)).toBe('critical');
    expect(getVitalStatus('systolic', 179)).toBe('high');
    expect(getVitalStatus('oxygenSaturation', 90)).toBe('critical');
    expect(getVitalStatus('oxygenSaturation', 91)).toBe('low');
    expect(getVitalStatus('oxygenSaturation', 100)).toBe('normal');
    expect(getVitalStatus('heartRate', 40)).toBe('critical');
    expect(getVitalStatus('respiratoryRate', 8)).toBe('critical');
    expect(getVitalStatus('temperature', 98.6)).toBe('normal');
  });
});

function vital(id: number, recordedAt: string, overrides: Partial<Vital> = {}): Vital {
  return { id, patientId: 1, recordedAt, ...overrides };
}

describe('sortVitalsByRecordedAt', () => {
  it('sorts ascending by recordedAt without mutating the input', () => {
    const vitals = [
      vital(1, '2024-03-02T10:00:00Z'),
      vital(2, '2024-01-02T10:00:00Z'),
      vital(3, '2024-02-02T10:00:00Z'),
    ];

    expect(sortVitalsByRecordedAt(vitals).map((v) => v.id)).toEqual([2, 3, 1]);
    expect(vitals.map((v) => v.id)).toEqual([1, 2, 3]);
  });
});

describe('getLatestVital', () => {
  it('returns undefined for an empty list', () => {
    expect(getLatestVital([])).toBeUndefined();
  });

  it('returns the newest reading regardless of input order', () => {
    const oldestFirst = [
      vital(1, '2024-01-02T10:00:00Z'),
      vital(2, '2024-02-02T10:00:00Z'),
      vital(3, '2024-03-02T10:00:00Z'),
    ];

    expect(getLatestVital(oldestFirst)?.id).toBe(3);
    expect(getLatestVital([...oldestFirst].reverse())?.id).toBe(3);
  });
});
