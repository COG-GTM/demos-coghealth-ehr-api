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
| AE | `Unknown department for cost center <cc>` | cost center not in `cost_center_department_map.csv` | add row (with `effective_from`) and re-run for the export date |

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

Checks (one JUnit test each, `HrPtoInterfaceContractTest`, tag `interface-contract`):

1. every cost center in the export resolves to a department
2. FHIR R4 validation has zero errors
3. no duplicate practitioners (same NPI or normalized name under several HR employee ids)

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
