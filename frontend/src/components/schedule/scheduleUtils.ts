import { addDays, subDays, addWeeks, subWeeks, format } from 'date-fns';
import type { Appointment } from '../../types';

export type ScheduleViewMode = 'day' | 'week';

export const SCHEDULE_START_HOUR = 7;
export const SCHEDULE_END_HOUR = 18;

export function getTimeSlots(): number[] {
  return Array.from(
    { length: SCHEDULE_END_HOUR - SCHEDULE_START_HOUR + 1 },
    (_, i) => i + SCHEDULE_START_HOUR
  );
}

export function shiftDate(date: Date, view: ScheduleViewMode, direction: 'prev' | 'next'): Date {
  if (view === 'day') {
    return direction === 'prev' ? subDays(date, 1) : addDays(date, 1);
  }
  return direction === 'prev' ? subWeeks(date, 1) : addWeeks(date, 1);
}

export function getAppointmentsForHour(appointments: Appointment[], hour: number): Appointment[] {
  return appointments.filter((apt) => new Date(apt.scheduledTime).getHours() === hour);
}

export function getAppointmentsForDay(appointments: Appointment[], day: Date): Appointment[] {
  const dayKey = format(day, 'yyyy-MM-dd');
  return appointments.filter((apt) => format(new Date(apt.scheduledTime), 'yyyy-MM-dd') === dayKey);
}
