package com.acme.sportplatform.results;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.application.PublishCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.ResultsPublicationLookupService;
import com.acme.sportplatform.results.infrastructure.jpa.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfficialPublicationTest {
    final CompetitionUnitRepository units = mock(CompetitionUnitRepository.class);
    final CompetitionUnitEntryRepository entries = mock(CompetitionUnitEntryRepository.class);
    final ResultRepository results = mock(ResultRepository.class);
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final PublishCompetitionUnitUseCase useCase = new PublishCompetitionUnitUseCase(units, entries, results, publisher);
    CompetitionUnitEntity unit;

    @BeforeEach
    void fixture() {
        unit = new CompetitionUnitEntity();
        unit.setId(UUID.randomUUID());
        unit.setEventDisciplineId(UUID.randomUUID());
        unit.setStatus("IN_PROGRESS");
        when(units.lockById(unit.getId())).thenReturn(Optional.of(unit));
        var entry = new CompetitionUnitEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setRegistrationId(UUID.randomUUID());
        when(entries.findByCompetitionUnitId(unit.getId())).thenReturn(List.of(entry));
        var result = new ResultEntity();
        result.setCompetitionUnitEntryId(entry.getId());
        result.setStatus("VALID");
        result.setFinalPlace(1);
        when(results.findByCompetitionUnitEntryIdIn(List.of(entry.getId()))).thenReturn(List.of(result));
    }

    @Test
    void legacyPublicationIsPreliminaryAndExplicitPublicationIsOfficial() {
        useCase.execute(unit.getId(), true);
        assertThat(unit.isOfficial()).isFalse();
        assertThat(unit.getStatus()).isEqualTo("FINISHED");
        useCase.execute(unit.getId(), true, true);
        assertThat(unit.isOfficial()).isTrue();
        verify(publisher, times(1)).publishEvent(any(ResultsPublishedEvent.class));
    }

    @Test
    void unpublishingClearsOfficialAndCannotDeclareAnUnpublishedProtocolOfficial() {
        unit.setStatus("FINISHED");
        unit.setOfficial(true);
        assertThatThrownBy(() -> useCase.execute(unit.getId(), false, true)).isInstanceOf(BusinessException.class);
        assertThat(unit.isOfficial()).isTrue();
        useCase.execute(unit.getId(), false, null);
        assertThat(unit.isOfficial()).isFalse();
        assertThat(unit.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void completedResultsCanBeCertifiedWithoutReopeningOrPublishingAgain() {
        unit.setStatus("COMPLETED");
        useCase.execute(unit.getId(), true, true);
        assertThat(unit.getStatus()).isEqualTo("COMPLETED");
        assertThat(unit.isOfficial()).isTrue();
        useCase.execute(unit.getId(), true, false);
        assertThat(unit.isOfficial()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void incompleteResultsCannotBeCertifiedAndCannotReplaceExistingCertification() {
        unit.setStatus("FINISHED");
        when(entries.findByCompetitionUnitId(unit.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> useCase.execute(unit.getId(), true, true)).isInstanceOf(BusinessException.class);
        assertThat(unit.isOfficial()).isFalse();
    }

    @Test
    void correctedTerminalOutcomesCanBeCertifiedWithoutAnArtificialPlace() {
        var result = results.findByCompetitionUnitEntryIdIn(
                List.of(entries.findByCompetitionUnitId(unit.getId()).getFirst().getId())).getFirst();
        result.setStatus("CORRECTED");
        result.setRawValue("DID_NOT_START");
        result.setTimeMs(null);
        result.setFinalPlace(null);
        useCase.execute(unit.getId(), true, true);
        assertThat(unit.isOfficial()).isTrue();
    }

    @Test
    void disciplineIsOfficialOnlyWhenEveryHeatIsPublishedAndOfficial() {
        var lookup = new ResultsPublicationLookupService(units, mock(JdbcTemplate.class));
        var ids = List.of(unit.getEventDisciplineId());
        when(units.findByEventDisciplineIdInOrderBySequenceNumberAsc(ids)).thenReturn(List.of(unit));
        assertThat(lookup.officialByDiscipline(ids)).containsEntry(ids.getFirst(), false);
        unit.setStatus("FINISHED");
        unit.setOfficial(true);
        assertThat(lookup.officialByDiscipline(ids)).containsEntry(ids.getFirst(), true);
        var draft = new CompetitionUnitEntity();
        draft.setEventDisciplineId(unit.getEventDisciplineId());
        draft.setStatus("DRAFT");
        when(units.findByEventDisciplineIdInOrderBySequenceNumberAsc(ids)).thenReturn(List.of(unit, draft));
        assertThat(lookup.officialByDiscipline(ids)).containsEntry(ids.getFirst(), false);
        when(units.findByEventDisciplineIdInOrderBySequenceNumberAsc(ids)).thenReturn(List.of());
        assertThat(lookup.officialByDiscipline(ids)).containsEntry(ids.getFirst(), false);
    }
}
