package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.*;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class DiplomaSettingsService implements DiplomaSettingsLookup {
    private final EventRepository events;
    private final ObjectMapper json;
    public DiplomaSettingsService(EventRepository events,ObjectMapper json) { this.events=events; this.json=json; }
    private ObjectNode settings(EventEntity event) {
        try { return (ObjectNode)json.readTree(event.getSettingsJson()==null?"{}":event.getSettingsJson()); }
        catch(Exception e) { throw new BusinessException("documents.invalid_template","Некорректные настройки грамот"); }
    }
    public DiplomaSettings read(EventEntity event) {
        try {
            var node=settings(event).get("diploma");
            return node==null || node.isNull()?DiplomaSettings.defaults():json.treeToValue(node,DiplomaSettings.class).normalized();
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("documents.invalid_template","Некорректные настройки грамот"); }
    }
    @Override public DiplomaSettings get(UUID eventId) { return read(events.findById(eventId).orElseThrow()); }
    public void apply(EventEntity event,DiplomaSettings value) {
        var d=value==null?DiplomaSettings.defaults():value.normalized(); d.validate();
        if (d.backgroundFileKey()!=null && !Objects.equals(d.backgroundFileKey(),read(event).backgroundFileKey()))
            throw new BusinessException("documents.invalid_template","Загрузите фон через форму этого события");
        persist(event,d);
    }
    private void persist(EventEntity event,DiplomaSettings d) {
        var node=settings(event); node.set("diploma",json.valueToTree(d)); event.setSettingsJson(node.toString());
    }
    @Override @Transactional public void setBackground(UUID eventId,String key) {
        var event=events.lockById(eventId).orElseThrow();
        if ("ARCHIVED".equals(event.getStatus())) throw new BusinessException("events.archived","Архивное событие доступно только для чтения");
        var d=read(event); persist(event,new DiplomaSettings(d.enabled(),d.title(),d.bodyTemplate(),d.footer(),d.showLogo(),key));
    }
}
