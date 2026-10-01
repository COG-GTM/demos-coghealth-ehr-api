import { format, addDays, startOfWeek } from 'date-fns';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '../common';
import { AppointmentCard } from './AppointmentCard';
import { cn } from '../../utils';
import {
  getAppointmentsForDay,
  getAppointmentsForHour,
  getTimeSlots,
  shiftDate,
} from './scheduleUtils';
import type { Appointment } from '../../types';

interface ScheduleViewProps {
  appointments: Appointment[];
  selectedDate: Date;
  onDateChange: (date: Date) => void;
  view: 'day' | 'week';
  onViewChange: (view: 'day' | 'week') => void;
  isLoading?: boolean;
}

export function ScheduleView({
  appointments,
  selectedDate,
  onDateChange,
  view,
  onViewChange,
  isLoading,
}: ScheduleViewProps) {
  const goToToday = () => onDateChange(new Date());
  const goPrev = () => onDateChange(shiftDate(selectedDate, view, 'prev'));
  const goNext = () => onDateChange(shiftDate(selectedDate, view, 'next'));

  const timeSlots = getTimeSlots();

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center justify-between p-4 border-b bg-white">
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={goToToday}>
            Today
          </Button>
          <div className="flex items-center">
            <button
              onClick={goPrev}
              className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-l-md border border-r-0"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={goNext}
              className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-r-md border"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
          <h2 className="text-lg font-semibold ml-2">
            {format(selectedDate, view === 'day' ? 'EEEE, MMMM d, yyyy' : "'Week of' MMMM d, yyyy")}
          </h2>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex border rounded-md overflow-hidden">
            <button
              onClick={() => onViewChange('day')}
              className={cn(
                'px-3 py-1.5 text-sm font-medium transition-colors',
                view === 'day' ? 'bg-primary-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'
              )}
            >
              Day
            </button>
            <button
              onClick={() => onViewChange('week')}
              className={cn(
                'px-3 py-1.5 text-sm font-medium transition-colors',
                view === 'week' ? 'bg-primary-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'
              )}
            >
              Week
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-auto">
        {view === 'day' ? (
          <div className="divide-y">
            {timeSlots.map((hour) => {
              const hourAppointments = getAppointmentsForHour(appointments, hour);
              const timeLabel = format(new Date().setHours(hour, 0, 0, 0), 'h:mm a');

              return (
                <div key={hour} className="flex min-h-[100px]">
                  <div className="w-20 flex-shrink-0 p-2 text-sm text-slate-500 text-right border-r bg-slate-50">
                    {timeLabel}
                  </div>
                  <div className="flex-1 p-2">
                    {hourAppointments.length > 0 ? (
                      <div className="space-y-2">
                        {hourAppointments.map((apt) => (
                          <AppointmentCard key={apt.id} appointment={apt} />
                        ))}
                      </div>
                    ) : (
                      <div className="h-full flex items-center justify-center text-sm text-slate-400">
                        Available
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="grid grid-cols-7 divide-x">
            {Array.from({ length: 7 }).map((_, dayIndex) => {
              const day = addDays(startOfWeek(selectedDate), dayIndex);
              const dayAppointments = getAppointmentsForDay(appointments, day);

              return (
                <div key={dayIndex} className="min-h-[500px]">
                  <div className="p-2 text-center border-b bg-slate-50 sticky top-0">
                    <div className="text-xs text-slate-500">{format(day, 'EEE')}</div>
                    <div className={cn(
                      'text-lg font-medium',
                      format(day, 'yyyy-MM-dd') === format(new Date(), 'yyyy-MM-dd') && 'text-primary-600'
                    )}>
                      {format(day, 'd')}
                    </div>
                  </div>
                  <div className="p-2 space-y-2">
                    {dayAppointments.map((apt) => (
                      <AppointmentCard key={apt.id} appointment={apt} />
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
