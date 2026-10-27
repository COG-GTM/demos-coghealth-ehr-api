export interface Patient {
  id: number;
  mrn: string;
  firstName: string;
  middleName?: string;
  lastName: string;
  dateOfBirth: string;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN';
  maritalStatus?: string;
  email?: string;
  phoneHome?: string;
  phoneMobile?: string;
  phoneWork?: string;
  address?: Address;
  mailingAddress?: Address;
  preferredLanguage?: string;
  ethnicity?: string;
  race?: string;
  religion?: string;
  identifiers?: PatientIdentifier[];
  emergencyContacts?: EmergencyContact[];
  active: boolean;
  deceased: boolean;
  deceasedDate?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Address {
  street1: string;
  street2?: string;
  city: string;
  state: string;
  zipCode: string;
  country?: string;
  formattedAddress?: string;
}

export interface PatientIdentifier {
  type: string;
  value: string;
  system?: string;
}

export interface EmergencyContact {
  name: string;
  relationship: string;
  phone: string;
  email?: string;
  priority: number;
}

export interface Provider {
  id: number;
  npi: string;
  firstName: string;
  lastName: string;
  credentials?: string;
  specialty?: string;
  email?: string;
  phone?: string;
  active: boolean;
}

export interface EncounterPatient {
  id: number;
  mrn: string;
  firstName: string;
  middleName?: string;
  lastName: string;
}

export interface Encounter {
  id: number;
  encounterNumber: string;
  patient: EncounterPatient;
  attendingProvider?: Provider;
  encounterType: EncounterType;
  status: EncounterStatus;
  scheduledTime?: string;
  checkInTime?: string;
  startTime?: string;
  endTime?: string;
  chiefComplaint?: string;
  location?: string;
  room?: string;
  notes?: ClinicalNote[];
  diagnoses?: Diagnosis[];
  version?: number;
}

export type EncounterType = 
  | 'OFFICE_VISIT'
  | 'FOLLOW_UP'
  | 'NEW_PATIENT'
  | 'ANNUAL_PHYSICAL'
  | 'URGENT_CARE'
  | 'TELEHEALTH'
  | 'PROCEDURE'
  | 'LAB_ONLY'
  | 'IMAGING';

export type EncounterStatus =
  | 'SCHEDULED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'ROOMED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

export interface Problem {
  id: number;
  patientId: number;
  icdCode: string;
  icdDescription: string;
  snomedCode?: string;
  description: string;
  status: 'ACTIVE' | 'RESOLVED' | 'INACTIVE';
  severity?: 'MILD' | 'MODERATE' | 'SEVERE';
  onsetDate?: string;
  resolutionDate?: string;
  isPrincipal: boolean;
  isChronic: boolean;
  notes?: string;
  recordedBy?: Provider;
  recordedAt: string;
}

export interface Allergy {
  id: number;
  patientId: number;
  allergen: string;
  allergenType: 'MEDICATION' | 'FOOD' | 'ENVIRONMENTAL' | 'OTHER';
  rxnormCode?: string;
  reaction?: string;
  severity: 'MILD' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING';
  status: 'ACTIVE' | 'INACTIVE' | 'ENTERED_IN_ERROR';
  onsetDate?: string;
  recordedBy?: Provider;
  recordedAt: string;
  verifiedBy?: Provider;
  verifiedAt?: string;
}

export interface Medication {
  id: number;
  patientId: number;
  drugName: string;
  genericName?: string;
  rxnormCode?: string;
  ndc?: string;
  dose: string;
  doseUnit: string;
  route: string;
  frequency: string;
  sig: string;
  quantity?: number;
  refills?: number;
  startDate: string;
  endDate?: string;
  status: 'ACTIVE' | 'DISCONTINUED' | 'COMPLETED' | 'ON_HOLD';
  isPrn: boolean;
  prnIndication?: string;
  indication?: string;
  prescribedBy?: Provider;
  prescribedAt: string;
  pharmacy?: string;
  notes?: string;
}

export interface Vital {
  id: number;
  patientId: number;
  encounterId?: number;
  recordedAt: string;
  recordedBy?: Provider;
  bloodPressureSystolic?: number;
  bloodPressureDiastolic?: number;
  bpPosition?: string;
  bpSite?: string;
  heartRate?: number;
  respiratoryRate?: number;
  temperature?: number;
  temperatureUnit?: 'F' | 'C';
  temperatureSite?: string;
  oxygenSaturation?: number;
  oxygenDelivery?: string;
  oxygenFlowRate?: number;
  height?: number;
  heightUnit?: 'in' | 'cm';
  weight?: number;
  weightUnit?: 'lbs' | 'kg';
  bmi?: number;
  painLevel?: number;
  notes?: string;
}

export interface LabResult {
  id: number;
  patientId: number;
  orderId?: number;
  testCode: string;
  testName: string;
  panelName?: string;
  value: string;
  unit?: string;
  referenceRange?: string;
  flag?: 'NORMAL' | 'LOW' | 'HIGH' | 'CRITICAL_LOW' | 'CRITICAL_HIGH' | 'ABNORMAL';
  status: 'PRELIMINARY' | 'FINAL' | 'CORRECTED' | 'CANCELLED';
  collectedAt?: string;
  resultedAt: string;
  reviewedBy?: Provider;
  reviewedAt?: string;
  notes?: string;
}

export interface LabPanel {
  panelName: string;
  orderedAt: string;
  collectedAt?: string;
  resultedAt?: string;
  status: string;
  results: LabResult[];
}

export interface ImagingResult {
  id: number;
  patientId: number;
  orderId?: number;
  studyType: string;
  studyDescription: string;
  bodyPart: string;
  laterality?: string;
  status: 'ORDERED' | 'SCHEDULED' | 'IN_PROGRESS' | 'PRELIMINARY' | 'FINAL';
  orderedAt: string;
  performedAt?: string;
  reportedAt?: string;
  impression?: string;
  findings?: string;
  radiologist?: Provider;
  reviewedBy?: Provider;
  reviewedAt?: string;
}

export interface ClinicalNote {
  id: number;
  patientId: number;
  encounterId?: number;
  noteType: NoteType;
  status: 'DRAFT' | 'SIGNED' | 'ADDENDUM' | 'AMENDED';
  title: string;
  chiefComplaint?: string;
  subjective?: string;
  objective?: string;
  assessment?: string;
  plan?: string;
  content?: string;
  author: Provider;
  createdAt: string;
  signedAt?: string;
  signedBy?: Provider;
  cosignedBy?: Provider;
  cosignedAt?: string;
  amendments?: NoteAmendment[];
}

export type NoteType =
  | 'PROGRESS_NOTE'
  | 'H_AND_P'
  | 'CONSULTATION'
  | 'PROCEDURE_NOTE'
  | 'DISCHARGE_SUMMARY'
  | 'TELEPHONE_ENCOUNTER'
  | 'NURSING_NOTE';

export interface NoteAmendment {
  id: number;
  content: string;
  author: Provider;
  createdAt: string;
}

export interface Order {
  id: number;
  patientId: number;
  encounterId?: number;
  orderType: 'MEDICATION' | 'LAB' | 'IMAGING' | 'REFERRAL' | 'PROCEDURE';
  status: 'DRAFT' | 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  priority: 'ROUTINE' | 'URGENT' | 'STAT';
  orderedBy: Provider;
  orderedAt: string;
  indication?: string;
  icdCode?: string;
  notes?: string;
}

export interface MedicationOrder extends Order {
  orderType: 'MEDICATION';
  medication: Medication;
}

export interface LabOrder extends Order {
  orderType: 'LAB';
  testCodes: string[];
  testNames: string[];
  collectionDate?: string;
  fasting?: boolean;
  specimenType?: string;
}

export interface ImagingOrder extends Order {
  orderType: 'IMAGING';
  studyType: string;
  bodyPart: string;
  laterality?: string;
  contrast?: boolean;
  clinicalHistory?: string;
}

export interface Referral extends Order {
  orderType: 'REFERRAL';
  specialty: string;
  referToProvider?: Provider;
  referToFacility?: string;
  reason: string;
  urgency: string;
  clinicalInfo?: string;
}

export interface Immunization {
  id: number;
  patientId: number;
  vaccineName: string;
  cvxCode: string;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: string;
  administeredAt: string;
  administeredBy?: Provider;
  site?: string;
  route?: string;
  dose?: string;
  doseNumber?: number;
  seriesComplete?: boolean;
  visPublicationDate?: string;
  visGivenDate?: string;
  notes?: string;
}

export interface Appointment {
  id: number;
  patient: Patient;
  provider: Provider;
  encounterType: EncounterType;
  status: EncounterStatus;
  scheduledTime: string;
  duration: number;
  location?: string;
  room?: string;
  chiefComplaint?: string;
  notes?: string;
  checkInTime?: string;
  roomedTime?: string;
}

export interface Message {
  id: number;
  subject: string;
  body: string;
  from: User;
  to: User[];
  patient?: Patient;
  category: 'PATIENT' | 'STAFF' | 'RESULT' | 'REFILL' | 'REFERRAL' | 'SYSTEM';
  priority: 'NORMAL' | 'HIGH' | 'URGENT';
  status: 'UNREAD' | 'READ' | 'REPLIED' | 'ARCHIVED';
  sentAt: string;
  readAt?: string;
}

export interface Task {
  id: number;
  title: string;
  description?: string;
  patient?: Patient;
  assignedTo: User;
  assignedBy: User;
  category: 'SIGN_NOTE' | 'REVIEW_RESULT' | 'CALL_PATIENT' | 'PRIOR_AUTH' | 'REFILL' | 'OTHER';
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  dueDate?: string;
  createdAt: string;
  completedAt?: string;
}

export interface User {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  role: 'PHYSICIAN' | 'NURSE' | 'MA' | 'FRONT_DESK' | 'ADMIN';
  providerId?: number;
}

export interface InboxCounts {
  results: number;
  messages: number;
  tasks: number;
}

export interface SearchResult {
  patients: Patient[];
  total: number;
}

export interface ApiError {
  message: string;
  code?: string;
  details?: Record<string, string>;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
