package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.scheduling.api.ValidationResponse;
import com.acme.sportplatform.scheduling.domain.Violation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PublishUnitsUseCase {
    private final SchedulingData data;
    private final ValidateUnitsUseCase validate;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public PublishUnitsUseCase(SchedulingData data, ValidateUnitsUseCase validate, org.springframework.context.ApplicationEventPublisher publisher) {
        this.data = data;
        this.validate = validate;
        this.publisher = publisher;
    }

    @Transactional(noRollbackFor = ValidationFailedException.class)
    public ValidationResponse forDiscipline(
            UUID disciplineId, AuthenticatedUserPrincipal principal) {
        var discipline = data.discipline(disciplineId);
        data.access.assertCanModify(discipline.eventId(), principal);
        data.locks.lockWritableForUpdate(discipline.eventId());
        var result = validate.execute(disciplineId, principal);
        assertValid(result.violations());
        boolean changed = data.units.findByDisciplineId(disciplineId).stream().anyMatch(u -> "DRAFT".equals(u.status()));
        data.units.publishDrafts(disciplineId);
        if (changed) publisher.publishEvent(new com.acme.sportplatform.scheduling.StartListPublishedEvent(
                UUID.randomUUID(), discipline.eventId(), disciplineId));
        return result;
    }

    @Transactional(noRollbackFor = ValidationFailedException.class)
    public ValidationResponse forEvent(UUID eventId, AuthenticatedUserPrincipal principal) {
        data.access.assertCanModify(eventId, principal);
        data.locks.lockWritableForUpdate(eventId);
        var disciplines = data.disciplines(eventId);
        List<Violation> violations = new ArrayList<>();
        for (var discipline : disciplines)
            violations.addAll(validate.execute(discipline.id(), principal).violations());
        assertValid(violations);
        boolean changed = disciplines.stream().anyMatch(d -> data.units.findByDisciplineId(d.id()).stream()
                .anyMatch(u -> "DRAFT".equals(u.status())));
        for (var discipline : disciplines) data.units.publishDrafts(discipline.id());
        if (changed) publisher.publishEvent(new com.acme.sportplatform.scheduling.StartListPublishedEvent(
                UUID.randomUUID(), eventId, null));
        return new ValidationResponse(List.copyOf(violations));
    }

    private void assertValid(List<Violation> violations) {
        if (violations.stream().anyMatch(v -> v.severity() == Violation.Severity.ERROR))
            throw new ValidationFailedException(violations);
    }

    // Only validation rejection commits the audit log; access and other failures roll back.
    private static final class ValidationFailedException extends BusinessException {
        ValidationFailedException(List<Violation> violations) {
            super(
                    "scheduling.validation_failed",
                    "Публикация невозможна: найдены ошибки в заплывах",
                    violations);
        }
    }
}
