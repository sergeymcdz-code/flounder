package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.imports.domain.ImportRow;
import com.acme.sportplatform.registrations.RegistrationImportLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ImportRowService {
    private final ProfileLookup profiles;
    private final RegistrationImportLookup registrations;

    public ImportRowService(ProfileLookup profiles, RegistrationImportLookup registrations) {
        this.profiles = profiles;
        this.registrations = registrations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result execute(UUID eventId, ImportRow row, EventDisciplineSummary discipline) {
        var profile = profiles.findOrCreateByExternalKey(row.externalKey(), row.profile());
        boolean created = registrations.upsert(eventId, discipline.id(), profile, row.seedTimeMs(), discipline.entryFeeAmount());
        return new Result(created, profile.created());
    }

    public record Result(boolean created, boolean createdProfile) {}
}
