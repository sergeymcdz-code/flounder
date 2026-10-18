package com.acme.sportplatform.registrations.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

class RegistrationMapperTest {
    @Test
    void mapsDueDatesAndActorsFromBatchContextWithoutPerRegistrationReads() {
        var fees = mock(RegistrationFeeService.class);
        var users = mock(UserLookup.class);
        UUID event = UUID.randomUUID(), actor = UUID.randomUUID();
        var reviewed = OffsetDateTime.parse("2026-10-17T10:00:00Z");
        when(users.findAllByIds(Set.of(actor)))
                .thenReturn(List.of(new UserLookup.User(actor, "a@example.org", "Иван", "Тестов")));
        var entities = new ArrayList<RegistrationEntity>();
        for (int i = 0; i < 20; i++) {
            var entity = new RegistrationEntity();
            entity.setEventId(event);
            entity.setPaidByUserId(actor);
            entity.setReviewedAt(reviewed);
            entity.setParticipantSnapshot("{}");
            entity.setRegistrationMeta("{}");
            entities.add(entity);
        }
        var rows =
                new RegistrationMapper(new ObjectMapper(), fees, users)
                        .toResponses(entities, Map.of(event, 7));
        assertThat(rows)
                .hasSize(20)
                .allSatisfy(
                        row -> {
                            assertThat(row.dueAt()).isEqualTo(reviewed.plusDays(7));
                            assertThat(row.paidBy().name()).isEqualTo("Иван Тестов");
                            var enriched = row.withEventContext("Кубок", "50 м", reviewed, "Europe/Samara", "cup")
                                    .withDocuments(Map.of("MEDICAL_CERT", "ACCEPTED"), List.of());
                            assertThat(enriched.paidBy()).isEqualTo(row.paidBy());
                            assertThat(enriched.dueAt()).isEqualTo(row.dueAt());
                            assertThat(enriched.eventTitle()).isEqualTo("Кубок");
                            assertThat(enriched.venueTimezone()).isEqualTo("Europe/Samara");
                        });
        verify(users).findAllByIds(Set.of(actor));
        verifyNoMoreInteractions(users);
        verifyNoInteractions(fees);
    }
}
