import { describe, expect, it } from 'vitest';
import { advanceChanges, calendarEntries, calendarPlan, calendarSettings, clubs, description, monday, readMetadata, swimmerPool, venues } from './calendar-plan.ts';
import { dataset, profileKey } from './plan.ts';
import { femaleNames, maleNames, surnames } from './names.ts';

const options = { demoDate: '2026-10-19' };
describe('calendar plan and stable identities', () => {
  it('snapshots 25 weeks with four extra Sundays and the revised archive scenarios', async () => {
    const pool = await swimmerPool();
    const plan = calendarPlan(options);
    expect(plan).toHaveLength(29);
    expect(plan.map(e => ({ week: e.week + 1, date: e.date, city: e.venue.city, title: e.title, venue: e.venue.name, status: e.status,
      entries: calendarEntries(e, pool).length, imported: e.count, corrections: e.corrections, waitlisted: e.waitlisted,
      corrected: e.correctedResults, dnsDnf: e.exceptionalResults, dsq: e.dsq }))).toMatchSnapshot();
    expect(calendarPlan(options)).toEqual(plan);
    expect(plan.filter(e => e.status === 'COMPLETED')).toHaveLength(14);
    expect(plan.filter(e => e.correctedResults)).toHaveLength(4);
    expect(plan.filter(e => e.dsq)).toHaveLength(3);
    expect(plan.filter(e => e.status === 'DRAFT')).toHaveLength(1);
    expect(plan.filter(e => e.documents)).toHaveLength(2);
    expect(plan.filter(e => e.waitlisted)).toHaveLength(1);
  });
  it('makes both bounds configurable and handles Sunday/year/leap boundaries', () => {
    expect(calendarSettings(options).start).toBe('2026-07-27');
    expect(calendarPlan(options).at(-1)!.date).toBe('2027-01-16');
    const datesAndStates = (date: string) => calendarPlan({ demoDate: date }).map(e => [e.date, e.status, e.count]);
    expect(datesAndStates('2026-10-25')).toEqual(datesAndStates(options.demoDate));
    expect(monday('2028-03-01')).toBe('2028-02-28');
    expect(calendarPlan({ ...options, start: '2026-12-28', weeks: 2 }).map(e => e.date)).toEqual(['2027-01-02', '2027-01-09']);
    for (const weeks of [0, -1, 1.5, NaN, 105]) expect(() => calendarPlan({ ...options, weeks })).toThrow('CALENDAR_WEEKS');
    expect(() => calendarPlan({ ...options, start: '2026-10-20' })).toThrow('понедельником');
    expect(() => calendarPlan({ ...options, prefix: 'bad\n' })).toThrow('CALENDAR_PREFIX');
  });
  it('has 900 distinct swimmers, 16 regional clubs, original set-M and eight participant accounts', async () => {
    const pool = await swimmerPool('owner@example.com');
    expect(pool).toHaveLength(900);
    expect(new Set(pool.map(p => profileKey(p.profile))).size).toBe(900);
    expect(clubs).toHaveLength(16);
    expect(new Set(pool.map(p => p.profile.club)).size).toBe(16);
    expect(pool.filter(p => p.email)).toHaveLength(8);
    expect(pool.filter(p => p.cohort === 'set-M')).toHaveLength(120);
    const originals = await dataset('2026-10-19', 'owner@example.com');
    for (const entry of originals.B) expect(pool.some(p => profileKey(p.profile) === profileKey(entry.profile))).toBe(true);
    expect(pool.find(p => p.email === 'owner@example.com')!.profile).toEqual(originals.C.find(e => e.email === 'owner@example.com')!.profile);
    for (const city of new Set(venues.map(v => v.city))) expect(pool.filter(p => p.city === city).length).toBeGreaterThan(60);
    expect(new Set(maleNames).size).toBeGreaterThanOrEqual(80);
    expect(new Set(femaleNames).size).toBeGreaterThanOrEqual(80);
    expect(new Set(surnames).size).toBeGreaterThanOrEqual(250);
  });
  it('keeps entries unique, a regional majority and 10–20% guests with repeated athletes', async () => {
    const pool = await swimmerPool();
    for (const plan of calendarPlan(options)) {
      const entries = calendarEntries(plan, pool);
      expect(new Set(entries.map(e => profileKey(e.profile) + e.code)).size).toBe(entries.length);
      expect(plan.disciplines.length).toBeGreaterThanOrEqual(6);
      expect(plan.disciplines.length).toBeLessThanOrEqual(12);
      expect(entries.every(e => plan.disciplines.some(d => d.code === e.code && d.gender === e.profile.gender))).toBe(true);
      if (plan.count >= 80) {
        const guests = entries.filter(e => !e.scenario && pool.find(p => profileKey(p.profile) === profileKey(e.profile))!.city !== plan.venue.city).length;
        expect(guests / plan.count).toBeGreaterThanOrEqual(.1);
        expect(guests / plan.count).toBeLessThanOrEqual(.2);
      }
    }
    const ownerPlans = calendarPlan(options).filter(e => e.owner);
    expect(ownerPlans).toHaveLength(3);
    expect(ownerPlans.map(e => e.status)).toEqual(['COMPLETED', 'REGISTRATION_OPEN', 'REGISTRATION_OPEN']);
  });
  it('round trips immutable metadata and refuses malformed descriptions', () => {
    const event = calendarPlan(options)[0];
    expect(description(event)).toBe('Тестовые данные SportGate. Сезон с 2026-07-27, 25 недель, неделя 1, суббота (точка отсчёта 2026-10-19).');
    expect(readMetadata(description(event))).toMatchObject({ start: '2026-07-27', weeks: 25, anchorDate: options.demoDate });
    expect(readMetadata('Вымышленные данные SportGate. S02')).toBeUndefined();
    const copy = calendarPlan({ ...options, prefix: 'QA «осень»' })[3];
    expect(description(copy)).toContain(', воскресенье (точка отсчёта 2026-10-19), копия «QA «осень»».');
    expect(readMetadata(description(copy))).toMatchObject({ version: 1, week: 2, slot: 1, prefix: 'QA «осень»' });
    for (const [before, after] of [['неделя 1', 'неделя 0'], ['неделя 1', 'неделя 26'], ['25 недель', '105 недель'],
      ['2026-07-27', '2026-07-28'], ['2026-07-27', '2026-02-30'], ['суббота', 'пятница'], ['2026-10-19', '2026-02-30']])
      expect(() => readMetadata(description(event).replace(before, after))).toThrow();
    expect(() => readMetadata('Тестовые данные SportGate\nS10 {')).toThrow('маркер');
  });
  it('uses month/year titles, stage numbers only for SportGate and unbranded venues', () => {
    const plan = calendarPlan({ ...options, weeks: 104 });
    expect(new Set(plan.map(e => e.title)).size).toBe(plan.length);
    expect(plan.filter(e => e.title.includes('этап')).every(e => e.title.includes('кубок SportGate'))).toBe(true);
    expect(plan.filter(e => !e.title.includes('этап')).every(e => /, [а-я]+ \d{4}/.test(e.title))).toBe(true);
    expect(venues.every(v => !v.name.includes('SportGate'))).toBe(true);
    expect(venues[1].name).toBe('Бассейн «Волга», Самара');
  });
  it('advances only the expected events and preserves the original season bounds', () => {
    const before = calendarPlan(options);
    const after = calendarPlan({ demoDate: '2026-10-26', anchorDate: options.demoDate, start: '2026-07-27' });
    expect(advanceChanges(before, after)).toEqual([
      { week: 12, slot: 0, status: 'COMPLETED', count: 140 },
      { week: 13, slot: 0, status: 'REGISTRATION_CLOSED', count: 126 },
      { week: 14, slot: 0, status: 'REGISTRATION_OPEN', count: 168 },
      { week: 14, slot: 1, status: 'REGISTRATION_OPEN', count: 172 },
      { week: 15, slot: 0, status: 'REGISTRATION_OPEN', count: 101 },
      { week: 16, slot: 0, status: 'REGISTRATION_OPEN', count: 21 },
    ]);
    expect(advanceChanges(after, calendarPlan({ demoDate: '2026-10-26', anchorDate: options.demoDate, start: '2026-07-27' }))).toEqual([]);
    const later = calendarPlan({ demoDate: '2026-11-16', anchorDate: options.demoDate, start: '2026-07-27' });
    expect(later.at(-1)!.status).toBe('PUBLISHED');
    expect(before.at(-1)!.status).toBe('DRAFT');
  });
});
