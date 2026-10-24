package com.acme.sportplatform.documents;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.documents.application.ExportService;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.results.*;
import com.acme.sportplatform.standings.StandingsLookup;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportServiceLimitTest {
    @Test
    void rejectsAnOversizedExportBeforeLoadingAthletesOrWritingAResponse() {
        var events = mock(EventLookup.class);
        var disciplines = mock(EventDisciplineLookup.class);
        var results = mock(ResultsProtocolLookup.class);
        var publications = mock(ResultsPublicationLookup.class);
        var standings = mock(StandingsLookup.class);
        var eventId = UUID.randomUUID();
        var disciplineId = UUID.randomUUID();
        when(events.getById(eventId)).thenReturn(new EventLookupResult(eventId, UUID.randomUUID(), "PUBLISHED", null, null, List.of()));
        when(disciplines.findByEventId(eventId)).thenReturn(List.of(new EventDisciplineSummary(disciplineId, eventId,
                "M-50", "50 м", "INDIVIDUAL", "HEAT", "TIME", "ASC", null, null, null, "{}", 50, "FREE", "M",
                List.of(), "BY_TIME_ALL", null, 1, 90, null)));
        when(publications.countExportEntries(List.of(disciplineId), false)).thenReturn((long) ExportService.MAX_ROWS + 1);
        var service = new ExportService(events, mock(EventPublicLookup.class), disciplines, results, publications, standings);
        assertThatThrownBy(() -> service.prepare(eventId, ExportService.Kind.RESULTS))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("documents.export_too_large"));
        verifyNoInteractions(results, standings);
    }
}
