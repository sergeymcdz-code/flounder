package com.acme.sportplatform.organizations.application;

import com.acme.sportplatform.common.exception.BusinessException;

public class LastOrganizationOwnerException extends BusinessException {
    public LastOrganizationOwnerException() {
        super("organizations.last_owner", "Нельзя удалить или понизить последнего владельца организации");
    }
}
