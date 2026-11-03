package com.acme.sportplatform.results.application;

import com.acme.sportplatform.results.ResultsPublicationLookup;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResultsPublicationLookupService implements ResultsPublicationLookup {
    private final CompetitionUnitRepository units;
    private final JdbcTemplate jdbc;

    public ResultsPublicationLookupService(CompetitionUnitRepository units, JdbcTemplate jdbc) {
        this.units = units;
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Boolean> officialByDiscipline(List<UUID> disciplineIds) {
        if (disciplineIds.isEmpty()) return Map.of();
        var grouped = units.findByEventDisciplineIdInOrderBySequenceNumberAsc(disciplineIds).stream()
                .collect(Collectors.groupingBy(u -> u.getEventDisciplineId()));
        Map<UUID, Boolean> result = new HashMap<>();
        for (var id : disciplineIds) {
            var heats = grouped.getOrDefault(id, List.of());
            result.put(id, !heats.isEmpty() && heats.stream().allMatch(u -> u.isOfficial()
                    && Set.of("FINISHED", "COMPLETED").contains(u.getStatus())));
        }
        return Map.copyOf(result);
    }

    @Override
    public long countExportEntries(List<UUID> disciplineIds, boolean publishedOnly) {
        if (disciplineIds.isEmpty()) return 0;
        String placeholders = String.join(",", Collections.nCopies(disciplineIds.size(), "?"));
        String sql = "select count(*) from competition_unit_entries e join competition_units u"
                + " on u.id=e.competition_unit_id where u.event_discipline_id in (" + placeholders + ")";
        if (publishedOnly) sql += " and u.status in ('FINISHED','COMPLETED')";
        return jdbc.queryForObject(sql, Long.class, disciplineIds.toArray());
    }
}
