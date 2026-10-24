import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { ThemeToggle } from '../components/ThemeToggle'
import { ActionBar } from '../components/ui/ActionBar'
import { Button } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { DataTable } from '../components/ui/DataTable'
import { EmptyState } from '../components/ui/EmptyState'
import { Notice } from '../components/ui/Notice'
import { PageHeader } from '../components/ui/PageHeader'
import { StatusBadge } from '../components/ui/StatusBadge'
import { Tabs } from '../components/ui/Tabs'
import { useToast } from '../components/ui/Toast'
import { statusDictionary, type StatusKind } from '../components/ui/status-dictionary'
import './ui-showcase.css'

const palette = [
  ['bg', 'Фон'], ['surface', 'Поверхность'], ['border', 'Граница'], ['text', 'Текст'],
  ['muted', 'Вторичный текст'], ['primary', 'Вода'], ['accent', 'Финиш'],
  ['success', 'Успех'], ['warning', 'Внимание'], ['danger', 'Ошибка'],
] as const
const swimmers = [
  { id: '1', place: 1, name: 'Александра Константинова', club: 'СШОР «Олимп», Самара', time: '00:27.84', status: 'VALID' },
  { id: '2', place: 2, name: 'Мария Александрова', club: 'Волна, Москва', time: '00:28.06', status: 'CORRECTED' },
  { id: '3', place: '—', name: 'Екатерина Смирнова', club: 'СШОР «Олимп», Самара', time: '—', status: 'DSQ' },
  { id: '4', place: '—', name: 'Анна Петрова', club: 'Волна, Москва', time: '—', status: 'DNS' },
  { id: '5', place: '—', name: 'Софья Васильева', club: 'Дельфин, Казань', time: '—', status: 'DNF' },
  { id: '6', place: '—', name: 'Дарья Морозова', club: 'Волна, Москва', time: '—', status: 'PENDING' },
]
function Section({ number, title, children }: { number: string; title: string; children: ReactNode }) {
  return <section className="ui-section"><h2><span>{number}</span>{title}</h2>{children}</section>
}

