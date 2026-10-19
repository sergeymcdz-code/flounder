// Real backend tests receive credentials from the owner's environment.
export function requiredCredential(name: string, fallback?: string): string {
  const value = process.env[name] || (fallback ? process.env[fallback] : undefined)
  if (!value?.trim()) throw new Error(`Задайте ${name} через окружение; пароль хранится в менеджере паролей владельца.`)
  return value
}
