package com.acme.sportplatform.notifications;

import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.events.NotificationEventLookup;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.notifications.application.NotificationEventListener;
import com.acme.sportplatform.notifications.infrastructure.*;
import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.registrations.*;
import com.acme.sportplatform.results.CompetitionUnitStore;
import com.acme.sportplatform.scheduling.ScheduleChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleChangedNotificationTest {
    @Test void groupsChildrenAndSharedMailboxIntoOneMessageAndLoadsAccountsOnce() {
        var registrations = mock(RegistrationLookup.class);
        var users = mock(UserLookup.class);
        var units = mock(CompetitionUnitStore.class);
        var disciplines = mock(EventDisciplineLookup.class);
        var events = mock(NotificationEventLookup.class);
        var settings = mock(NotificationSettingsRepository.class);
        var outbox = mock(NotificationOutboxRepository.class);
        UUID event = UUID.randomUUID(), organization = UUID.randomUUID(), disciplineId = UUID.randomUUID();
        UUID parent = UUID.randomUUID(), alias = UUID.randomUUID(), noEmail = UUID.randomUUID();
        UUID childOne = UUID.randomUUID(), childTwo = UUID.randomUUID(), shared = UUID.randomUUID();
        UUID imported = UUID.randomUUID(), blank = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Instant before = Instant.parse("2026-10-19T06:00:00Z");
        when(events.findNotificationContext(event)).thenReturn(Optional.of(new NotificationEventLookup.Context(
                event, organization, "Кубок", before.atOffset(ZoneOffset.UTC), "cup", null)));
        when(settings.enabled(organization, "schedule_changed")).thenReturn(true);
        var discipline = mock(EventDisciplineSummary.class);
        when(discipline.id()).thenReturn(disciplineId);
        when(discipline.name()).thenReturn("50 м");
        when(disciplines.findByEventId(event)).thenReturn(List.of(discipline));
        when(units.findByDisciplineId(disciplineId)).thenReturn(List.of(
                new CompetitionUnitStore.Unit(first, disciplineId, 1, null, "PUBLISHED", 90, before,
                        List.of(entry(childOne, 1), entry(shared, 2), entry(imported, 3), entry(blank, 4))),
                new CompetitionUnitStore.Unit(second, disciplineId, 2, null, "PUBLISHED", 90, before.plusSeconds(90),
                        List.of(entry(childTwo, 1)))));
        when(registrations.findByIdIn(anyList())).thenReturn(List.of(
                registration(childOne, parent, event, disciplineId, "Иванов", "Иван"),
                registration(childTwo, parent, event, disciplineId, "Иванова", "Анна"),
                registration(shared, alias, event, disciplineId, "Петров", "Пётр"),
                registration(imported, null, event, disciplineId, "Импорт", "Без аккаунта"),
                registration(blank, noEmail, event, disciplineId, "Без", "Почты")));
        when(users.findAllByIds(anyCollection())).thenReturn(List.of(
                new UserLookup.User(parent, "Parent@Example.org", "Родитель", "Иванов"),
                new UserLookup.User(alias, " parent@example.ORG ", "Второй аккаунт", "Петров"),
                new UserLookup.User(noEmail, " ", "Без почты", "Петров")));
        var listener = new NotificationEventListener(registrations, units, mock(VenueLookup.class), users,
                disciplines, events, settings, outbox, new ObjectMapper(), Clock.fixed(before, ZoneOffset.UTC), "https://example.org");
        listener.scheduleChanged(new ScheduleChangedEvent(event, List.of(first, second), before.atOffset(ZoneOffset.UTC), List.of(
                new ScheduleChangedEvent.UnitChange(first, before, before.plusSeconds(600)),
                new ScheduleChangedEvent.UnitChange(second, before.plusSeconds(90), before.plusSeconds(690)))));
        verify(registrations).findByIdIn(anyList());
        verify(users).findAllByIds(argThat(ids -> ids.size() == 3 && ids.containsAll(List.of(parent, alias, noEmail))));
        verify(users, never()).findById(any());
        var payload = ArgumentCaptor.forClass(Map.class);
        verify(outbox).enqueue(eq(event), eq("parent@example.org"), eq("schedule_changed"), payload.capture(),
                startsWith("schedule_changed:"), eq(before));
        verifyNoMoreInteractions(outbox);
        var changes = (List<?>) payload.getValue().get("changes");
        assertThat(changes).hasSize(3);
        assertThat(changes.stream().map(row -> Objects.toString(((Map<?, ?>) row).get("participant"))).toList())
                .containsExactly("Иванов Иван", "Иванова Анна", "Петров Пётр");
        assertThat(payload.getValue().get("link")).isEqualTo("https://example.org/e/cup/schedule");
    }
    private CompetitionUnitStore.Entry entry(UUID registration, int lane) {
        return new CompetitionUnitStore.Entry(UUID.randomUUID(), lane, registration, null, "ASSIGNED");
    }
    private RegistrationLookupResult registration(UUID id, UUID user, UUID event, UUID discipline, String last, String first) {
        return new RegistrationLookupResult(id, user, discipline, "CONFIRMED",
                "{\"lastName\":\"" + last + "\",\"firstName\":\"" + first + "\"}", event, UUID.randomUUID(), null, "PUBLIC", "PAID");
    }
}
