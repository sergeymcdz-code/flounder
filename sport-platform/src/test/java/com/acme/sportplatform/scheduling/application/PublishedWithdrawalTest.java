package com.acme.sportplatform.scheduling.application;

import static org.junit.jupiter.api.Assertions.*;

import com.acme.sportplatform.results.CompetitionUnitStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublishedWithdrawalTest {
    private final UUID registration = UUID.randomUUID();

    private CompetitionUnitStore.Unit unit(String status, String entryStatus) {
        return new CompetitionUnitStore.Unit(UUID.randomUUID(), UUID.randomUUID(), 3, null,
                status, 90, null, List.of(new CompetitionUnitStore.Entry(
                        UUID.randomUUID(), 4, registration, 30_000L, entryStatus)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "FINISHED", "CANCELLED"})
    void withdrawalWarningAppliesOnlyToPublishedHeats(String status) {
        assertTrue(SchedulingData.publishedWithdrawalViolations(unit(status, "WITHDRAWN")).isEmpty());
    }

    @Test
    void assignedEntriesDoNotProduceWithdrawalWarnings() {
        assertTrue(SchedulingData.publishedWithdrawalViolations(unit("PUBLISHED", "ASSIGNED")).isEmpty());
    }

    @Test
    void warningIdentifiesWithdrawnRegistrationAndLane() {
        var unit = unit("PUBLISHED", "WITHDRAWN");
        var warnings = SchedulingData.publishedWithdrawalViolations(unit);
        assertEquals(1, warnings.size());
        var warning = warnings.getFirst();
        assertEquals(registration, warning.registrationId());
        assertEquals(unit.disciplineId(), warning.eventDisciplineId());
        assertEquals(3, warning.heatNumber());
        assertEquals("PUBLISHED_HEAT_CHANGED", warning.code());
        assertEquals(com.acme.sportplatform.scheduling.domain.Violation.Severity.WARNING, warning.severity());
        assertEquals("Опубликованный заплыв изменён: участник снят, дорожка 4", warning.message());
    }
}
