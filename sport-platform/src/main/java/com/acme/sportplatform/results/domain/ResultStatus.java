package com.acme.sportplatform.results.domain;

public enum ResultStatus {
    VALID,
    PENDING,
    CORRECTED,
    DISQUALIFIED,
    DID_NOT_START,
    DID_NOT_FINISH,
    EXHIBITION;

    public String apiCode() {
        return switch (this) {
            case DID_NOT_START -> "DNS";
            case DID_NOT_FINISH -> "DNF";
            case DISQUALIFIED -> "DSQ";
            default -> name();
        };
    }
}