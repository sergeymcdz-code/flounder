package com.acme.sportplatform.registrations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.*;
import com.acme.sportplatform.registrations.api.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

class PublicRegistrationUseCaseTest {
    @Test
    void loadsPublishedDisciplinesOnceForWholeBatch() {
        var events = mock(EventPublicLookup.class);
        var disciplines = mock(EventDisciplineLookup.class);
        var profiles = mock(ProfileLookup.class);
        var create = mock(CreateRegistrationUseCase.class);
        var event = mock(EventPublicLookupResult.class);
        var eventId = UUID.randomUUID();
        when(event.id()).thenReturn(eventId);
        when(event.status()).thenReturn("REGISTRATION_OPEN");
        when(events.getPublicBySlug("demo")).thenReturn(event);
        var profile = mock(ProfileLookupResult.class);
        when(profile.gender()).thenReturn("F");
        when(profile.birthDate()).thenReturn(LocalDate.of(2015, 6, 1));
        when(profiles.upsertForUser(any(), any())).thenReturn(profile);
        var one = discipline(eventId);
        var two = discipline(eventId);
        when(disciplines.findPublishedByEventId(eventId)).thenReturn(List.of(one, two));
        var request =
                new PublicRegistrationRequest(
                        new ProfileData(
                                "Иванова", "Анна", null, LocalDate.of(2015, 6, 1), "F", null),
                        List.of(
                                new PublicRegistrationRequest.Item(one.id(), 32150L),
                                new PublicRegistrationRequest.Item(two.id(), 33000L)));
        assertEquals(
                2,
                new PublicRegistrationUseCase(events, disciplines, profiles, create)
                        .submit("demo", UUID.randomUUID(), request)
                        .size());
        verify(disciplines, times(1)).findPublishedByEventId(eventId);
        verify(disciplines, never()).findById(any());
    }

    private EventDisciplineSummary discipline(UUID eventId) {
        var d = mock(EventDisciplineSummary.class);
        when(d.id()).thenReturn(UUID.randomUUID());
        when(d.eventId()).thenReturn(eventId);
        when(d.gender()).thenReturn("F");
        when(d.ageGroups()).thenReturn(List.of());
        return d;
    }
}
