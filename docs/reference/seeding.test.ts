import { describe, expect, it } from 'vitest';
import { laneOrder } from './lanes.js';
import { heatSizes, seedEvent } from './seeding.js';
import { scheduleHeats } from './schedule.js';
import type { AgeGroup, Athlete, Entry, EventDef } from './types.js';

describe('laneOrder', () => {
  it('8 lanes', () => expect(laneOrder(8)).toEqual([4, 5, 3, 6, 2, 7, 1, 8]));
  it('6 lanes', () => expect(laneOrder(6)).toEqual([3, 4, 2, 5, 1, 6]));
  it('10 lanes', () => expect(laneOrder(10)).toEqual([5, 6, 4, 7, 3, 8, 2, 9, 1, 10]));
});

describe('heatSizes', () => {
  it.each([
    [17, 8, [3, 6, 8]],
    [9, 8, [3, 6]],
    [16, 8, [8, 8]],
    [11, 8, [3, 8]],
    [20, 8, [4, 8, 8]],
    [7, 6, [3, 4]],
    [13, 6, [3, 4, 6]],
    [0, 8, []],
    [3, 8, [3]],
    [1, 8, [1]],
  ])('n=%i lanes=%i', (n, lanes, expected) => expect(heatSizes(n, lanes)).toEqual(expected));
});

function mk(n: number, opts: { ntCount?: number; years?: number[]; gender?: 'M' | 'F' } = {}) {
  const athletes = new Map<string, Athlete>();
  const entries: Entry[] = [];
  for (let i = 0; i < n; i++) {
    const id = `a${i}`;
    athletes.set(id, { id, lastName: `Фам${i}`, firstName: 'Имя', birthYear: opts.years?.[i % (opts.years.length)] ?? 2014, gender: opts.gender ?? 'M' });
    const nt = opts.ntCount ? i >= n - opts.ntCount : false;
    entries.push({ id: `e${i}`, eventId: 'ev', athleteId: id, seedTimeMs: nt ? null : (30 + i) * 1000, status: 'CONFIRMED' });
  }
  return { athletes, entries };
}
const groups: AgeGroup[] = [
  { id: 'g1', code: 'M-2014-2015', name: '2014-2015', gender: 'M', birthYearFrom: 2014, birthYearTo: 2015 },
  { id: 'g2', code: 'M-2012-2013', name: '2012-2013', gender: 'M', birthYearFrom: 2012, birthYearTo: 2013 },
];
const ev = (mode: EventDef['seedingMode'], ageGroupIds: string[] = []): EventDef => ({
  id: 'ev', number: 1, distance: 50, stroke: 'FREE', gender: 'M', ageGroupIds, seedingMode: mode, sessionId: 's1', orderInSession: 1,
});

