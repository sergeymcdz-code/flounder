package com.acme.sportplatform.competition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ImportDisciplineLookup {
    List<Discipline> findByEventId(UUID eventId);

    record Discipline(UUID id, String code, String name, String status, String gender,
                      String ageGroupsJson, BigDecimal entryFee) {}
}
