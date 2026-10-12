import type { EventDef, Heat, SchedulingRules, Session } from './types.js';

export const DEFAULT_SCHEDULING_RULES: SchedulingRules = {
  heatDurationSecByDistance: { 25: 60, 50: 90, 100: 150, 200: 270, 400: 480, 800: 840, 1500: 1500 },
  defaultHeatDurationSec: 180,
  breakBetweenEventsSec: 60,
  minRestBetweenStartsSec: 15 * 60,
};

export function heatDurationSec(distance: number, rules: SchedulingRules): number {
  return rules.heatDurationSecByDistance[distance] ?? rules.defaultHeatDurationSec;
}

export interface ScheduledHeat extends Heat {
  scheduledAt: string;
  /** Абсолютный порядковый номер заплыва в сессии (для табло «сейчас идёт заплыв №N») */
  sequenceInSession: number;
}

/**
 * Раскладывает заплывы по времени внутри сессий: виды программы в порядке orderInSession,
 * заплывы друг за другом, пауза между видами. Чистая функция, детерминирована.
 */
export function scheduleHeats(
  sessions: Session[],
  events: EventDef[],
  heats: Heat[],
  rules: SchedulingRules = DEFAULT_SCHEDULING_RULES,
): ScheduledHeat[] {
  const out: ScheduledHeat[] = [];
  const bySession = new Map<string, EventDef[]>();
  for (const ev of events) {
    const list = bySession.get(ev.sessionId) ?? [];
    list.push(ev);
    bySession.set(ev.sessionId, list);
  }
  for (const session of sessions) {
    const evs = (bySession.get(session.id) ?? []).slice().sort((a, b) => a.orderInSession - b.orderInSession || a.number - b.number);
    let t = new Date(session.startAt).getTime();
    if (Number.isNaN(t)) throw new Error(`Session ${session.id}: invalid startAt ${session.startAt}`);
    const brk = (session.breakBetweenEventsSec ?? rules.breakBetweenEventsSec) * 1000;
    let seq = 1;
    let firstEvent = true;
    for (const ev of evs) {
      const evHeats = heats.filter((h) => h.eventId === ev.id).slice().sort((a, b) => a.number - b.number);
      if (evHeats.length === 0) continue;
      if (!firstEvent) t += brk;
      firstEvent = false;
      const dur = heatDurationSec(ev.distance, rules) * 1000;
      for (const h of evHeats) {
        out.push({ ...h, scheduledAt: new Date(t).toISOString(), sequenceInSession: seq++ });
        t += dur;
      }
    }
  }
  return out;
}
