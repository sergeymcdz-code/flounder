import { useCallback, useEffect } from 'react'
import { Link, useParams } from 'react-router'
import { calendarApi, organizationLogo } from '../../api/calendar.api'
import { usePublicData } from './usePublicData'
import { DataState } from './PublicLayout'
import { CalendarCards } from './CalendarCards'
import styles from './Calendar.module.css'
export function PublicOrganizationPage() {
  const { slug = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => calendarApi.organization(slug, signal), [slug])
  const state = usePublicData(load)
  const org = state.data
  useEffect(() => {
    const title = org ? `${org.name} · SportGate` : 'Организация · SportGate'
    const previousTitle = document.title
    document.title = title
    const metas = ['og:title', 'og:description'].map((property, index) => {
      const existing = document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`)
      const node = existing ?? document.createElement('meta'); const previous = node.getAttribute('content')
      node.setAttribute('property', property); node.content = index === 0 ? title : org?.description ?? 'Календарь стартов и результаты соревнований'
      if (!existing) document.head.append(node)
      return { node, existing, previous }
    })
    return () => {
      document.title = previousTitle
      metas.forEach(({ node, existing, previous }) => { if (!existing) node.remove(); else if (previous === null) node.removeAttribute('content'); else node.content = previous })
    }
  }, [org])
  if (state.errorStatus === 404) return <><h1>Организация не найдена</h1><Link to="/">К календарю</Link></>
  return <>
    <DataState {...state} />
    {org && <>
      <section className={styles.orgHeader}>
        {org.hasLogo ? <img className={styles.logo} src={organizationLogo(slug)} alt={`Логотип ${org.name}`} /> : <span className={styles.initials} aria-hidden="true">{org.name.split(' ').slice(0, 2).map(s => s[0]).join('')}</span>}
        <div><p className={styles.eyebrow}>Организатор соревнований</p><h1>{org.name}</h1>{org.city && <p>{org.city}</p>}</div>
      </section>
      {org.description && <p className={styles.description}>{org.description}</p>}
      <div className={styles.contacts}>
        {org.website && /^https?:\/\//i.test(org.website) && <a href={org.website} target="_blank" rel="noopener noreferrer">Сайт организации ↗</a>}
        {org.publicEmail && <a href={`mailto:${org.publicEmail}`}>{org.publicEmail}</a>}
        {org.publicPhone && <a href={`tel:${org.publicPhone.replace(/[^+0-9]/g, '')}`}>{org.publicPhone}</a>}
      </div>
      <dl className={styles.stats}>
        <div><dt>Соревнований</dt><dd>{org.stats.eventsTotal}</dd></div>
        <div><dt>Ближайших стартов</dt><dd>{org.stats.eventsUpcoming}</dd></div>
        <div><dt>Участий спортсменов</dt><dd>{org.stats.participantsTotal}</dd></div>
      </dl>
      <section><h2>Ближайшие старты</h2><CalendarCards items={org.upcoming} />{org.stats.eventsUpcoming > org.upcoming.length && <Link to={`/?organization=${encodeURIComponent(slug)}`}>Все ближайшие старты →</Link>}</section>
      <section><h2>Прошедшие старты</h2><CalendarCards items={org.past} />{org.stats.eventsTotal - org.stats.eventsUpcoming > org.past.length && <Link to={`/?organization=${encodeURIComponent(slug)}&status=past`}>Все прошедшие старты →</Link>}</section>
    </>}
  </>
}
