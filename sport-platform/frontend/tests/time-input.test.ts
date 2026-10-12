import assert from 'node:assert/strict'
import { test } from 'node:test'
import { maskTime, normalizeTime } from '../src/features/results/time-input.ts'

test('normalizes seconds, fractions, and hours without losing precision', () => {
  for (const [input, expected] of [
    ['32.1', '00:32.10'], ['32.15', '00:32.15'], ['00:32.1', '00:32.10'],
    ['1:02:03.4', '62:03.40'], ['62:03.40', '62:03.40'], ['0,1', '00:00.10'],
  ]) assert.equal(normalizeTime(input), expected)
})

test('rejects malformed or out-of-range times', () => {
  for (const input of ['', '32', '00:60.00', '1:60:00.00', '32.123', '-32.1', 'dns']) {
    assert.equal(normalizeTime(input), null)
  }
})

test('inserts separators during digit entry and preserves explicit short fractions', () => {
  let value = ''
  for (const digit of '003215') value = maskTime(value + digit)
  assert.equal(value, '00:32.15')
  assert.equal(maskTime('32.1'), '32.1')
  assert.equal(normalizeTime(maskTime('00:32.1')), '00:32.10')
})
