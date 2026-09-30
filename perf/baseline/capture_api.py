#!/usr/bin/env python3
"""Capture the API contract of a running service for docs/migration/baseline.md.

    python3 perf/baseline/capture_api.py http://localhost:8080/api docs/migration/baseline/java11/api

Writes:
  openapi.json    springdoc output of /v3/api-docs, keys sorted so runs diff cleanly
  responses.json  one entry per request: method, path, synthetic request body, status,
                  content type and a PHI-free view of the response body

Response bodies are reduced to their shape (every scalar replaced by its JSON type name,
arrays reduced to their first element plus a length) so seeded patient data never lands in
the repo. Spring error bodies keep their non-identifying fields, CSV bodies keep only their
header row, and multi-line text reports keep only their first line and each line's "Label:"
prefix. GET requests run first; the mutating requests at the end create their own synthetic rows
or use the SCHEDULED seed encounters 16-18, so run this after the load test, against a
disposable database.
"""
import json
import sys
import urllib.error
import urllib.request

BASE, OUT = sys.argv[1].rstrip('/'), sys.argv[2]
ERROR_KEYS = {'status', 'error', 'path', 'message'}


def shape(v):
    if isinstance(v, dict):
        return {k: shape(x) for k, x in v.items()}
    if isinstance(v, list):
        return {'array_length': len(v), 'item': shape(v[0]) if v else None}
    if v is None:
        return 'null'
    if isinstance(v, bool):
        return 'boolean'
    if isinstance(v, (int, float)):
        return 'number'
    return 'string'


