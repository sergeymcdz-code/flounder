import type { AgeGroup, Athlete, Entry, EventDef, Heat, LaneAssignment, PoolConfig, Violation } from './types.js';
import { laneOrder } from './lanes.js';

export interface SeedingOptions {
  /** Круговой посев трёх сильнейших заплывов (SW 3.1.1.3). По умолчанию выключен (прямой посев). */
  circleSeedTopHeats?: boolean | undefined;
}

export interface SeedEventInput {
  event: EventDef;
  entries: Entry[];
  athletes: Map<string, Athlete>;
  ageGroups: AgeGroup[];
  pool: PoolConfig;
  options?: SeedingOptions | undefined;
}

export interface SeedEventResult {
  heats: Heat[];
  issues: Violation[];
  /** Заявки, которые не попали в посев (не допущены), с причиной в issues */
  excludedEntryIds: string[];
}

/** Находит возрастную группу спортсмена среди групп вида программы. */
export function resolveAgeGroup(athlete: Athlete, groups: AgeGroup[]): AgeGroup | null {
  for (const g of groups) {
    const genderOk = g.gender === 'X' || g.gender === athlete.gender;
    const fromOk = g.birthYearFrom === null || athlete.birthYear >= g.birthYearFrom; // from = старший (меньший) год
    const toOk = g.birthYearTo === null || athlete.birthYear <= g.birthYearTo; // to = младший (больший) год
    if (genderOk && fromOk && toOk) return g;
  }
  return null;
}

/**
 * Размеры заплывов по SW 3.1.1.1: слабейший заплыв первый, все последующие полные,
 * ни в одном заплыве не меньше minSwimmers (по умолчанию 3), если участников хватает.
 */
export function heatSizes(n: number, lanes: number, minSwimmers = 3): number[] {
  if (n <= 0) return [];
  if (lanes <= 0) throw new Error('lanes must be > 0');
  if (n <= lanes) return [n];
  const full = Math.floor(n / lanes);
  const rest = n % lanes;
  if (rest === 0) return new Array<number>(full).fill(lanes);
  if (rest >= minSwimmers) return [rest, ...new Array<number>(full).fill(lanes)];
  // забираем недостающих из второго заплыва
  const need = minSwimmers - rest;
  const second = lanes - need;
  return [minSwimmers, second, ...new Array<number>(full - 1).fill(lanes)];
}

function compareBySeed(a: Entry, b: Entry, athletes: Map<string, Athlete>): number {
  // быстрее — раньше; NT — в конец; при равенстве — детерминированно по фамилии/имени/id
  if (a.seedTimeMs === null && b.seedTimeMs !== null) return 1;
  if (a.seedTimeMs !== null && b.seedTimeMs === null) return -1;
  if (a.seedTimeMs !== null && b.seedTimeMs !== null && a.seedTimeMs !== b.seedTimeMs) {
    return a.seedTimeMs - b.seedTimeMs;
  }
  const aa = athletes.get(a.athleteId);
  const ba = athletes.get(b.athleteId);
  const an = `${aa?.lastName ?? ''} ${aa?.firstName ?? ''}`;
  const bn = `${ba?.lastName ?? ''} ${ba?.firstName ?? ''}`;
  const c = an.localeCompare(bn, 'ru');
  return c !== 0 ? c : a.id.localeCompare(b.id);
}

/**
 * Прямой посев одной группы участников (уже отсортированных от быстрого к медленному).
 * Возвращает заплывы в порядке проведения: слабейший первым.
 */
function straightSeed(
  sorted: Entry[],
  lanes: number,
  minSwimmers: number,
  circleTop: boolean,
): LaneAssignment[][] {
  const sizes = heatSizes(sorted.length, lanes, minSwimmers);
  const order = laneOrder(lanes);
  const heats: LaneAssignment[][] = sizes.map(() => []);
  // индекс по быстрым: fastest первым заполняет последний заплыв
  let cursor = 0;
  const topCircle = circleTop && sizes.length >= 3 && sizes.slice(-3).every((s) => s === lanes);
  if (topCircle) {
    // круговой посев: три последних заплыва заполняются по кругу: L, L-1, L-2 на дорожку 4, затем L, L-1, L-2 на дорожку 5 ...
    const last = sizes.length - 1;
    const circleHeats = [last, last - 1, last - 2];
    for (let laneIdx = 0; laneIdx < lanes; laneIdx++) {
      for (const h of circleHeats) {
        const e = sorted[cursor++];
        if (!e) break;
        heats[h]!.push({ lane: order[laneIdx]!, entryId: e.id, athleteId: e.athleteId, seedTimeMs: e.seedTimeMs });
      }
    }
    for (let h = last - 3; h >= 0; h--) {
      for (let laneIdx = 0; laneIdx < sizes[h]!; laneIdx++) {
        const e = sorted[cursor++];
        if (!e) break;
        heats[h]!.push({ lane: order[laneIdx]!, entryId: e.id, athleteId: e.athleteId, seedTimeMs: e.seedTimeMs });
      }
    }
  } else {
    for (let h = sizes.length - 1; h >= 0; h--) {
      for (let laneIdx = 0; laneIdx < sizes[h]!; laneIdx++) {
        const e = sorted[cursor++];
        if (!e) break;
        heats[h]!.push({ lane: order[laneIdx]!, entryId: e.id, athleteId: e.athleteId, seedTimeMs: e.seedTimeMs });
      }
    }
  }
  for (const h of heats) h.sort((a, b) => a.lane - b.lane);
  return heats;
}

