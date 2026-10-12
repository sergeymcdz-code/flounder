package com.acme.sportplatform.events;

import java.util.UUID;

/** Temporary boundary until T01 provides EventAccessService. */
public interface EventManagementAccess {
    void assertCanManage(UUID eventId);

    boolean canManage(UUID eventId);
}
