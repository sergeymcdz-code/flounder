package com.acme.sportplatform.publication.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.publication.infrastructure.*;

@Service
@Transactional
public class LiveManagementService {
    private final EntityManager entityManager;
    private final EventLiveStateRepository states;
    private final PublicationReadRepository reads;
    private final Clock clock;
    public LiveManagementService(EntityManager entityManager, EventLiveStateRepository states,
            PublicationReadRepository reads, @Qualifier("publicationClock") Clock clock) {
        this.entityManager = entityManager; this.states = states; this.reads = reads; this.clock = clock;
    }
    @Caching(evict = {
        @CacheEvict(cacheNames = "publicLive", cacheManager = "publicationCacheManager", allEntries = true),
        @CacheEvict(cacheNames = "publicStartList", cacheManager = "publicationCacheManager", allEntries = true),
        @CacheEvict(cacheNames = "publicSchedule", cacheManager = "publicationCacheManager", allEntries = true)
    })
    public void setCurrent(UUID eventId, UUID unitId, Authentication authentication) {
        assertCanManageAndLock(eventId,authentication);
        if (unitId != null && reads.units(eventId).stream().noneMatch(u -> u.id().equals(unitId) && PublicPublicationService.canRun(u)))
            throw new BusinessException("publication.invalid_live_unit","Для табло выберите опубликованный заплыв этого события.");
        updateState(eventId,unitId);
    }
    @Caching(evict = {
        @CacheEvict(cacheNames = "publicLive", cacheManager = "publicationCacheManager", allEntries = true),
        @CacheEvict(cacheNames = "publicStartList", cacheManager = "publicationCacheManager", allEntries = true),
        @CacheEvict(cacheNames = "publicSchedule", cacheManager = "publicationCacheManager", allEntries = true)
    })
    public void start(UUID unitId, Authentication authentication) { transition(unitId,authentication,true); }
    @Caching(evict = {
        @CacheEvict(cacheNames = "publicLive", cacheManager = "publicationCacheManager", allEntries = true),
        @CacheEvict(cacheNames = "publicStartList", cacheManager = "publicationCacheManager", allEntries = true),
        @CacheEvict(cacheNames = "publicSchedule", cacheManager = "publicationCacheManager", allEntries = true)
    })
    public void finish(UUID unitId, Authentication authentication) { transition(unitId,authentication,false); }
    private void transition(UUID unitId, Authentication authentication, boolean start) {
        List<?> events = entityManager.createNativeQuery("""
                select d.event_id from competition_units u join event_disciplines d on d.id = u.event_discipline_id where u.id = :unitId
                """).setParameter("unitId",unitId).getResultList();
        if (events.isEmpty()) throw new BusinessException("publication.unit_not_found","Заплыв не найден.");
        UUID eventId = (UUID)events.getFirst();
        assertCanManageAndLock(eventId,authentication);
        var units = reads.units(eventId);
        var unit = units.stream().filter(u -> u.id().equals(unitId)).findFirst()
                .orElseThrow(() -> new BusinessException("publication.invalid_transition","Заплыв должен быть опубликован."));
        String expected = start ? "PUBLISHED" : "IN_PROGRESS";
        if (!expected.equals(unit.status())) throw new BusinessException("publication.invalid_transition",
                start ? "Начать можно только опубликованный заплыв." : "Завершить можно только текущий заплыв.");
        if (!start && !states.findById(eventId).map(s -> unitId.equals(s.getCurrentUnitId())).orElse(false))
            throw new BusinessException("publication.invalid_transition","Завершить можно только текущий заплыв.");
        int changed = entityManager.createNativeQuery("""
                update competition_units set status = :status, updated_at = :now, version = version + 1
                where id = :id and status = :expected
                """).setParameter("status",start ? "IN_PROGRESS" : "FINISHED").setParameter("now",OffsetDateTime.now(clock))
                .setParameter("id",unitId).setParameter("expected",expected).executeUpdate();
        if (changed != 1) throw new BusinessException("results.concurrent_update","Заплыв изменён другим пользователем. Обновите страницу.");
        UUID nextId = start ? unitId : units.subList(units.indexOf(unit) + 1,units.size()).stream()
                .filter(PublicPublicationService::canRun).map(u -> u.id()).findFirst().orElse(null);
        updateState(eventId,nextId);
    }
    private void assertCanManageAndLock(UUID eventId, Authentication authentication) {
        List<?> owners = entityManager.createNativeQuery("select created_by_user_id from events where id = :id for update")
                .setParameter("id",eventId).getResultList();
        if (owners.isEmpty()) throw new BusinessException("publication.event_not_found","Событие не найдено.");
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        boolean owner = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                && principal.getUserId().equals(owners.getFirst());
        // TODO T01: replace the temporary owner/admin check with EventAccessService.assertCanManage.
        if (!admin && !owner) throw new BusinessException("publication.access_denied","Нет прав на управление этим событием.");
    }
    private void updateState(UUID eventId, UUID unitId) {
        var state = states.findById(eventId).orElseGet(() -> new EventLiveStateEntity(eventId));
        state.update(unitId,OffsetDateTime.now(clock));
        states.save(state);
    }
}
