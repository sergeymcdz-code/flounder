package com.acme.sportplatform.documents;

import com.acme.sportplatform.documents.domain.Protocol;
import com.acme.sportplatform.documents.domain.Protocol.*;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {
    @Test
    void setMProducesValidCyrillicA4ProtocolsUnderThreeSeconds() throws Exception {
        var protocol = setM();
        var renderer = new PdfRenderer();
        for (boolean results : List.of(false, true)) {
            long start = System.nanoTime();
            byte[] pdf = renderer.render(protocol, results);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(durationMs).as("200 entries, results=%s", results).isLessThanOrEqualTo(3000);
            try (var document = PDDocument.load(pdf)) {
                assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
                var text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
                assertThat(text).contains("Коновалов", "Кубок Самары", "Сформировано", "Страница", "2012", "СШОР");
                if (results) assertThat(text).contains("CORRECTED", "DNS", "DNF", "DSQ", "Абсолют", "2010–2015");
                else assertThat(text).contains("NT", "Снят", "10:00:00", "Заявочное время");
                for (var page : document.getPages()) {
                    assertThat(page.getMediaBox().getWidth()).isBetween(594f, 597f);
                    assertThat(page.getMediaBox().getHeight()).isBetween(840f, 843f);
                    var stripper = new PDFTextStripper();
                    stripper.setStartPage(document.getPages().indexOf(page) + 1);
                    stripper.setEndPage(document.getPages().indexOf(page) + 1);
                    assertThat(stripper.getText(document)).contains("Кубок Самары", "Бассейн", "Страница");
                    assertThat(page.getResources().getFontNames()).isNotEmpty();
                    for (var font : page.getResources().getFontNames())
                        assertThat(page.getResources().getFont(font).isEmbedded()).isTrue();
                }
                System.out.printf(Locale.ROOT, "M03 %s: %d entries, %d pages, %d ms%n", results ? "results" : "start-list", 200, document.getNumberOfPages(), durationMs);
            }
            if (Boolean.getBoolean("documents.writeSamples")) {
                var output = Path.of(System.getProperty("documents.sampleDir", "target/m03-samples"));
                Files.createDirectories(output);
                Files.write(output.resolve(results ? "results.pdf" : "start-list.pdf"), pdf);
            }
        }
    }

    private Protocol setM() throws Exception {
        var file = Path.of("../docs/scripts/demo/out/set-M.json");
        if (!Files.exists(file)) file = Path.of("docs/scripts/demo/out/set-M.json");
        var fixture = new ObjectMapper().readTree(file.toFile());
        Map<Integer, JsonNode> athletes = new HashMap<>();
        fixture.path("athletes").forEach(a -> athletes.put(a.path("id").asInt(), a));
        Map<String, List<JsonNode>> entries = new TreeMap<>();
        fixture.path("entries").forEach(e -> entries.computeIfAbsent(e.path("disciplineCode").asText(), code -> new ArrayList<>()).add(e));
        assertThat(fixture.path("entries").size()).isEqualTo(200);
        var disciplines = new ArrayList<Discipline>();
        int number = 0;
        for (var discipline : entries.entrySet()) {
            var heats = new ArrayList<Heat>();
            var ranked = new ArrayList<ResultRow>();
            var unranked = new ArrayList<ResultRow>();
            var rows = discipline.getValue();
            for (int offset = 0; offset < rows.size(); offset += 8) {
                var lanes = new ArrayList<StartRow>();
                for (int lane = 1; lane <= 8; lane++) {
                    int index = offset + lane - 1;
                    if (index >= rows.size()) { lanes.add(new StartRow(lane, "—", "—", "—", "—", false)); continue; }
                    var entry = rows.get(index);
                    var athlete = athletes.get(entry.path("athleteId").asInt());
                    var name = athlete.path("lastName").asText() + " " + athlete.path("firstName").asText();
                    var year = athlete.path("birthYear").asText();
                    var club = athlete.path("club").asText();
                    var seed = entry.path("seedTime").isNull() ? "NT" : entry.path("seedTime").asText("NT");
                    lanes.add(new StartRow(lane, name, year, club, index == 0 ? "—" : seed.isBlank() ? "NT" : seed, index == 0));
                    String status = switch (index) { case 0 -> "DNS"; case 1 -> "DNF"; case 2 -> "DSQ"; default -> "VALID"; };
                    long timeMs = index <= 4 ? 32150L : 32150L + (index - 4) * 250L;
                    String time = String.format(Locale.ROOT, "%02d:%02d.%02d", timeMs / 60000, timeMs / 1000 % 60, timeMs / 10 % 100);
                    var result = new ResultRow(index < 3 ? null : index == 4 ? 1 : index - 2, name, year, club, index < 3 ? "—" : time, status, index == 3);
                    (index < 3 ? unranked : ranked).add(result);
                }
                heats.add(new Heat(offset / 8 + 1, "2010–2015", "10:00:00", lanes));
            }
            var overall = new ResultSection("Абсолют", ranked, unranked);
            var group = new ResultSection("2010–2015", ranked, unranked);
            disciplines.add(new Discipline(++number, discipline.getKey() + " · Вольный стиль", "Утро", heats, List.of(overall, group)));
        }
        return new Protocol(new Header("Кубок Самары", "Бассейн, Самара", "17.10.2026", "Федерация плавания", "13.10.2026 21:00:00 GMT+04:00"), disciplines);
    }
}
