package com.acme.sportplatform.registrations.application;

import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.*;
import com.acme.sportplatform.files.FileLookup;
import com.acme.sportplatform.registrations.ParticipantDocumentUploadedEvent;
import com.acme.sportplatform.registrations.api.ParticipantDocumentResponse;
import com.acme.sportplatform.registrations.domain.DocumentType;
import com.acme.sportplatform.registrations.domain.DocumentStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;

@Service
@Transactional
public class ParticipantDocumentService {
    private final ParticipantDocumentRepository documents;
    private final RegistrationRepository registrations;
    private final ProfileLookup profiles;
    private final EventAccessService access;
    private final FileLookup files;
    private final ApplicationEventPublisher publisher;
    public ParticipantDocumentService(ParticipantDocumentRepository documents, RegistrationRepository registrations,
            ProfileLookup profiles, EventAccessService access, FileLookup files, ApplicationEventPublisher publisher) {
        this.documents = documents; this.registrations = registrations; this.profiles = profiles;
        this.access = access; this.files = files; this.publisher = publisher;
    }
    public ParticipantDocumentResponse upload(UUID profileId, UUID eventId, DocumentType type, MultipartFile file,
            LocalDate issuedAt, LocalDate validUntil, AuthenticatedUserPrincipal principal) {
        boolean owner = owns(profileId, principal);
        if (!owner) {
            if (eventId == null) throw denied();
            assertManager(profileId, eventId, principal);
        } else if (eventId != null && !registrations.existsByEventIdAndParticipantProfileId(eventId, profileId)) {
            throw new BusinessException("documents.not_found", "Заявка на событие не найдена");
        }
        if (issuedAt != null && validUntil != null && issuedAt.isAfter(validUntil))
            throw new BusinessException("documents.invalid_dates", "Дата выдачи должна быть не позже срока действия");
        if (file.getSize() > 10 * 1024 * 1024) throw new BusinessException("files.too_large", "Размер документа превышает 10 МБ");
        String name = safeName(file.getOriginalFilename());
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(10 * 1024 * 1024 + 1); }
        catch (IOException ex) { throw new BusinessException("files.read_failed", "Не удалось прочитать документ"); }
        var stored = files.put(bytes, name, file.getContentType(), principal.getUserId());
        var d = new ParticipantDocumentEntity();
        d.setId(UUID.randomUUID()); d.setProfileId(profileId); d.setEventId(eventId); d.setType(type.name());
        d.setFileKey(stored.key()); d.setOriginalName(name); d.setContentType(stored.contentType()); d.setSizeBytes(stored.sizeBytes());
        d.setIssuedAt(issuedAt); d.setValidUntil(validUntil); d.setStatus(DocumentStatus.PENDING.name());
        d.setUploadedBy(principal.getUserId()); d.setUploadedAt(OffsetDateTime.now()); documents.save(d);
        publisher.publishEvent(new ParticipantDocumentUploadedEvent(d.getId(), profileId, eventId, principal.getUserId(), type.name(), d.getUploadedAt()));
        return response(d);
    }
    @Transactional(readOnly = true)
    public List<ParticipantDocumentResponse> list(UUID profileId, UUID eventId, AuthenticatedUserPrincipal principal) {
        boolean owner = owns(profileId, principal);
        if (!owner) {
            if (eventId == null) throw denied();
            assertManager(profileId, eventId, principal);
        }
        return documents.findByProfileIdOrderByUploadedAtDesc(profileId).stream()
                .filter(d -> owner && eventId == null || d.getEventId() == null || d.getEventId().equals(eventId))
                .map(this::response).toList();
    }
    public void delete(UUID profileId, UUID id, AuthenticatedUserPrincipal principal) {
        var d = documents.lockById(id).orElseThrow(this::notFound);
        if (!d.getProfileId().equals(profileId)) throw notFound();
        if (!owns(profileId, principal)) throw denied();
        if (!DocumentStatus.PENDING.name().equals(d.getStatus())) throw new BusinessException("documents.not_pending", "Можно удалить только документ на проверке");
        documents.delete(d); files.delete(d.getFileKey(), principal.getUserId());
    }
    public FileLookup.SignedDownload download(UUID id, UUID eventId, AuthenticatedUserPrincipal principal) {
        var d = accessible(id, eventId, principal);
        var link = files.presignedUrl(d.getFileKey(), id, principal.getUserId());
        // Re-check the organizer event scope when fetching the signed content.
        return new FileLookup.SignedDownload(link.url() + (eventId == null ? "" : "&eventId=" + eventId), link.expiresAt());
    }
    public Content content(UUID id, UUID eventId, Long expires, String signature, AuthenticatedUserPrincipal principal) {
        if (principal == null || expires == null || signature == null) throw new BusinessException("files.not_found", "Файл не найден");
        var d = documents.findById(id).orElseThrow(this::notFound);
        files.verify(d.getFileKey(), principal.getUserId(), expires, signature);
        assertDocumentAccess(d, eventId, principal);
        return new Content(response(d), files.get(d.getFileKey(), principal.getUserId()));
    }
    private ParticipantDocumentEntity accessible(UUID id, UUID eventId, AuthenticatedUserPrincipal principal) {
        var d = documents.findById(id).orElseThrow(this::notFound);
        assertDocumentAccess(d, eventId, principal); return d;
    }
    private void assertDocumentAccess(ParticipantDocumentEntity d, UUID eventId, AuthenticatedUserPrincipal principal) {
        if (owns(d.getProfileId(), principal)) return;
        UUID scope = d.getEventId() == null ? eventId : d.getEventId();
        if (scope == null || eventId != null && !scope.equals(eventId)) throw notFound();
        assertManager(d.getProfileId(), scope, principal);
    }
    private boolean owns(UUID profileId, AuthenticatedUserPrincipal p) {
        return p != null && p.getUserId().equals(profiles.getById(profileId).userId());
    }
    private void assertManager(UUID profileId, UUID eventId, AuthenticatedUserPrincipal principal) {
        boolean manager = principal != null && principal.getAuthorities().stream().anyMatch(a ->
                Set.of("ROLE_ORGANIZER", "ROLE_OPERATOR", "ROLE_PLATFORM_ADMIN").contains(a.getAuthority()));
        if (!manager) throw denied();
        access.assertCanManage(eventId, principal);
        if (!registrations.existsByEventIdAndParticipantProfileId(eventId, profileId)) throw notFound();
    }
    private String safeName(String name) {
        if (name == null) return "document";
        String safe = name.replace('\\', '/'); safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (safe.isBlank() || safe.length() > 255) throw new BusinessException("files.invalid_name", "Некорректное имя файла");
        return safe;
    }
    private BusinessException denied() { return new BusinessException("documents.access_denied", "Нет доступа к документам профиля"); }
    private BusinessException notFound() { return new BusinessException("documents.not_found", "Документ не найден"); }
    private ParticipantDocumentResponse response(ParticipantDocumentEntity d) {
        return new ParticipantDocumentResponse(d.getId(), d.getProfileId(), d.getEventId(), d.getType(), d.getOriginalName(),
                d.getContentType(), d.getSizeBytes(), d.getIssuedAt(), d.getValidUntil(), d.getStatus(), d.getRejectReason(), d.getUploadedAt());
    }
    public record Content(ParticipantDocumentResponse document, byte[] bytes) {}
}
