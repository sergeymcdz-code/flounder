import { readFile } from 'node:fs/promises';
import { fullName } from './names.ts';
import { validateDemoDate } from './bodies.ts';

export const DEFAULT_DEMO_DATE = '2026-10-19';
export const DEFAULT_OWNER_EMAIL = 'sergeymcdzkz@gmail.com';
export function ownerEmail(value = DEFAULT_OWNER_EMAIL): string {
  const email = value.trim().toLowerCase();
  if (email.length > 255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('DEMO_OWNER_EMAIL должен содержать корректный e-mail отдельного участника.');
  return email;
}
export const demoEvents = [
  { key: 'A', title: 'Кубок SportGate — осень', slug: 'kubok-sportgate-osen', offset: -8, status: 'COMPLETED' },
  { key: 'B', title: 'Открытый кубок Самары по плаванию', slug: 'otkrytyy-kubok-samary-po-plavaniyu', offset: 1, status: 'REGISTRATION_CLOSED' },
  { key: 'C', title: 'Первенство области среди юношей', slug: 'pervenstvo-oblasti-sredi-yunoshey', offset: 5, status: 'REGISTRATION_OPEN' },
] as const;
export type EventKey = typeof demoEvents[number]['key'];
export type DemoEvent = typeof demoEvents[number];
export function selectedEvents(value = 'A,B,C'): DemoEvent[] {
  const keys = value.split(',').map(x => x.trim());
  if (!keys.length || keys.some(key => !demoEvents.some(event => event.key === key)) || new Set(keys).size !== keys.length)
    throw new Error('DEMO_EVENTS должен содержать уникальные значения A,B,C через запятую.');
  return demoEvents.filter(event => keys.includes(event.key));
}
export function shiftDate(date: string, days: number): string {
  const parsed = new Date(`${validateDemoDate(date)}T12:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}
export function eventDates(demoDate: string, event: DemoEvent) {
  const date = shiftDate(demoDate, event.offset);
  return {
    date, eventStartAt: `${date}T10:00:00+04:00`, eventEndAt: `${date}T18:00:00+04:00`,
    registrationOpenAt: `${shiftDate(demoDate, -30)}T09:00:00+04:00`,
    registrationCloseAt: `${shiftDate(demoDate, event.key === 'A' ? -9 : event.key === 'B' ? -1 : 4)}T18:00:00+04:00`,
  };
}
export type Profile = { lastName: string; firstName: string; middleName: string; birthDate: string; gender: string; club: string };
export type Entry = { profile: Profile; code: string; seedTimeMs: number | null; state?: 'NEEDS_CORRECTION' | 'WAITLISTED'; email?: string };
export function profileKey(profile: Pick<Profile, 'lastName' | 'firstName' | 'birthDate' | 'gender'>) {
  return `${profile.lastName}|${profile.firstName}|${profile.birthDate}|${profile.gender}`.toLowerCase().replaceAll('ё', 'е');
}
export const extraClubs = ['Олимп, Тольятти', 'Волга, Сызрань', 'Искра, Новокуйбышевск', 'Заря, Ульяновск'];
// Plausible training times: age and distance affect pace; all values are deterministic.
export function entryTime(profile: Profile, distance: number, index: number, demoDate: string): number {
  const age = Number(demoDate.slice(0, 4)) - Number(profile.birthDate.slice(0, 4));
  const fifty = 29_000 + Math.max(0, 17 - age) * 2_800 + (profile.gender === 'F' ? 1_500 : 0) + index % 9 * 190 + index * 37 % 700;
  return Math.round(fifty * distance / 50 * (distance === 50 ? 1 : distance === 100 ? 1.12 : 1.24) / 10) * 10;
}
export async function dataset(demoDate: string, requestedOwnerEmail?: string): Promise<Record<EventKey, Entry[]>> {
  const email = ownerEmail(requestedOwnerEmail);
  const source = JSON.parse(await readFile(new URL('../../docs/scripts/demo/out/set-M.json', import.meta.url), 'utf8')) as {
    athletes: (Omit<Profile, 'gender'> & { id: number; gender: string })[];
    entries: { athleteId: number; disciplineCode: string; seedTimeMs: number | null }[];
  };
  const originals = source.athletes.map(athlete => ({ ...athlete,
    birthDate: athlete.birthDate.split('.').reverse().join('-'), gender: athlete.gender === 'М' ? 'M' : 'F' }));
  const used = new Set(originals.map(profileKey));
  const extra: Profile[] = [];
  for (let i = 0; extra.length < 180; i++) {
    const gender = extra.length >= 176 ? 'M' : i % 2 === 0 ? 'M' : 'F';
    const profile = { ...fullName(i, gender),
      birthDate: `${2010 + i % 7}-${String(1 + i % 12).padStart(2, '0')}-${String(1 + i % 28).padStart(2, '0')}`,
      gender, club: extraClubs[i % extraClubs.length] };
    if (!used.has(profileKey(profile))) { extra.push(profile); used.add(profileKey(profile)); }
  }
  const A = extra.slice(0, 96).flatMap((profile, i) => (i < 64 ? [50, 100] : [50]).map(distance => ({
    profile, code: `${profile.gender}-${distance}-FREE`, seedTimeMs: entryTime(profile, distance, i, demoDate),
  })));
  const B = source.entries.map(entry => ({ profile: originals.find(p => p.id === entry.athleteId)!, code: entry.disciplineCode, seedTimeMs: entry.seedTimeMs }));
  const shared = ['M', 'F'].flatMap(gender => originals.filter(p => p.gender === gender).slice(0, 20));
  const counts = { M: 0, F: 0 };
  const C: Entry[] = [...shared, ...extra.slice(96, 176)].map((profile, i) => {
    const slot = counts[profile.gender as 'M' | 'F']++;
    const distance = slot < 16 ? 50 : slot < 31 ? 100 : slot < 46 ? 200 : 400;
    return { profile, code: `${profile.gender}-${distance}-FREE`, seedTimeMs: entryTime(profile, distance, i, demoDate),
      ...(i >= 114 ? { state: 'NEEDS_CORRECTION' as const, email: i === 114 ? email : `s02-c-${i}@flounder.demo` } : {}) };
  });
  C.push(...extra.slice(176).map((profile, i) => ({ profile, code: 'M-50-FREE', seedTimeMs: entryTime(profile, 50, i, demoDate), state: 'WAITLISTED' as const, email: `s02-wait-${i}@flounder.demo` })));
  return { A, B, C };
}
