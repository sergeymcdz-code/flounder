import { dataset, DEFAULT_DEMO_DATE, entryTime, ownerEmail, profileKey, shiftDate, type Entry, type Profile } from './plan.ts';
import { validateDemoDate } from './bodies.ts';
import { fullName } from './names.ts';

export const CALENDAR_MARKER = 'Тестовые данные SportGate';
export const WEEK_MS = 7 * 86_400_000;
export const venues = [
  { city: 'Самара', name: 'Бассейн «Дворец спорта», Самара', timezone: 'Europe/Samara', offset: '+04:00', lanes: 8, courseLength: 25 },
  { city: 'Самара', name: 'Бассейн «Волга», Самара', timezone: 'Europe/Samara', offset: '+04:00', lanes: 10, courseLength: 50 },
  { city: 'Тольятти', name: 'Бассейн «Олимп», Тольятти', timezone: 'Europe/Samara', offset: '+04:00', lanes: 8, courseLength: 25 },
  { city: 'Казань', name: 'Бассейн «Акватика», Казань', timezone: 'Europe/Moscow', offset: '+03:00', lanes: 10, courseLength: 50 },
  { city: 'Уфа', name: 'Бассейн «Урал», Уфа', timezone: 'Asia/Yekaterinburg', offset: '+05:00', lanes: 10, courseLength: 50 },
  { city: 'Саратов', name: 'Бассейн «Юность», Саратов', timezone: 'Europe/Saratov', offset: '+04:00', lanes: 8, courseLength: 25 },
  { city: 'Ульяновск', name: 'Бассейн «Заря», Ульяновск', timezone: 'Europe/Ulyanovsk', offset: '+04:00', lanes: 8, courseLength: 25 },
  { city: 'Пенза', name: 'Бассейн «Сура», Пенза', timezone: 'Europe/Moscow', offset: '+03:00', lanes: 8, courseLength: 25 },
  { city: 'Оренбург', name: 'Бассейн «Нептун», Оренбург', timezone: 'Asia/Yekaterinburg', offset: '+05:00', lanes: 8, courseLength: 25 },
] as const;
export type CalendarVenue = typeof venues[number];
export const clubs = [
  ...['СШОР №1', 'Волна', 'Чайка', 'Акватика', 'Орбита', 'Дельфин', 'Старт', 'Нептун'].map(name => ({ name, city: 'Самара', cohort: 'set-M' })),
  ...['Самара', 'Тольятти', 'Казань', 'Уфа', 'Саратов', 'Ульяновск', 'Пенза', 'Оренбург'].map((city, i) => ({
    name: i === 0 ? 'Искра, Новокуйбышевск' : ['Резерв Самары', 'Олимп', 'Казанские волны', 'Урал', 'Юность', 'Заря', 'Сура', 'Оренбургские старты'][i] + ', ' + city,
    city, cohort: 'S10',
  })),
];
export type Swimmer = { profile: Profile; city: string; cohort: string; email?: string };
export type CalendarEntry = Entry & { email?: string; scenario?: 'OWNER' | 'CORRECTION' | 'WAITLIST' };
export type CalendarOptions = { demoDate?: string; start?: string; weeks?: number; prefix?: string; anchorDate?: string; advance?: boolean };
export type CalendarMeta = { version: 1; start: string; weeks: number; prefix: string; anchorDate: string; week: number; slot: number };
export type CalendarEvent = CalendarMeta & {
  title: string; date: string; venue: CalendarVenue; offset: number; status: string;
  capacity: number; initialCount: number; count: number; corrections: number; waitlisted: number;
  documents: number; correctedResults: number; exceptionalResults: number; dsq: number;
  owner: boolean; sessionCount: number; disciplines: ReturnType<typeof program>;
  dates: { eventStartAt: string; eventEndAt: string; registrationOpenAt: string; registrationCloseAt: string };
};
export function monday(date: string): string {
  const day = new Date(`${validateDemoDate(date)}T12:00:00Z`).getUTCDay();
  return shiftDate(date, -((day + 6) % 7));
}
export const weekDifference = (a: string, b: string) => Math.round((Date.parse(monday(a)) - Date.parse(monday(b))) / WEEK_MS);
export function calendarSettings(options: CalendarOptions = {}) {
  const demoDate = validateDemoDate(options.demoDate ?? DEFAULT_DEMO_DATE);
  const anchorDate = validateDemoDate(options.anchorDate ?? demoDate);
  const start = validateDemoDate(options.start ?? shiftDate(monday(anchorDate), -84));
  if (monday(start) !== start) throw new Error('CALENDAR_START должен быть понедельником.');
  const weeks = options.weeks ?? 25;
  if (!Number.isSafeInteger(weeks) || weeks < 1 || weeks > 104) throw new Error('CALENDAR_WEEKS должен быть целым числом от 1 до 104.');
  const prefix = options.prefix ?? '';
  if (prefix.length > 80 || /[\r\n\0]/.test(prefix)) throw new Error('CALENDAR_PREFIX: не более 80 символов, без переносов строк.');
  return { demoDate, anchorDate, start, weeks, prefix };
}
const strokes = [
  [50, 'FREE', 'вольный стиль'], [100, 'FREE', 'вольный стиль'], [200, 'FREE', 'вольный стиль'], [400, 'FREE', 'вольный стиль'],
  [50, 'BACK', 'на спине'], [100, 'BACK', 'на спине'], [50, 'BREAST', 'брасс'], [100, 'BREAST', 'брасс'],
  [50, 'FLY', 'баттерфляй'], [100, 'FLY', 'баттерфляй'], [200, 'MEDLEY', 'комплекс'],
] as const;
export function program(week: number, sessionCount = 2) {
  // Always include sprint freestyle for owner and waitlist scenarios.
  const selected = [strokes[0], strokes[1], ...Array.from({ length: 1 + week % 4 }, (_, i) => strokes[2 + (week + i) % 9])];
  return selected.flatMap(([distanceM, stroke, label], i) => ['M', 'F'].map((gender, j) => ({
    code: `${gender}-${distanceM}-${stroke}`, name: `${distanceM} м ${label}, ${gender === 'M' ? 'юноши' : 'девушки'}`,
    distanceM, stroke, gender, sessionIndex: i % sessionCount, orderInSession: Math.floor(i / sessionCount) * 2 + j + 1,
    number: i * 2 + j + 1,
  })));
}
export function calendarPlan(options: CalendarOptions = {}): CalendarEvent[] {
  const settings = calendarSettings(options);
  const elapsed = Math.max(0, weekDifference(settings.demoDate, settings.anchorDate));
  const result: CalendarEvent[] = [];
  for (let week = 0; week < settings.weeks; week++) {
    for (let slot = 0; slot < (week % 6 === 2 ? 2 : 1); slot++) {
      const venue = venues[(week + slot * 3) % venues.length];
      const date = shiftDate(settings.start, week * 7 + 5 + slot);
      const initialOffset = weekDifference(date, settings.anchorDate);
      const offset = weekDifference(date, settings.demoDate);
      const capacity = 160 + week % 4 * 20;
      const base = initialOffset <= 0 ? 100 + week % 5 * 20 : initialOffset <= 3 ? Math.round(capacity * (.4 + (week % 5) * .1)) : initialOffset <= 8 ? week % 3 * 10 : 0;
      const ticks = Math.max(0, Math.min(elapsed, initialOffset - 1) - Math.max(0, initialOffset - 4));
      // Reserve the first weekly increment for correction scenarios when a distant event opens.
      const scenarioReserve = initialOffset >= 4 && ticks > 0 ? 3 + week % 4 : 0;
      const queueReserve = slot === 0 && initialOffset >= 2 && elapsed >= initialOffset - 1 ? 4 : 0;
      const count = Math.min(capacity - 10, base + ticks * (10 + week % 6) - scenarioReserve - queueReserve);
      const status = offset < 0 ? 'COMPLETED' : offset === 0 ? 'REGISTRATION_CLOSED' : offset <= 3 ? 'REGISTRATION_OPEN' :
        week === settings.weeks - 1 && slot === 0 && offset >= 9 ? 'DRAFT' : 'PUBLISHED';
      const cityGenitive = { Самара: 'Самары', Тольятти: 'Тольятти', Казань: 'Казани', Уфа: 'Уфы', Саратов: 'Саратова', Ульяновск: 'Ульяновска', Пенза: 'Пензы', Оренбург: 'Оренбурга' }[venue.city];
      const titleTemplates = [
        `Кубок города ${cityGenitive}`, `Открытое первенство ${cityGenitive} среди юношей и девушек`,
        `Клубные старты «Волна» — ${venue.city}`, `Первенство области — ${venue.city}`, `Мемориал тренеров Поволжья — ${venue.city}`,
        `Спринт-фестиваль — ${venue.city}`, `${Number(date.slice(5, 7)) >= 9 && Number(date.slice(5, 7)) <= 11 ? 'Осенний' : 'Зимний'} кубок SportGate`,
      ];
      const month = ['январь', 'февраль', 'март', 'апрель', 'май', 'июнь', 'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь'][Number(date.slice(5, 7)) - 1];
      const series = week % titleTemplates.length === 6;
      const suffix = series ? ` · этап ${week + 1}${slot ? ' (воскресенье)' : ''}` : `, ${month} ${date.slice(0, 4)}`;
      let title = `${settings.prefix ? settings.prefix + ' ' : ''}${titleTemplates[week % titleTemplates.length]}${suffix}`;
      if (result.some(event => event.title === title)) title += ` (${date})`;
      const scenarios = initialOffset >= 1 && offset <= 3;
      const sessionCount = week % 4 === 1 ? 1 : 2;
      result.push({ ...settings, version: 1, week, slot, title, date, venue, offset, status, capacity, initialCount: base, count,
        corrections: scenarios ? 3 + week % 4 : 0,
        waitlisted: scenarios && offset <= 1 && slot === 0 ? 4 : 0,
        documents: scenarios && [1, 2].includes(initialOffset) && slot === 0 ? 2 : 0,
        correctedResults: offset < 0 && week % 3 === 0 ? 1 + week % 2 : 0,
        exceptionalResults: offset < 0 && week % 3 === 0 ? 3 : 0,
        dsq: offset < 0 && week % 5 === 1 ? 1 : 0,
        owner: slot === 0 && [-1, 1, 2].includes(initialOffset), sessionCount, disciplines: program(week, sessionCount),
        dates: { eventStartAt: `${date}T10:00:00${venue.offset}`, eventEndAt: `${date}T18:00:00${venue.offset}`,
          registrationOpenAt: `${shiftDate(monday(date), -21)}T06:00:00${venue.offset}`,
          registrationCloseAt: `${monday(date)}T00:00:00${venue.offset}` },
      });
    }
  }
  return result;
}
export function metadata(event: CalendarEvent): CalendarMeta {
  const { version, start, weeks, prefix, anchorDate, week, slot } = event;
  return { version, start, weeks, prefix, anchorDate, week, slot };
}
export function description(event: CalendarEvent) {
  return `${CALENDAR_MARKER}. Сезон с ${event.start}, ${event.weeks} недель, неделя ${event.week + 1}, ${event.slot ? 'воскресенье' : 'суббота'} (точка отсчёта ${event.anchorDate})${event.prefix ? `, копия «${event.prefix}»` : ''}.`;
}
export function readMetadata(value: unknown): CalendarMeta | undefined {
  if (typeof value !== 'string' || !value.startsWith(CALENDAR_MARKER)) return;
  const match = /^Тестовые данные SportGate\. Сезон с (\d{4}-\d{2}-\d{2}), (\d+) недель, неделя (\d+), (суббота|воскресенье) \(точка отсчёта (\d{4}-\d{2}-\d{2})\)(?:, копия «(.+)»)?\.$/.exec(value);
  if (!match) throw new Error('Повреждён маркер календаря S10. Выполните DEMO_CALENDAR=true npx tsx reset.ts, затем повторите calendar.ts.');
  // This fixed human-readable grammar is metadata version 1; unknown formats are refused.
  const meta: CalendarMeta = { version: 1, start: match[1], weeks: Number(match[2]), prefix: match[6] ?? '',
    anchorDate: match[5], week: Number(match[3]) - 1, slot: match[4] === 'суббота' ? 0 : 1 };
  calendarSettings({ ...meta, demoDate: meta.anchorDate });
  if (meta.version !== 1 || !Number.isSafeInteger(meta.week) || meta.week < 0 || meta.week >= meta.weeks || ![0, 1].includes(meta.slot))
    throw new Error('Неверный маркер календаря S10. Проверьте событие и reset.');
  return meta;
}
export async function swimmerPool(requestedOwnerEmail?: string): Promise<Swimmer[]> {
  const source = await dataset(DEFAULT_DEMO_DATE, requestedOwnerEmail);
  const originals = [...new Map(source.B.map(entry => [profileKey(entry.profile), entry.profile])).values()];
  const owner = source.C.find(entry => entry.email === ownerEmail(requestedOwnerEmail))!.profile;
  const pool: Swimmer[] = originals.map(profile => ({ profile, city: 'Самара', cohort: 'set-M' }));
  const used = new Set([...Object.values(source).flat().map(e => profileKey(e.profile))]);
  pool.push({ profile: owner, city: 'Самара', cohort: 'S10', email: ownerEmail(requestedOwnerEmail) });
  for (let i = 0; pool.length < 900; i++) {
    const club = clubs[8 + i % 8];
    const gender = Math.floor(i / 8) % 2 ? 'F' : 'M';
    const profile = { ...fullName(500 + i, gender, true), birthDate: `${2010 + i % 7}-${String(1 + i % 12).padStart(2, '0')}-${String(1 + i % 28).padStart(2, '0')}`, gender, club: club.name };
    if (used.has(profileKey(profile))) continue;
    used.add(profileKey(profile));
    pool.push({ profile, city: club.city, cohort: 'S10', ...(i < 7 ? { email: `s10-parent-${i + 1}@flounder.demo` } : {}) });
  }
  return pool;
}
export function calendarEntries(event: CalendarEvent, pool: Swimmer[]): CalendarEntry[] {
  const available = pool.filter(p => !p.email);
  const locals = available.filter(p => p.city === event.venue.city);
  const guests = available.filter(p => p.city !== event.venue.city);
  const entries: CalendarEntry[] = [];
  const used = new Set<string>();
  // A repeating 6 local / 1 guest pattern keeps guests at 14–15% of starts.
  for (let i = 0; entries.length < event.count; i++) {
    if (i > 10_000) throw new Error('Пул календаря слишком мал для программы.');
    const swimmers = i % 7 === 6 ? guests : locals;
    const swimmer = swimmers[(Math.floor(i / 7) * (i % 7 === 6 ? 1 : 6) + i % 7 + event.week * 13) % swimmers.length];
    const disciplines = event.disciplines.filter(d => d.gender === swimmer.profile.gender);
    const discipline = disciplines[(Math.floor(i / 7) + Math.floor(i / swimmers.length)) % disciplines.length];
    const key = profileKey(swimmer.profile) + discipline.code;
    if (used.has(key)) continue;
    used.add(key);
    entries.push({ profile: swimmer.profile, code: discipline.code,
      seedTimeMs: Math.round(entryTime(swimmer.profile, discipline.distanceM, pool.indexOf(swimmer), event.anchorDate) * (1 - Math.min(event.week, 52) * .0015) / 10) * 10 });
  }
  const parents = pool.filter(p => p.email?.startsWith('s10-parent-'));
  const append = (swimmer: Swimmer, scenario: CalendarEntry['scenario'], distance = 100) => {
    const code = `${swimmer.profile.gender}-${distance}-FREE`;
    entries.push({ profile: swimmer.profile, code, seedTimeMs: entryTime(swimmer.profile, distance, 2, event.anchorDate), email: swimmer.email, scenario });
  };
  if (event.owner) append(pool.find(p => p.email && !p.email.startsWith('s10-parent-'))!, 'OWNER');
  for (const swimmer of parents.slice(0, event.corrections)) append(swimmer, 'CORRECTION');
  // Four male accounts are deliberately used on another discipline for overflow.
  for (const swimmer of parents.slice(0, event.waitlisted)) append(swimmer, 'WAITLIST', 50);
  return entries;
}
export function advanceChanges(before: CalendarEvent[], after: CalendarEvent[]) {
  return after.filter(next => {
    const previous = before.find(e => e.week === next.week && e.slot === next.slot);
    return !previous || previous.status !== next.status || previous.count !== next.count;
  }).map(({ week, slot, status, count }) => ({ week, slot, status, count }));
}
