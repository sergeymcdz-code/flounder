package com.acme.sportplatform.events.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.common.exception.BusinessException;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;

class EventLifecycleServiceTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        Set<String> allowed = Set.of("DRAFT:PUBLISHED", "PUBLISHED:REGISTRATION_OPEN",
                "REGISTRATION_OPEN:REGISTRATION_CLOSED", "REGISTRATION_CLOSED:REGISTRATION_OPEN",
                "REGISTRATION_CLOSED:COMPLETED", "LIVE:COMPLETED", "COMPLETED:ARCHIVED");
        return Arrays.stream(EventStatus.values()).flatMap(from -> Arrays.stream(EventStatus.values())
                .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to, allowed.contains(from + ":" + to))));
    }

    @ParameterizedTest @MethodSource("transitions")
    void transitionTable(EventStatus from, EventStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransition(to));
        var repository = mock(EventRepository.class);
        var entity = new EventEntity(); entity.setId(UUID.randomUUID()); entity.setStatus(from.name());
        when(repository.lockById(entity.getId())).thenReturn(Optional.of(entity));
        var service = new EventLifecycleService(repository, mock(EventMapper.class), mock(ApplicationEventPublisher.class), Clock.systemUTC(), mock(jakarta.persistence.EntityManager.class));
        if (allowed) { service.transition(entity.getId(), to); assertEquals(to.name(), entity.getStatus()); }
        else { assertEquals("events.invalid_transition", assertThrows(BusinessException.class, () -> service.transition(entity.getId(), to)).getCode());
            verify(repository, never()).save(any()); }
    }

    @Test
    void deadlinesUseClockAndRepeatedTicksDoNotRepublish() {
        var clock = Clock.fixed(Instant.parse("2030-06-01T10:00:00Z"), ZoneOffset.UTC);
        var repository = mock(EventRepository.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var entity = new EventEntity(); entity.setId(UUID.randomUUID()); entity.setStatus("PUBLISHED");
        entity.setRegistrationOpenAt(OffsetDateTime.now(clock));
        entity.setRegistrationCloseAt(OffsetDateTime.now(clock).plusMinutes(1));
        when(repository.lockById(entity.getId())).thenReturn(Optional.of(entity));
        var service = new EventLifecycleService(repository, mock(EventMapper.class), publisher, clock, mock(jakarta.persistence.EntityManager.class));
        service.advanceDue(entity.getId()); service.advanceDue(entity.getId());
        assertEquals("REGISTRATION_OPEN", entity.getStatus());
        verify(publisher, times(1)).publishEvent(any(EventRegistrationOpenedEvent.class));
        new EventLifecycleService(repository, mock(EventMapper.class), publisher, Clock.offset(clock, Duration.ofMinutes(1)), mock(jakarta.persistence.EntityManager.class))
                .advanceDue(entity.getId());
        service.advanceDue(entity.getId());
        assertEquals("REGISTRATION_CLOSED", entity.getStatus());
        verify(publisher, times(1)).publishEvent(any(EventRegistrationClosedEvent.class));
        entity.setStatus("DRAFT"); service.advanceDue(entity.getId()); assertEquals("DRAFT", entity.getStatus());
    }

    @Test
    void missedOpenAndCloseDeadlinesAreCaughtUpInOneTick() {
        var repository = mock(EventRepository.class); var publisher = mock(ApplicationEventPublisher.class);
        var entity = new EventEntity(); entity.setId(UUID.randomUUID()); entity.setStatus("PUBLISHED");
        var now = OffsetDateTime.parse("2030-06-01T10:00:00Z");
        entity.setRegistrationOpenAt(now.minusDays(2)); entity.setRegistrationCloseAt(now.minusDays(1));
        when(repository.lockById(entity.getId())).thenReturn(Optional.of(entity));
        var service = new EventLifecycleService(repository, mock(EventMapper.class), publisher, Clock.fixed(now.toInstant(), ZoneOffset.UTC), mock(jakarta.persistence.EntityManager.class));
        service.advanceDue(entity.getId()); service.advanceDue(entity.getId());
        assertEquals("REGISTRATION_CLOSED", entity.getStatus());
        verify(publisher, times(1)).publishEvent(any(EventRegistrationOpenedEvent.class));
        verify(publisher, times(1)).publishEvent(any(EventRegistrationClosedEvent.class));
    }
}
