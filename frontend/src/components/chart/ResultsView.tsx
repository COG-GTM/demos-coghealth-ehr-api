import { useState } from 'react';
import { FlaskConical, Image, FileText, TrendingUp, Check, AlertTriangle, Clock } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import { Card, CardHeader, CardBody, Badge, Button, Modal, Tabs, TabList, Tab, TabPanel, EmptyState } from '../common';
import { formatDate, formatDateTime, cn } from '../../utils';
import type { LabResult, LabPanel, ImagingResult } from '../../types';

interface ResultsViewProps {
  labPanels: LabPanel[];
  imagingResults: ImagingResult[];
  onMarkReviewed?: (type: 'lab' | 'imaging', id: number) => void;
  isLoading?: boolean;
}

const flagColors = {
  NORMAL: 'text-slate-600',
  LOW: 'text-warning-600',
  HIGH: 'text-danger-600',
  CRITICAL_LOW: 'text-danger-600 font-bold',
  CRITICAL_HIGH: 'text-danger-600 font-bold',
  ABNORMAL: 'text-warning-600',
};

const flagBadges = {
  NORMAL: null,
  LOW: { variant: 'warning' as const, label: 'L' },
  HIGH: { variant: 'danger' as const, label: 'H' },
  CRITICAL_LOW: { variant: 'danger' as const, label: 'LL' },
  CRITICAL_HIGH: { variant: 'danger' as const, label: 'HH' },
  ABNORMAL: { variant: 'warning' as const, label: 'A' },
};

