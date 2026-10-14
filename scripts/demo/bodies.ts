export const ORGANIZATION_NAME = 'Федерация плавания Самарской области (демо)';
export const VENUE_NAME = 'Дворец спорта, 25 м, 8 дорожек';
export const EVENT_TITLE = 'Открытый кубок Самары по плаванию (демо)';
export const REGULATION_CODE = 'T10-DEMO-SWIM';
export const accounts = [
  { key: 'ORG', email: 'org@flounder.demo', role: 'organizer', memberRole: 'owner', firstName: 'Организатор' },
  { key: 'SEC', email: 'sec@flounder.demo', role: 'operator', memberRole: 'operator', firstName: 'Секретарь' },
  { key: 'JUDGE', email: 'judge@flounder.demo', role: 'judge', memberRole: 'judge', firstName: 'Судья' },
  { key: 'PARENT', email: 'parent@flounder.demo', role: 'participant', memberRole: null, firstName: 'Родитель' },
] as const;
export type Account = typeof accounts[number];

export const loginBody = (email: string, password: string) => ({ email, password });
export const userBody = (account: Account, password: string) => ({
  ...loginBody(account.email, password), firstName: account.firstName, lastName: 'Демо',
  birthDate: '1990-01-01', gender: 'M', city: 'Самара', countryCode: 'RU', clubName: 'Дельфин',
});
export const roleBody = (roleCode: string) => ({ roleCode });
export const memberBody = (userId: string, memberRole: string) => ({ userId, memberRole });
export const organizationBody = () => ({ type: 'federation', name: ORGANIZATION_NAME, contactEmail: 'org@flounder.demo' });
export const venueBody = () => ({
  name: VENUE_NAME, countryCode: 'RU', city: 'Самара', timezone: 'Europe/Samara',
  venueMeta: { lanes: 8, courseLength: 25 },
});

export function demoEventTitle(suffix = ''): string {
  const title = EVENT_TITLE + suffix;
  if (title.length > 255 || /[\r\n\0]/.test(suffix)) throw new Error('EVENT_TITLE_SUFFIX содержит перенос строки или превышает длину названия 255 символов.');
  return title;
}
export function samaraDate(value: string | Date): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error('eventStartAt должен содержать корректную дату ISO-8601 с зоной.');
  return new Date(date.getTime() + 4 * 3600_000).toISOString().slice(0, 10);
}

// Calendar arithmetic uses Samara's fixed UTC+04:00, independently of the host timezone.
export function nextSaturday(now = new Date()): string {
  const local = new Date(now.getTime() + 4 * 3600_000);
  let days = (6 - local.getUTCDay() + 7) % 7;
  if (days === 0 && local.getUTCHours() >= 10) days = 7;
  local.setUTCDate(local.getUTCDate() + days);
  return local.toISOString().slice(0, 10);
}
export function validateDemoDate(date: string): string {
  const parsed = new Date(`${date}T00:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(parsed.getTime()) ||
      parsed.toISOString().slice(0, 10) !== date || parsed.getUTCDay() !== 6) {
    throw new Error('DEMO_DATE должна быть существующей субботой в формате YYYY-MM-DD.');
  }
  return date;
}
export function eventBody(ids: { organizationId: string; venueId: string; sportId: string; regulationVersionId: string }, date: string, now: Date, titleSuffix = '', log: (message: string) => void = console.warn) {
  validateDemoDate(date);
  const close = `${date}T09:30:00+04:00`;
  const expired = Date.parse(close) <= now.getTime();
  if (expired) log('WARNING: плановое закрытие регистрации уже наступило; демо-окно регистрации сдвинуто на минуту до и после запуска. Дата события сохранена.');
  return {
    ...ids, title: demoEventTitle(titleSuffix), description: 'Вымышленные данные для демонстрации SportGate. Набор M, 200 заявок.',
    registrationOpenAt: new Date(now.getTime() - (expired ? 60_000 : 0)).toISOString(),
    registrationCloseAt: expired ? new Date(Math.max(Date.parse(close), now.getTime() + 60_000)).toISOString() : close,
    eventStartAt: `${date}T10:00:00+04:00`, eventEndAt: `${date}T18:00:00+04:00`,
  };
}
export const sessionBodies = (date: string) => [
  { name: 'Сессия 1 (утро)', orderNo: 1, startsAt: `${date}T10:00:00+04:00`, breakBetweenEventsSec: 60 },
  { name: 'Сессия 2 (день)', orderNo: 2, startsAt: `${date}T15:00:00+04:00`, breakBetweenEventsSec: 60 },
];
export function disciplineSpecs() {
  return [50, 100].flatMap((distanceM, sessionIndex) => ['M', 'F'].map((gender, genderIndex) => ({
    code: `${gender}-${distanceM}-FREE`,
    name: `${distanceM} м вольный стиль, ${gender === 'M' ? 'юноши' : 'девушки'}`,
    distanceM, gender, sessionIndex, orderInSession: genderIndex + 1,
    number: sessionIndex * 2 + genderIndex + 1,
  })));
}
export function disciplineBody(spec: ReturnType<typeof disciplineSpecs>[number], disciplineTemplateId: string, sessionId: string) {
  return {
    disciplineTemplateId, code: spec.code, name: spec.name,
    entryFeeAmount: 0, entryFeeCurrency: 'RUB',
    distanceM: spec.distanceM, stroke: 'FREE', gender: spec.gender,
    seedingMode: 'BY_AGE_GROUP', sessionId, orderInSession: spec.orderInSession, heatDurationSec: null,
    ageGroups: [[2014, 2015], [2012, 2013], [2010, 2011]].map(([birthYearFrom, birthYearTo]) => ({
      code: `${spec.gender}-${birthYearFrom}-${birthYearTo}`,
      name: `${spec.gender === 'M' ? 'Юноши' : 'Девушки'} ${birthYearFrom}–${birthYearTo}`,
      gender: spec.gender, birthYearFrom, birthYearTo,
    })),
  };
}
export function templateBody(sportId: string, spec: ReturnType<typeof disciplineSpecs>[number]) {
  return { sportId, code: `T10-${spec.code}`, name: spec.name,
    competitionFormat: 'HEATS', unitType: 'HEAT', resultType: 'TIME', rankingStrategy: 'ASC' };
}
export const regulationTemplateBody = (sportId: string) => ({
  sportId, code: REGULATION_CODE, name: 'Демо-регламент плавания T10', description: 'Только для демо-стенда',
});
export const regulationVersionBody = () => ({ versionNo: 1,
  rulesJson: { sections: [{ code: 'swimming', title: 'Плавание: места по времени, возрастные группы' }] },
  notes: 'Демонстрационный регламент T10',
});
export function importBody(bytes: Uint8Array): FormData {
  const form = new FormData();
  form.append('file', new Blob([Uint8Array.from(bytes)], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  }), 'set-M.xlsx');
  return form;
}
