import { ResultStatus } from '../results/ResultStatus'
import { StatusLegend } from '../results/StatusLegend'
import type { Heat } from './public.types'
import { seedTime } from './format'
import styles from './LivePage.module.css'

export function LiveLanes({ heat, lanes }: { heat: Heat; lanes: number }) {
  const entries = Array.from({ length: lanes }, (_, index) => {
    const lane = heat.lanes.find((item) => item.lane === index + 1)
    return lane && !lane.empty ? lane : undefined
  })
  return (
    <>
      <div className={styles.lanesContainer}>
        <table className={`${styles.lanes} fl-table fl-protocol fl-lanes`} role="table" tabIndex={0}>
          <caption className="fl-sr-only">
            Дорожки заплыва №{heat.number}. Заявочное время
          </caption>
          <thead>
            <tr>
              <th scope="col">
                <abbr title="Дорожка">Дор</abbr>
              </th>
              <th scope="col">
                <span className={styles.desktop}>Фамилия Имя</span>
                <span className={styles.mobile}>Участник</span>
              </th>
              <th scope="col" className={styles.desktop}>
                Год
              </th>
              <th scope="col" className={styles.desktop}>
                Клуб
              </th>
              <th scope="col" data-numeric>
                Время
              </th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry, index) => {
              const display =
                entry && !entry.withdrawn ? seedTime(entry.seedTimeMs) : '—'
              const [seconds, hundredths] = display.split('.')
              return (
                <tr
                  key={index}
                  className={entry?.withdrawn ? styles.withdrawn : undefined}
                >
                  <th scope="row">
                    <span className={styles.laneNumber}>{index + 1}</span>
                  </th>
                  <td className={styles.participant}>
                    {entry ? (
                      <>
                        <span className={styles.name}>
                          {entry.athlete.lastName} {entry.athlete.firstName}
                        </span>
                        {entry.withdrawn && <small>Снят</small>}
                      </>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className={styles.year} data-numeric>
                    {entry?.athlete.birthYear ?? '—'}
                  </td>
                  <td className={styles.club}>{entry?.athlete.club ?? '—'}</td>
                  <td
                    data-numeric
                    className={`${styles.stopwatch} fl-stopwatch`}
                  >
                    {hundredths ? (
                      <span aria-label={display}>
                        {seconds}
                        <small>.{hundredths}</small>
                      </span>
                    ) : (
                      <ResultStatus status={display} />
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <StatusLegend
        statuses={entries.map((entry) =>
          entry && !entry.withdrawn && entry.seedTimeMs === null ? 'NT' : null,
        )}
      />
    </>
  )
}
