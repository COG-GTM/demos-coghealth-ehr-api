// Read-only load profile for the migration performance baseline (docs/migration/baseline.md).
//
//   k6 run -e PROFILE=latency    -e RATE=200 -e DURATION=60s perf/k6/baseline.js
//   k6 run -e PROFILE=throughput -e VUS=32   -e DURATION=60s perf/k6/baseline.js
//
// PROFILE=warmup     constant 50 req/s for 30s; results are not recorded.
// PROFILE=latency    open model: constant RATE req/s, reports p50/p95/p99 at a fixed load.
// PROFILE=throughput closed model: VUS virtual users with no think time, reports max req/s.
//
// Endpoint IDs default to the rows seeded by Flyway V3__seed_data.sql on an empty database.
// Encounter 15 is excluded: its seed row has encounter_type 'IN_PROGRESS', which is not an
// EncounterType constant, so GET /v1/encounters/15 fails (recorded in docs/migration/baseline.md).
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const PROFILE = __ENV.PROFILE || 'latency';
const RATE = parseInt(__ENV.RATE || '200', 10);
const VUS = parseInt(__ENV.VUS || '32', 10);
const DURATION = __ENV.DURATION || '60s';
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

const PATIENT_IDS = range(__ENV.PATIENT_IDS || '1-15');
const ENCOUNTER_IDS = range(__ENV.ENCOUNTER_IDS || '1-14,16-18');
const PROVIDER_IDS = range(__ENV.PROVIDER_IDS || '1-8');
const SEARCH_TERMS = ['smith', 'garcia', 'an', 'john', 'mar'];

const ENDPOINTS = [
  { name: 'GET /v1/encounters/{id}', weight: 30, path: () => `/v1/encounters/${pick(ENCOUNTER_IDS)}` },
  { name: 'GET /v1/patients/{id}', weight: 20, path: () => `/v1/patients/${pick(PATIENT_IDS)}` },
  { name: 'GET /v1/patients/mrn/{mrn}', weight: 10, path: (d) => `/v1/patients/mrn/${pick(d.mrns)}` },
  { name: 'GET /v1/patients/search', weight: 20, path: () => `/v1/patients/search?q=${pick(SEARCH_TERMS)}&size=10` },
  { name: 'GET /v1/providers', weight: 10, path: () => '/v1/providers' },
  { name: 'GET /v1/providers/{id}', weight: 10, path: () => `/v1/providers/${pick(PROVIDER_IDS)}` },
];
const TOTAL_WEIGHT = ENDPOINTS.reduce((s, e) => s + e.weight, 0);

const SCENARIOS = {
  warmup: {
    executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: '30s',
    preAllocatedVUs: 20, maxVUs: 100,
  },
  latency: {
    executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
    preAllocatedVUs: 50, maxVUs: 400,
  },
  throughput: {
    executor: 'constant-vus', vus: VUS, duration: DURATION,
  },
};
if (!SCENARIOS[PROFILE]) {
  throw new Error(`Unknown PROFILE ${PROFILE}; expected one of ${Object.keys(SCENARIOS).join(', ')}`);
}

const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  [`http_req_duration{scenario:${PROFILE}}`]: ['max>=0'],
  [`http_reqs{scenario:${PROFILE}}`]: ['count>=0'],
};
for (const e of ENDPOINTS) {
  // Declaring a threshold per endpoint makes k6 report the tagged sub-metric in the summary.
  thresholds[`http_req_duration{endpoint:${e.name}}`] = ['max>=0'];
  thresholds[`http_reqs{endpoint:${e.name}}`] = ['count>=0'];
}

export const options = {
  scenarios: { [PROFILE]: SCENARIOS[PROFILE] },
  thresholds,
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: true,
};

function range(spec) {
  const out = [];
  for (const part of spec.split(',')) {
    const [lo, hi] = part.split('-').map((n) => parseInt(n, 10));
    for (let i = lo; i <= (hi || lo); i++) out.push(i);
  }
  return out;
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function params(name) {
  const headers = { Accept: 'application/json' };
  if (AUTH_TOKEN) headers.Authorization = `Bearer ${AUTH_TOKEN}`;
  return { headers, tags: { endpoint: name, name } };
}

export function setup() {
  const mrns = [];
  for (const id of PATIENT_IDS) {
    const res = http.get(`${BASE_URL}/v1/patients/${id}`, { ...params('setup'), responseType: 'text' });
    if (res.status !== 200) fail(`setup: GET /v1/patients/${id} returned ${res.status}`);
    mrns.push(res.json('mrn'));
  }
  for (const id of ENCOUNTER_IDS) {
    const res = http.get(`${BASE_URL}/v1/encounters/${id}`, params('setup'));
    if (res.status !== 200) fail(`setup: GET /v1/encounters/${id} returned ${res.status}`);
  }
  for (const id of PROVIDER_IDS) {
    const res = http.get(`${BASE_URL}/v1/providers/${id}`, params('setup'));
    if (res.status !== 200) fail(`setup: GET /v1/providers/${id} returned ${res.status}`);
  }
  const data = { mrns };
  for (const e of ENDPOINTS) {
    const res = http.get(`${BASE_URL}${e.path(data)}`, params('setup'));
    if (res.status !== 200) fail(`setup: ${e.name} returned ${res.status}; the load profile needs every endpoint to return 200`);
  }
  return data;
}

export default function (data) {
  let r = Math.random() * TOTAL_WEIGHT;
  let e = ENDPOINTS[ENDPOINTS.length - 1];
  for (const candidate of ENDPOINTS) {
    r -= candidate.weight;
    if (r < 0) { e = candidate; break; }
  }
  const res = http.get(`${BASE_URL}${e.path(data)}`, params(e.name));
  check(res, { 'status is 200': (x) => x.status === 200 });
}

function seconds(d) {
  const m = /^(\d+)(s|m)$/.exec(d);
  if (!m) throw new Error(`DURATION must look like 60s or 5m, got ${d}`);
  return parseInt(m[1], 10) * (m[2] === 'm' ? 60 : 1);
}

function fmt(v) {
  return v === undefined ? '-' : v.toFixed(2);
}

export function handleSummary(data) {
  const lines = [`profile=${PROFILE} rate=${PROFILE === 'latency' ? RATE : '-'} vus=${PROFILE === 'throughput' ? VUS : '-'} duration=${DURATION}`];
  const row = (label, dur, reqs) => lines.push(
    `${label.padEnd(34)} reqs=${String(reqs ? reqs.values.count : '-').padStart(7)}  ` +
    `p50=${fmt(dur.values.med)}ms p95=${fmt(dur.values['p(95)'])}ms p99=${fmt(dur.values['p(99)'])}ms max=${fmt(dur.values.max)}ms`);
  const all = data.metrics[`http_reqs{scenario:${PROFILE}}`];
  row('ALL', data.metrics[`http_req_duration{scenario:${PROFILE}}`], all);
  for (const e of ENDPOINTS) {
    row(e.name, data.metrics[`http_req_duration{endpoint:${e.name}}`], data.metrics[`http_reqs{endpoint:${e.name}}`]);
  }
  const duration = PROFILE === 'warmup' ? 30 : seconds(DURATION);
  lines.push(`throughput=${fmt(all.values.count / duration)} req/s  failed=${fmt(data.metrics.http_req_failed.values.rate * 100)}%`);
  const out = { stdout: lines.join('\n') + '\n' };
  if (SUMMARY_OUT) out[SUMMARY_OUT] = JSON.stringify(data, null, 2);
  return out;
}
