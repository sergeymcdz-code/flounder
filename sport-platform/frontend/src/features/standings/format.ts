const russianPluralRules = new Intl.PluralRules('ru')

export function swimCount(count: number): string {
  const form = russianPluralRules.select(count)
  const noun = form === 'one' ? 'заплыв' : form === 'few' ? 'заплыва' : 'заплывов'
  return `${count} ${noun}`
}
