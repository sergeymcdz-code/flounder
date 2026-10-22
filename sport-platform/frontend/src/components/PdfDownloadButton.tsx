import { useState } from 'react'
import { downloadProtocol } from '../api/protocols.api'
import styles from './PdfDownloadButton.module.css'

export function PdfDownloadButton({
  path,
  filename,
  accessToken,
  disabled = false,
  label = 'Скачать PDF',
}: {
  path: string
  filename: string
  accessToken?: string
  disabled?: boolean
  label?: string
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
      <button className="fl-btn fl-btn-ghost" type="button" disabled={disabled || busy} onClick={() => void download()}>
        {busy ? 'Формируем PDF…' : label}
      </button>
      {error && <span role="alert">{error}</span>}
    </span>
  )
}
