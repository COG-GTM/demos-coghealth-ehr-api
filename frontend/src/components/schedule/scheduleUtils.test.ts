import { describe, it, expect } from 'vitest';
import {
  SCHEDULE_END_HOUR,
  SCHEDULE_START_HOUR,
  getAppointmentsForDay,
  getAppointmentsForHour,
  getTimeSlots,
  shiftDate,
} from './scheduleUtils';
import type { Appointment } from '../../types';

function appointmentAt(id: number, scheduledTime: string): Appointment {
  return { id, scheduledTime } as unknown as Appointment;
}

describe('getTimeSlots', () => {
  it('covers 7am through 6pm inclusive', () => {
    const slots = getTimeSlots();
    expect(slots).toHaveLength(12);
    expect(slots[0]).toBe(SCHEDULE_START_HOUR);
    expect(slots[slots.length - 1]).toBe(SCHEDULE_END_HOUR);
  });
});

describe('shiftDate', () => {
  const date = new Date(2024, 2, 13, 9, 30, 0);

  it('shifts by a day in day view', () => {
    expect(shiftDate(date, 'day', 'prev')).toEqual(new Date(2024, 2, 12, 9, 30, 0));
    expect(shiftDate(date, 'day', 'next')).toEqual(new Date(2024, 2, 14, 9, 30, 0));
  });

  it('shifts by a week in week view', () => {
    expect(shiftDate(date, 'week', 'prev')).toEqual(new Date(2024, 2, 6, 9, 30, 0));
    expect(shiftDate(date, 'week', 'next')).toEqual(new Date(2024, 2, 20, 9, 30, 0));
  });

  it('crosses month and DST boundaries', () => {
    expect(shiftDate(new Date(2024, 1, 29, 12, 0, 0), 'day', 'next')).toEqual(new Date(2024, 2, 1, 12, 0, 0));
    expect(shiftDate(new Date(2024, 2, 10, 12, 0, 0), 'day', 'prev')).toEqual(new Date(2024, 2, 9, 12, 0, 0));
  });
});

describe('getAppointmentsForHour', () => {
  const appointments = [
    appointmentAt(1, '2024-03-13T07:00:00'),
    appointmentAt(2, '2024-03-13T07:45:00'),
    appointmentAt(3, '2024-03-13T08:00:00'),
  ];

  it('matches on the local hour regardless of minutes', () => {
    expect(getAppointmentsForHour(appointments, 7).map((a) => a.id)).toEqual([1, 2]);
    expect(getAppointmentsForHour(appointments, 8).map((a) => a.id)).toEqual([3]);
  });

  it('returns nothing for an hour with no appointments', () => {
    expect(getAppointmentsForHour(appointments, 9)).toEqual([]);
  });
});

describe('getAppointmentsForDay', () => {
  const appointments = [
    appointmentAt(1, '2024-03-13T00:15:00'),
    appointmentAt(2, '2024-03-13T23:45:00'),
    appointmentAt(3, '2024-03-14T08:00:00'),
  ];

  it('keeps appointments on their local calendar day, including midnight edges', () => {
    expect(getAppointmentsForDay(appointments, new Date(2024, 2, 13)).map((a) => a.id)).toEqual([1, 2]);
    expect(getAppointmentsForDay(appointments, new Date(2024, 2, 14)).map((a) => a.id)).toEqual([3]);
    expect(getAppointmentsForDay(appointments, new Date(2024, 2, 15))).toEqual([]);
  });
});
