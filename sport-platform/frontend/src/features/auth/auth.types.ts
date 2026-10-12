export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export type AuthSession = {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: number
}


export type Role = 'platform_admin' | 'organizer' | 'operator' | 'participant'


export type RegisterRequest = {
  email: string
  password: string
  phone: string
  firstName: string
  lastName: string
  middleName: string | null
  birthDate: string
  gender: string | null
  city: string | null
  countryCode: string | null
  clubName: string | null
}

export type RegisterResponse = {
  id: string
  email: string
  phone: string | null
  status: string
  createdAt: string
}