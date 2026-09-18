import type { Vital } from '../types';

export type VitalStatus = 'normal' | 'high' | 'low' | 'critical';

export interface VitalRange {
  low: number;
  high: number;
  criticalLow: number;
  criticalHigh: number;
}

export const VITAL_RANGES: Record<string, VitalRange> = {
  systolic: { low: 90, high: 140, criticalLow: 80, criticalHigh: 180 },
  diastolic: { low: 60, high: 90, criticalLow: 50, criticalHigh: 120 },
  heartRate: { low: 60, high: 100, criticalLow: 40, criticalHigh: 150 },
  temperature: { low: 97, high: 99.5, criticalLow: 95, criticalHigh: 103 },
  oxygenSaturation: { low: 95, high: 100, criticalLow: 90, criticalHigh: 101 },
  respiratoryRate: { low: 12, high: 20, criticalLow: 8, criticalHigh: 30 },
};

export function getVitalStatus(type: string, value: number | undefined): VitalStatus {
  if (value === undefined) return 'normal';
  const range = VITAL_RANGES[type];
  if (!range) return 'normal';
  if (value <= range.criticalLow || value >= range.criticalHigh) return 'critical';
  if (value < range.low) return 'low';
  if (value > range.high) return 'high';
  return 'normal';
}

export function sortVitalsByRecordedAt(vitals: Vital[]): Vital[] {
  return [...vitals].sort(
    (a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime()
  );
}

export function getLatestVital(vitals: Vital[]): Vital | undefined {
  return sortVitalsByRecordedAt(vitals).at(-1);
}
