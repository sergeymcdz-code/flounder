export function plural(value: number, forms: readonly [string, string, string]) {
  const count = Math.abs(Math.trunc(value))
  const tail = count % 100
  return forms[tail >= 11 && tail <= 14 ? 2 : count % 10 === 1 ? 0 : count % 10 >= 2 && count % 10 <= 4 ? 1 : 2]
}
