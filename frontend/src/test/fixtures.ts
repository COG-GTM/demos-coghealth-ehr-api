import type { Allergy, Patient } from '../types';

export function makePatient(overrides: Partial<Patient> = {}): Patient {
  return {
    id: 1,
    mrn: 'MRN-001',
    firstName: 'Ada',
    lastName: 'Lovelace',
    dateOfBirth: '1980-05-10',
    gender: 'FEMALE',
    active: true,
    deceased: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

export function makeAllergy(overrides: Partial<Allergy> = {}): Allergy {
  return {
    id: 1,
    patientId: 1,
    allergen: 'Penicillin',
    allergenType: 'MEDICATION',
    severity: 'MILD',
    status: 'ACTIVE',
    recordedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}
