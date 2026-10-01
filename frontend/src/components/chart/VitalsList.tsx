import { useState } from 'react';
import { Plus, TrendingUp, TrendingDown, Minus, Activity } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Card, CardHeader, CardBody, Badge, Button, Modal, Input, EmptyState } from '../common';
import { formatDate, formatDateTime, formatBloodPressure, cn } from '../../utils';
import { getLatestVital, getVitalStatus, sortVitalsByRecordedAt } from '../../utils/vitals';
import type { Vital } from '../../types';

interface VitalsListProps {
  vitals: Vital[];
  onAdd?: (vital: Partial<Vital>) => void;
  isLoading?: boolean;
  compact?: boolean;
}

export function VitalsList({ vitals, onAdd, isLoading, compact }: VitalsListProps) {
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedVital, setSelectedVital] = useState<string>('bloodPressure');

  const sortedVitals = sortVitalsByRecordedAt(vitals);
  const latestVitals = getLatestVital(vitals);

  const statusColors = {
    normal: 'text-success-600',
    high: 'text-danger-600',
    low: 'text-warning-600',
    critical: 'text-danger-600 font-bold',
  };

  if (compact) {
    return (
      <Card>
        <CardHeader className="flex items-center justify-between">
          <span>Recent Vitals</span>
          {latestVitals && (
            <span className="text-xs text-slate-500">{formatDate(latestVitals.recordedAt)}</span>
          )}
        </CardHeader>
        <CardBody>
          {!latestVitals ? (
            <div className="text-sm text-slate-500 text-center">No vitals recorded</div>
          ) : (
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <div className="text-slate-500 text-xs">BP</div>
                <div className={cn('font-medium', statusColors[getVitalStatus('systolic', latestVitals.bloodPressureSystolic)])}>
                  {formatBloodPressure(latestVitals.bloodPressureSystolic, latestVitals.bloodPressureDiastolic)} mmHg
                </div>
              </div>
              <div>
                <div className="text-slate-500 text-xs">HR</div>
                <div className={cn('font-medium', statusColors[getVitalStatus('heartRate', latestVitals.heartRate)])}>
                  {latestVitals.heartRate || '--'} bpm
                </div>
              </div>
              <div>
                <div className="text-slate-500 text-xs">Temp</div>
                <div className={cn('font-medium', statusColors[getVitalStatus('temperature', latestVitals.temperature)])}>
                  {latestVitals.temperature || '--'}°F
                </div>
              </div>
              <div>
                <div className="text-slate-500 text-xs">SpO2</div>
                <div className={cn('font-medium', statusColors[getVitalStatus('oxygenSaturation', latestVitals.oxygenSaturation)])}>
                  {latestVitals.oxygenSaturation || '--'}%
                </div>
              </div>
              <div>
                <div className="text-slate-500 text-xs">Weight</div>
                <div className="font-medium">{latestVitals.weight || '--'} lbs</div>
              </div>
              <div>
                <div className="text-slate-500 text-xs">BMI</div>
                <div className="font-medium">{latestVitals.bmi || '--'}</div>
              </div>
            </div>
          )}
        </CardBody>
      </Card>
    );
  }

  const chartData = sortedVitals.slice(-10).map((v) => ({
    date: formatDate(v.recordedAt, 'MM/dd'),
    systolic: v.bloodPressureSystolic,
    diastolic: v.bloodPressureDiastolic,
    heartRate: v.heartRate,
    temperature: v.temperature,
    oxygenSaturation: v.oxygenSaturation,
    weight: v.weight,
  }));

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Vital Signs</h2>
        {onAdd && (
          <Button size="sm" onClick={() => setShowAddModal(true)}>
            <Plus className="w-4 h-4" />
            Record Vitals
          </Button>
        )}
      </div>

      {vitals.length === 0 ? (
        <EmptyState
          icon={Activity}
          title="No vitals recorded"
          description="No vital signs have been recorded for this patient"
          action={onAdd && <Button size="sm" onClick={() => setShowAddModal(true)}>Record Vitals</Button>}
        />
      ) : (
        <>
          <Card>
            <CardHeader>Latest Vitals - {latestVitals && formatDateTime(latestVitals.recordedAt)}</CardHeader>
            <CardBody>
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                <VitalCard
                  label="Blood Pressure"
                  value={formatBloodPressure(latestVitals?.bloodPressureSystolic, latestVitals?.bloodPressureDiastolic)}
                  unit="mmHg"
                  status={getVitalStatus('systolic', latestVitals?.bloodPressureSystolic)}
                />
                <VitalCard
                  label="Heart Rate"
                  value={latestVitals?.heartRate}
                  unit="bpm"
                  status={getVitalStatus('heartRate', latestVitals?.heartRate)}
                />
                <VitalCard
                  label="Temperature"
                  value={latestVitals?.temperature}
                  unit="°F"
                  status={getVitalStatus('temperature', latestVitals?.temperature)}
                />
                <VitalCard
                  label="SpO2"
                  value={latestVitals?.oxygenSaturation}
                  unit="%"
                  status={getVitalStatus('oxygenSaturation', latestVitals?.oxygenSaturation)}
                />
                <VitalCard
                  label="Resp Rate"
                  value={latestVitals?.respiratoryRate}
                  unit="/min"
                  status={getVitalStatus('respiratoryRate', latestVitals?.respiratoryRate)}
                />
                <VitalCard
                  label="Pain"
                  value={latestVitals?.painLevel}
                  unit="/10"
                  status="normal"
                />
              </div>
              <div className="grid grid-cols-3 gap-4 mt-4 pt-4 border-t">
                <VitalCard label="Height" value={latestVitals?.height} unit="in" status="normal" />
                <VitalCard label="Weight" value={latestVitals?.weight} unit="lbs" status="normal" />
                <VitalCard label="BMI" value={latestVitals?.bmi} unit="" status="normal" />
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center gap-4">
                <span>Trends</span>
                <select
                  value={selectedVital}
                  onChange={(e) => setSelectedVital(e.target.value)}
                  className="text-sm border rounded px-2 py-1"
                >
                  <option value="bloodPressure">Blood Pressure</option>
                  <option value="heartRate">Heart Rate</option>
                  <option value="temperature">Temperature</option>
                  <option value="oxygenSaturation">SpO2</option>
                  <option value="weight">Weight</option>
                </select>
              </div>
            </CardHeader>
            <CardBody>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="date" tick={{ fontSize: 12 }} stroke="#64748b" />
                    <YAxis tick={{ fontSize: 12 }} stroke="#64748b" />
                    <Tooltip />
                    {selectedVital === 'bloodPressure' ? (
                      <>
                        <Line type="monotone" dataKey="systolic" stroke="#ef4444" strokeWidth={2} dot={{ r: 4 }} />
                        <Line type="monotone" dataKey="diastolic" stroke="#3b82f6" strokeWidth={2} dot={{ r: 4 }} />
                      </>
                    ) : (
                      <Line type="monotone" dataKey={selectedVital} stroke="#3b82f6" strokeWidth={2} dot={{ r: 4 }} />
                    )}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>History</CardHeader>
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>BP</th>
                  <th>HR</th>
                  <th>Temp</th>
                  <th>SpO2</th>
                  <th>RR</th>
                  <th>Weight</th>
                  <th>Pain</th>
                </tr>
              </thead>
              <tbody>
                {[...sortedVitals].reverse().slice(0, 10).map((v) => (
                  <tr key={v.id}>
                    <td className="text-sm">{formatDateTime(v.recordedAt)}</td>
                    <td className={cn('text-sm', statusColors[getVitalStatus('systolic', v.bloodPressureSystolic)])}>
                      {formatBloodPressure(v.bloodPressureSystolic, v.bloodPressureDiastolic)}
                    </td>
                    <td className={cn('text-sm', statusColors[getVitalStatus('heartRate', v.heartRate)])}>
                      {v.heartRate || '--'}
                    </td>
                    <td className={cn('text-sm', statusColors[getVitalStatus('temperature', v.temperature)])}>
                      {v.temperature || '--'}
                    </td>
                    <td className={cn('text-sm', statusColors[getVitalStatus('oxygenSaturation', v.oxygenSaturation)])}>
                      {v.oxygenSaturation || '--'}%
                    </td>
                    <td className="text-sm">{v.respiratoryRate || '--'}</td>
                    <td className="text-sm">{v.weight || '--'}</td>
                    <td className="text-sm">{v.painLevel ?? '--'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        </>
      )}

      <Modal
        isOpen={showAddModal}
        onClose={() => setShowAddModal(false)}
        title="Record Vital Signs"
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setShowAddModal(false)}>Cancel</Button>
            <Button onClick={() => setShowAddModal(false)}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <Input label="Systolic BP" type="number" placeholder="120" />
            <Input label="Diastolic BP" type="number" placeholder="80" />
            <div>
              <label className="label">Position</label>
              <select className="input">
                <option>Sitting</option>
                <option>Standing</option>
                <option>Lying</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Input label="Heart Rate" type="number" placeholder="72" />
            <Input label="Respiratory Rate" type="number" placeholder="16" />
            <Input label="Temperature (°F)" type="number" step="0.1" placeholder="98.6" />
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Input label="SpO2 (%)" type="number" placeholder="98" />
            <Input label="Pain Level (0-10)" type="number" min="0" max="10" placeholder="0" />
            <div />
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Input label="Height (in)" type="number" placeholder="70" />
            <Input label="Weight (lbs)" type="number" placeholder="180" />
            <div>
              <label className="label">BMI</label>
              <input type="text" className="input bg-slate-50" value="--" disabled />
            </div>
          </div>
          <div>
            <label className="label">Notes</label>
            <textarea className="input" rows={2} placeholder="Additional notes..." />
          </div>
        </div>
      </Modal>
    </div>
  );
}

function VitalCard({
  label,
  value,
  unit,
  status,
}: {
  label: string;
  value: number | string | undefined;
  unit: string;
  status: 'normal' | 'high' | 'low' | 'critical';
}) {
  const statusColors = {
    normal: 'text-slate-900',
    high: 'text-danger-600',
    low: 'text-warning-600',
    critical: 'text-danger-600',
  };

  const statusBg = {
    normal: 'bg-white',
    high: 'bg-danger-50',
    low: 'bg-warning-50',
    critical: 'bg-danger-100',
  };

  return (
    <div className={cn('p-3 rounded-lg border', statusBg[status])}>
      <div className="text-xs text-slate-500 mb-1">{label}</div>
      <div className={cn('text-xl font-semibold', statusColors[status])}>
        {value ?? '--'}
        <span className="text-sm font-normal text-slate-500 ml-1">{unit}</span>
      </div>
    </div>
  );
}
