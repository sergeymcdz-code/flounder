package com.acme.sportplatform.architecture;

import com.acme.sportplatform.SportPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(SportPlatformApplication.class).verify();
    }
}