package com.acme.sportplatform.scheduling;
import java.util.Map;
import java.util.UUID;
public interface BibNumberLookup { Map<UUID, Integer> byEvent(UUID eventId); }
