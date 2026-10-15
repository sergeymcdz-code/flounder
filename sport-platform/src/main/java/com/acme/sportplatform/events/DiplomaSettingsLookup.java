package com.acme.sportplatform.events;
import java.util.UUID;
public interface DiplomaSettingsLookup {
    DiplomaSettings get(UUID eventId);
    void setBackground(UUID eventId, String key);
}
