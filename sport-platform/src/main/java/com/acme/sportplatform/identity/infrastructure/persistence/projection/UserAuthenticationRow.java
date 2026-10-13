package com.acme.sportplatform.identity.infrastructure.persistence.projection;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;

public interface UserAuthenticationRow {
    UserEntity getUser();
    String getRoleCode();
}
