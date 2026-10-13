import { Link } from 'react-router'
import { usePublicEvent } from './publicContext'
import { date, localTimeLabel, time } from './format'
import styles from './Public.module.css'
import eventStyles from './EventPage.module.css'

export function PublicEventPage() {
  const event = usePublicEvent()
  const startDate = date(event.eventStartAt, event.venue?.timezone)
  const endDate = date(event.eventEndAt, event.venue?.timezone)
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  return (
    <>
      <p className={styles.eyebrow}>Соревнования по плаванию</p>
      <h1 className={eventStyles.hero}>{event.title}</h1>
      <section className={`${styles.card} ${eventStyles.overview}`}>
        <h2>О соревновании</h2>
        <p>
          {startDate}
          {endDate !== startDate ? ` — ${endDate}` : ''}
        </p>
        <p>
          {event.venue
            ? `${event.venue.name}, ${event.venue.city}`
            : 'Площадка не указана'}
        </p>
        {event.venue && (
          <p>
            {event.venue.lanes} дорожек ·{' '}
            {event.venue.courseLength !== null
              ? `бассейн ${event.venue.courseLength} м`
              : 'длина бассейна не указана'}
          </p>
        )}
        {event.description && <p>{event.description}</p>}
        {event.registrationOpen && (
          <Link className={`${styles.button} fl-button-primary`} to={`${base}/register`}>
            Подать заявку
          </Link>
        )}
      </section>
      <div className={styles.grid}>
        {[
          [
            'start-list',
            'Стартовый протокол',
            'Заплывы, дорожки и заявочное время',
          ],
          ['schedule', 'Расписание', 'Все старты по сессиям'],
          ['live', 'Табло', 'Сейчас в бассейне и следующие заплывы'],
          ['results', 'Результаты', 'Абсолют и возрастные группы'],
        ].map(([path, title, description]) => (
          <Link key={path} className={styles.cardLink} to={`${base}/${path}`}>
            <strong>{title} →</strong>
            <span>{description}</span>
          </Link>
        ))}
      </div>
      <section className={`${styles.card} ${eventStyles.sessions}`}>
        <h2>Сессии и виды программы</h2>
        <p className={styles.muted}>
          {localTimeLabel(event.eventStartAt, event.venue?.timezone)}
        </p>
        {[...event.sessions]
          .sort((a, b) => a.orderNo - b.orderNo)
          .map((session) => (
            <div key={session.id}>
              <h3>
                {session.name} · {time(session.startsAt, event.venue?.timezone)}
              </h3>
              <ul className={styles.list}>
                {event.disciplines
                  .filter((item) => item.sessionId === session.id)
                  .sort(
                    (a, b) =>
                      (a.orderInSession ?? Infinity) -
                      (b.orderInSession ?? Infinity),
                  )
                  .map((item) => (
                    <li key={item.id}>
                      Вид № {item.number} · {item.name}
                      {!item.unitsPublished && (
                        <small>Стартовый протокол ещё не опубликован</small>
                      )}
                    </li>
                  ))}
              </ul>
            </div>
          ))}
        {event.disciplines.some((item) => item.sessionId === null) && (
          <div>
            <h3>Без сессии</h3>
            <ul className={styles.list}>
              {event.disciplines
                .filter((item) => item.sessionId === null)
                .sort(
                  (a, b) =>
                    (a.orderInSession ?? Infinity) -
                      (b.orderInSession ?? Infinity) || a.number - b.number,
                )
                .map((item) => (
                  <li key={item.id}>
                    Вид № {item.number} · {item.name}
                    {!item.unitsPublished && (
                      <small>Стартовый протокол ещё не опубликован</small>
                    )}
                  </li>
                ))}
            </ul>
          </div>
        )}
      </section>
    </>
  )
}
