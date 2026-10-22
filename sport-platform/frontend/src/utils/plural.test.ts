import { expect, it } from 'vitest'
import { plural } from './plural'
it.each([[0, 'видов'], [1, 'вид'], [2, 'вида'], [4, 'вида'], [5, 'видов'], [11, 'видов'], [14, 'видов'], [21, 'вид'], [22, 'вида'], [25, 'видов'], [111, 'видов']] as const)('uses Russian agreement for %i', (count, expected) => {
  expect(plural(count, ['вид', 'вида', 'видов'])).toBe(expected)
})
