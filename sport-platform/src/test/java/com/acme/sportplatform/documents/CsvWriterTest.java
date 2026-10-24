package com.acme.sportplatform.documents;

import com.acme.sportplatform.documents.application.export.CsvWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {
    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"https://example.org\")", "+1", "-1", "@SUM(A1)", "\t=1", " \r\n+1"})
    void neutralizesFormulasIncludingLeadingWhitespace(String input) {
        assertThat(CsvWriter.escape(input)).startsWith("\"'").endsWith("\"");
    }

    @Test
    void writesBomSemicolonCrLfNullsAndLosslessQuotedText() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var csv = new CsvWriter(output)) {
            csv.row(Arrays.asList("Волна; \"А\"\r\nБ", null, 9, "Иванов"));
        }
        assertThat(output.toByteArray()).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("\ufeff\"Волна; \"\"А\"\"\r\nБ\";\"\";\"9\";\"Иванов\"\r\n");
        output.write(1); // Closing the writer must leave the servlet-owned output open.
    }
}
