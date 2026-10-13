package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.imports.domain.ImportRow;
import com.acme.sportplatform.imports.domain.ImportRowException;
import org.springframework.stereotype.Service;

@Service
public class ImportRowValidator {
    public void validate(ImportRow row, EventDisciplineSummary discipline) {
        if (discipline == null) throw new ImportRowException("Дисциплина (код)", "неизвестный код дисциплины в событии");
        String gender = row.profile().gender();
        int year = row.profile().birthDate().getYear();
        if (discipline.gender() != null && !"X".equals(discipline.gender()) && !gender.equals(discipline.gender())) {
            throw new ImportRowException("Пол (М/Ж)", "не подходит под возрастные группы вида");
        }
        if (!discipline.ageGroups().isEmpty()
                && discipline.ageGroups().stream().noneMatch(group -> group.includes(gender, year))) {
            throw new ImportRowException("Дата рождения (ДД.ММ.ГГГГ)", "не подходит под возрастные группы вида");
        }
    }
}
