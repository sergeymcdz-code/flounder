import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const base = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
// Load tests are restricted to explicitly local targets, never the demo server.
if (!/^https?:\/\/(localhost|127\.0\.0\.1|api)(:\d+)?$/.test(base)) {
  throw new Error('BASE_URL must point to localhost, 127.0.0.1 or the local api container');
}
const root = `/api/v1/public/events/${encodeURIComponent(__ENV.SLUG)}`;
const paths = [root, ...['start-list', 'schedule', 'live', 'results'].map(p => `${root}/${p}`),
  `${root}/athletes?q=${encodeURIComponent(__ENV.ATHLETE_QUERY || 'Ива')}`];
const names = ['event', 'start-list', 'schedule', 'live', 'results', 'athletes'];
const thresholds = { checks: ['rate==1'], http_req_duration: ['p(95)<300'], http_req_failed: ['rate<0.005'], dropped_iterations: ['count==0'] };
for (const name of names) {
  thresholds[`http_req_duration{name:${name}}`] = ['p(95)<300'];
  thresholds[`http_reqs{name:${name}}`] = ['count>0'];
  thresholds[`http_req_failed{name:${name}}`] = ['rate<0.005'];
}
export const options = {
  scenarios: { reads: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: '2m',
    preAllocatedVUs: 50, maxVUs: 200, gracefulStop: '30s' } },
  thresholds,
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (!__ENV.SLUG) throw new Error('SLUG is required');
}

export default function (data) {
  const i = exec.scenario.iterationInTest % paths.length;
  const response = http.get(`${base}${paths[i]}`, {
    tags: { name: names[i] },
  });
  check(response, { 'HTTP 200': r => r.status === 200 });
}
