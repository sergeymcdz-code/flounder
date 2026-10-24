package com.acme.sportplatform.registrations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventProtocolHeader;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class ListRegistrationsForReviewUseCaseTest {
    @Test
    void serializesHumanContextForScopedListAndReadsEachEventOnlyOnce() {
        var repository = mock(RegistrationRepository.class);
        var events = mock(EventLookup.class);
        var summaries = mock(DocumentSummaryService.class);
        var fees = mock(RegistrationFeeService.class);
        var principal = mock(AuthenticatedUserPrincipal.class);
        var userId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var date = OffsetDateTime.parse("2026-10-24T10:00:00Z");
        when(principal.getUserId()).thenReturn(userId);
        when(events.accessibleEventIds(userId)).thenReturn(Set.of(eventId));
        when(events.protocolHeader(eventId)).thenReturn(new EventProtocolHeader(eventId, "Кубок Москвы", null, null, date, date, "cup"));
        var entities = List.of(registration(eventId), registration(eventId));
        when(repository.findByEventIdIn(Set.of(eventId))).thenReturn(entities);
        when(fees.dueDaysFor(any())).thenReturn(Map.of());
        when(summaries.enrichForManager(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var mapper = new RegistrationMapper(objectMapper, fees, mock(UserLookup.class));
        var rows = new ListRegistrationsForReviewUseCase(repository, mapper, events, summaries, fees)
                .execute(null, null, principal);
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            var json = objectMapper.valueToTree(row);
            assertThat(json.path("participantFullName").asText()).isEqualTo("Иванова Анна Сергеевна");
            assertThat(json.path("participantBirthYear").asInt()).isEqualTo(2012);
            assertThat(json.path("participantClubName").asText()).isEqualTo("Волна");
            assertThat(json.path("eventTitle").asText()).isEqualTo("Кубок Москвы");
            assertThat(row.eventStartAt()).isEqualTo(date);
            assertThat(json.hasNonNull("eventStartAt")).isTrue();
            assertThat(row.participantSnapshot().path("birthDate").asText()).isEqualTo("2012-06-15");
        });
        verify(events, times(1)).protocolHeader(eventId);
        verify(repository, never()).findAll();
    }

    private RegistrationEntity registration(UUID eventId) {
        var entity = new RegistrationEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventId(eventId);
        entity.setParticipantSnapshot("""
                {"lastName":"Иванова","firstName":"Анна","middleName":"Сергеевна","birthDate":"2012-06-15","clubName":"Волна"}
                """);
        entity.setRegistrationMeta("{}");
        return entity;
    }
}
