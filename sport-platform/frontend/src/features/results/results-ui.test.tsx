import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/http'
import { resultErrorMessage } from './results-ui'

describe('result loading errors', () => {
  it('distinguishes connection errors from server responses and preserves the next step', () => {
    expect(resultErrorMessage(new TypeError('Failed to fetch'), 'Повторите загрузку.')).toBe('Нет связи с сервером. Повторите загрузку.')
    expect(resultErrorMessage(new ApiError(503, 'Повторите позже.'), 'Повторите загрузку.')).toBe('Сервер ответил ошибкой (503). Повторите позже.')
  })
})
