export function plural(value: number, forms: readonly [string, string, string]) {
  const count = Math.abs(Math.trunc(value))
  const tail = count % 100
  return forms[tail >= 11 && tail <= 14 ? 2 : count % 10 === 1 ? 0 : count % 10 >= 2 && count % 10 <= 4 ? 1 : 2]
}

/** Number with an agreeing Russian noun: `formatCount(3, ['участник', 'участника', 'участников'])` → «3 участника». */
export function formatCount(value: number, forms: readonly [string, string, string]) {
  return `${value} ${plural(value, forms)}`
}
