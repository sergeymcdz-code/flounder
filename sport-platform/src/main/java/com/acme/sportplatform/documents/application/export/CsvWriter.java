package com.acme.sportplatform.documents.application.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Excel-compatible UTF-8 CSV. Every value, including metadata, uses the same escaping. */
public final class CsvWriter implements AutoCloseable {
    private final Writer writer;

    public CsvWriter(OutputStream output) throws IOException {
        writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writer.write('\ufeff');
    }

    public void row(List<?> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) writer.write(';');
            writer.write(escape(cells.get(i)));
        }
        writer.write("\r\n");
    }

    public static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        // Spreadsheet applications can trim whitespace/control characters before parsing formulas.
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() throws IOException {
        writer.flush(); // The servlet owns the response stream.
    }
}
