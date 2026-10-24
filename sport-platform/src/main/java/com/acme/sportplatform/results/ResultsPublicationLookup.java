package com.acme.sportplatform.results;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Certification of a discipline requires all of its heats, including unpublished heats. */
public interface ResultsPublicationLookup {
    Map<UUID, Boolean> officialByDiscipline(List<UUID> disciplineIds);

    /** Count before materializing the bounded export snapshot. */
    long countExportEntries(List<UUID> disciplineIds, boolean publishedOnly);
}