export default function UiShowcase() {
  const toast = useToast()
  const [dialog, setDialog] = useState<'primary' | 'danger' | null>(null)
  const [loading, setLoading] = useState(false)
  const [params, setParams] = useSearchParams()
  const compact = params.get('density') === 'compact'
  return <main className="ui-showcase">
    <header className="ui-masthead"><a href="/dev/ui" className="ui-brand">SPORT<span>GATE</span><small>ПЛАВАНИЕ / ДИЗАЙН-СИСТЕМА</small></a><ThemeToggle /></header>
    <div className="ui-intro"><p className="ui-eyebrow">D04 · ОСНОВА ИНТЕРФЕЙСА</p><h1>Чёткий ритм.<br />Точный результат.</h1><p>Токены и компоненты SportGate. Одна система для старта, дистанции и финиша.</p><span className="ui-version">01 / ФУНДАМЕНТ</span></div>
    <Section number="01" title="Цвет и типографика">
      <div className="ui-palette">{palette.map(([token, label]) => <div key={token} className="ui-swatch"><span style={{ background: `var(--color-${token})` }} /><strong>{label}</strong><code>--color-{token}</code></div>)}</div>
      <div className="ui-type-grid"><div><p className="ui-label">GOLOS TEXT / ОСНОВНОЙ ТЕКСТ</p><p>Соревнования по плаванию. Заявки, стартовые протоколы и результаты.</p><div className="ui-type-scale">{[12, 14, 16, 18, 20, 24, 28, 32].map(size => <span key={size} style={{ fontSize: `var(--font-size-${size})` }}>{size}</span>)}</div></div><div className="ui-timing"><p className="ui-label">OSWALD / ТАБЛИЧНЫЕ ЦИФРЫ</p><strong className="fl-numeric">00:27.84</strong><p className="fl-numeric">01 место · 50 м · Вольный стиль</p></div></div>
    </Section>
    <Section number="02" title="Кнопки и действия">
      <div className="ui-stack">{(['primary', 'secondary', 'ghost', 'danger'] as const).map(variant => <div key={variant} className="ui-button-row"><code>{variant}</code><Button variant={variant} size="sm" onClick={() => toast({ text: 'Нажатие обработано' })}>Малая</Button><Button variant={variant} onClick={() => toast({ text: 'Действие выполнено' })}>Обычная</Button><Button variant={variant} size="lg" onClick={() => toast({ text: 'Действие выполнено' })}>Крупная</Button><Button variant={variant} disabled>Недоступна</Button><Button variant={variant} loading>Сохранить</Button></div>)}</div>
      <ActionBar className="ui-demo-actions"><Button variant="primary" loading={loading} onClick={() => setLoading(true)}>Проверить загрузку</Button><Button onClick={() => setLoading(false)}>Сбросить загрузку</Button><Button as="a" href="#ui-table">К таблице ↓</Button></ActionBar>
    </Section>
    <Section number="03" title="Статусы">
      <div className="ui-status-grid">{(Object.keys(statusDictionary) as StatusKind[]).map(kind => <div key={kind}><p className="ui-label">{{ event: 'Соревнование', heat: 'Заплыв', registration: 'Заявка', payment: 'Оплата', document: 'Документ', result: 'Результат' }[kind]}</p><div className="ui-badges">{Object.keys(statusDictionary[kind]).map(code => <StatusBadge key={code} kind={kind} code={code} />)}</div></div>)}</div>
    </Section>
    <Section number="04" title="Заголовок и навигация">
      <div className="ui-component-frame"><PageHeader title="Кубок SportGate" badge={<StatusBadge kind="event" code="PUBLISHED" />} meta="24.10.2026 · Самара · Бассейн 50 м" actions={<><Button onClick={() => toast({ text: 'Протокол готов к проверке' })}>Протокол</Button><Button variant="primary" onClick={() => setDialog('primary')}>Опубликовать</Button></>} /><Tabs label="Разделы витрины" items={[{ to: '/dev/ui', label: 'Обзор' }, { to: '/dev/ui/entries', label: 'Заявки', count: 128, attention: 3 }, { to: '/dev/ui/results', label: 'Результаты', count: 24 }, { to: '/dev/ui/settings', label: 'Настройки соревнования' }]} /></div>
    </Section>
    <Section number="05" title="Таблица результатов">
      <div id="ui-table" className="ui-table-demo"><ActionBar><Button size="sm" aria-pressed={!compact} onClick={() => setParams({})}>Обычная плотность</Button><Button size="sm" aria-pressed={compact} onClick={() => setParams({ density: 'compact' })}>Компактная</Button></ActionBar><p className="ui-label">ЛИПКИЙ ЗАГОЛОВОК · ПРОКРУТКА ВНУТРИ КОНТЕЙНЕРА</p><DataTable caption="Женщины · 50 м вольный стиль · Финал" rows={swimmers} rowKey={row => row.id} minWidth={760} compact={compact} maxHeight={280} columns={[
        { key: 'place', header: 'Место', numeric: true, render: row => row.place },
        { key: 'name', header: 'Участница', rowHeader: true, render: row => row.name },
        { key: 'club', header: 'Команда', render: row => row.club },
        { key: 'time', header: 'Результат', numeric: true, render: row => row.time },
        { key: 'status', header: 'Статус', render: row => <StatusBadge kind="result" code={row.status} /> },
      ]} /></div>
    </Section>
    <Section number="06" title="Обратная связь">
      <div className="ui-feedback-grid"><div className="ui-stack"><Notice>Черновик доступен только организатору.</Notice><Notice tone="info">Стартовый протокол готов к проверке.</Notice><Notice tone="warning">Три заявки требуют исправления.</Notice><Notice tone="danger">Не удалось сохранить. Проверьте соединение и повторите попытку.</Notice></div><EmptyState title="Заплывов пока нет" action={<Button variant="primary" onClick={() => toast({ text: 'Пример действия: добавить заплыв' })}>Добавить заплыв</Button>}><p>Добавьте первый заплыв, чтобы подготовить стартовый протокол.</p></EmptyState></div>
      <ActionBar className="ui-demo-actions"><Button onClick={() => toast({ text: 'Изменения сохранены' })}>Toast: успех</Button><Button onClick={() => toast({ text: 'Не удалось сохранить. Повторите попытку.', tone: 'danger' })}>Toast: ошибка</Button><Button onClick={() => setDialog('primary')}>Подтверждение</Button><Button variant="danger" onClick={() => setDialog('danger')}>Диалог с причиной</Button></ActionBar>
    </Section>
    <footer className="ui-footer"><strong>SPORTGATE</strong><span>4 px сетка · 44 px тап-цели · Светлая / тёмная тема</span></footer>
    {dialog && <ConfirmDialog title={dialog === 'danger' ? 'Снять заявку?' : 'Опубликовать протокол?'} description={dialog === 'danger' ? 'Участник увидит причину снятия.' : 'Участники увидят обновлённый стартовый протокол.'} confirmLabel={dialog === 'danger' ? 'Снять заявку' : 'Опубликовать'} tone={dialog} reason={dialog === 'danger'} onClose={() => setDialog(null)} onConfirm={() => { setDialog(null); toast({ text: 'Действие подтверждено' }) }} />}
  </main>
}
