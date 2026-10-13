package com.acme.sportplatform.competition.application;
import com.acme.sportplatform.competition.AgeGroup;
import com.acme.sportplatform.competition.api.CreateEventDisciplineRequest;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.events.SessionLookup;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SwimDisciplineValidator {
    private final SessionLookup sessions;

    public SwimDisciplineValidator(SessionLookup sessions) {
        this.sessions = sessions;
    }

    public void apply(EventDisciplineEntity d, CreateEventDisciplineRequest r) {
        if (r.distanceM() != null) d.setDistanceM(r.distanceM());
        if (r.stroke() != null) d.setStroke(r.stroke().name());
        if (r.gender() != null) d.setGender(r.gender());
        if (r.ageGroups() != null) {
            if (r.ageGroups().stream().anyMatch(Objects::isNull))
                invalid("ageGroups","Возрастная группа не может быть пустой");
            d.setAgeGroups(List.copyOf(r.ageGroups()));
        }
        if (r.seedingMode() != null) d.setSeedingMode(r.seedingMode().name());
        if (r.sessionId() != null) d.setSessionId(r.sessionId());
        if (r.orderInSession() != null) d.setOrderInSession(r.orderInSession());
        if (r.heatDurationSec() != null) d.setHeatDurationSec(r.heatDurationSec());
        if (d.getDistanceM() != null
                && !Set.of(25, 50, 100, 200, 400, 800, 1500).contains(d.getDistanceM()))
            invalid("distanceM","Недопустимая дистанция");
        if (d.getStroke() != null
                && java.util.Arrays.stream(com.acme.sportplatform.competition.Stroke.values())
                        .noneMatch(stroke -> stroke.name().equals(d.getStroke())))
            invalid("stroke","Недопустимый стиль плавания");
        if (d.getGender() != null && !Set.of("M", "F", "X").contains(d.getGender()))
            invalid("gender","Пол должен быть M, F или X");
        if (!Set.of("BY_TIME_ALL", "BY_AGE_GROUP").contains(d.getSeedingMode()))
            invalid("seedingMode","Недопустимый режим посева");
        if (d.getOrderInSession() != null && d.getOrderInSession() <= 0)
            invalid("orderInSession", "Порядок должен быть положительным");
        if ( d.getHeatDurationSec() != null && d.getHeatDurationSec() <= 0)
            invalid("heatDurationSec", "Длительность должна быть положительной");
        if (d.getSessionId() != null)
            sessions.assertBelongsToEvent(d.getSessionId(), d.getEventId());
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < d.getAgeGroups().size(); index++) {
            AgeGroup g = d.getAgeGroups().get(index);
            String prefix = "ageGroups[" + index + "].";
            if (g == null
                    || g.code() == null
                    || g.code().isBlank()
                    || g.name() == null
                    || g.name().isBlank()
                    || g.gender() == null
                    || !Set.of("M", "F", "X").contains(g.gender()))
                invalid("ageGroups","У группы должны быть код, название и пол M, F или X");
            if (!codes.add(g.code())) invalid(prefix + "code","Коды возрастных групп должны быть уникальны");
            if (g.birthYearFrom() != null
                    && g.birthYearTo() != null
                    && g.birthYearFrom() > g.birthYearTo())
                invalid(
                        prefix + "birthYearFrom","Начальный год рождения не может быть больше конечного");
            if (d.getGender() != null
                    && !"X".equals(d.getGender())
                    && !d.getGender().equals(g.gender()))
                invalid(prefix + "gender","Пол возрастной группы не совпадает с полом дисциплины");
        }
        var groups = d.getAgeGroups();
        for (int i = 0; i < groups.size(); i++)
            for (int j = i + 1; j < groups.size(); j++) {
                var a = groups.get(i);
                var b = groups.get(j);
                if ((a.gender().equals(b.gender())
                                || "X".equals(a.gender())
                                || "X".equals(b.gender()))
                        && Math.max(from(a), from(b)) <= Math.min(to(a), to(b)))
                    invalid(
                            "ageGroups[" + j + "].birthYearFrom","Возрастные группы пересекаются по годам рождения");
            }
    }

    private int from(AgeGroup g) {
        return g.birthYearFrom() == null ? Integer.MIN_VALUE : g.birthYearFrom();
    }

    private int to(AgeGroup g) {
        return g.birthYearTo() == null ? Integer.MAX_VALUE : g.birthYearTo();
    }

    private void invalid(String field, String message) {
        throw com.acme.sportplatform.common.RequestValidation.field(
                "competition.invalid_swim_fields", field, message);
    }

    public String generatedName(EventDisciplineEntity d) {
        if (d.getDistanceM() == null || d.getStroke() == null || d.getGender() == null) {
            invalid("name","Для автоматического названия укажите дистанцию, стиль и пол");
        }
        String stroke =
                switch (d.getStroke()) {
                    case "FREE" -> "вольный стиль";
                    case "BACK" -> "на спине";
                    case "BREAST" -> "брасс";
                    case "FLY" -> "баттерфляй";
                    default -> "комплекс";
                };
        return d.getDistanceM()
                + " м "
                + stroke
                + ", "
                + switch (d.getGender()) {
                    case "F" -> "девушки";
                    case "M" -> "юноши";
                    default -> "смешанный";
                };
    }

    public String generatedCode(EventDisciplineEntity d) {
        generatedName(d);
        return d.getGender() + "-" + d.getDistanceM() + "-" + d.getStroke();
    }
}
