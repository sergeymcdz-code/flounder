package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.competition.ImportDisciplineLookup.Discipline;
import com.acme.sportplatform.identity.ImportedProfileLookup;
import com.acme.sportplatform.imports.domain.ImportRow;
import com.acme.sportplatform.registrations.ImportedRegistrationLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ImportRowService {
    private final ImportedProfileLookup profiles;
    private final ImportedRegistrationLookup registrations;

    public ImportRowService(ImportedProfileLookup profiles, ImportedRegistrationLookup registrations) {
        this.profiles = profiles;
        this.registrations = registrations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result execute(UUID eventId, ImportRow row, Discipline discipline) {
        ImportedProfileLookup.Result profile = profiles.findOrCreateByExternalKey(row.profile());
        boolean created = registrations.upsert(eventId, discipline.id(), profile.profile(), row.seedTimeMs(), discipline.entryFee());
        return new Result(created, profile.created());
    }

    public record Result(boolean created, boolean createdProfile) {}
}
