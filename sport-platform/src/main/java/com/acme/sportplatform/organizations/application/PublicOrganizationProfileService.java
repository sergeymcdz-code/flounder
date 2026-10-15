package com.acme.sportplatform.organizations.application;

import java.io.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.FileLookup;
import com.acme.sportplatform.organizations.*;
import com.acme.sportplatform.organizations.api.PublicOrganizationProfile;

@Service
@Transactional
public class PublicOrganizationProfileService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FileLookup files;
    private final OrganizationPublicSlugs slugs;
    public PublicOrganizationProfileService(NamedParameterJdbcTemplate jdbc, FileLookup files, OrganizationPublicSlugs slugs) {
        this.jdbc = jdbc; this.files = files; this.slugs = slugs;
    }
    private Map<String,Object> require(UUID id) {
        slugs.lock();
        var rows = jdbc.queryForList("select * from organizations where id=:id for update", Map.of("id", id));
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }
    public PublicOrganizationProfile profile(UUID id) {
        slugs.ensure(id);
        var row = require(id);
        return new PublicOrganizationProfile((String) row.get("public_slug"), (String) row.get("description"),
                (String) row.get("website"), (String) row.get("public_email"), (String) row.get("public_phone"),
                (Boolean) row.get("is_public"), row.get("logo_file_key") != null);
    }
    public PublicOrganizationProfile save(UUID id, PublicOrganizationProfile profile) {
        slugs.lock();
        require(id);
        if (Set.of("new", "admin", "api").contains(profile.publicSlug()))
            throw new BusinessException("organizations.invalid_slug", "Этот адрес зарезервирован");
        try {
            var params = new HashMap<String,Object>();
            params.put("id", id); params.put("slug", profile.publicSlug()); params.put("description", profile.description());
            params.put("website", profile.website()); params.put("email", profile.publicEmail());
            params.put("phone", profile.publicPhone()); params.put("visible", profile.isPublic());
            jdbc.update("""
                    update organizations set public_slug=:slug, description=:description, website=:website,
                        public_email=:email, public_phone=:phone, is_public=:visible, updated_at=now() where id=:id
                    """, params);
        } catch (DuplicateKeyException ex) { throw new BusinessException("organizations.slug_taken", "Этот адрес уже занят"); }
        return profile(id);
    }
    public PublicOrganization publicBySlug(String slug) {
        var rows = jdbc.query("""
                select *, meta->>'city' as city from organizations where public_slug=:slug and is_public=true
                """, Map.of("slug", slug), (r,n) -> new PublicOrganization(r.getObject("id", UUID.class), r.getString("name"),
                r.getString("public_slug"), r.getString("city"), r.getString("description"), r.getString("website"),
                r.getString("public_email"), r.getString("public_phone"), r.getString("logo_file_key") != null));
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }
    public Map<UUID,PublicOrganizationCard> cards(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<UUID,PublicOrganizationCard>();
        jdbc.query("select id, name, public_slug, meta->>'city' as city, logo_file_key, is_public from organizations where id in (:ids)",
                Map.of("ids", ids), r -> {
                    UUID id = r.getObject("id", UUID.class);
                    boolean visible = r.getBoolean("is_public");
                    result.put(id, new PublicOrganizationCard(id, r.getString("name"), visible ? r.getString("public_slug") : null,
                            r.getString("city"), visible && r.getString("logo_file_key") != null));
                });
        return Map.copyOf(result);
    }
    public PublicOrganizationProfile upload(UUID id, MultipartFile file) {
        var row = require(id);
        if (file.getSize() > 2 * 1024 * 1024) throw new BusinessException("organizations.logo_too_large", "Размер логотипа превышает 2 МБ");
        String old = (String) row.get("logo_file_key");
        try (var input = file.getInputStream()) {
            var stored = files.putPublic(input, file.getSize(), file.getOriginalFilename(), file.getContentType());
            jdbc.update("update organizations set logo_file_key=:key, updated_at=now() where id=:id", Map.of("key", stored.key(), "id", id));
        } catch (IOException ex) { throw new BusinessException("organizations.logo_read_failed", "Не удалось прочитать логотип"); }
        if (old != null) files.deletePublic(old);
        return profile(id);
    }
    public void deleteLogo(UUID id) {
        String old = (String) require(id).get("logo_file_key");
        jdbc.update("update organizations set logo_file_key=null, updated_at=now() where id=:id", Map.of("id", id));
        if (old != null) files.deletePublic(old);
    }
    public byte[] ownerLogo(UUID id) {
        String key=(String) require(id).get("logo_file_key");
        if (key==null) throw notFound();
        return readLogo(key);
    }
    private byte[] readLogo(String key) {
        try (var input = files.getPublic(key)) { return input.readAllBytes(); }
        catch (IOException ex) { throw new BusinessException("organizations.logo_read_failed", "Не удалось прочитать логотип"); }
    }
    public byte[] logo(String slug) {
        var rows = jdbc.queryForList("select logo_file_key from organizations where public_slug=:slug and is_public=true", Map.of("slug", slug));
        if (rows.isEmpty() || rows.getFirst().get("logo_file_key") == null) throw notFound();
        return readLogo((String) rows.getFirst().get("logo_file_key"));
    }
    private BusinessException notFound() { return new BusinessException("organizations.not_found", "Организация не найдена"); }
}
