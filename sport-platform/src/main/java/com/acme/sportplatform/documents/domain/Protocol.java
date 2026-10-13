package com.acme.sportplatform.documents.domain;

import java.util.List;

public record Protocol(Header header, List<Discipline> disciplines) {
    public record Header(String title, String venue, String date, String organization,
            String generatedAt) {}
    public record Discipline(int number, String name, String session, List<Heat> heats,
            List<ResultSection> resultSections) {}
    public record Heat(int number, String ageGroup, String startsAt, List<StartRow> rows) {}
    public record StartRow(int lane, String athlete, String year, String club, String seed,
            boolean withdrawn) {}
    public record ResultSection(String name, List<ResultRow> ranked, List<ResultRow> unranked) {}
    public record ResultRow(Integer place, String athlete, String year, String club,
            String result, String status, boolean corrected) {}
}
