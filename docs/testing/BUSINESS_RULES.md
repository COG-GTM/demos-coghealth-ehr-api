# Business rules (test-mined)

Rules mined from `AGENTS.md`, `README.md`, `docs/interfaces/hr-pto-sync.md`, code comments, enums, annotations and existing tests. Each rule is asserted by at least one test whose `@DisplayName` starts with the rule ID (see [COVERAGE.md](COVERAGE.md) for the traceability matrix).

> `docs/PHI_DATA_FLOW.md` and `HIPAA_COMPLIANCE.md`, referenced by AGENTS.md, do not exist in this repository; HIPAA rules come from the AGENTS.md *Critical: HIPAA Compliance* and *Known HIPAA Issues* sections.

Status: `covered` = asserted, production already correct; `defect-fixed` = test exposed a violation that was fixed in this PR; `open-question` = current behaviour asserted, intended rule needs a decision.

## `com.medchart.ehr.service`

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-SVC-01 | Every patient read/search/create/update is audited via @AuditAccess with the matching AuditAction (READ/SEARCH/CREATE/UPDATE). | `src/main/java/com/medchart/ehr/service/PatientService.java:45` | covered |
| BR-SVC-02 | Unknown patient ids/MRNs raise a not-found error that names only the id/MRN, never SSN or other PHI. | `src/main/java/com/medchart/ehr/service/PatientService.java:46` | covered |
| BR-SVC-03 | SSN-based patient lookup is disabled for HIPAA and always throws UnsupportedOperationException. | `src/main/java/com/medchart/ehr/service/PatientService.java:126` | covered |
| BR-SVC-04 | Patient search without a sort delegates to the repository's case-insensitive name/MRN search with the caller's paging. | `src/main/java/com/medchart/ehr/service/PatientService.java:60` | covered |
| BR-SVC-05 | Patient search sort accepts only allow-listed columns (lastName, firstName, mrn, dateOfBirth) and ASC/DESC; anything else is rejected before any SQL runs (no SQL injection). | `src/main/java/com/medchart/ehr/service/PatientService.java:132` | defect-fixed |
| BR-SVC-06 | A patient cannot be created with an MRN that already exists. | `src/main/java/com/medchart/ehr/service/PatientService.java:91` | covered |
| BR-SVC-07 | New patients without an MRN get a generated MRN; created patients are active. | `src/main/java/com/medchart/ehr/service/PatientService.java:91` | covered |
| BR-SVC-08 | Updates apply DTO fields to the stored patient; updating an unknown patient is a not-found error. | `src/main/java/com/medchart/ehr/service/PatientService.java:111` | covered |
| BR-SVC-09 | An appointment is scheduled only when insurance eligibility is verified; ineligible patients are rejected with the payer reason and copay is surfaced. | `src/main/java/com/medchart/ehr/service/AppointmentService.java:34` | covered |
| BR-SVC-10 | Eligibility checks are cache-first; the external gateway is called only on a miss (AGENTS.md Insurance Eligibility Pattern). | `src/main/java/com/medchart/ehr/service/AppointmentService.java:74` | covered |
| BR-SVC-11 | Cached eligibility expires after 24h (TTL); stale or undated entries are re-verified with the gateway. | `src/main/java/com/medchart/ehr/service/AppointmentService.java:28` | defect-fixed |
| BR-SVC-12 | The patient SSN is never stored in the eligibility cache (AGENTS.md known gap #4). | `src/main/java/com/medchart/ehr/service/AppointmentService.java:91` | defect-fixed |
| BR-SVC-13 | Only eligible gateway responses are cached; ineligible responses are not. | `src/main/java/com/medchart/ehr/service/AppointmentService.java:89` | covered |
| BR-SVC-14 | The insurance gateway returns structured eligibility results, masks the SSN in logs, and degrades to a structured failure on timeout/interruption. | `src/main/java/com/medchart/ehr/service/InsuranceGateway.java:29` | covered |
| BR-SVC-15 | Claims get a CLM- reference and start PENDING. | `src/main/java/com/medchart/ehr/service/InsuranceGateway.java:56` | covered |
| BR-SVC-16 | Provider notifications are @Async, support EMAIL/SMS/IN_APP/ALL and report channel failures gracefully in the result instead of throwing. | `src/main/java/com/medchart/ehr/service/ProviderNotificationService.java:36` | covered |
| BR-SVC-17 | Critical alerts are audited (@AuditAccess); critical-alert escalation and care-team fan-out are documented stubs. | `src/main/java/com/medchart/ehr/service/ProviderNotificationService.java:87` | covered |
| BR-SVC-18 | New encounters get number ENC-<yyyy>-<seq> and status SCHEDULED. | `src/main/java/com/medchart/ehr/service/EncounterService.java:88` | covered |
| BR-SVC-19 | Encounter lifecycle: check-in -> IN_PROGRESS start -> COMPLETED (with notes), cancel, no-show; unknown ids are no-ops. | `src/main/java/com/medchart/ehr/service/EncounterService.java:103` | covered |
| BR-SVC-20 | Provider schedules cover the whole day and date ranges include the end date (exclusive next-day bound). | `src/main/java/com/medchart/ehr/service/EncounterService.java:72` | covered |
| BR-SVC-21 | Provider deactivation is a soft delete (active=false); provider search binds the term as a parameter. | `src/main/java/com/medchart/ehr/service/ProviderService.java:76` | covered |
| BR-SVC-22 | Login loads the user by username; unknown users raise UsernameNotFoundException. | `src/main/java/com/medchart/ehr/service/CustomUserDetailsService.java:22` | covered |
| BR-SVC-23 | Read-only encounter/provider lookups delegate to the repositories; providers see only their own encounters. | `src/main/java/com/medchart/ehr/service/EncounterService.java:45` | covered |

## `com.medchart.ehr.service.chronic`

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-CHR-01 | PDC for an empty or reversed measurement period is 0. | `src/main/java/com/medchart/ehr/service/chronic/MedicationAdherenceTracker.java:41` | covered |
| BR-CHR-02 | PDC for a real period is not implemented (stub throws); thresholds >=80% adherent, 50-79% partial, <50% non-adherent are documented only. | `src/main/java/com/medchart/ehr/service/chronic/MedicationAdherenceTracker.java:19` | open-question |
| BR-CHR-03 | Chronic medication/adherence PHI access methods are annotated @AuditAccess. | `src/main/java/com/medchart/ehr/service/chronic/ChronicMedicationService.java:22` | covered |
| BR-CHR-04 | Unimplemented chronic-care and pharmacy (NCPDP) operations throw UnsupportedOperationException instead of fabricating clinical data. | `src/main/java/com/medchart/ehr/service/chronic/PharmacyIntegrationService.java:46` | covered |
| BR-CHR-05 | Non-adherence alerts go IN_APP to the provider with PDC to one decimal and patient/medication metadata. | `src/main/java/com/medchart/ehr/service/chronic/MedicationNotificationService.java:43` | covered |
| BR-CHR-06 | Care-gap alerts notify the provider with the gap description. | `src/main/java/com/medchart/ehr/service/chronic/MedicationNotificationService.java:78` | covered |
| BR-CHR-07 | Critical lab values escalate as HIGH priority critical alerts. | `src/main/java/com/medchart/ehr/service/chronic/MedicationNotificationService.java:112` | covered |
| BR-CHR-08 | The daily digest is skipped when there is nothing to report and otherwise emailed with category totals. | `src/main/java/com/medchart/ehr/service/chronic/MedicationNotificationService.java:149` | covered |
| BR-CHR-09 | Refill reminders are a documented stub. | `src/main/java/com/medchart/ehr/service/chronic/MedicationNotificationService.java:131` | covered |
| BR-CHR-10 | Chronic data carriers (AtRiskPatient, CareGap, PharmacyFillData) preserve the values they are built with. | `src/main/java/com/medchart/ehr/service/chronic/MedicationAdherenceTracker.java:123` | covered |

## `com.medchart.ehr.mapper`

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-MAP-01 | Outbound FHIR resources are R4 Patient resources with the patient id as a string. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:43` | covered |
| BR-MAP-02 | MRN uses system http://hospital.example.org/mrn (use=official); SSN uses http://hl7.org/fhir/sid/us-ssn (use=secondary), empty when unknown. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:30` | open-question |
| BR-MAP-03 | The FHIR name is official with family and given [first, middle?]. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:158` | covered |
| BR-MAP-04 | Gender maps to FHIR AdministrativeGender codes both ways (case-insensitive); unknown/unrecognised codes map to UNKNOWN and round-trip unchanged. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:182` | defect-fixed |
| BR-MAP-05 | birthDate is ISO yyyy-MM-dd and omitted when unknown. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:29` | covered |
| BR-MAP-06 | Telecom contains home phone, mobile phone and email, only when present. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:191` | covered |
| BR-MAP-07 | Home address maps street lines, city, state, postalCode and country US. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:207` | covered |
| BR-MAP-08 | active is exported true only when explicitly active; inbound patients default to active. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:137` | covered |
| BR-MAP-09 | Deceased patients export deceasedBoolean and deceasedDateTime when known. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:73` | covered |
| BR-MAP-10 | Marital status uses HL7 v3 MaritalStatus codes S/M/D/W, otherwise UNK; omitted when null. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:236` | covered |
| BR-MAP-11 | Inbound identifiers are matched by system (others ignored); sparse names map to nulls. | `src/main/java/com/medchart/ehr/mapper/FhirPatientMapper.java:91` | covered |
| BR-MAP-12 | PatientDTO never exposes SSN; DTO->entity never trusts client id/createdAt/updatedAt/version. | `src/main/java/com/medchart/ehr/mapper/PatientMapper.java:18` | covered |
| BR-MAP-13 | Patient updates are partial: null DTO properties never overwrite stored values (NullValuePropertyMappingStrategy.IGNORE). | `src/main/java/com/medchart/ehr/mapper/PatientMapper.java:9` | covered |
| BR-MAP-14 | Null inputs map to null; sparse patients map to null nested values. | `src/main/java/com/medchart/ehr/mapper/PatientMapper.java:39` | covered |

## `com.medchart.ehr.interop.fhir`

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-FHIR-01 | A mapped HR row exports Practitioner (NPI system), PractitionerRole (Organization/dept-<id>), Schedule (actor) and a busy-unavailable Slot. | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ExportService.java:38` | covered |
| BR-FHIR-02 | Unmapped cost centers export no NPI system, organization or actor and pass PTO through unparsed. | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ExportService.java:117` | covered |
| BR-FHIR-03 | Slot instants carry the America/New_York offset valid on that date (DST aware: -04:00 / -05:00). | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ExportService.java:34` | defect-fixed |
| BR-FHIR-04 | Unparseable PTO text on a mapped row is exported verbatim so the contract validator flags it. | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ExportService.java:107` | covered |
| BR-FHIR-05 | Rows without PTO export no Slot; rows without NPI export only the HR identifier. | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ExportService.java:47` | covered |
| BR-FHIR-06 | Practitioner must have an identifier with the NPI system (ERROR otherwise). | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ContractValidator.java:74` | covered |
| BR-FHIR-07 | PractitionerRole must reference a department Organization (ERROR otherwise). | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ContractValidator.java:85` | covered |
| BR-FHIR-08 | Slot start/end must carry a timezone offset (ERROR otherwise). | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ContractValidator.java:90` | covered |
| BR-FHIR-09 | A resource that is not valid JSON is a FATAL finding. | `src/main/java/com/medchart/ehr/interop/fhir/FhirR4ContractValidator.java:70` | covered |
| BR-FHIR-10 | Only ERROR and FATAL findings count as errors; resource references are Type/id. | `src/main/java/com/medchart/ehr/interop/fhir/ContractFinding.java:33` | covered |
| BR-FHIR-11 | The contract PASSes only with no unknown cost centers, no duplicate practitioners and no FHIR errors; report.json/report.md/resources are written to the report dir (default target/interface-contract). | `src/main/java/com/medchart/ehr/interop/fhir/InterfaceContractReport.java:104` | covered |
| BR-FHIR-12 | Unknown cost centers are grouped by cost center and reported as AE errors. | `src/main/java/com/medchart/ehr/interop/fhir/InterfaceContractCheck.java:59` | covered |
| BR-FHIR-13 | The same practitioner under multiple HR ids (same normalized name OR same NPI) is a duplicate. | `src/main/java/com/medchart/ehr/interop/fhir/InterfaceContractCheck.java:69` | defect-fixed |

## `com.medchart.ehr.audit`

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-AUD-01 | Audited calls record the annotation's action, resource type, description and acting user (system default). | `src/main/java/com/medchart/ehr/audit/AuditAspect.java:25` | covered |
| BR-AUD-02 | The first Long argument of an audited call is recorded as the patient id. | `src/main/java/com/medchart/ehr/audit/AuditAspect.java:58` | covered |
| BR-AUD-03 | Successful access is recorded success=true and the result is returned unchanged. | `src/main/java/com/medchart/ehr/audit/AuditAspect.java:44` | covered |
| BR-AUD-04 | Failed access is still audited (success=false, error message) and the exception propagates. | `src/main/java/com/medchart/ehr/audit/AuditAspect.java:50` | covered |
| BR-AUD-05 | Client IP is the first X-Forwarded-For hop, else remote address; user agent captured; outside requests or on errors both are null and never block the call. | `src/main/java/com/medchart/ehr/audit/AuditAspect.java:78` | covered |
| BR-AUD-06 | Audit events are persisted asynchronously in their own transaction (REQUIRES_NEW) and persistence failures never break the clinical workflow. | `src/main/java/com/medchart/ehr/audit/AuditService.java:18` | covered |
| BR-AUD-07 | PHI access logs who, role, patient, MRN, action, reason, IP, session and time. | `src/main/java/com/medchart/ehr/audit/PatientAccessLogger.java:38` | covered |
| BR-AUD-08 | Denied access is logged as a failed event with the reason. | `src/main/java/com/medchart/ehr/audit/PatientAccessLogger.java:71` | covered |
| BR-AUD-09 | Bulk access is logged once with '[BULK: n records]'. | `src/main/java/com/medchart/ehr/audit/PatientAccessLogger.java:110` | covered |
| BR-AUD-10 | Patient access reports echo patient and period; aggregate counts are placeholder zeros (stub). | `src/main/java/com/medchart/ehr/audit/PatientAccessLogger.java:130` | covered |
| BR-AUD-11 | Prescriptions are audited as MedicationOrder CREATE; controlled substances include the DEA schedule. | `src/main/java/com/medchart/ehr/audit/MedicationAuditLogger.java:31` | covered |
| BR-AUD-12 | Prescription changes are UPDATE (old->new) and cancellations DELETE (with reason). | `src/main/java/com/medchart/ehr/audit/MedicationAuditLogger.java:69` | covered |
| BR-AUD-13 | E-prescribe transmissions are EXPORT events, successful only for SUCCESS/ACCEPTED. | `src/main/java/com/medchart/ehr/audit/MedicationAuditLogger.java:127` | covered |
| BR-AUD-14 | PDMP checks are Patient READ events; medication history access is MedicationHistory READ with source and count. | `src/main/java/com/medchart/ehr/audit/MedicationAuditLogger.java:156` | covered |
| BR-AUD-15 | Audit events default to success=true; the action vocabulary includes ACCESS_DENIED, EMERGENCY_ACCESS, BREAK_THE_GLASS, EXPORT, PRINT. | `src/main/java/com/medchart/ehr/audit/AuditAction.java:15` | covered |

## `com.medchart.ehr.legacy`

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-LEG-01 | Eligibility is cached per patient+payer with its cache time; re-caching replaces. | `src/main/java/com/medchart/ehr/legacy/InsuranceCache.java:16` | covered |
| BR-LEG-02 | Clearing a patient's cache removes only that patient's entries (no MRN prefix collisions). | `src/main/java/com/medchart/ehr/legacy/InsuranceCache.java:44` | covered |
| BR-LEG-03 | Legacy MRN/SSN lookups bind parameters and return null when not found (bypasses the audit layer - AGENTS.md gap #3). | `src/main/java/com/medchart/ehr/legacy/LegacyPatientLookup.java:21` | open-question |
| BR-LEG-04 | Legacy name/free-text searches are case-insensitive contains matches with bound parameters and exclude SSN. | `src/main/java/com/medchart/ehr/legacy/LegacyPatientLookup.java:77` | covered |
| BR-LEG-05 | Legacy demographics are returned keyed by field name for the requested id. | `src/main/java/com/medchart/ehr/legacy/LegacyPatientLookup.java:51` | covered |
| BR-LEG-06 | The encounter summary report prints DOB and provider name in the labelled fields and never the SSN. | `src/main/java/com/medchart/ehr/legacy/ReportGenerator.java:91` | defect-fixed |
| BR-LEG-07 | Date-range encounter CSV covers the whole end day (exclusive next-day bound) and excludes SSN. | `src/main/java/com/medchart/ehr/legacy/EncounterExportService.java:24` | covered |
| BR-LEG-08 | Encounter history exports show only the SSN's last four digits (XXX-XX-nnnn). | `src/main/java/com/medchart/ehr/legacy/EncounterExportService.java:87` | defect-fixed |
| BR-LEG-09 | The daily report returns the active-patient roster and deletes the PHI temp file (AGENTS.md gap #5). | `src/main/java/com/medchart/ehr/legacy/ReportGenerator.java:152` | defect-fixed |
| BR-LEG-10 | Department productivity binds department/provider ids as parameters (no SQL concatenation). | `src/main/java/com/medchart/ehr/legacy/ReportGenerator.java:118` | defect-fixed |
| BR-LEG-11 | HR PTO rows with an unknown cost center are rejected with an AE error and the run is PARTIAL. | `docs/interfaces/hr-pto-sync.md:41` | covered |
| BR-LEG-12 | HR PTO rows without PTO or not Approved are skipped. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:95` | covered |
| BR-LEG-13 | One SIU^S15 is sent per PTO day that falls on one of the surgeon's OR block weekdays, with control id HRPTO<date><seq5>. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:103` | covered |
| BR-LEG-14 | OR blocks load from or_block by HR employee id; without a datasource none are loaded. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:208` | covered |
| BR-LEG-15 | Each run is appended to runs.log and errors to error-queue.log (AE/AR codes); malformed lines are ignored; write failures are logged, not fatal. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:290` | covered |
| BR-LEG-16 | '--hr-pto-sync[=date]' runs once for that date (or latest); the nightly 02:00 job runs the latest export. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:62` | covered |
| BR-LEG-17 | PTO periods: ISO ranges inclusive, invalid ISO end = single day, free-text m/d[-m/d] uses the first date token, blank = none. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:128` | covered |
| BR-LEG-18 | SIU NTE omits a blank PTO end; AIP carries the NPI marker only when an NPI exists. | `src/main/java/com/medchart/ehr/legacy/HrPtoSyncJob.java:175` | covered |
| BR-LEG-19 | HR export reader: header/blank/malformed (<9 fields) lines skipped, quoted fields unwrapped, notes optional, dir override, latest resolution. | `src/main/java/com/medchart/ehr/legacy/HrExportReader.java:77` | covered |
| BR-LEG-20 | Cost-center mapping is trimmed and exact; unknown departments have blank names. | `src/main/java/com/medchart/ehr/legacy/DepartmentMapping.java:58` | covered |
| BR-LEG-21 | HR approval is case-insensitive; blank PTO start means no PTO. | `src/main/java/com/medchart/ehr/legacy/HrPtoRecord.java:21` | covered |
| BR-LEG-22 | The all-patients file export contains active patients' demographics without SSN and fails loudly when unwritable. | `src/main/java/com/medchart/ehr/legacy/EncounterExportService.java:96` | covered |