/**
 * Посев вида программы: допуск → сортировка → распределение по заплывам и дорожкам.
 * Детерминирован: одинаковый вход даёт одинаковый результат.
 */
export function seedEvent(input: SeedEventInput): SeedEventResult {
  const { event, entries, athletes, ageGroups, pool } = input;
  const issues: Violation[] = [];
  const excludedEntryIds: string[] = [];
  const minSwimmers = pool.minSwimmersInHeat ?? 3;
  const eventGroups = event.ageGroupIds.length
    ? event.ageGroupIds.map((id) => ageGroups.find((g) => g.id === id)).filter((g): g is AgeGroup => !!g)
    : [];

  // 1. допуск
  const eligible: { entry: Entry; group: AgeGroup | null }[] = [];
  const seenAthletes = new Set<string>();
  for (const entry of entries) {
    if (entry.eventId !== event.id) continue;
    if (entry.status !== 'CONFIRMED') continue;
    const athlete = athletes.get(entry.athleteId);
    if (!athlete) {
      issues.push({ code: 'ATHLETE_NOT_FOUND', severity: 'ERROR', message: `Заявка ${entry.id}: спортсмен не найден`, eventId: event.id, entryId: entry.id });
      excludedEntryIds.push(entry.id);
      continue;
    }
    if (seenAthletes.has(athlete.id)) {
      issues.push({ code: 'DUPLICATE_ENTRY', severity: 'ERROR', message: `${athlete.lastName} ${athlete.firstName}: повторная заявка на вид №${event.number}`, eventId: event.id, entryId: entry.id, athleteId: athlete.id });
      excludedEntryIds.push(entry.id);
      continue;
    }
    if (event.gender !== 'X' && athlete.gender !== event.gender) {
      issues.push({ code: 'GENDER_MISMATCH', severity: 'ERROR', message: `${athlete.lastName} ${athlete.firstName}: пол не соответствует виду №${event.number}`, eventId: event.id, entryId: entry.id, athleteId: athlete.id });
      excludedEntryIds.push(entry.id);
      continue;
    }
    let group: AgeGroup | null = null;
    if (eventGroups.length) {
      group = resolveAgeGroup(athlete, eventGroups);
      if (!group) {
        issues.push({ code: 'AGE_GROUP_MISMATCH', severity: 'ERROR', message: `${athlete.lastName} ${athlete.firstName} (${athlete.birthYear}): не подходит ни под одну возрастную группу вида №${event.number}`, eventId: event.id, entryId: entry.id, athleteId: athlete.id });
        excludedEntryIds.push(entry.id);
        continue;
      }
    }
    seenAthletes.add(athlete.id);
    eligible.push({ entry, group });
  }

  // 2. группировка и посев
  const heats: Heat[] = [];
  let heatNo = 1;
  const pushHeats = (lanesSets: LaneAssignment[][], groupId: string | null) => {
    for (const lanes of lanesSets) {
      heats.push({ number: heatNo++, eventId: event.id, ageGroupId: groupId, lanes });
    }
  };
  const circle = input.options?.circleSeedTopHeats ?? false;

  if (event.seedingMode === 'BY_AGE_GROUP' && eventGroups.length) {
    for (const g of eventGroups) {
      const list = eligible.filter((x) => x.group?.id === g.id).map((x) => x.entry);
      list.sort((a, b) => compareBySeed(a, b, athletes));
      if (list.length === 0) continue;
      if (list.length < minSwimmers) {
        issues.push({ code: 'SMALL_GROUP', severity: 'WARNING', message: `Вид №${event.number}, группа ${g.name}: только ${list.length} участн. — заплыв неполный`, eventId: event.id });
      }
      pushHeats(straightSeed(list, pool.lanes, minSwimmers, circle), g.id);
    }
  } else {
    const list = eligible.map((x) => x.entry);
    list.sort((a, b) => compareBySeed(a, b, athletes));
    if (list.length > 0) pushHeats(straightSeed(list, pool.lanes, minSwimmers, circle), null);
  }

  if (heats.length === 0) {
    issues.push({ code: 'NO_ENTRIES', severity: 'WARNING', message: `Вид №${event.number}: нет допущенных заявок`, eventId: event.id });
  }
  return { heats, issues, excludedEntryIds };
}
