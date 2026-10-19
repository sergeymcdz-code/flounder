import { describe, expect, it, vi } from 'vitest';
import {
  accounts, disciplineBody, disciplineSpecs, importBody, loginBody, memberBody,
  samaraDate, demoEventTitle, organizationBody, regulationTemplateBody, regulationVersionBody, roleBody,
  sessionBodies, templateBody, userBody, validateDemoDate, venueBody,
} from './bodies.ts';
import { config } from './config.ts';

import { DEFAULT_OWNER_EMAIL, demoEvents, eventDates } from './plan.ts';
describe('request bodies: existing API and specification §5', () => {
  it('takes passwords from its caller, and assigns roles and membership separately', () => {
    for (const account of accounts) {
      expect(userBody(account, 'unit-test-password')).toMatchObject({ email: account.email, password: 'unit-test-password', birthDate: '1990-01-01' });
      expect(userBody(account, 'unit-test-password')).not.toHaveProperty('roles');
      expect(roleBody(account.role)).toEqual({ roleCode: account.role });
      if (account.memberRole) expect(memberBody('user-id', account.memberRole)).toEqual({ userId: 'user-id', memberRole: account.memberRole });
    }
    expect(loginBody('test@example.invalid', 'unit-test-password')).toEqual({ email: 'test@example.invalid', password: 'unit-test-password' });
  });
  it('always includes metadata supported by T01', () => {
    expect(organizationBody()).toMatchObject({ type: 'federation', contactEmail: 'org@flounder.demo' });
    expect(venueBody()).toMatchObject({ timezone: 'Europe/Samara' });
    expect(venueBody()).toHaveProperty('venueMeta', { lanes: 8, courseLength: 25 });
  });
  it('accepts a Monday demo date and converts timestamps to Samara independently of the host', () => {
    expect(samaraDate('2026-10-16T22:00:00Z')).toBe('2026-10-17');
    expect(samaraDate('2026-10-17T00:30:00+04:00')).toBe('2026-10-17');
    expect(() => samaraDate('invalid')).toThrow();
    expect(validateDemoDate('2026-10-19')).toBe('2026-10-19');
    expect(() => validateDemoDate('2026-02-31')).toThrow();
  });
  it.each(['2026-10-18T12:00:00Z', '2026-10-17T05:31:00Z', '2026-10-17T05:30:00Z'])
  ('keeps demo-relative registration dates after wall-clock time passes (%s)', value => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date(value));
      expect(eventDates('2026-10-19', demoEvents[2])).toMatchObject({
        registrationOpenAt: '2026-09-19T09:00:00+04:00', registrationCloseAt: '2026-10-23T18:00:00+04:00',
        eventStartAt: '2026-10-24T10:00:00+04:00', eventEndAt: '2026-10-24T18:00:00+04:00',
      });
    } finally { vi.useRealTimers(); }
  });
  it('closes B registration one day before the demo', () => {
    expect(eventDates('2026-10-19', demoEvents[1]).registrationCloseAt).toBe('2026-10-18T18:00:00+04:00');
  });
  it('creates two sessions and four disciplines in specification order', () => {
    expect(sessionBodies('2026-10-17')).toEqual([
      { name: 'Сессия 1 (утро)', orderNo: 1, startsAt: '2026-10-17T10:00:00+04:00', breakBetweenEventsSec: 60 },
      { name: 'Сессия 2 (день)', orderNo: 2, startsAt: '2026-10-17T15:00:00+04:00', breakBetweenEventsSec: 60 },
    ]);
    const specs = disciplineSpecs();
    expect(specs.map(x => x.code)).toEqual(['M-50-FREE', 'F-50-FREE', 'M-100-FREE', 'F-100-FREE']);
    for (const spec of specs) {
      const body = disciplineBody(spec, 'template', `session-${spec.sessionIndex + 1}`);
      expect(body).toMatchObject({ disciplineTemplateId: 'template', code: spec.code, distanceM: spec.distanceM,
        gender: spec.gender, stroke: 'FREE', seedingMode: 'BY_AGE_GROUP', sessionId: `session-${spec.sessionIndex + 1}`,
        orderInSession: spec.orderInSession, heatDurationSec: null, entryFeeAmount: 0 });
      expect(body.ageGroups.map(group => [group.birthYearFrom, group.birthYearTo])).toEqual([[2014, 2016], [2012, 2013], [2010, 2011]]);
      expect(body.ageGroups.every(group => group.gender === spec.gender && group.code === `${spec.gender}-${group.birthYearFrom}-${group.birthYearTo}`)).toBe(true);
      expect(templateBody('swim', spec)).toMatchObject({ sportId: 'swim', resultType: 'TIME', rankingStrategy: 'ASC' });
    }
    expect(regulationTemplateBody('swim')).toMatchObject({ sportId: 'swim', code: 'T10-DEMO-SWIM' });
    expect(regulationVersionBody()).toMatchObject({ versionNo: 1, rulesJson: { sections: [{ code: 'swimming' }] } });
  });
  it('uploads the original workbook as multipart file, not JSON', async () => {
    const bytes = new Uint8Array([80, 75, 3, 4]);
    const body = importBody(bytes);
    expect([...body.keys()]).toEqual(['file']);
    const file = body.get('file') as File;
    expect(file.name).toBe('set-M.xlsx');
    expect(file.type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    expect(new Uint8Array(await file.arrayBuffer())).toEqual(bytes);
  });
});
it('uses an exact suffix for backup event identity', () => {
  expect(demoEventTitle(' · резерв')).toContain('по плаванию · резерв');
  expect(demoEventTitle(' · резерв')).not.toBe(demoEventTitle());
  expect(() => demoEventTitle('\nother')).toThrow();
});
describe('configuration guard', () => {
  const env = { DEMO_SEED_ENABLED: 'true', API_URL: 'http://localhost:8080', ADMIN_EMAIL: 'admin@example.invalid',
    ADMIN_PASSWORD: 'unit-test-password', DEMO_PASSWORD: 'unit-test-password' };
  it('requires an explicit demo environment and passwords', () => {
    expect(() => config({ ...env, DEMO_SEED_ENABLED: '' })).toThrow();
    expect(() => config({ ...env, NODE_ENV: 'production' })).toThrow();
    expect(() => config({ ...env, DEMO_PASSWORD: undefined })).toThrow('DEMO_PASSWORD');
    expect(config({ ...env, ORG_PASSWORD: 'unit-test-org' }).passwords.ORG).toBe('unit-test-org');
    expect(config({ ...env, REQUIRE_VENUE_META: 'true', EVENT_TITLE_SUFFIX: ' · резерв' })).toMatchObject({ requireVenueMeta: true, eventTitleSuffix: ' · резерв' });
  });
  it.each(['ADMIN_PASSWORD', 'DEMO_PASSWORD', 'ORG_PASSWORD', 'SEC_PASSWORD', 'JUDGE_PASSWORD', 'PARENT_PASSWORD', 'OTHER_PASSWORD'])
  ('rejects published and short passwords in %s before seed or reset', name => {
    for (const value of ['Demo2026!', '', 'short', '12345678901']) {
      for (const needsUsers of [true, false]) {
        expect(() => config({ ...env, [name]: value }, needsUsers))
          .toThrow('Задайте свой пароль: опубликованный пароль запрещён');
      }
    }
    expect(config({ ...env, [name]: '123456789012' }).adminEmail).toBe(env.ADMIN_EMAIL);
  });
  it('reads DEMO_OWNER_EMAIL and defaults to the owner-approved separate mailbox', () => {
    expect(config(env).ownerEmail).toBe(DEFAULT_OWNER_EMAIL);
    expect(config({ ...env, DEMO_OWNER_EMAIL: ' Owner@Example.com ' }).ownerEmail).toBe('owner@example.com');
    expect(() => config({ ...env, DEMO_OWNER_EMAIL: 'not-an-email' })).toThrow('DEMO_OWNER_EMAIL');
  });
  it('rejects URL credentials and a duplicated API prefix', () => {
    expect(() => config({ ...env, API_URL: 'https://user:pass@example.invalid' })).toThrow();
    expect(() => config({ ...env, API_URL: 'http://localhost:8080/api/v1' })).toThrow();
  });
});
