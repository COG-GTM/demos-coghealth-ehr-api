import { useState, useEffect, useRef } from 'react';
import { Save, Send, FileText, Clock, User } from 'lucide-react';
import { Card, CardHeader, CardBody, Button, Input, Badge, Modal } from '../common';
import { formatDateTime, cn } from '../../utils';
import type { ClinicalNote, NoteType, Patient, Provider } from '../../types';

interface NoteEditorProps {
  note?: ClinicalNote;
  patient: Patient;
  encounterId?: number;
  onSave?: (note: Partial<ClinicalNote>) => void;
  onSign?: (noteId: number) => void;
  isLoading?: boolean;
}

const noteTypeLabels: Record<NoteType, string> = {
  PROGRESS_NOTE: 'Progress Note',
  H_AND_P: 'History & Physical',
  CONSULTATION: 'Consultation',
  PROCEDURE_NOTE: 'Procedure Note',
  DISCHARGE_SUMMARY: 'Discharge Summary',
  TELEPHONE_ENCOUNTER: 'Telephone Encounter',
  NURSING_NOTE: 'Nursing Note',
};

const smartTexts: Record<string, string> = {
  '.normalexam': 'General: Alert, oriented, no acute distress\nHEENT: PERRLA, EOMI, oropharynx clear\nCardiovascular: RRR, no murmurs, rubs, or gallops\nLungs: Clear to auscultation bilaterally\nAbdomen: Soft, non-tender, non-distended\nExtremities: No edema, pulses intact\nNeurological: Grossly intact',
  '.diabetesfu': 'Patient presents for routine diabetes follow-up. Reports good compliance with medications. Checking blood sugars regularly. Denies hypoglycemic episodes, polyuria, polydipsia, or blurred vision.',
  '.htnfu': 'Patient presents for hypertension follow-up. Reports good compliance with medications. Denies headaches, chest pain, shortness of breath, or visual changes.',
  '.ros14': 'Constitutional: No fever, chills, or weight changes\nEyes: No vision changes\nENT: No hearing loss, sore throat\nCardiovascular: No chest pain, palpitations\nRespiratory: No cough, shortness of breath\nGI: No nausea, vomiting, diarrhea\nGU: No dysuria, frequency\nMusculoskeletal: No joint pain\nSkin: No rashes\nNeurological: No headaches, dizziness\nPsychiatric: No depression, anxiety\nEndocrine: No heat/cold intolerance\nHematologic: No easy bruising\nAllergic: No seasonal allergies',
};

