package com.acme.sportplatform.scheduling.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.results.CompetitionUnitStore;
import com.acme.sportplatform.results.RuleExecutionLog;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.*;

class SchedulingReadTest {
    final UUID eventId = UUID.randomUUID(),
            disciplineId = UUID.randomUUID(),
            venueId = UUID.randomUUID();
    final CompetitionUnitStore units = mock(CompetitionUnitStore.class);
    final EventAccessService access = mock(EventAccessService.class);
    final EventLookup events = mock(EventLookup.class);
    final EventLockService locks = mock(EventLockService.class);
    final EventDisciplineLookup disciplines = mock(EventDisciplineLookup.class);
    final RegistrationLookup registrations = mock(RegistrationLookup.class);
    final EventSessionLookup sessions = mock(EventSessionLookup.class);
    final VenueLookup venues = mock(VenueLookup.class);
    final RuleExecutionLog logs = mock(RuleExecutionLog.class);
    final SchedulingData data =
            new SchedulingData(
                    units,
                    access,
                    events,
                    locks,
                    disciplines,
                    registrations,
                    sessions,
                    venues,
                    new ObjectMapper());

    EventDisciplineLookupResult discipline() {
        var result = mock(EventDisciplineLookupResult.class);
        when(result.id()).thenReturn(disciplineId);
        when(result.eventId()).thenReturn(eventId);
        when(result.distanceM()).thenReturn(50);
        when(result.stroke()).thenReturn("FREE");
        when(result.gender()).thenReturn("M");
        when(result.code()).thenReturn("50m");
        when(result.ageGroups()).thenReturn(List.of());
        when(result.settingsJson()).thenReturn("{}");
        when(result.seedingMode()).thenReturn("BY_TIME_ALL");
        return result;
    }

    @Test
    void validationReadsWithoutAcquiringEventLockAndStillWritesAuditLog() {
        var discipline = discipline();
        when(disciplines.getForScheduling(disciplineId)).thenReturn(discipline);
        when(disciplines.findForSchedulingByEventId(eventId)).thenReturn(List.of(discipline));
        when(events.getById(eventId))
                .thenReturn(
                        new EventLookupResult(
                                eventId, null, "DRAFT", null, null, List.of(), venueId, null,
                                "{}"));
        when(venues.getMetadata(venueId)).thenReturn("{\"lanes\":8}");
        var result = new ValidateUnitsUseCase(data, logs).execute(disciplineId, null);
        assertTrue(result.violations().isEmpty());
        verify(locks, never()).lockForUpdate(any());
        verify(access).assertCanManage(eventId, null);
        verify(logs).begin(eq(eventId), eq(disciplineId), eq("VALIDATION"), any(), isNull());
        verify(logs).finish(isNull(), eq(result), eq(List.of()), anyLong());
    }

    @Test
    void disciplineListUsesBulkLookupWithoutPerDisciplineReads() {
        var discipline = discipline();
        when(disciplines.findForSchedulingByEventId(eventId)).thenReturn(List.of(discipline));
        assertEquals(List.of(discipline), data.disciplines(eventId));
        verify(disciplines).findForSchedulingByEventId(eventId);
        verify(disciplines, never()).getForScheduling(any());
        verify(disciplines, never()).findByEventId(any());
    }
}