describe('seedEvent', () => {
  it('17 swimmers, 8 lanes: fastest heat last, 30.00 on lane 4', () => {
    const { athletes, entries } = mk(17);
    const r = seedEvent({ event: ev('BY_TIME_ALL'), entries, athletes, ageGroups: [], pool: { lanes: 8, courseLength: 25 } });
    expect(r.issues.filter((i) => i.severity === 'ERROR')).toEqual([]);
    expect(r.heats.map((h) => h.lanes.length)).toEqual([3, 6, 8]);
    const last = r.heats[2]!;
    expect(last.lanes.find((l) => l.lane === 4)?.seedTimeMs).toBe(30000);
    expect(last.lanes.find((l) => l.lane === 5)?.seedTimeMs).toBe(31000);
    expect(last.lanes.find((l) => l.lane === 3)?.seedTimeMs).toBe(32000);
    const first = r.heats[0]!;
    expect(first.lanes.map((l) => l.seedTimeMs).sort()).toEqual([44000, 45000, 46000]);
  });
  it('NT swimmers go to the first heat after timed', () => {
    const { athletes, entries } = mk(10, { ntCount: 2 });
    const r = seedEvent({ event: ev('BY_TIME_ALL'), entries, athletes, ageGroups: [], pool: { lanes: 8, courseLength: 25 } });
    expect(r.heats.map((h) => h.lanes.length)).toEqual([3, 7]);
    const nts = r.heats[0]!.lanes.filter((l) => l.seedTimeMs === null);
    expect(nts.length).toBe(2);
  });
  it('BY_AGE_GROUP: two groups of 5 → heats 1 and 2 with codes', () => {
    const { athletes, entries } = mk(10, { years: [2014, 2012] });
    const r = seedEvent({ event: ev('BY_AGE_GROUP', ['g1', 'g2']), entries, athletes, ageGroups: groups, pool: { lanes: 8, courseLength: 25 } });
    expect(r.heats.map((h) => [h.number, h.ageGroupId, h.lanes.length])).toEqual([[1, 'g1', 5], [2, 'g2', 5]]);
  });
  it('eligibility errors', () => {
    const { athletes, entries } = mk(3, { years: [2014, 2007, 2014] });
    athletes.get('a0')!.gender = 'F';
    entries.push({ id: 'dup', eventId: 'ev', athleteId: 'a2', seedTimeMs: 1000, status: 'CONFIRMED' });
    const r = seedEvent({ event: ev('BY_TIME_ALL', ['g1', 'g2']), entries, athletes, ageGroups: groups, pool: { lanes: 8, courseLength: 25 } });
    expect(r.issues.map((i) => i.code).sort()).toEqual(['AGE_GROUP_MISMATCH', 'DUPLICATE_ENTRY', 'GENDER_MISMATCH']);
    expect(r.heats[0]!.lanes.length).toBe(1);
  });
  it('deterministic', () => {
    const { athletes, entries } = mk(37);
    const a = seedEvent({ event: ev('BY_TIME_ALL'), entries, athletes, ageGroups: [], pool: { lanes: 8, courseLength: 25 } });
    const b = seedEvent({ event: ev('BY_TIME_ALL'), entries: entries.slice().reverse(), athletes, ageGroups: [], pool: { lanes: 8, courseLength: 25 } });
    expect(JSON.stringify(a.heats)).toBe(JSON.stringify(b.heats));
  });
  it('performance: 200 entries x 4 events < 5s', () => {
    const t0 = performance.now();
    for (let k = 0; k < 4; k++) {
      const { athletes, entries } = mk(200);
      seedEvent({ event: ev('BY_TIME_ALL'), entries, athletes, ageGroups: [], pool: { lanes: 8, courseLength: 25 } });
    }
    expect(performance.now() - t0).toBeLessThan(5000);
  });
});

describe('schedule', () => {
  it('example from ТЗ 4.6/10', () => {
    const events: EventDef[] = [
      { ...ev('BY_TIME_ALL'), id: 'e50', distance: 50, orderInSession: 1 },
      { ...ev('BY_TIME_ALL'), id: 'e100', distance: 100, orderInSession: 2, number: 2 },
    ];
    const heats = [1, 2, 3].map((n) => ({ number: n, eventId: 'e50', ageGroupId: null, lanes: [] }))
      .concat([1, 2].map((n) => ({ number: n, eventId: 'e100', ageGroupId: null, lanes: [] })));
    const s = scheduleHeats([{ id: 's1', name: 'S1', startAt: '2026-10-17T10:00:00+04:00' }], events, heats);
    expect(s.map((h) => h.scheduledAt)).toEqual([
      '2026-10-17T06:00:00.000Z', '2026-10-17T06:01:30.000Z', '2026-10-17T06:03:00.000Z',
      '2026-10-17T06:05:30.000Z', '2026-10-17T06:08:00.000Z',
    ]);
    expect(s.map((h) => h.sequenceInSession)).toEqual([1, 2, 3, 4, 5]);
  });
});
