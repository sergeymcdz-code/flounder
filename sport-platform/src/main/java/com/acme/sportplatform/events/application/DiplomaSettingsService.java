package com.acme.sportplatform.events.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.DiplomaSettings;
import com.acme.sportplatform.events.DiplomaSettingsLookup;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DiplomaSettingsService implements DiplomaSettingsLookup {
    private final EventRepository events;
    private final ObjectMapper json;

    public DiplomaSettingsService(EventRepository events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    private ObjectNode settings(EventEntity event) {
        try {
            var node =
                    json.readTree(event.getSettingsJson() == null ? "{}" : event.getSettingsJson());
            if (node instanceof ObjectNode object) {
                return object;
            }
        } catch (JsonProcessingException ex) {
            throw invalidSettings();
        }
        throw invalidSettings();
    }

    public DiplomaSettings read(EventEntity event) {
        try {
            var node = settings(event).get("diploma");
            return node == null || node.isNull()
                    ? DiplomaSettings.defaults()
                    : json.treeToValue(node, DiplomaSettings.class).normalized();
        } catch (JsonProcessingException ex) {
            throw invalidSettings();
        }
    }

    @Override
    public DiplomaSettings get(UUID eventId) {
        return read(events.findById(eventId).orElseThrow());
    }

    public void apply(EventEntity event, DiplomaSettings patch) {
        if (patch == null) {
            return;
        }
        var current = read(event);
        var merged = patch.mergeWith(current);
        merged.validate();
        if (merged.backgroundFileKey() != null
                && !Objects.equals(merged.backgroundFileKey(), current.backgroundFileKey())) {
            throw new BusinessException(
                    "documents.invalid_template", "Загрузите фон через форму этого события");
        }
        persist(event, merged);
    }

    private void persist(EventEntity event, DiplomaSettings settings) {
        var node = settings(event);
        node.set("diploma", json.valueToTree(settings));
        event.setSettingsJson(node.toString());
    }

    @Override
    @Transactional
    public void setBackground(UUID eventId, String key) {
        var event = events.lockById(eventId).orElseThrow();
        if ("ARCHIVED".equals(event.getStatus())) {
            throw new BusinessException(
                    "events.archived", "Архивное событие доступно только для чтения");
        }
        var current = read(event);
        persist(
                event,
                new DiplomaSettings(
                        current.enabled(),
                        current.title(),
                        current.bodyTemplate(),
                        current.footer(),
                        current.showLogo(),
                        key));
    }

    private BusinessException invalidSettings() {
        return new BusinessException("documents.invalid_template", "Некорректные настройки грамот");
    }
}
