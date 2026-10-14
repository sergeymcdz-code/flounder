package com.acme.sportplatform.events.domain;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    REGISTRATION_OPEN,
    REGISTRATION_CLOSED,
    LIVE,
    COMPLETED,
    ARCHIVED;

    public boolean canTransition(EventStatus target) {
        return switch (this) {
            case DRAFT -> target == PUBLISHED;
            case PUBLISHED, REGISTRATION_CLOSED -> target == REGISTRATION_OPEN
                    || this == REGISTRATION_CLOSED && target == COMPLETED;
            case REGISTRATION_OPEN -> target == REGISTRATION_CLOSED;
            case LIVE -> target == COMPLETED;
            case COMPLETED -> target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}