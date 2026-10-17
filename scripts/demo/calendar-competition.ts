import type { Api, Row } from './api.ts';
import type { CalendarEvent } from './calendar-plan.ts';

export type CalendarRegistration = Row & {
  participantProfileId: string; eventDisciplineId: string; participantUserId?: string;
  participantSnapshot: { lastName: string; firstName: string; birthDate: string; gender: string };
  seedTimeMs: number | null;
};
type HeatEntry = { entryId: string; registrationId: string; laneOrPosition?: number; resultStatus?: string; timeMs?: number | null };
type Heat = Row & { entries: HeatEntry[] };
export type CalendarTokens = { admin: string; organizer: string; judge: string; secretary: string };
export async function calendarCompetition(api: Api, event: Row, plan: CalendarEvent, disciplines: Row[], registrations: CalendarRegistration[], tokens: CalendarTokens) {
  let generated = false;
  const units: Row[] = [];
  for (const discipline of disciplines) {
    const path = `/event-disciplines/${discipline.id}/competition-units`;
    let existing = await api.request<Row[]>('GET', path, undefined, tokens.organizer);
    if (!existing.length && registrations.some(r => r.eventDisciplineId === discipline.id && ['CONFIRMED', 'PAID', 'ADMITTED'].includes(String(r.status)))) {
      if (event.status === 'COMPLETED') throw new Error('Завершённое событие без заплывов. Выполните reset календаря.');
      await api.request('POST', `/event-disciplines/${discipline.id}/generate-competition-units`, { force: false, circleSeedTopHeats: false }, tokens.organizer);
      generated = true;
      existing = await api.request<Row[]>('GET', path, undefined, tokens.organizer);
    }
    units.push(...existing);
  }
  if (generated) await api.request('POST', `/events/${event.id}/schedule/recalculate`, undefined, tokens.organizer);
  if (units.some(unit => ['DRAFT', 'SCHEDULED'].includes(String(unit.status)))) {
    const validation = await api.request<{ violations: { severity: string }[] }>('POST', `/events/${event.id}/competition-units/publish`, undefined, tokens.organizer);
    if (validation.violations.some(v => v.severity === 'ERROR')) throw new Error('Валидатор отклонил календарный посев. Проверьте отчёт API.');
  }
  // Reload after scheduling so the first two heats are the actual session-1 heats.
  const scheduled: Row[] = [];
  for (const discipline of disciplines) scheduled.push(...await api.request<Row[]>('GET', `/event-disciplines/${discipline.id}/competition-units`, undefined, tokens.organizer));
  scheduled.sort((a, b) => Date.parse(String(a.scheduledAt)) - Date.parse(String(b.scheduledAt)) || Number(a.sequenceNumber) - Number(b.sequenceNumber) || a.id.localeCompare(b.id));
  const sessionOne = disciplines.find(d => d.sessionId)?.sessionId;
  const target = plan.status === 'COMPLETED' ? scheduled : scheduled.filter(unit => disciplines.find(d => d.id === unit.eventDisciplineId)?.sessionId === sessionOne).slice(0, 2);
  if (plan.status === 'REGISTRATION_CLOSED' && target.length !== 2) throw new Error('Недостаточно заплывов для двух финишей первой сессии.');
  let serial = 0;
  let corrected = 0;
  for (const [unitIndex, unit] of target.entries()) {
    let heat = await api.request<Heat>('GET', `/competition-units/${unit.id}`, undefined, tokens.organizer);
    heat.entries.sort((a, b) => (a.laneOrPosition ?? 0) - (b.laneOrPosition ?? 0) || a.entryId.localeCompare(b.entryId));
    const values = heat.entries.map(entry => {
      // The two demonstration heats may already be published/finished when advancing.
      const index = unitIndex < 2 ? -1 : serial++;
      const seed = registrations.find(r => r.id === entry.registrationId)?.seedTimeMs ?? 48_000;
      const status = plan.exceptionalResults && [3, 5, 7].includes(index) ? (index === 5 ? 'DNF' : 'DNS') : plan.dsq && index === 9 ? 'DSQ' : 'VALID';
      return { competitionUnitEntryId: entry.entryId, status, timeMs: status === 'VALID' ? Math.round(seed * .98 / 10) * 10 : null };
    });
    if (heat.status !== 'FINISHED') {
      if (event.status === 'COMPLETED') throw new Error('Незавершённый заплыв в завершённом событии. Выполните reset календаря.');
      if (heat.status === 'PUBLISHED') await api.request('POST', `/competition-units/${unit.id}/start`, undefined, tokens.judge);
      else if (heat.status !== 'IN_PROGRESS') throw new Error(`Неподходящий статус заплыва: ${heat.status}.`);
      const missing = values.filter(v => !heat.entries.find(e => e.entryId === v.competitionUnitEntryId)?.resultStatus || heat.entries.find(e => e.entryId === v.competitionUnitEntryId)?.resultStatus === 'PENDING');
      if (missing.length) await api.request('POST', `/competition-units/${unit.id}/results`, missing, tokens.judge);
      await api.request('POST', `/competition-units/${unit.id}/publication`, { published: true }, tokens.judge);
      await api.request('POST', `/competition-units/${unit.id}/finish`, undefined, tokens.judge);
      heat = await api.request<Heat>('GET', `/competition-units/${unit.id}`, undefined, tokens.organizer);
    }
    for (const value of values) {
      if (corrected >= plan.correctedResults || value.status !== 'VALID') continue;
      corrected++;
      const current = heat.entries.find(e => e.entryId === value.competitionUnitEntryId)!;
      if (current.resultStatus !== 'CORRECTED') {
        if (event.status === 'COMPLETED') throw new Error('Нет ожидаемого исправления в завершённом событии. Выполните reset календаря.');
        await api.request('POST', `/competition-units/${unit.id}/results`, [{ ...value, timeMs: value.timeMs! + 100 }], tokens.secretary);
      }
    }
    if (heat.entries.some(e => !e.resultStatus || e.resultStatus === 'PENDING')) throw new Error('Заплыв завершён без полного результата.');
  }
  const all: Heat[] = [];
  for (const unit of scheduled) all.push(await api.request<Heat>('GET', `/competition-units/${unit.id}`, undefined, tokens.organizer));
  if (all.some(h => target.some(u => u.id === h.id) && h.status !== 'FINISHED')) throw new Error('Не подтверждён финиш заплыва.');
  return { heats: all.length, finishedHeats: all.filter(h => h.status === 'FINISHED').length,
    results: all.flatMap(h => h.entries).filter(e => e.resultStatus && e.resultStatus !== 'PENDING').length,
    corrected: all.flatMap(h => h.entries).filter(e => e.resultStatus === 'CORRECTED').length };
}
