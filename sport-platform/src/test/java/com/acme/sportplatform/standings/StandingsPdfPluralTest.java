package com.acme.sportplatform.standings;

import com.acme.sportplatform.documents.domain.Protocol;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class StandingsPdfPluralTest {
    private static final PdfRenderer RENDERER = new PdfRenderer();

    @ParameterizedTest
    @CsvSource({"1,заплыв", "2,заплыва", "5,заплывов", "11,заплывов", "14,заплывов", "21,заплыв", "22,заплыва", "25,заплывов", "111,заплывов"})
    void preliminaryPdfUsesRussianPluralForms(int count, String noun) throws Exception {
        var view = new StandingsView("Клубный кубок", "AGE_GROUP", List.of(), List.of(), count, 0,
                Instant.parse("2026-10-17T12:00:00Z"), true);
        var header = new Protocol.Header("Кубок", "Бассейн", "17.10.2026", "Организатор", "12:00");
        try (var pdf = PDDocument.load(RENDERER.renderStandings(header, view))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("не завершено " + count + " " + noun);
        }
    }
}
