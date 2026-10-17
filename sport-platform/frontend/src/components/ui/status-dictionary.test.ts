import { expect, it } from 'vitest'
import { statusDictionary, statusLabel, statusTone, type StatusKind } from './status-dictionary'
it('labels every code in the shared vocabulary with a semantic tone', () => {
  for (const kind of Object.keys(statusDictionary) as StatusKind[]) for (const [code, [label, tone]] of Object.entries(statusDictionary[kind])) {
    expect(statusLabel(kind, code)).toBe(label)
    expect(statusTone(kind, code)).toBe(tone)
    expect(label).not.toBe('')
  }
  for (const code of ['WITHDRAWN', 'ADMITTED', 'UNDER_REVIEW']) expect(statusLabel('registration', code)).not.toBe(code)
  expect(statusLabel('event', 'REGISTRATIONOPEN')).toBe(statusLabel('event', 'REGISTRATION_OPEN'))
  expect(statusTone('event', 'REGISTRATIONCLOSED')).toBe('info')
})
