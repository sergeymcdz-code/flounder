import { expect, it } from 'vitest'
import { formatCount, plural } from './plural'
it.each([[0, 'видов'], [1, 'вид'], [2, 'вида'], [4, 'вида'], [5, 'видов'], [11, 'видов'], [14, 'видов'], [21, 'вид'], [22, 'вида'], [25, 'видов'], [111, 'видов']] as const)('uses Russian agreement for %i', (count, expected) => {
  expect(plural(count, ['вид', 'вида', 'видов'])).toBe(expected)
})
it.each([[1, '1 участник'], [3, '3 участника'], [5, '5 участников'], [12, '12 участников'], [21, '21 участник']] as const)('formats a count with its noun: %i', (count, expected) => {
  expect(formatCount(count, ['участник', 'участника', 'участников'])).toBe(expected)
})