export function NoteEditor({ note, patient, encounterId, onSave, onSign, isLoading }: NoteEditorProps) {
  const [noteType, setNoteType] = useState<NoteType>(note?.noteType || 'PROGRESS_NOTE');
  const [chiefComplaint, setChiefComplaint] = useState(note?.chiefComplaint || '');
  const [subjective, setSubjective] = useState(note?.subjective || '');
  const [objective, setObjective] = useState(note?.objective || '');
  const [assessment, setAssessment] = useState(note?.assessment || '');
  const [plan, setPlan] = useState(note?.plan || '');
  const [showTemplates, setShowTemplates] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  const [isDirty, setIsDirty] = useState(false);

  const autosaveRef = useRef<() => void>(() => {});

  useEffect(() => {
    const interval = setInterval(() => autosaveRef.current(), 30000);
    return () => clearInterval(interval);
  }, []);

  const handleSave = () => {
    onSave?.({
      noteType,
      chiefComplaint,
      subjective,
      objective,
      assessment,
      plan,
      status: 'DRAFT',
    });
    setLastSaved(new Date());
    setIsDirty(false);
  };

  useEffect(() => {
    autosaveRef.current = () => {
      if (isDirty && onSave) {
        handleSave();
      }
    };
  });

  const handleSign = () => {
    if (note?.id) {
      onSign?.(note.id);
    }
  };

  const handleTextChange = (
    setter: React.Dispatch<React.SetStateAction<string>>,
    value: string
  ) => {
    let processedValue = value;
    Object.entries(smartTexts).forEach(([trigger, replacement]) => {
      if (value.endsWith(trigger)) {
        processedValue = value.slice(0, -trigger.length) + replacement;
      }
    });
    setter(processedValue);
    setIsDirty(true);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h2 className="text-lg font-semibold">
            {note ? 'Edit Note' : 'New Note'}
          </h2>
          {note?.status && (
            <Badge variant={note.status === 'SIGNED' ? 'success' : 'warning'}>
              {note.status}
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-2">
          {lastSaved && (
            <span className="text-xs text-slate-500 flex items-center gap-1">
              <Clock className="w-3 h-3" />
              Auto-saved {formatDateTime(lastSaved.toISOString())}
            </span>
          )}
          <Button variant="secondary" onClick={() => setShowTemplates(true)}>
            <FileText className="w-4 h-4" />
            Templates
          </Button>
          <Button variant="secondary" onClick={() => handleSave()}>
            <Save className="w-4 h-4" />
            Save Draft
          </Button>
          <Button onClick={handleSign}>
            <Send className="w-4 h-4" />
            Sign Note
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>Visit Information</CardHeader>
        <CardBody>
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="label">Note Type</label>
              <select
                className="input"
                value={noteType}
                onChange={(e) => setNoteType(e.target.value as NoteType)}
              >
                {Object.entries(noteTypeLabels).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label">Date</label>
              <input type="date" className="input" defaultValue={new Date().toISOString().split('T')[0]} />
            </div>
            <div>
              <label className="label">Provider</label>
              <input type="text" className="input bg-slate-50" value="Dr. Sarah Chen" disabled />
            </div>
          </div>
          <div className="mt-4">
            <label className="label">Chief Complaint</label>
            <input
              type="text"
              className="input"
              value={chiefComplaint}
              onChange={(e) => handleTextChange(setChiefComplaint, e.target.value)}
              placeholder="Reason for visit..."
            />
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader>Subjective</CardHeader>
        <CardBody>
          <textarea
            className="input min-h-[150px] font-mono text-sm"
            value={subjective}
            onChange={(e) => handleTextChange(setSubjective, e.target.value)}
            placeholder="History of present illness, review of systems...

SmartText shortcuts:
.diabetesfu - Diabetes follow-up template
.htnfu - Hypertension follow-up template
.ros14 - 14-point review of systems"
          />
        </CardBody>
      </Card>

      <Card>
        <CardHeader>Objective</CardHeader>
        <CardBody>
          <div className="mb-4 p-3 bg-slate-50 rounded-md">
            <div className="text-xs text-slate-500 uppercase tracking-wider mb-2">Vitals (auto-imported)</div>
            <div className="grid grid-cols-6 gap-4 text-sm">
              <div><span className="text-slate-500">BP:</span> 142/88</div>
              <div><span className="text-slate-500">HR:</span> 72</div>
              <div><span className="text-slate-500">Temp:</span> 98.6</div>
              <div><span className="text-slate-500">SpO2:</span> 98%</div>
              <div><span className="text-slate-500">Wt:</span> 195 lbs</div>
              <div><span className="text-slate-500">BMI:</span> 28.0</div>
            </div>
          </div>
          <label className="label">Physical Exam</label>
          <textarea
            className="input min-h-[150px] font-mono text-sm"
            value={objective}
            onChange={(e) => handleTextChange(setObjective, e.target.value)}
            placeholder="Physical examination findings...

SmartText shortcuts:
.normalexam - Normal physical exam template"
          />
        </CardBody>
      </Card>

      <Card>
        <CardHeader>Assessment</CardHeader>
        <CardBody>
          <textarea
            className="input min-h-[120px] font-mono text-sm"
            value={assessment}
            onChange={(e) => handleTextChange(setAssessment, e.target.value)}
            placeholder="1. Problem #1 - ICD-10 code - Assessment
2. Problem #2 - ICD-10 code - Assessment"
          />
          <div className="mt-2">
            <Button variant="ghost" size="sm">
              + Add Problem from List
            </Button>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader>Plan</CardHeader>
        <CardBody>
          <textarea
            className="input min-h-[150px] font-mono text-sm"
            value={plan}
            onChange={(e) => handleTextChange(setPlan, e.target.value)}
            placeholder="1. Problem #1:
   - Medication changes
   - Labs ordered
   - Follow-up

2. Problem #2:
   - Treatment plan"
          />
          <div className="mt-3 flex gap-2">
            <Button variant="ghost" size="sm">+ Add Order</Button>
            <Button variant="ghost" size="sm">+ Add Follow-up</Button>
            <Button variant="ghost" size="sm">+ Add Referral</Button>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader>Patient Instructions</CardHeader>
        <CardBody>
          <div className="space-y-2">
            <label className="flex items-center gap-2">
              <input type="checkbox" className="rounded" />
              <span className="text-sm">Diabetes education handout</span>
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" className="rounded" />
              <span className="text-sm">Low sodium diet</span>
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" className="rounded" />
              <span className="text-sm">Exercise recommendations</span>
            </label>
          </div>
          <div className="mt-4 flex gap-2">
            <Button variant="secondary" size="sm">Print AVS</Button>
            <Button variant="secondary" size="sm">Send to Portal</Button>
          </div>
        </CardBody>
      </Card>

      <Modal
        isOpen={showTemplates}
        onClose={() => setShowTemplates(false)}
        title="Note Templates"
        size="lg"
      >
        <div className="space-y-2">
          {[
            { name: 'Office Visit - Follow-up', type: 'PROGRESS_NOTE' },
            { name: 'Office Visit - New Patient', type: 'PROGRESS_NOTE' },
            { name: 'Annual Physical', type: 'H_AND_P' },
            { name: 'Diabetes Management', type: 'PROGRESS_NOTE' },
            { name: 'Hypertension Follow-up', type: 'PROGRESS_NOTE' },
            { name: 'Telephone Encounter', type: 'TELEPHONE_ENCOUNTER' },
          ].map((template) => (
            <button
              key={template.name}
              onClick={() => {
                setNoteType(template.type as NoteType);
                setShowTemplates(false);
              }}
              className="w-full text-left p-3 hover:bg-slate-50 rounded-md border"
            >
              <div className="font-medium">{template.name}</div>
              <div className="text-xs text-slate-500">{noteTypeLabels[template.type as NoteType]}</div>
            </button>
          ))}
        </div>
      </Modal>
    </div>
  );
}
