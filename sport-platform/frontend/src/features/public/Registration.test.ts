import { describe, expect, it } from 'vitest'
import { demoForm } from '../../mocks/demo-data'
import { eligible, maskSeedTime, parseSeedTime } from './registrationFields'
import { authReturnTarget } from '../../utils/authReturn'

describe('Public registration', () => {
  it('filters disciplines by gender and inclusive birth-year boundaries', () => {
    const profile = {
      lastName: 'Иванов',
      firstName: 'Иван',
      middleName: '',
      birthDate: '2015-12-31',
      gender: 'M' as const,
      club: '',
    }
    expect(eligible(demoForm.disciplines[0], profile)).toBe(true)
    expect(eligible(demoForm.disciplines[1], profile)).toBe(false)
    expect(
      eligible(
        {
          ...demoForm.disciplines[0],
          gender: 'X',
          ageGroups: [
            {
              code: 'open',
              name: 'Все',
              gender: 'X',
              birthYearFrom: null,
              birthYearTo: null,
            },
          ],
        },
        profile,
      ),
    ).toBe(true)
    expect(
      eligible(demoForm.disciplines[0], {
        ...profile,
        birthDate: '2016-01-01',
      }),
    ).toBe(false)
  })
  it('masks times and rejects invalid seconds while supporting empty NT', () => {
    expect(maskSeedTime('003215')).toBe('00:32.15')
    expect(maskSeedTime('32.15')).toBe('00:32.15')
    expect(parseSeedTime('32.15')).toBe(32150)
    expect(parseSeedTime('00:32.15')).toBe(32150)
    expect(parseSeedTime('01:60.00')).toBeUndefined()
    expect(parseSeedTime('00:00.00')).toBeUndefined()
    expect(parseSeedTime('')).toBeNull()
  })
  it('preserves the public return URL and blocks external redirects', () => {
    expect(authReturnTarget('?next=%2Fe%2Fdemo%2Fregister')).toBe(
      '/e/demo/register',
    )
    expect(authReturnTarget('?next=//evil.example')).toBe('/my/registrations')
    expect(authReturnTarget('?next=%2F%5Cevil.example')).toBe(
      '/my/registrations',
    )
  })
})
