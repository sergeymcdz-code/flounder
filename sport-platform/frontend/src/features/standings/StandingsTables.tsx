import type { Standings } from '../../api/standings.api'
import { swimCount } from './format'
import styles from './Standings.module.css'

export function StandingsTables({ data, disciplineNames = {} }: {
  data: Standings; disciplineNames?: Record<string, string>
}) {
  return <div className={styles.tables}>
    {data.preliminary && <p className={styles.notice} role="status">Предварительный зачёт · не завершено {swimCount(data.unitsPending)}</p>}
    <p>{data.scope === 'AGE_GROUP' ? 'По местам в возрастных группах' : 'По местам в абсолюте'}</p>
    <section aria-labelledby="medal-heading">
      <h2 id="medal-heading">Медальный зачёт</h2>
      <table><thead><tr><th scope="col">Клуб</th><th scope="col">Золото</th><th scope="col">Серебро</th><th scope="col">Бронза</th></tr></thead>
        <tbody>{data.medals.map(row => <tr key={row.club}><th scope="row">{row.club}</th><td>{row.gold}</td><td>{row.silver}</td><td>{row.bronze}</td></tr>)}</tbody>
      </table>
    </section>
    <section aria-labelledby="team-heading">
      <h2 id="team-heading">Командный зачёт</h2>
      <table><thead><tr><th scope="col">Клуб</th><th scope="col">Очки</th></tr></thead>
        <tbody>{data.team.map(row => <tr key={row.club}><th scope="row">{row.club}
          {row.breakdown && <details className={styles.details}><summary>Из чего сложилось</summary>
            <p>Зачтено результатов: {row.scoredResults} из {row.breakdown.length}</p>
            <ul>{row.breakdown.map((item, index) => <li key={index}>{disciplineNames[item.disciplineId] ?? item.disciplineId}{item.ageGroupCode ? ` · ${item.ageGroupCode}` : ''} · {item.athlete}: место {item.place}, очки {item.points}{!item.counted && ' · Не зачтён: лимит клуба'}</li>)}</ul>
          </details>}
        </th><td>{row.points}</td></tr>)}</tbody>
      </table>
    </section>
    {data.team.length === 0 && <p>Опубликованных результатов пока нет.</p>}
    <p className={styles.timestamp}>Рассчитано: {new Date(data.calculatedAt).toLocaleString('ru-RU')}</p>
  </div>
}
