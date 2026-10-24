package com.acme.sportplatform.registrations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RegistrationResponse(
        UUID id,
        UUID eventId,
        UUID eventDisciplineId,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        UUID participantUserId,
        UUID participantProfileId,
        String status,
        JsonNode participantSnapshot,
        JsonNode registrationMeta,
        String reviewNote,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long seedTimeMs,
        String seedTime,
        String source,
        String paymentStatus,
        java.util.Map<String, String> documents,
        java.util.List<String> admissionWarnings,
        Integer waitlistPosition,
        java.math.BigDecimal feeAmount, String feeCurrency, java.math.BigDecimal paidAmount, OffsetDateTime paidAt,
        PaidBy paidBy, String paymentMethod, String paymentReference, String paymentComment, OffsetDateTime dueAt,
        String eventTitle, String disciplineName, OffsetDateTime eventStartAt, String venueTimezone, String publicSlug) {
    @com.fasterxml.jackson.annotation.JsonProperty("participantFullName")
    public String participantFullName() {
        return java.util.stream.Stream.of("lastName", "firstName", "middleName")
                .map(key -> participantSnapshot == null ? "" : participantSnapshot.path(key).asText(""))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    @com.fasterxml.jackson.annotation.JsonProperty("participantBirthYear")
    public Integer participantBirthYear() {
        String date = participantSnapshot == null ? "" : participantSnapshot.path("birthDate").asText("");
        try {
            return java.time.LocalDate.parse(date).getYear();
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    @com.fasterxml.jackson.annotation.JsonProperty("participantClubName")
    public String participantClubName() {
        return participantSnapshot == null ? null : participantSnapshot.path("clubName").asText(null);
    }

    public record PaidBy(UUID userId, String name) {}
    public RegistrationResponse(UUID id, UUID eventId, UUID eventDisciplineId, UUID participantUserId,
            UUID participantProfileId, String status, JsonNode participantSnapshot, JsonNode registrationMeta,
            String reviewNote, UUID reviewedByUserId, OffsetDateTime reviewedAt, OffsetDateTime submittedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, Long seedTimeMs, String seedTime, String source,
            String paymentStatus, java.util.Map<String,String> documents, java.util.List<String> admissionWarnings, Integer waitlistPosition, java.math.BigDecimal feeAmount, String feeCurrency, java.math.BigDecimal paidAmount, OffsetDateTime paidAt, PaidBy paidBy, String paymentMethod, String paymentReference, String paymentComment, OffsetDateTime dueAt) {
        this(id,eventId,eventDisciplineId,participantUserId,participantProfileId,status,participantSnapshot,registrationMeta,
            reviewNote,reviewedByUserId,reviewedAt,submittedAt,createdAt,updatedAt,seedTimeMs,seedTime,source,paymentStatus,
            documents,admissionWarnings,waitlistPosition,feeAmount,feeCurrency,paidAmount,paidAt,paidBy,paymentMethod,paymentReference,paymentComment,dueAt,null,null,null,null,null);
    }
    public RegistrationResponse(UUID id, UUID eventId, UUID eventDisciplineId, UUID participantUserId,
            UUID participantProfileId, String status, JsonNode participantSnapshot, JsonNode registrationMeta,
            String reviewNote, UUID reviewedByUserId, OffsetDateTime reviewedAt, OffsetDateTime submittedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, Long seedTimeMs, String seedTime, String source,
            String paymentStatus, java.util.Map<String,String> documents, java.util.List<String> admissionWarnings, Integer waitlistPosition) {
        this(id,eventId,eventDisciplineId,participantUserId,participantProfileId,status,participantSnapshot,registrationMeta,
            reviewNote,reviewedByUserId,reviewedAt,submittedAt,createdAt,updatedAt,seedTimeMs,seedTime,source,paymentStatus,
            documents,admissionWarnings,waitlistPosition,null,null,null,null,null,null,null,null,null);
    }
    public RegistrationResponse(UUID id, UUID eventId, UUID eventDisciplineId, UUID participantUserId,
            UUID participantProfileId, String status, JsonNode participantSnapshot, JsonNode registrationMeta,
            String reviewNote, UUID reviewedByUserId, OffsetDateTime reviewedAt, OffsetDateTime submittedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, Long seedTimeMs, String seedTime, String source,
            String paymentStatus) {
        this(id, eventId, eventDisciplineId, participantUserId, participantProfileId, status, participantSnapshot,
                registrationMeta, reviewNote, reviewedByUserId, reviewedAt, submittedAt, createdAt, updatedAt,
                seedTimeMs, seedTime, source, paymentStatus, java.util.Map.of(), java.util.List.of(), null);
    }
    public RegistrationResponse(UUID id, UUID eventId, UUID eventDisciplineId, UUID participantUserId,
            UUID participantProfileId, String status, JsonNode participantSnapshot, JsonNode registrationMeta,
            String reviewNote, UUID reviewedByUserId, OffsetDateTime reviewedAt, OffsetDateTime submittedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, Long seedTimeMs, String seedTime, String source,
            String paymentStatus, Integer waitlistPosition) {
        this(id, eventId, eventDisciplineId, participantUserId, participantProfileId, status, participantSnapshot,
                registrationMeta, reviewNote, reviewedByUserId, reviewedAt, submittedAt, createdAt, updatedAt,
                seedTimeMs, seedTime, source, paymentStatus, java.util.Map.of(), java.util.List.of(), waitlistPosition);
    }
    public RegistrationResponse withEventContext(String title, String discipline, OffsetDateTime startsAt, String timezone, String slug) {
        return new RegistrationResponse(id, eventId, eventDisciplineId, participantUserId, participantProfileId,
                status, participantSnapshot, registrationMeta, reviewNote, reviewedByUserId, reviewedAt,
                submittedAt, createdAt, updatedAt, seedTimeMs, seedTime, source, paymentStatus, documents, admissionWarnings,
                waitlistPosition, feeAmount, feeCurrency, paidAmount, paidAt, paidBy, paymentMethod, paymentReference, paymentComment, dueAt,
                title, discipline, startsAt, timezone, slug);
    }
    public RegistrationResponse withDocuments(java.util.Map<String, String> documents, java.util.List<String> warnings) {
        return new RegistrationResponse(id, eventId, eventDisciplineId, participantUserId, participantProfileId,
                status, participantSnapshot, registrationMeta, reviewNote, reviewedByUserId, reviewedAt,
                submittedAt, createdAt, updatedAt, seedTimeMs, seedTime, source, paymentStatus, documents, warnings,
                waitlistPosition, feeAmount, feeCurrency, paidAmount, paidAt, paidBy, paymentMethod, paymentReference, paymentComment, dueAt, eventTitle, disciplineName, eventStartAt, venueTimezone, publicSlug);
    }
}
