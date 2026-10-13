package com.acme.sportplatform.imports.api;

import java.util.List;

public record ImportReport(int total, int created, int updated, int skipped,
                           List<RowError> errors, int createdProfiles) {
    public record RowError(int row, String column, String message) {}
}
