import { Link } from 'react-router'
import { usePublicEvent } from './publicContext'
import { date, time } from './format'
import styles from './Public.module.css'

export function PublicEventPage() {
  const event = usePublicEvent()
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  return (
    <>
      <p className={styles.eyebrow}>Соревнования по плаванию</p>
      <h1>{event.title}</h1>
      <section className={styles.card}>
        <h2>О соревновании</h2>
        <p>
          {date(event.eventStartAt)}
          {event.eventEndAt.slice(0, 10) !== event.eventStartAt.slice(0, 10)
            ? ` — ${date(event.eventEndAt)}`
            : ''}
        </p>
        <p>
          {event.venue.name}, {event.venue.city}
        </p>
        <p>
          {event.venue.lanes} дорожек · бассейн {event.venue.courseLength} м
        </p>
        {event.description && <p>{event.description}</p>}
        {event.registrationOpen && (
          <Link className={styles.button} to={`${base}/register`}>
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
          ['live', 'Live', 'Сейчас в бассейне и следующие заплывы'],
          ['results', 'Результаты', 'Абсолют и возрастные группы'],
        ].map(([path, title, description]) => (
          <Link key={path} className={styles.cardLink} to={`${base}/${path}`}>
            <strong>{title} →</strong>
            <span>{description}</span>
          </Link>
        ))}
      </div>
      <section className={styles.card}>
        <h2>Сессии и виды программы</h2>
        {[...event.sessions]
          .sort((a, b) => a.orderNo - b.orderNo)
          .map((session) => (
            <div key={session.id}>
              <h3>
                {session.name} · {time(session.startsAt)}
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
