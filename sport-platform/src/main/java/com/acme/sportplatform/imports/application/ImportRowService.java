package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.competition.ImportDisciplineLookup.Discipline;
import com.acme.sportplatform.identity.ImportedProfileLookup;
import com.acme.sportplatform.imports.domain.ImportRow;
import com.acme.sportplatform.imports.domain.ImportRowException;
import com.acme.sportplatform.registrations.ImportedRegistrationLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ImportRowService {
    private final ImportedProfileLookup profiles;
    private final ImportedRegistrationLookup registrations;
    private final ObjectMapper mapper;

    public ImportRowService(ImportedProfileLookup profiles, ImportedRegistrationLookup registrations, ObjectMapper mapper) {
        this.profiles = profiles;
        this.registrations = registrations;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result execute(UUID eventId, ImportRow row, Discipline discipline) {
        if (discipline == null) throw new ImportRowException("Дисциплина (код)", "неизвестный код дисциплины в событии");
        if (!"PUBLISHED".equals(discipline.status())) {
            throw new ImportRowException("Дисциплина (код)", "дисциплина не опубликована");
        }
        validateGroups(row, discipline);
        ImportedProfileLookup.Result profile = profiles.findOrCreateByExternalKey(row.profile());
        boolean created = registrations.upsert(eventId, discipline.id(), profile.profile(), row.seedTimeMs(), discipline.entryFee());
        return new Result(created, profile.created());
    }

    private void validateGroups(ImportRow row, Discipline discipline) {
        String gender = row.profile().gender();
        int year = row.profile().birthDate().getYear();
        if (discipline.gender() != null && !"X".equals(discipline.gender()) && !gender.equals(discipline.gender())) {
            throw new ImportRowException("Пол (М/Ж)", "не подходит под возрастные группы вида");
        }
        JsonNode groups;
        try { groups = mapper.readTree(discipline.ageGroupsJson()); }
        catch (Exception ex) {
            throw new ImportRowException("Дисциплина (код)", "неверная настройка возрастных групп вида");
        }
        if (!groups.isArray()) throw new ImportRowException("Дисциплина (код)", "неверная настройка возрастных групп вида");
        if (groups.isEmpty()) return;
        for (JsonNode group : groups) {
            String groupGender = group.path("gender").asText("X");
            JsonNode from = group.path("birthYearFrom");
            JsonNode to = group.path("birthYearTo");
            if (("X".equals(groupGender) || gender.equals(groupGender))
                    && (from.isMissingNode() || from.isNull() || year >= from.asInt())
                    && (to.isMissingNode() || to.isNull() || year <= to.asInt())) return;
        }
        throw new ImportRowException("Дата рождения (ДД.ММ.ГГГГ)", "не подходит под возрастные группы вида");
    }

    public record Result(boolean created, boolean createdProfile) {}
}
