package com.acme.sportplatform.registrations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class RegistrationLookupServiceTest {
    @Test
    void resolvesImportedProfileOwnersInOneBatch() {
        var repository = mock(RegistrationRepository.class);
        var profiles = mock(ProfileLookup.class);
        var first = row();
        var second = row();
        UUID owner = UUID.randomUUID();
        var profile = mock(ProfileLookupResult.class);
        when(profile.id()).thenReturn(first.getParticipantProfileId());
        when(profile.userId()).thenReturn(owner);
        when(repository.findByIdIn(anyList())).thenReturn(List.of(first, second));
        when(profiles.findByIdIn(anyCollection())).thenReturn(List.of(profile));
        var service = new RegistrationLookupService(repository, profiles);
        var result = service.findByIdIn(List.of(first.getId(), second.getId()));
        assertEquals(owner, result.get(0).participantUserId());
        assertNull(result.get(1).participantUserId());
        verify(profiles, times(1)).findByIdIn(anyCollection());
        verify(profiles, never()).getById(any());
    }

    private RegistrationEntity row() {
        var r = new RegistrationEntity();
        r.setId(UUID.randomUUID());
        r.setParticipantProfileId(UUID.randomUUID());
        r.setStatus("CONFIRMED");
        r.setParticipantSnapshot("{}");
        return r;
    }
}
