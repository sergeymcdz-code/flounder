package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.scheduling.api.ValidationResponse;
import com.acme.sportplatform.scheduling.domain.Violation;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PublishUnitsUseCase {
    private final SchedulingData data;
    private final ValidateUnitsUseCase validate;

    public PublishUnitsUseCase(SchedulingData data, ValidateUnitsUseCase validate) {
        this.data = data;
        this.validate = validate;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ValidationResponse forDiscipline(UUID disciplineId, Authentication principal) {
        var discipline = data.discipline(disciplineId);
        data.access.assertCanManage(discipline.eventId(), principal);
        data.locks.lockForUpdate(discipline.eventId());
        var result = validate.execute(disciplineId, principal);
        assertValid(result.violations());
        data.units.publishDrafts(disciplineId);
        return result;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ValidationResponse forEvent(UUID eventId, Authentication principal) {
        data.access.assertCanManage(eventId, principal);
        data.locks.lockForUpdate(eventId);
        var disciplines = data.disciplines(eventId);
        List<Violation> violations = new ArrayList<>();
        for (var discipline : disciplines)
            violations.addAll(validate.execute(discipline.id(), principal).violations());
        assertValid(violations);
        for (var discipline : disciplines) data.units.publishDrafts(discipline.id());
        return new ValidationResponse(List.copyOf(violations));
    }

    private void assertValid(List<Violation> violations) {
        if (violations.stream().anyMatch(v -> v.severity() == Violation.Severity.ERROR))
            throw new BusinessException(
                    "scheduling.validation_failed",
                    "Публикация невозможна: найдены ошибки в заплывах",
                    violations);
    }
}
