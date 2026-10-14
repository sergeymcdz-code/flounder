package com.acme.sportplatform.registrations.domain;

import java.util.Set;

public enum RegistrationStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    CONFIRMED,
    NEEDS_CORRECTION,
    REJECTED,
    PAID,
    ADMITTED,
    WAITLISTED,
    WITHDRAWN;

    public boolean canTransition(RegistrationStatus target) {
        return (switch (this) {
                    case DRAFT -> Set.of(SUBMITTED, WITHDRAWN);
                    case SUBMITTED ->
                            Set.of(UNDER_REVIEW, CONFIRMED, NEEDS_CORRECTION, REJECTED, WITHDRAWN);
                    case UNDER_REVIEW -> Set.of(CONFIRMED, NEEDS_CORRECTION, REJECTED, WITHDRAWN);
                    case NEEDS_CORRECTION -> Set.of(SUBMITTED, WAITLISTED, WITHDRAWN);
                    case WAITLISTED -> Set.of(SUBMITTED, CONFIRMED, REJECTED, WITHDRAWN);
                    case CONFIRMED -> Set.of(PAID, ADMITTED, REJECTED, WITHDRAWN);
                    case PAID -> Set.of(ADMITTED, WITHDRAWN);
                    case ADMITTED -> Set.of(WITHDRAWN);
                    case REJECTED, WITHDRAWN -> Set.<RegistrationStatus>of();
                })
                .contains(target);
    }
}