def call(method, path, body=None, raw_body=None, content_type='application/json'):
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    elif raw_body is not None:
        data = raw_body.encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Accept', 'application/json, text/plain, */*')
    if data is not None:
        req.add_header('Content-Type', content_type)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            status, headers, payload = r.status, r.headers, r.read()
    except urllib.error.HTTPError as e:
        status, headers, payload = e.code, e.headers, e.read()
    return status, headers, payload


def view(status, ctype, payload):
    if not payload:
        return {'body': None}
    text = payload.decode('utf-8', 'replace')
    if 'json' in (ctype or ''):
        try:
            doc = json.loads(text)
        except ValueError:
            doc = None
        if doc is not None:
            if status >= 400 and isinstance(doc, dict) and 'timestamp' in doc:
                return {'body': {k: (v if k in ERROR_KEYS else shape(v)) for k, v in doc.items()}}
            if isinstance(doc, dict) and set(doc) == {'names'}:
                return {'body': doc}
            return {'body_shape': shape(doc)}
    lines = text.splitlines()
    if 'csv' in (ctype or ''):
        return {'body_first_line': lines[0], 'body_lines': len(lines)}
    if len(lines) == 1 and len(text) < 200:
        return {'body_text': text, 'body_is_json': False}
    labels = list(dict.fromkeys(l.split(':')[0].strip() for l in lines if ':' in l))
    return {'body_first_line': lines[0], 'body_line_labels': labels, 'body_lines': len(lines)}


records = []


def record(method, path, body=None, raw_body=None, content_type='application/json', note=None):
    status, headers, payload = call(method, path, body, raw_body, content_type)
    ctype = headers.get('Content-Type')
    entry = {'method': method, 'path': path}
    if body is not None:
        entry['request_body'] = body
    if raw_body is not None:
        entry['request_body_text'] = raw_body
    entry.update({'status': status, 'content_type': ctype})
    if headers.get('Content-Disposition'):
        entry['content_disposition'] = headers.get('Content-Disposition')
    entry.update(view(status, ctype, payload))
    if note:
        entry['note'] = note
    records.append(entry)
    print(f'{status} {method} {path}')
    return status, payload


def get_json(path):
    status, _, payload = call('GET', path)
    if status != 200:
        raise SystemExit(f'GET {path} returned {status}')
    return json.loads(payload)


status, _, api_docs = call('GET', '/v3/api-docs')
if status != 200:
    raise SystemExit(f'/v3/api-docs returned {status}')
with open(f'{OUT}/openapi.json', 'w') as f:
    json.dump(json.loads(api_docs), f, indent=2, sort_keys=True)
    f.write('\n')

patient = get_json('/v1/patients/1')
provider = get_json('/v1/providers/1')
encounter = get_json('/v1/encounters/1')

# ---- read-only -------------------------------------------------------------------------------
record('GET', '/actuator/health')
record('GET', '/actuator/info')
record('GET', '/actuator/metrics', note='metric names kept verbatim')
record('GET', '/actuator/prometheus', note='prometheus endpoint is not exposed')
record('GET', '/v3/api-docs', note='full document in openapi.json')

record('GET', '/v1/patients/1')
record('GET', '/v1/patients/999999', note='EntityNotFoundException has no handler: 500, not 404')
record('GET', f"/v1/patients/mrn/{patient['mrn']}")
record('GET', '/v1/patients/mrn/UNKNOWN-MRN')
record('GET', '/v1/patients/search?q=a&page=0&size=5')
record('GET', '/v1/patients/search')

record('GET', '/v1/providers')
record('GET', '/v1/providers?active=true')
record('GET', '/v1/providers/1')
record('GET', '/v1/providers/999999')
record('GET', f"/v1/providers/npi/{provider['npi']}")
record('GET', '/v1/providers/npi/0000000000')
record('GET', f"/v1/providers/department/{urllib.request.quote(provider['department'])}")
record('GET', f"/v1/providers/specialty/{urllib.request.quote(provider['specialty'])}")
record('GET', '/v1/providers/departments')
record('GET', '/v1/providers/specialties')
record('GET', '/v1/providers/search?lastName=a')
record('GET', '/v1/providers/search')

record('GET', '/v1/encounters/1')
record('GET', '/v1/encounters/15', note="seed row 15 has encounter_type 'IN_PROGRESS', not an EncounterType")
record('GET', '/v1/encounters/999999')
record('GET', f"/v1/encounters/number/{encounter['encounterNumber']}")
record('GET', '/v1/encounters/number/ENC-0000-000000')
record('GET', '/v1/encounters/patient/1')
record('GET', '/v1/encounters/patient/1/paged?page=0&size=5')
record('GET', '/v1/encounters/provider/1')
record('GET', '/v1/encounters/provider/2/schedule?date=2024-03-20')
record('GET', '/v1/encounters/provider/2/schedule')
record('GET', '/v1/encounters/date-range?startDate=2024-01-01&endDate=2024-12-31')
record('GET', '/v1/encounters/date-range?startDate=2024-01-01&endDate=2024-01-31')
record('GET', '/v1/encounters/status/SCHEDULED')
record('GET', '/v1/encounters/status/COMPLETED')
record('GET', '/v1/encounters/status/BOGUS')

record('GET', '/v1/export/encounters?startDate=2024-01-01&endDate=2024-12-31')
record('GET', '/v1/export/encounters')
record('GET', '/v1/export/patient/1/encounters')
record('GET', '/v1/export/reports/patient-roster')
record('GET', '/v1/export/reports/encounter-summary?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59')
record('GET', '/v1/export/reports/daily')

# ---- auth ------------------------------------------------------------------------------------
# AuthController is mapped to /api/auth under the /api context path, so the URL is /api/api/auth/*.
signup = {'username': 'baseline_probe', 'email': 'baseline.probe@example.invalid', 'password': 'Baseline-Probe-1',
          'firstName': 'Baseline', 'lastName': 'Probe'}
record('POST', '/auth/register', signup, note='not mapped: the controller is at /api/auth under the context path')
record('POST', '/api/auth/register', signup)
record('POST', '/api/auth/register', signup, note='duplicate username')
record('POST', '/api/auth/login', {'username': 'baseline_probe', 'password': 'Baseline-Probe-1'},
       note='needs an HS512-sized medchart.security.jwt.secret; the application.yml default fails with WeakKeyException')
record('POST', '/api/auth/login', {'username': 'baseline_probe', 'password': 'wrong-password'})
record('POST', '/auth/login', {'username': 'baseline_probe', 'password': 'Baseline-Probe-1'},
       note='not mapped: the controller is at /api/auth under the context path')

# ---- mutating (synthetic data) ---------------------------------------------------------------
new_patient = {'firstName': 'Synthetic', 'lastName': 'Baseline', 'dateOfBirth': '1980-01-01', 'gender': 'UNKNOWN',
               'email': 'synthetic.baseline@example.invalid'}
record('POST', '/v1/patients', new_patient,
       note='the entity inserts NULL for active and deceased instead of the column defaults: NOT NULL violation')
new_patient.update(active=True, deceased=False)
status, payload = record('POST', '/v1/patients', new_patient)
record('POST', '/v1/patients', {'firstName': '', 'lastName': ''}, note='bean validation failure')
if status == 201:
    pid = json.loads(payload)['id']
    record('PUT', f'/v1/patients/{pid}', dict(new_patient, middleName='Updated'))
record('PUT', '/v1/patients/999999', new_patient)

new_provider = {'npi': '9999999901', 'firstName': 'Synthetic', 'lastName': 'Provider', 'providerType': 'PHYSICIAN',
                'specialty': 'Baseline', 'department': 'Baseline'}
status, payload = record('POST', '/v1/providers', new_provider)
if status == 200:
    created = json.loads(payload)
    prov_id = created['id']
    record('PUT', f'/v1/providers/{prov_id}', dict(new_provider, credentials='MD'),
           note='without version the entity is treated as new and re-inserted: unique violation on providers.npi')
    record('PUT', f'/v1/providers/{prov_id}', dict(new_provider, credentials='MD', version=created['version']))
    record('DELETE', f'/v1/providers/{prov_id}')
record('PUT', '/v1/providers/999999', new_provider)

new_encounter = {'patient': {'id': 1}, 'attendingProvider': {'id': 1}, 'encounterType': 'OFFICE_VISIT',
                 'encounterDateTime': '2030-01-02T09:00:00', 'chiefComplaint': 'Synthetic baseline visit'}
record('POST', '/v1/encounters', new_encounter,
       note='patient/provider references without version are treated as transient entities (HHH000437)')
# PatientDTO does not expose version; the seed rows are at version 0.
new_encounter['patient']['version'] = patient.get('version', 0)
new_encounter['attendingProvider']['version'] = provider['version']
status, payload = record('POST', '/v1/encounters', new_encounter)
if status == 200:
    enc = json.loads(payload)
    update = dict(new_encounter, encounterNumber=enc['encounterNumber'], status='SCHEDULED', location='Room 1')
    record('PUT', f"/v1/encounters/{enc['id']}", update,
           note='without version the entity is re-inserted: unique violation on encounters.encounter_number')
    record('PUT', f"/v1/encounters/{enc['id']}", dict(update, version=enc['version']))
record('PUT', '/v1/encounters/999999', new_encounter)

record('POST', '/v1/encounters/16/check-in')
record('POST', '/v1/encounters/16/start')
record('POST', '/v1/encounters/16/complete', raw_body='Synthetic completion note', content_type='text/plain')
record('POST', '/v1/encounters/17/cancel')
record('POST', '/v1/encounters/18/no-show')
record('POST', '/v1/encounters/999999/check-in', note='unknown ids are ignored: 200 with no body')

with open(f'{OUT}/responses.json', 'w') as f:
    json.dump({'base_url': BASE, 'note': 'paths are relative to base_url (context path /api)',
               'requests': records}, f, indent=2)
    f.write('\n')
print(f'{len(records)} requests recorded in {OUT}/responses.json')
