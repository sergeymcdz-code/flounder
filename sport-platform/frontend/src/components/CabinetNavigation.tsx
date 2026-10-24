import { useAuth } from '../app/providers/AuthProvider'

export type NavigationItem = { to: string; label: string; end?: boolean }
export const adminNavigation: NavigationItem[] = [
  { to: '/admin/users', label: 'Пользователи' },
  { to: '/admin/organizations', label: 'Организации' },
  { to: '/admin/registration-requests', label: 'Заявки организаций' },
  { to: '/admin/sports', label: 'Виды спорта' },
  { to: '/admin/registrations/review', label: 'Проверка заявок' },
]
export function useCabinetNavigation(): NavigationItem[] {
  const { isOrganizer, isPlatformAdmin, isOperator, hasRole, memberships = [], currentOrgId } = useAuth()
  const current = memberships.find(m => m.organizationId === currentOrgId)
  const canCreate = isPlatformAdmin || !!current && ['owner', 'admin', 'organizer'].includes(current.memberRole)
  const canManageOrg = !!current && ['owner', 'admin'].includes(current.memberRole)
  if (hasRole('judge') && !isOrganizer && !isPlatformAdmin && !isOperator) return [{ to: '/operator/results', label: 'Судейство' }]
  return [
    { to: '/organizer/events', label: 'Соревнования', end: true },
    ...(isOrganizer || isPlatformAdmin ? [
      ...(canCreate ? [{ to: '/organizer/events/new', label: 'Создать соревнование' }] : []),
      { to: '/organizer/regulations/create', label: 'Регламенты' },
      { to: '/organizer/organization/public', label: 'Публичная страница организации' },
      { to: '/organizer/notification-settings', label: 'Настройки' },
      { to: '/organizer/organizations', label: 'Аналитика' },
    ] : []),
    ...(canManageOrg ? [{ to: '/organizer/organization', label: 'Организация' }] : []),
  ]
}
