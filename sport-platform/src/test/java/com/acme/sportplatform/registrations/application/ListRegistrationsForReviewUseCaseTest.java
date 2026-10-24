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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.acme.sportplatform.organizations.VenueLookup;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class ListRegistrationsForReviewUseCaseTest {
    @ParameterizedTest
    @CsvSource({"true,true,Europe/Samara", "true,false,UTC", "false,false,UTC"})
    void serializesHumanContextAndCachesEventAndVenueLookups(
            boolean hasVenue, boolean venueExists, String expectedTimezone) {
        var repository = mock(RegistrationRepository.class);
        var events = mock(EventLookup.class);
        var venues = mock(VenueLookup.class);
        var summaries = mock(DocumentSummaryService.class);
        var fees = mock(RegistrationFeeService.class);
        var principal = mock(AuthenticatedUserPrincipal.class);
        var userId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var otherEventId = UUID.randomUUID();
        var eventIds = Set.of(eventId, otherEventId);
        var venueId = hasVenue ? UUID.randomUUID() : null;
        var date = OffsetDateTime.parse("2026-10-23T21:30:00Z");
        if (hasVenue) {
            when(venues.existsById(venueId)).thenReturn(venueExists);
            if (venueExists) when(venues.getTimezone(venueId)).thenReturn(expectedTimezone);
        }
        when(principal.getUserId()).thenReturn(userId);
        when(events.accessibleEventIds(userId)).thenReturn(eventIds);
        when(events.protocolHeader(eventId)).thenReturn(new EventProtocolHeader(eventId, "Кубок Москвы", null, venueId, date, date, "cup"));
        when(events.protocolHeader(otherEventId)).thenReturn(new EventProtocolHeader(otherEventId, "Кубок Москвы", null, venueId, date, date, "cup"));
        var entities = List.of(registration(eventId), registration(eventId), registration(otherEventId));
        when(repository.findByEventIdIn(eventIds)).thenReturn(entities);
        when(fees.dueDaysFor(any())).thenReturn(Map.of());
        when(summaries.enrichForManager(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var mapper = new RegistrationMapper(objectMapper, fees, mock(UserLookup.class));
        var rows = new ListRegistrationsForReviewUseCase(repository, mapper, events, summaries, fees, venues)
                .execute(null, null, principal);
        assertThat(rows).hasSize(3).allSatisfy(row -> {
            var json = objectMapper.valueToTree(row);
            assertThat(json.path("participantFullName").asText()).isEqualTo("Иванова Анна Сергеевна");
            assertThat(json.path("participantBirthYear").asInt()).isEqualTo(2012);
            assertThat(json.path("participantClubName").asText()).isEqualTo("Волна");
            assertThat(json.path("eventTitle").asText()).isEqualTo("Кубок Москвы");
            assertThat(row.eventStartAt()).isEqualTo(date);
            assertThat(json.path("venueTimezone").asText()).isEqualTo(expectedTimezone);
            assertThat(json.hasNonNull("eventStartAt")).isTrue();
            assertThat(row.participantSnapshot().path("birthDate").asText()).isEqualTo("2012-06-15");
        });
        verify(events, times(1)).protocolHeader(eventId);
        verify(events, times(1)).protocolHeader(otherEventId);
        if (hasVenue) {
            verify(venues, times(1)).existsById(venueId);
            if (venueExists) verify(venues, times(1)).getTimezone(venueId);
            else verify(venues, never()).getTimezone(any());
            verifyNoMoreInteractions(venues);
        } else verifyNoInteractions(venues);
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
