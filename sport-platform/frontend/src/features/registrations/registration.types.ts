export type RegistrationMeta = {
  emergencyContactName: string
  emergencyContactPhone: string
  medicalCertificate: boolean
  agreeToRules: boolean
}

export type ParticipantSnapshot = {
  firstName: string
  lastName: string
  middleName: string | null
  birthDate: string | null
  gender: string | null
  city: string | null
  countryCode: string | null
  clubName: string | null
  sportMeta: Record<string, unknown>
}

export type CreateRegistrationRequest = {
  eventDisciplineId: string
  registrationMeta: RegistrationMeta
}

export type RegistrationStatus =
  | 'SUBMITTED'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'NEEDS_CORRECTION'
  | 'CANCELLED'

export type Registration = {
  id: string
  eventId: string
  eventDisciplineId: string
  participantUserId: string
  participantProfileId: string
  status: RegistrationStatus
  participantSnapshot: ParticipantSnapshot
  registrationMeta: RegistrationMeta
  reviewNote: string | null
  reviewedByUserId: string | null
  reviewedAt: string | null
  submittedAt: string
  createdAt: string
  updatedAt: string
}