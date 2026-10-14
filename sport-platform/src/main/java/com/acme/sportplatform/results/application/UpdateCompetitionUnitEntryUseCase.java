package com.acme.sportplatform.results.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.events.EventLockService;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.organizations.VenueLookup;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateCompetitionUnitEntryUseCase {
    private final JdbcTemplate jdbc;
    private final EventDisciplineLookup disciplines;
    private final EventAccessService access;
    private final EventLookup events;
    private final EventLockService locks;
    private final VenueLookup venues;
    private final ObjectMapper json;

    public UpdateCompetitionUnitEntryUseCase(
            JdbcTemplate jdbc,
            EventDisciplineLookup disciplines,
            EventAccessService access,
            EventLookup events,
            EventLockService locks,
            VenueLookup venues,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.disciplines = disciplines;
        this.access = access;
        this.events = events;
        this.locks = locks;
        this.venues = venues;
        this.json = json;
    }

    @Transactional
    public void execute(UUID id, int lane, AuthenticatedUserPrincipal principal) {
        var rows =
                jdbc.query(
                        """
select u.id,u.event_discipline_id from competition_units u join competition_unit_entries e on e.competition_unit_id=u.id where e.id=?
""",
                        (rs, n) ->
                                new UUID[] {
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("event_discipline_id", UUID.class)
                                },
                        id);
        if (rows.isEmpty())
            throw new BusinessException("results.entry_not_found", "Участник заплыва не найден");
        var row = rows.getFirst();
        var discipline = disciplines.getForScheduling(row[1]);
        access.assertCanModify(discipline.eventId(), principal);
        locks.lockWritableForUpdate(discipline.eventId());
        int lanes;
        try {
            lanes =
                    json.readTree(
                                    venues.getMetadata(
                                            events.getById(discipline.eventId()).venueId()))
                            .path("lanes")
                            .asInt(8);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    "results.invalid_pool", "Некорректные настройки дорожек бассейна");
        }
        if (lane < 1 || lane > lanes)
            throw new BusinessException(
                    "results.lane_out_of_range", "Дорожка вне диапазона бассейна");
        Integer conflicts =
                jdbc.queryForObject(
                        "select count(*) from competition_unit_entries where competition_unit_id=?"
                                + " and lane_or_position=? and id<>?",
                        Integer.class,
                        row[0],
                        lane,
                        id);
        if (conflicts != null && conflicts > 0)
            throw new BusinessException("results.lane_conflict", "Эта дорожка уже занята");
        if (jdbc.update(
                        "update competition_unit_entries set lane_or_position=? where id=?",
                        lane,
                        id)
                == 0)
            throw new BusinessException("results.entry_not_found", "Участник заплыва не найден");
        jdbc.update(
                "update competition_units set updated_at=now(),version=version+1 where id=?",
                row[0]);
    }
}
