package com.acme.sportplatform.imports.domain;

public class ImportRowException extends RuntimeException {
    private final String column;

    public ImportRowException(String column, String message) {
        super(message);
        this.column = column;
    }

    public String column() { return column; }
}
