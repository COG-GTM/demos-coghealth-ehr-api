# hr-pto-sync — provider time off to OR schedule

Nightly interface job that turns approved Workday time off for perioperative providers into
OR block holds in the EHR scheduling module (OpTime feed via IE-Prod).

## Data flow

```
Workday PTO export (CSV, daily)                       src/main/resources/interfaces/hr/export/workday_pto_export_<yyyyMMdd>.csv
        |
        v
HrExportReader            parse rows (employee_id, names, npi, cost_center, pto_start, pto_end, pto_type, status, notes)
        |
        v
DepartmentMapping         cost_center -> EHR department id        src/main/resources/interfaces/hr/cost_center_department_map.csv
        |                 (DB copy: department_cost_center_map)
        v
HrPtoSyncJob              per approved PTO day x OR block (or_block via provider_hr_identity):
        |                 HL7 v2.5 SIU^S15 (cancel/hold) to IE-Prod outbound queue
        v
target/interfaces/ie-prod/outbound/<exportDate>/<controlId>.hl7
target/interfaces/ie-prod/error-queue.log      rejected rows
target/interfaces/ie-prod/runs.log             one JSON line per run
```

Output directory is `hr.pto.sync.out-dir` (default `target/interfaces/ie-prod`).
Export directory can be overridden with `-Dhr.export.dir=<path>` (default: classpath `interfaces/hr/export`).

## Schedule

| Trigger | When |
|---|---|
| `@Scheduled(cron = "0 0 2 * * *")` | 02:00 server time, latest export file |
| `--hr-pto-sync[=yyyyMMdd]` application argument | once at startup (CommandLineRunner) |
| `POST /api/interfaces/hr-pto-sync/run?exportDate=yyyyMMdd` | on demand |

## Error codes

| Code | Text | Cause | Action |
|---|---|---|---|
| AE | `Unknown department for cost center <cc>` | no row for the cost center in `cost_center_department_map.csv` effective on the export date | add row (with `effective_from`; end-date the superseded row with `effective_to`, inclusive) and re-run for the export date |

Cost centers are resolved as of the export date (`DepartmentMapping.lookupForExport`), so a row with
`effective_to = 2026-09-30` still resolves for the 20260930 export but not for 20261001.

Rows without `pto_start` or with status other than `Approved` are skipped (counted as `recordsSkipped`).
Rows for an employee with no `or_block` produce no messages.

## Endpoints

| Method | Path | Returns |
|---|---|---|
| GET | `/api/interfaces/hr-pto-sync/errors` | error queue entries `{timestamp, exportDate, employeeId, code, message}` |
| GET | `/api/interfaces/hr-pto-sync/runs` | run log entries `{exportDate, records, messagesSent, recordsRejected, recordsSkipped, status}` |
| GET | `/api/interfaces/hr-pto-sync/exports` | available export dates |
| POST | `/api/interfaces/hr-pto-sync/run` | run result for one export date |
| GET | `/api/interfaces/fhir/contract-report?exportDate=` | contract report (see below) as JSON |

## Interface contract check

`com.medchart.ehr.interop.fhir.InterfaceContractCheck` converts one export to FHIR R4
(Practitioner, PractitionerRole, Schedule, Slot) and validates it with the HAPI FHIR R4 instance
validator plus the identifier / reference rules of the downstream Practitioner and Scheduling interfaces.
Report: `target/interface-contract/report.json`, `report.md`, exported resources under `resources/`.

FHIR export rules (`FhirR4ExportService`):

- one Practitioner per person (`PractitionerDeduplicator`): rows are merged by NPI, otherwise by normalized
  name (upper case, letters only) plus an HR identity link (Workday cross-reference in notes, e.g.
  `legacy record - see E104422`) when the NPIs do not conflict. The Practitioner carries the NPI
  (`http://hl7.org/fhir/sid/us-npi`) and every HR employee id (`urn:coghealth:workday:employee-id`).
- PractitionerRole.organization = `Organization/dept-<department_id>` effective on the export date.
- Schedule.actor = the PractitionerRole.
- one Slot (`busy-unavailable`) per distinct approved PTO period, instants in America/New_York
  (`PtoPeriodParser`): `pto_start`/`pto_end` as ISO dates (00:00:00 to 23:59:59); free text left in
  `pto_start` (`10/13-10/17 vacation`, `Thu 10/22, half day AM`; AM = 00:00-12:00, PM = 12:00-23:59:59);
  `notes` are parsed only when both columns are empty. Month/day without year takes the export year,
  rolled forward when it would be more than 6 months before the export date.

Checks (one JUnit test each, `HrPtoInterfaceContractTest`, tag `interface-contract`):

1. every cost center in the export resolves to a department
2. FHIR R4 validation has zero errors
3. no duplicate practitioners (after de-duplication, no two Practitioners share an NPI or normalized name)

Run locally:

```bash
scripts/interface-contract.sh            # latest export
scripts/interface-contract.sh 20260930   # specific export date
# equivalent
mvn -B test -Dgroups=interface-contract -Dhr.export.date=20260930
```

The tag is excluded from the default `mvn test`, so the `build` check on pull requests is unaffected.

CI: `.github/workflows/nightly-interface-contract.yml` (job `build`) runs on a schedule, on
`workflow_dispatch` (input `export_date`, default latest) and on pull requests touching
`src/main/resources/interfaces/**`; the report directory is uploaded as artifact `interface-contract`.

## Database objects (V4)

| Table | Purpose |
|---|---|
| `provider_hr_identity` | `providers.id` <-> Workday `hr_employee_id`, `npi` (nullable) |
| `or_block` | weekly OR block per provider: `department_id`, `weekday`, `start_time`, `end_time`, `room` |
| `department_cost_center_map` | `cost_center` -> `department_id`, `effective_from`, `effective_to` |

## Change record

| Date | Trigger | What changed | How verified |
|---|---|---|---|
| 2026-10-08 | Nightly interface contract (`build`) failed on main @ `013cf50` for export 20261008: 4 unknown cost centers, 1 duplicate practitioner, 25 FHIR R4 errors | Workday cost center restructure effective 2026-10-01 (CC-200-5410/5411 -> CC-230-5410/5411, CC-200-5412/5420 -> CC-240-5412/5420, same EHR departments) added as effective-dated rows in `cost_center_department_map.csv` and `department_cost_center_map` (V5; old rows end-dated 2026-09-30); lookups are as of the export date. FHIR export: NPI identifier system always set, PractitionerRole linked to department, Schedule.actor always set, Slot instants in America/New_York from `PtoPeriodParser`, practitioners de-duplicated by NPI then name + HR identity. | `mvn -B test` (new unit tests per rule) and `mvn -B test -Dgroups=interface-contract -Dhr.export.date=<d>` PASS for every export 20260928-20261008 |
