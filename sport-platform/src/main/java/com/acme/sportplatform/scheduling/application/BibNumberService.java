package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.*;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.scheduling.BibNumberLookup;
import com.acme.sportplatform.scheduling.domain.BibAllocator;
import com.acme.sportplatform.scheduling.infrastructure.BibNumberRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BibNumberService implements BibNumberLookup {
    public record Assignment(int assigned, int kept, int total) {}
    public record Row(UUID profileId, int bibNumber, String lastName, String firstName, Integer birthYear, String club) {}
    private final BibNumberRepository numbers;
    private final RegistrationLookup registrations;
    private final ProfileLookup profiles;
    private final SchedulingData data;
    public BibNumberService(BibNumberRepository numbers, RegistrationLookup registrations, ProfileLookup profiles, SchedulingData data) {
        this.numbers=numbers; this.registrations=registrations; this.profiles=profiles; this.data=data;
    }
    @Override public Map<UUID,Integer> byEvent(UUID eventId) { return numbers.byEvent(eventId); }
    public List<Row> list(UUID eventId, AuthenticatedUserPrincipal principal) {
        data.access.assertCanManage(eventId, principal);
        var assigned = byEvent(eventId);
        return profiles.findByIdIn(assigned.keySet()).stream().map(p -> new Row(p.id(), assigned.get(p.id()), p.lastName(), p.firstName(),
                p.birthDate() == null ? null : p.birthDate().getYear(), p.clubName())).sorted(Comparator.comparingInt(Row::bibNumber)).toList();
    }
    private List<BibAllocator.Athlete> athletes(UUID eventId) {
        var ids = registrations.findByEventId(eventId).stream().filter(r -> Set.of("CONFIRMED","PAID","ADMITTED").contains(r.status()))
                .map(r -> r.participantProfileId()).distinct().toList();
        return profiles.findByIdIn(ids).stream().map(p -> new BibAllocator.Athlete(p.id(),p.lastName(),p.firstName(),p.birthDate(),p.clubName())).toList();
    }
    private void writable(UUID eventId, AuthenticatedUserPrincipal principal) {
        data.access.assertCanModify(eventId, principal); data.locks.lockWritableForUpdate(eventId);
    }
    private void assertUnlocked(UUID eventId) {
        if (data.disciplines(eventId).stream().flatMap(d -> data.units.findByDisciplineId(d.id()).stream())
                .anyMatch(h -> Set.of("IN_PROGRESS","FINISHED","COMPLETED").contains(h.status())))
            throw new BusinessException("scheduling.bib_locked", "После начала стартов номера менять нельзя");
    }
    @Transactional
    public Assignment assign(UUID eventId, BibAllocator.Mode mode, int startFrom, boolean reassign, AuthenticatedUserPrincipal principal) {
        writable(eventId, principal);
        if (reassign) assertUnlocked(eventId);
        var athletes = athletes(eventId);
        var old = reassign ? Map.<UUID,Integer>of() : byEvent(eventId);
        var allocated = BibAllocator.assign(athletes, old, mode, startFrom);
        if (reassign) numbers.clear(eventId);
        allocated.forEach((profile,bib) -> { if (!old.containsKey(profile)) numbers.save(eventId,profile,bib); });
        int kept = (int) athletes.stream().filter(a -> old.containsKey(a.id())).count();
        return new Assignment(athletes.size()-kept,kept,athletes.size());
    }
    @Transactional
    public Row change(UUID eventId, UUID profileId, int bib, AuthenticatedUserPrincipal principal) {
        writable(eventId, principal); assertUnlocked(eventId);
        var old = byEvent(eventId);
        if (!old.containsKey(profileId)) throw new BusinessException("scheduling.bib_not_found", "Сначала назначьте участнику стартовый номер");
        if (old.entrySet().stream().anyMatch(e -> !e.getKey().equals(profileId) && e.getValue()==bib))
            throw new BusinessException("scheduling.bib_taken", "Этот стартовый номер уже занят");
        numbers.save(eventId,profileId,bib);
        return list(eventId,principal).stream().filter(r -> r.profileId().equals(profileId)).findFirst().orElseThrow();
    }
}
