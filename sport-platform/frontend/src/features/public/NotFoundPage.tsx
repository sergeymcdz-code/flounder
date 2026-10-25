import { useEffect } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'

export function NotFoundPage() {
  useEffect(() => { document.title = 'Страница не найдена · SportGate' }, [])
  return <EmptyState headingLevel={1} title="Страница не найдена"
    action={<Button as={Link} to="/" variant="primary">К календарю соревнований</Button>}>
    <p>Проверьте адрес или откройте календарь соревнований.</p>
  </EmptyState>
}