export function ResultsView({ labPanels, imagingResults, onMarkReviewed, isLoading }: ResultsViewProps) {
  const [selectedResult, setSelectedResult] = useState<LabResult | null>(null);
  const [showTrendModal, setShowTrendModal] = useState(false);

  const allLabResults = labPanels.flatMap((p) => p.results);
  const unreviewedLabs = allLabResults.filter((r) => !r.reviewedAt);
  const unreviewedImaging = imagingResults.filter((r) => !r.reviewedAt);

  return (
    <div className="space-y-4">
      <Tabs defaultTab="labs">
        <div className="flex items-center justify-between mb-4">
          <TabList>
            <Tab value="labs">
              <FlaskConical className="w-4 h-4 mr-2" />
              Lab Results
              {unreviewedLabs.length > 0 && (
                <Badge variant="danger" className="ml-2">{unreviewedLabs.length}</Badge>
              )}
            </Tab>
            <Tab value="imaging">
              <Image className="w-4 h-4 mr-2" />
              Imaging
              {unreviewedImaging.length > 0 && (
                <Badge variant="danger" className="ml-2">{unreviewedImaging.length}</Badge>
              )}
            </Tab>
          </TabList>
        </div>

        <TabPanel value="labs">
          {labPanels.length === 0 ? (
            <EmptyState
              icon={FlaskConical}
              title="No lab results"
              description="No laboratory results on file for this patient"
            />
          ) : (
            <div className="space-y-4">
              {labPanels.map((panel, idx) => (
                <Card key={idx}>
                  <CardHeader className="flex items-center justify-between">
                    <div>
                      <span className="font-medium">{panel.panelName}</span>
                      <span className="text-sm text-slate-500 ml-3">
                        {formatDateTime(panel.resultedAt || panel.orderedAt)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={panel.status === 'FINAL' ? 'success' : 'warning'}>
                        {panel.status}
                      </Badge>
                      {panel.results.some((r) => !r.reviewedAt) && onMarkReviewed && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() =>
                            panel.results
                              .filter((r) => !r.reviewedAt)
                              .forEach((r) => onMarkReviewed('lab', r.id))
                          }
                        >
                          <Check className="w-4 h-4" />
                          Mark Reviewed
                        </Button>
                      )}
                    </div>
                  </CardHeader>
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Test</th>
                        <th>Result</th>
                        <th>Flag</th>
                        <th>Reference Range</th>
                        <th>Status</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {panel.results.map((result) => (
                        <tr key={result.id} className={cn(!result.reviewedAt && 'bg-primary-50/50')}>
                          <td className="font-medium">{result.testName}</td>
                          <td className={cn('font-mono', result.flag && flagColors[result.flag])}>
                            {result.value} {result.unit}
                          </td>
                          <td>
                            {result.flag && flagBadges[result.flag] && (
                              <Badge variant={flagBadges[result.flag]!.variant}>
                                {flagBadges[result.flag]!.label}
                              </Badge>
                            )}
                          </td>
                          <td className="text-sm text-slate-500">{result.referenceRange || '--'}</td>
                          <td>
                            {result.reviewedAt ? (
                              <span className="text-xs text-success-600 flex items-center gap-1">
                                <Check className="w-3 h-3" />
                                Reviewed
                              </span>
                            ) : (
                              <span className="text-xs text-warning-600 flex items-center gap-1">
                                <Clock className="w-3 h-3" />
                                Pending
                              </span>
                            )}
                          </td>
                          <td>
                            <button
                              onClick={() => {
                                setSelectedResult(result);
                                setShowTrendModal(true);
                              }}
                              className="p-1 text-slate-400 hover:text-primary-600"
                              title="View Trend"
                            >
                              <TrendingUp className="w-4 h-4" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Card>
              ))}
            </div>
          )}
        </TabPanel>

        <TabPanel value="imaging">
          {imagingResults.length === 0 ? (
            <EmptyState
              icon={Image}
              title="No imaging results"
              description="No imaging studies on file for this patient"
            />
          ) : (
            <div className="space-y-4">
              {imagingResults.map((result) => (
                <Card key={result.id}>
                  <CardHeader className="flex items-center justify-between">
                    <div>
                      <span className="font-medium">{result.studyDescription}</span>
                      <span className="text-sm text-slate-500 ml-3">
                        {formatDateTime(result.performedAt || result.orderedAt)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={result.status === 'FINAL' ? 'success' : 'warning'}>
                        {result.status}
                      </Badge>
                      {!result.reviewedAt && onMarkReviewed && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => onMarkReviewed('imaging', result.id)}
                        >
                          <Check className="w-4 h-4" />
                          Mark Reviewed
                        </Button>
                      )}
                    </div>
                  </CardHeader>
                  <CardBody>
                    <div className="space-y-3">
                      <div>
                        <div className="text-xs text-slate-500 uppercase tracking-wider mb-1">Body Part</div>
                        <div className="text-sm">
                          {result.bodyPart}
                          {result.laterality && ` (${result.laterality})`}
                        </div>
                      </div>
                      {result.impression && (
                        <div>
                          <div className="text-xs text-slate-500 uppercase tracking-wider mb-1">Impression</div>
                          <div className="text-sm bg-slate-50 p-3 rounded-md">{result.impression}</div>
                        </div>
                      )}
                      {result.findings && (
                        <div>
                          <div className="text-xs text-slate-500 uppercase tracking-wider mb-1">Findings</div>
                          <div className="text-sm text-slate-600 whitespace-pre-wrap">{result.findings}</div>
                        </div>
                      )}
                      <div className="flex items-center gap-4 text-xs text-slate-500 pt-2 border-t">
                        {result.radiologist && (
                          <span>Radiologist: Dr. {result.radiologist.lastName}</span>
                        )}
                        {result.reportedAt && (
                          <span>Reported: {formatDateTime(result.reportedAt)}</span>
                        )}
                      </div>
                    </div>
                  </CardBody>
                </Card>
              ))}
            </div>
          )}
        </TabPanel>
      </Tabs>

      <Modal
        isOpen={showTrendModal}
        onClose={() => setShowTrendModal(false)}
        title={selectedResult ? `${selectedResult.testName} Trend` : 'Result Trend'}
        size="lg"
      >
        {selectedResult && (
          <div className="space-y-4">
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={[
                    { date: '10/01', value: 95 },
                    { date: '11/01', value: 102 },
                    { date: '12/01', value: 98 },
                    { date: '01/01', value: parseFloat(selectedResult.value) || 100 },
                  ]}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} stroke="#64748b" />
                  <YAxis tick={{ fontSize: 12 }} stroke="#64748b" />
                  <Tooltip />
                  <Line type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={2} dot={{ r: 4 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <div className="text-sm text-slate-500">
              Reference Range: {selectedResult.referenceRange || 'Not specified'}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
