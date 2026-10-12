/**
 * Доменные типы Flounder. Нейтральны к виду спорта: «заплыв» = CompetitionUnit (heat),
 * но первый сценарий — плавание, поэтому терминология в комментариях плавательная.
 */

export type Gender = 'M' | 'F' | 'X'; // X = смешанная/без разделения

export type Stroke = 'FREE' | 'BACK' | 'BREAST' | 'FLY' | 'MEDLEY';

export interface AgeGroup {
  id: string;
  code: string; // например "2014-2015"
  name: string; // "Юноши 2014–2015 г.р."
  gender: Gender;
  /** Год рождения включительно; null = без ограничения */
  birthYearFrom: number | null; // старший год (меньшее число), например 2014; birthYear >= from
  birthYearTo: number | null; // младший год (большее число), например 2015; birthYear <= to
}

/** Вид программы (дисциплина): 50 м в/с, девушки, 2012–2013 */
export interface EventDef {
  id: string;
  number: number; // порядковый номер вида программы в соревновании
  distance: number; // метры
  stroke: Stroke;
  gender: Gender;
  /** Какие возрастные группы участвуют; пусто = все */
  ageGroupIds: string[];
  /** Посев: все вместе по времени или отдельно по возрастным группам */
  seedingMode: 'BY_TIME_ALL' | 'BY_AGE_GROUP';
  sessionId: string;
  orderInSession: number;
}

export interface Athlete {
  id: string;
  lastName: string;
  firstName: string;
  birthYear: number;
  gender: Gender;
  club?: string | undefined;
}

export interface Entry {
  id: string;
  eventId: string;
  athleteId: string;
  /** Заявочное время в миллисекундах; null = NT (без времени) */
  seedTimeMs: number | null;
  status: 'SUBMITTED' | 'CONFIRMED' | 'WITHDRAWN' | 'REJECTED';
}

export interface LaneAssignment {
  lane: number; // 1..lanes
  entryId: string;
  athleteId: string;
  seedTimeMs: number | null;
}

export interface Heat {
  /** Номер заплыва внутри вида программы, с 1 */
  number: number;
  eventId: string;
  ageGroupId: string | null; // заполнен при посеве BY_AGE_GROUP
  lanes: LaneAssignment[];
  /** Расчётное время старта (ISO) — заполняет планировщик */
  scheduledAt?: string | undefined;
}

export interface PoolConfig {
  lanes: number; // 6, 8, 10
  courseLength: 25 | 50;
  /** Минимальное число участников в первом (самом слабом) заплыве. SW 3.1.1.1: не менее 3 */
  minSwimmersInHeat?: number | undefined;
}

export interface Session {
  id: string;
  name: string;
  /** ISO datetime старта сессии (с учётом часового пояса площадки) */
  startAt: string;
  /** Пауза между видами программы, сек */
  breakBetweenEventsSec?: number | undefined;
}

export interface SchedulingRules {
  /** Длительность заплыва по дистанции, сек (включая выход/уход). Ключ — дистанция в метрах */
  heatDurationSecByDistance: Record<number, number>;
  /** Длительность по умолчанию, если дистанции нет в таблице */
  defaultHeatDurationSec: number;
  /** Пауза между видами программы, сек, если у сессии не задано иное */
  breakBetweenEventsSec: number;
  /** Минимальный интервал между стартами одного спортсмена, сек (мягкое ограничение) */
  minRestBetweenStartsSec: number;
}

export type ResultStatus = 'OK' | 'DNS' | 'DNF' | 'DSQ';

export interface ResultInput {
  entryId: string;
  timeMs: number | null;
  status: ResultStatus;
}

export interface RankedResult extends ResultInput {
  athleteId: string;
  eventId: string;
  ageGroupId: string | null;
  /** Место в абсолюте по виду программы; null если не ранжируется */
  placeOverall: number | null;
  /** Место в своей возрастной группе */
  placeInAgeGroup: number | null;
}

export type ViolationSeverity = 'ERROR' | 'WARNING';

export interface Violation {
  code: string;
  severity: ViolationSeverity;
  message: string;
  eventId?: string | undefined;
  heatNumber?: number | undefined;
  athleteId?: string | undefined;
  entryId?: string | undefined;
}
