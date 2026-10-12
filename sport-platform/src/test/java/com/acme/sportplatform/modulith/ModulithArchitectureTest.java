package com.acme.sportplatform.modulith;

import com.acme.sportplatform.SportPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    ApplicationModules modules = ApplicationModules.of(SportPlatformApplication.class);

    @Test
    void verifyArchitecture() {
        modules.verify();
    }
}