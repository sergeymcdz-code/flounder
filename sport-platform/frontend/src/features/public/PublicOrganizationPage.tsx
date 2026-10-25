import { useCallback, useEffect } from 'react'
import { Link, useParams } from 'react-router'
import { calendarApi, organizationLogo } from '../../api/calendar.api'
import { usePublicData } from './usePublicData'
import { PageHeader } from '../../components/ui/PageHeader'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { CalendarCards } from './CalendarCards'
import styles from './PublicOrganizationPage.module.css'
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
  return <div className={styles.page}>
    <Button as={Link} to="/" aria-label="К календарю" variant="ghost" size="sm" className="fl-back-link">← К календарю</Button>
    <section className={styles.hero}>
      {org && (org.hasLogo ? <img className={styles.logo} src={organizationLogo(slug)} alt={`Логотип ${org.name}`} /> : <span className={styles.initials} aria-hidden="true">{org.name.split(' ').slice(0, 2).map(word => word[0]).join('')}</span>)}
      <div className={styles.heading}><p className="fl-eyebrow">Организатор соревнований</p><PageHeader title={state.errorStatus === 404 ? 'Организация не найдена' : org?.name || 'Организация'} meta={org?.city || undefined} /></div>
    </section>
    {state.loading && <LoadingState message="Загружаем организацию…" />}
    {state.errorStatus === 404 ? <EmptyState title="Проверьте адрес страницы" action={<Button as={Link} to="/">Найти соревнование</Button>}><p>Страница организации недоступна или ещё не опубликована.</p></EmptyState> : state.error && <ErrorState message={state.error} onRetry={state.retry} />}
    {org && <>
      {org.description && <p className={styles.description}>{org.description}</p>}
      {(org.website || org.publicEmail || org.publicPhone) && <div className={styles.contacts} aria-label="Контакты организации">
        {org.website && /^https?:\/\//i.test(org.website) && <Button as="a" href={org.website} target="_blank" rel="noopener noreferrer" variant="ghost">Сайт организации ↗</Button>}
        {org.publicEmail && <Button as="a" href={`mailto:${org.publicEmail}`} variant="ghost">{org.publicEmail}</Button>}
        {org.publicPhone && <Button as="a" href={`tel:${org.publicPhone.replace(/[^+0-9]/g, '')}`} variant="ghost">{org.publicPhone}</Button>}
      </div>}
      <dl className={styles.stats}>
        <div><dt>Соревнований</dt><dd>{org.stats.eventsTotal}</dd></div>
        <div><dt>Ближайших стартов</dt><dd>{org.stats.eventsUpcoming}</dd></div>
        <div><dt>Участий спортсменов</dt><dd>{org.stats.participantsTotal}</dd></div>
      </dl>
      <section className={styles.events}><h2>Ближайшие старты</h2>
        {org.upcoming.length ? <CalendarCards items={org.upcoming} /> : <EmptyState title="Ближайших стартов пока нет" action={<Button as={Link} to="/" variant="ghost">Открыть календарь</Button>}><p>Новые соревнования появятся после публикации организатором.</p></EmptyState>}
        {org.stats.eventsUpcoming > org.upcoming.length && <Button as={Link} to={`/?organization=${encodeURIComponent(slug)}`} variant="ghost">Все ближайшие старты →</Button>}
      </section>
      <section className={styles.events}><h2>Прошедшие старты</h2>
        {org.past.length ? <CalendarCards items={org.past} /> : <EmptyState title="Прошедших стартов пока нет" action={<Button as={Link} to={`/?organization=${encodeURIComponent(slug)}&status=past`} variant="ghost">Открыть календарь организации</Button>}><p>Здесь будут завершённые соревнования и результаты.</p></EmptyState>}
        {org.stats.eventsTotal - org.stats.eventsUpcoming > org.past.length && <Button as={Link} to={`/?organization=${encodeURIComponent(slug)}&status=past`} variant="ghost">Все прошедшие старты →</Button>}
      </section>
    </>}
  </div>
}
