import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const base = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
// Load tests are restricted to explicitly local targets, never the demo server.
if (!/^https?:\/\/(localhost|127\.0\.0\.1|api)(:\d+)?$/.test(base)) {
  throw new Error('BASE_URL must point to localhost, 127.0.0.1 or the local api container');
}
const event = `/api/v1/events/${__ENV.EVENT_ID}`;
const discipline = `/api/v1/event-disciplines/${__ENV.DISCIPLINE_ID}`;
const paths = ['/api/v1/organizer/events', event, `${event}/registrations`, `${event}/disciplines`,
  `${discipline}/competition-units`, `${discipline}/results`, `${event}/analytics`];
const names = ['events', 'event', 'registrations', 'disciplines', 'competition-units', 'results', 'analytics'];
const thresholds = { checks: ['rate==1'], http_req_duration: ['p(95)<500'], http_req_failed: ['rate<0.005'], dropped_iterations: ['count==0'] };
for (const name of names) {
  thresholds[`http_req_duration{name:${name}}`] = ['p(95)<500'];
  thresholds[`http_reqs{name:${name}}`] = ['count>0'];
  thresholds[`http_req_failed{name:${name}}`] = ['rate<0.005'];
}
export const options = {
  scenarios: { reads: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '2m',
    preAllocatedVUs: 10, maxVUs: 40, gracefulStop: '30s' } },
  thresholds,
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  for (const key of ['EVENT_ID', 'DISCIPLINE_ID', 'ORGANIZER_EMAIL', 'ORGANIZER_PASSWORD']) {
    if (!__ENV[key]) throw new Error(`${key} is required`);
  }
  const response = http.post(`${base}/api/v1/auth/login`, JSON.stringify({
    email: __ENV.ORGANIZER_EMAIL, password: __ENV.ORGANIZER_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } });
  if (response.status !== 200 || !response.json('accessToken')) throw new Error('Organizer login failed');
  return { token: response.json('accessToken') };
}

export default function (data) {
  const i = exec.scenario.iterationInTest % paths.length;
  const response = http.get(`${base}${paths[i]}`, {
    tags: { name: names[i] },
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(response, { 'HTTP 200': r => r.status === 200 });
}
