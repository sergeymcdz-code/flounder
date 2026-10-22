package com.acme.sportplatform.organizations.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationPublicSlugsTest {
    @ParameterizedTest
    @CsvSource({"Клуб Волна,klub-volna", "Ёлки и щука,yolki-i-shchuka", "ADMIN,org-admin", "api,org-api", "new,org-new", "Я,org-ya", "!!!,org-"})
    void followsEventTransliterationAndAvoidsReservedSlugs(String name, String expected) {
        assertThat(OrganizationPublicSlugs.base(name)).isEqualTo(expected).matches("^[a-z0-9][a-z0-9-]{2,127}$");
    }
}
