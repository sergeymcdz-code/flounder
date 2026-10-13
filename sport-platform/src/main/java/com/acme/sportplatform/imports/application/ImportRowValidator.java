package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.competition.ImportDisciplineLookup.Discipline;
import com.acme.sportplatform.imports.domain.ImportRow;
import com.acme.sportplatform.imports.domain.ImportRowException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ImportRowValidator {
    private final ObjectMapper mapper;

    public ImportRowValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public void validate(ImportRow row, Discipline discipline) {
        if (discipline == null) throw new ImportRowException("Дисциплина (код)", "неизвестный код дисциплины в событии");
        if (!"PUBLISHED".equals(discipline.status())) {
            throw new ImportRowException("Дисциплина (код)", "дисциплина не опубликована");
        }
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
}
