package com.acme.sportplatform.organizations.domain;

import com.acme.sportplatform.common.exception.BusinessException;

public class InvitationExpiredException extends BusinessException {
    public InvitationExpiredException() {
        super("organizations.invitation_expired", "Срок приглашения истёк");
    }
}
