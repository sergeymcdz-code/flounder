package com.acme.sportplatform.documents.domain;

import java.util.List;
import java.util.UUID;

public record Protocol(Header header, List<Discipline> disciplines, boolean showBibs) {
    public Protocol(Header header, List<Discipline> disciplines) { this(header,disciplines,false); }
    public record Header(String title, String venue, String date, String organization, String generatedAt) {}
    public record Discipline(int number, String name, String session, List<Heat> heats,
            List<ResultSection> resultSections, UUID id, UUID sessionId) {
        public Discipline(int number,String name,String session,List<Heat> heats,List<ResultSection> resultSections) {
            this(number,name,session,heats,resultSections,null,null);
        }
    }
    public record Heat(int number, String ageGroup, String startsAt, List<StartRow> rows) {}
    public record StartRow(int lane, String athlete, String year, String club, String seed, boolean withdrawn, Integer bib) {
        public StartRow(int lane,String athlete,String year,String club,String seed,boolean withdrawn) { this(lane,athlete,year,club,seed,withdrawn,null); }
    }
    public record ResultSection(String name, List<ResultRow> ranked, List<ResultRow> unranked, String code) {
        public ResultSection(String name,List<ResultRow> ranked,List<ResultRow> unranked) { this(name,ranked,unranked,null); }
    }
    public record ResultRow(Integer place, String athlete, String year, String club, String result, String status, boolean corrected,
            Integer bib, String lastName, String firstName) {
        public ResultRow(Integer place,String athlete,String year,String club,String result,String status,boolean corrected) {
            this(place,athlete,year,club,result,status,corrected,null,athlete,"");
        }
    }
}
