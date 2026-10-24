import { useState } from 'react'
import { Button } from './ui/Button'
import { downloadProtocol } from '../api/protocols.api'
import styles from './PdfDownloadButton.module.css'

export function PdfDownloadButton({
  path,
  filename,
  accessToken,
  disabled = false,
  label = 'Скачать PDF',
  variant = 'ghost',
  size = 'md',
}: {
  path: string
  filename: string
  accessToken?: string
  disabled?: boolean
  label?: string
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function download() {
    setBusy(true)
    setError('')
    try {
      await downloadProtocol(path, filename, accessToken)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Не удалось скачать PDF.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <span className={styles.control}>
      <Button variant={variant} size={size} disabled={disabled || busy} onClick={() => void download()}>
        {busy ? 'Формируем PDF…' : label}
      </Button>
      {error && <span role="alert">{error}</span>}
    </span>
  )
}
