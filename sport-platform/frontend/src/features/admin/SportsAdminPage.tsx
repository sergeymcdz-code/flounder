import { useEffect, useState } from 'react'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import { getSports, type Sport } from '../../api/sports.api'

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return 'У вашей учётной записи нет прав на просмотр этого раздела.'
    }
    return error.message
  }
  return 'Не удалось загрузить список видов спорта.'
}

export function SportsAdminPage() {
  const { session } = useAuth()
  const accessToken = session?.accessToken
  const [sports, setSports] = useState<Sport[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) {
      setIsLoading(false)
      return
    }

    const authenticatedAccessToken = accessToken
    let isMounted = true

    async function loadSports() {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const data = await getSports(authenticatedAccessToken)

        if (isMounted) {
          setSports(data)
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(getErrorMessage(error))
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadSports()

    return () => {
      isMounted = false
    }
  }, [accessToken])

  return (
    <section className="admin-section">
      <p className="eyebrow">Панель администратора</p>
      <h1>Виды спорта</h1>

      {isLoading ? <p>Загрузка...</p> : null}

      {errorMessage ? (
        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage ? (
        sports.length === 0 ? (
          <p>Виды спорта пока не добавлены.</p>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Код</th>
                <th>Название</th>
                <th>Активен</th>
              </tr>
            </thead>
            <tbody>
              {sports.map((sport) => (
                <tr key={sport.id}>
                  <td>{sport.code}</td>
                  <td>{sport.name}</td>
                  <td>{sport.isActive ? 'Да' : 'Нет'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      ) : null}
    </section>
  )
}